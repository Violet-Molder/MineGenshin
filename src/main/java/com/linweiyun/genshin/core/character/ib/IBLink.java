package com.linweiyun.genshin.core.character.ib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 与对方模组（IB）的唯一接缝 —— 「对方在不在」「对方那个角色长什么样」「对方的模型怎么拿到」。
 *
 * <h2>这一层只读、只调，不改对方</h2>
 * 本类只做三件事：查模组是否加载、读对方的 {@code ib_character/*} 定义文件、取对方的模型字节。
 * 对方的类、事件、资源包一律不碰。
 *
 * <h2>资源怎么定位</h2>
 * 对方是「同名查找」：本 MOD 的 {@code minegenshin:character/miyabi/…} 对应的就是对方的
 * {@code imaginary_branch:ib_character/miyabi/…}。对方的文件夹布局与本 MOD 不同
 * （对方把模型放 {@code geo/}、动画放 {@code animations/}、贴图放 {@code textures/character/}，
 * 每个角色自己的 {@code basics.json} 与 {@code renderer/render.json} 说明这三者的具体文件名），
 * 所以这里不猜路径，直接问对方那两份文件。
 *
 * <h2>加密模型</h2>
 * 对方把真模型 XOR 之后伪装成 {@code sounds/&lt;id&gt;_bgm.ogg}，再由它自己的资源包在运行时还原。
 * 那个资源包是<b>全局生效</b>的（注册在对方的事件总线上、Position.TOP），所以正常情况下
 * 这里用普通 {@link ResourceManager} 读 {@code geo/&lt;id&gt;.geo.json} 拿到的已经是解密后的真模型 ——
 * 这就是「能直接读就直接读」。读到的东西不是合法 geo JSON（说明那份还是加密字节或残骸）时，
 * 才转去调用对方的解密模块（见 {@link IBDecryptBridge}）。
 */
public final class IBLink {

    /** 对方的模组 id / 资源命名空间。 */
    public static final String MOD_ID = "imaginary_branch";

    /** 对方每个角色一个文件夹的根目录（对方叫 {@code ib_character}，不是本 MOD 的 {@code character}）。 */
    public static final String CHARACTER_ROOT = "ib_character";

    private static final String BASICS_FILE = "basics.json";
    private static final String RENDER_FILE = "renderer/render.json";
    private static final String RENDERER_DIR = "renderer/";
    private static final String ITEM_TEXTURE_FILE = "item/item.png";
    private static final String GEO_ROOT = "geo/";
    private static final String GEO_SUFFIX = ".geo.json";
    private static final String ANIMATION_SUFFIX = ".animation.json";

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    @Nullable
    private static Boolean loaded;

    private static volatile ResourceManager lastManager;
    private static volatile Map<String, IBRenderDefinition> definitions = Map.of();
    private static volatile Map<String, IBRenderDefinition> missing = Map.of();

    private IBLink() {
    }

    /** 对方模组在不在。整个联动（角色注册、动画登记、资源解析）都以它为准。 */
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached != null) {
            return cached;
        }

        ModList modList;
        try {
            modList = ModList.get();
        } catch (Throwable notReady) {
            // 模组列表还没建好：这次按「不在」处理，但别把结论缓存下来（下一次再问）
            LOGGER.warn("[IBLink] 模组列表尚未就绪，本次按「{} 不在」处理", MOD_ID);
            return false;
        }

        if (modList == null) {
            return false;
        }

        boolean present = modList.isLoaded(MOD_ID);
        loaded = present;

        if (present) {
            LOGGER.info("[IBLink] 检测到 {}，联动角色按对方资源解析", MOD_ID);
        }

        return present;
    }

    /** 对方命名空间下的资源路径。 */
    public static Identifier asset(String relativePath) {
        return Identifier.fromNamespaceAndPath(MOD_ID, relativePath);
    }

    /** 对方那个角色的定义；对方不在、或对方没这个角色时返回 null。 */
    @Nullable
    public static IBRenderDefinition definition(String ibCharacterId) {
        if (!isLoaded() || ibCharacterId == null || ibCharacterId.isEmpty()) {
            return null;
        }

        ResourceManager manager = clientResourceManager();
        if (manager == null) {
            return null;
        }

        if (manager != lastManager) {
            synchronized (IBLink.class) {
                if (manager != lastManager) {
                    definitions = Map.of();
                    missing = Map.of();
                    lastManager = manager;
                }
            }
        }

        IBRenderDefinition cached = definitions.get(ibCharacterId);
        if (cached != null) {
            return cached;
        }
        if (missing.containsKey(ibCharacterId)) {
            return null;
        }

        IBRenderDefinition read = read(manager, ibCharacterId);
        synchronized (IBLink.class) {
            if (read == null) {
                Map<String, IBRenderDefinition> extended = new LinkedHashMap<>(missing);
                extended.put(ibCharacterId, new IBRenderDefinition(ibCharacterId, null, null, null, null, 1.0F, Map.of()));
                missing = Map.copyOf(extended);
            } else {
                Map<String, IBRenderDefinition> extended = new LinkedHashMap<>(definitions);
                extended.put(ibCharacterId, read);
                definitions = Map.copyOf(extended);
            }
        }

        return read;
    }

    /**
     * 取对方那个角色的模型字节，喂给 GeckoLib 烘培。
     *
     * @param ibCharacterId 对方侧角色 id
     * @param direct        普通资源读取拿到的那份字节（可能已经是解密后的真模型，也可能是加密字节 / 残骸）；可为 null
     * @return 可解析的模型 JSON 字节；两条路都拿不到时返回 null
     */
    @Nullable
    public static byte[] modelBytes(String ibCharacterId, @Nullable byte[] direct) {
        if (looksLikeGeo(direct)) {
            return direct;
        }

        IBRenderDefinition definition = definition(ibCharacterId);
        byte[] decrypted = definition == null ? null : IBDecryptBridge.modelBytes(definition.model());
        if (looksLikeGeo(decrypted)) {
            LOGGER.info("[IBLink] 角色 '{}' 的模型不是明文，已改用对方解密模块取回", ibCharacterId);
            return decrypted;
        }

        LOGGER.warn("[IBLink] 角色 '{}' 的模型既不是明文、对方解密模块也没给出可用结果，本次跳过", ibCharacterId);
        return null;
    }

    /** 这段字节是不是可用的 geo JSON（有没有 {@code geometry} 段）。 */
    public static boolean looksLikeGeo(@Nullable byte[] bytes) {
        JsonObject json = parseObject(bytes);
        if (json == null) {
            return false;
        }
        return json.has("minecraft:geometry") || json.has("geometry");
    }

    // ==================== 内部 ====================

    @Nullable
    private static IBRenderDefinition read(ResourceManager manager, String ibCharacterId) {
        String folder = CHARACTER_ROOT + "/" + ibCharacterId + "/";
        JsonObject basics = readJson(manager, asset(folder + BASICS_FILE));

        if (basics == null) {
            LOGGER.warn("[IBLink] 对方没有角色 '{}'（读不到 {}），本角色按缺资源处理", ibCharacterId, folder + BASICS_FILE);
            return null;
        }

        JsonObject render = readJson(manager, asset(folder + RENDER_FILE));

        if (render == null) {
            LOGGER.warn("[IBLink] 对方角色 '{}' 缺少 {}，本角色按缺资源处理", ibCharacterId, folder + RENDER_FILE);
            return null;
        }

        Identifier model = resolveInRenderer(folder, string(render, "Model"), GEO_SUFFIX);
        Identifier animation = resolveInRenderer(folder, string(render, "Animation"), ANIMATION_SUFFIX);
        Identifier texture = resolve(string(render, "Texture"));

        if (model == null || animation == null || texture == null) {
            LOGGER.warn("[IBLink] 对方角色 '{}' 的 render.json 里 Model / Animation / Texture 不全，本角色按缺资源处理",
                    ibCharacterId);
            return null;
        }

        Identifier itemTexture = resolve(string(basics, "ItemTexture"));
        if (itemTexture == null) {
            itemTexture = asset(folder + ITEM_TEXTURE_FILE);
        }

        String id = string(basics, "ID");
        return new IBRenderDefinition(
                id == null || id.isBlank() ? ibCharacterId : id,
                model,
                animation,
                texture,
                itemTexture,
                basics.has("BodyScale") && basics.get("BodyScale").isJsonPrimitive()
                        ? basics.get("BodyScale").getAsFloat()
                        : 1.0F,
                readLang(basics));
    }

    /**
     * {@code render.json} 里的 Model / Animation。
     *
     * <p>对方 26.2 的写法是<b>文件名</b>（{@code "miyabi"}），文件与 {@code render.json} 同在
     * {@code ib_character/<id>/renderer/} 下；也兼容写成带目录的完整相对路径或 {@code namespace:path}。
     */
    @Nullable
    private static Identifier resolveInRenderer(String characterFolder, @Nullable String rawName, String extension) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        String name = rawName.trim();
        if (name.indexOf(':') >= 0) {
            return Identifier.tryParse(name);
        }
        if (name.indexOf('/') >= 0) {
            return asset(name);
        }

        return asset(characterFolder + RENDERER_DIR + name + (name.endsWith(extension) ? "" : extension));
    }

    /** 对方写的是相对自己命名空间根目录的路径，也可能写成 {@code namespace:path}。 */
    @Nullable
    private static Identifier resolve(@Nullable String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String trimmed = rawPath.trim();
        return trimmed.indexOf(':') >= 0
                ? Identifier.tryParse(trimmed)
                : asset(trimmed);
    }

    private static Map<String, String> readLang(JsonObject basics) {
        if (!basics.has("lang") || !basics.get("lang").isJsonObject()) {
            return Map.of();
        }

        Map<String, String> lang = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : basics.getAsJsonObject("lang").entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                lang.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return Map.copyOf(lang);
    }

    @Nullable
    private static JsonObject readJson(ResourceManager manager, Identifier location) {
        Optional<Resource> found = manager.getResource(location);
        if (found.isEmpty()) {
            return null;
        }

        try (Reader reader = found.get().openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            LOGGER.warn("[IBLink] 读取 {} 失败：{}", location, e.toString());
            return null;
        }
    }

    @Nullable
    private static JsonObject parseObject(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception notJson) {
            return null;
        }
    }

    @Nullable
    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    @Nullable
    private static ResourceManager clientResourceManager() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getResourceManager();
    }

    /**
     * 给扫描用：把模型文件路径里的角色 id 取出来。
     *
     * <p>两套布局都要认：对方 26.2 的 {@code ib_character/<id>/renderer/<文件名>.geo.json}，
     * 以及更早的顶层 {@code geo/<id>.geo.json}。
     */
    @Nullable
    public static String trackIdOf(Identifier location) {
        String path = location.getPath();

        String characterPrefix = CHARACTER_ROOT + "/";
        if (path.startsWith(characterPrefix) && path.contains("/" + RENDERER_DIR) && path.endsWith(GEO_SUFFIX)) {
            String rest = path.substring(characterPrefix.length());
            int slash = rest.indexOf('/');
            return slash <= 0 ? null : rest.substring(0, slash);
        }

        if (!path.startsWith(GEO_ROOT) || !path.endsWith(GEO_SUFFIX)) {
            return null;
        }

        int slash = path.lastIndexOf('/');
        return path.substring(slash + 1, path.length() - GEO_SUFFIX.length());
    }

    /** 读一份对方资源的原始字节；读不到返回 null。 */
    @Nullable
    public static byte[] readBytes(@Nullable Resource resource) {
        if (resource == null) {
            return null;
        }

        try (InputStream in = resource.open()) {
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
