package com.linweiyun.genshin.core.character.ib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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

public final class IBLink {
    public static final String MOD_ID = "imaginary_branch";

    public static final String CHARACTER_ROOT = "ib_character";

    private static final String BASICS_FILE = "basics.json";
    private static final String RENDER_FILE = "renderer/render.json";
    private static final String ITEM_TEXTURE_FILE = "item/item.png";
    private static final String GEO_ROOT = "geo/";
    private static final String GEO_SUFFIX = ".geo.json";

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    @Nullable
    private static Boolean loaded;

    private static volatile ResourceManager lastManager;
    private static volatile Map<String, IBRenderDefinition> definitions = Map.of();
    private static volatile Map<String, IBRenderDefinition> missing = Map.of();

    private IBLink() {
    }
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached != null) {
            return cached;
        }

        ModList modList;
        try {
            modList = ModList.get();
        } catch (Throwable notReady) {
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

    public static ResourceLocation asset(String relativePath) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, relativePath);
    }
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

    @Nullable
    public static byte[] modelBytes(String ibCharacterId, @Nullable byte[] direct) {
        if (looksLikeGeo(direct)) {
            return direct;
        }

        byte[] decrypted = IBDecryptBridge.modelBytes(ibCharacterId);
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

        ResourceLocation model = resolve(string(render, "Model"));
        ResourceLocation animation = resolve(string(render, "Animation"));
        ResourceLocation texture = resolve(string(render, "Texture"));

        if (model == null || animation == null || texture == null) {
            LOGGER.warn("[IBLink] 对方角色 '{}' 的 render.json 里 Model / Animation / Texture 不全，本角色按缺资源处理",
                    ibCharacterId);
            return null;
        }

        ResourceLocation itemTexture = resolve(string(basics, "ItemTexture"));
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

    @Nullable
    private static ResourceLocation resolve(@Nullable String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String trimmed = rawPath.trim();
        return trimmed.indexOf(':') >= 0
                ? ResourceLocation.tryParse(trimmed)
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
    private static JsonObject readJson(ResourceManager manager, ResourceLocation location) {
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
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getResourceManager();
    }

    @Nullable
    public static String trackIdOf(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(GEO_ROOT) || !path.endsWith(GEO_SUFFIX)) {
            return null;
        }

        int slash = path.lastIndexOf('/');
        return path.substring(slash + 1, path.length() - GEO_SUFFIX.length());
    }

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
