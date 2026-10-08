package com.linweiyun.genshin.asset.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.core.character.ib.IBLink;
import com.linweiyun.genshin.core.character.ib.IBRenderDefinition;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色资源的「来源表」—— 每个角色可以逐项声明自己的模型 / 动画 / 贴图 / 头像 / 立绘从哪里读。
 *
 * <h2>文件在哪、长什么样</h2>
 * <pre>
 * assets/minegenshin/character/miyabi/resources.json
 * </pre>
 * <pre>
 * {
 *   "ib_character": "miyabi",              // 联动角色在对方模组里的 id；省略 = 与本角色同名
 *   "sources": {
 *     "model":      "@ib",                 // 从对方同名角色的 render.json 里取模型
 *     "animation":  "@ib",
 *     "texture":    "@ib",
 *     "avatar":     "@ib-item",            // 对方那个角色的信物图标，当头像是现成的
 *     "avatar_hud": "@ib-item",
 *     "splash":     "character/miyabi/textures/pose_prepare.png",   // 立绘归我们自己
 *     "splash_ready": "character/miyabi/textures/pose_already.png"
 *   },
 *   "extra_animations": ["imaginary_branch:animations/miyabi_final.animation.json"],
 *   "bones": { "body_root": "bone2", "weapon": ["blade_right"] }
 * }
 * </pre>
 *
 * <h2>每个槽位可以填什么</h2>
 * <ul>
 *   <li>{@code "@ib"} —— 问对方（IB）：「你这个同名的角色，这个槽位用的是哪个文件」
 *       （对方在自己的 {@code ib_character/&lt;id&gt;/renderer/render.json} 里写着）；
 *       模型槽位就是「能直接读就直接读、读不到就调对方解密模块」那条路；</li>
 *   <li>{@code "@ib-item"} —— 对方那个角色的信物图标（{@code ib_character/&lt;id&gt;/item/item.png}），
 *       头像用得上；</li>
 *   <li>{@code "namespace:path"} —— 直接点名（例如 {@code imaginary_branch:geo/miyabi.geo.json}）；</li>
 *   <li>{@code "character/x/x.geo.json"} —— 本 MOD 里的相对路径；</li>
 *   <li>整个槽位不写 —— 走本 MOD 的默认约定，和没有这个文件时的行为一致。</li>
 * </ul>
 *
 * <h2>拿不到时怎么办</h2>
 * 解析不出来（对方不在、对方没有这个角色、路径写错）一律退回本 MOD 的默认约定，
 * 不会抛异常、也不会把别的角色的资源串过来。
 */
public final class CharacterResourceSources {

    /** 每个角色文件夹里的来源表文件名。 */
    public static final String SOURCE_FILE = "resources.json";

    private static final String SOURCES_KEY = "sources";
    private static final String BONES_KEY = "bones";
    private static final String IB_CHARACTER_KEY = "ib_character";
    private static final String EXTRA_ANIMATIONS_KEY = "extra_animations";
    private static final String SCALE_KEY = "scale";

    /** 槽位值：问对方要这个槽位的文件。 */
    private static final String FROM_IB = "@ib";

    /** 槽位值：对方的角色信物图标。 */
    private static final String FROM_IB_ITEM = "@ib-item";

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    private static volatile ResourceManager lastManager;
    private static volatile Map<String, JsonObject> declared = Map.of();
    private static volatile Map<String, CharacterBoneSpec> boneSpecs = Map.of();

    private CharacterResourceSources() {
    }

    /** 一个解析出来的来源。 */
    public record Source(String namespace, String path) {

        /** 供 GUI 直接喂给贴图加载器的字符串形式。 */
        public String asString() {
            return this.namespace + ":" + this.path;
        }
    }

    /**
     * 本角色在对方模组里的 id —— 来源表里写了 {@code ib_character} 就用它，否则与本角色同名。
     *
     * <p>「在对方项目里找和自己同名的角色」的默认形态。
     */
    public static String ibCharacterId(String characterId) {
        JsonObject json = declared(characterId);
        if (json != null && json.has(IB_CHARACTER_KEY) && !json.get(IB_CHARACTER_KEY).isJsonNull()) {
            String id = json.get(IB_CHARACTER_KEY).getAsString().trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        return characterId;
    }

    /** 有没有给这个角色写来源表。 */
    public static boolean declaredFor(String characterId) {
        JsonObject json = declared(characterId);
        return json != null && json.size() > 0;
    }

    /**
     * 解析某个槽位的来源。
     *
     * @return 解析结果；这个槽位没声明、或声明的对方资源拿不到时返回 null（由调用方走默认约定）
     */
    @Nullable
    public static Source source(String characterId, CharacterResourceSlot slot) {
        JsonObject json = declared(characterId);
        if (json == null || !json.has(SOURCES_KEY) || !json.get(SOURCES_KEY).isJsonObject()) {
            return null;
        }

        JsonObject sources = json.getAsJsonObject(SOURCES_KEY);
        if (!sources.has(slot.key()) || sources.get(slot.key()).isJsonNull()) {
            return null;
        }

        String raw = sources.get(slot.key()).getAsString().trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (FROM_IB.equalsIgnoreCase(raw) || FROM_IB_ITEM.equalsIgnoreCase(raw)) {
            return fromIb(characterId, slot, FROM_IB_ITEM.equalsIgnoreCase(raw));
        }

        return parse(raw);
    }

    /**
     * 槽位 → {@code namespace:path} 字符串，没声明时给本 MOD 默认路径。
     *
     * @param fallbackRelative 本 MOD 里的相对路径，例如 {@code character/miyabi/textures/avatar.png}
     */
    public static String pathOf(String characterId, CharacterResourceSlot slot, String fallbackRelative) {
        Source resolved = source(characterId, slot);
        return resolved == null ? GenshinAssets.MOD_ID + ":" + fallbackRelative : resolved.asString();
    }

    /**
     * 槽位 → 可喂给 GeckoLib / 资源管理器的 {@link ResourceLocation}。
     *
     * <p>模型与动画会按各自后缀剥掉尾巴（{@code .geo.json} / {@code .animation.json}），
     * 与 GeckoLib 的缓存键一致；贴图原样使用。
     */
    public static ResourceLocation identifier(String characterId, CharacterResourceSlot slot, ResourceLocation fallback) {
        Source resolved = source(characterId, slot);
        if (resolved == null) {
            return fallback;
        }

        String path = stripSuffix(resolved.path(), slot);
        return path.isEmpty() ? fallback : ResourceLocation.fromNamespaceAndPath(resolved.namespace(), path);
    }

    /** 效果额外动画文件；没声明时返回传入的兜底值。 */
    public static List<String> extraAnimations(String characterId, List<String> fallback) {
        JsonObject json = declared(characterId);
        if (json == null || !json.has(EXTRA_ANIMATIONS_KEY) || !json.get(EXTRA_ANIMATIONS_KEY).isJsonArray()) {
            return fallback;
        }

        JsonArray array = json.getAsJsonArray(EXTRA_ANIMATIONS_KEY);
        List<String> paths = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive()) {
                continue;
            }
            Source parsed = parse(entry.getAsString());
            if (parsed != null) {
                paths.add(parsed.asString());
            }
        }

        return paths.isEmpty() ? fallback : List.copyOf(paths);
    }

    // ==================== 界面贴图（直接喂 SpriteTexture 的字符串） ====================

    /** 角色选择界面的头像。联动角色通常指到对方那个角色的信物图标。 */
    public static String avatar(String textureId) {
        return pathOf(textureId, CharacterResourceSlot.AVATAR, textureFolder(textureId) + "avatar.png");
    }

    /** HUD / 装备页 / 背包里的小头像。 */
    public static String avatarHud(String textureId) {
        return pathOf(textureId, CharacterResourceSlot.AVATAR_HUD, textureFolder(textureId) + "avatar_hud.png");
    }

    /** 立绘（准备动作）。 */
    public static String splash(String textureId) {
        return firstExisting(
                pathOf(textureId, CharacterResourceSlot.SPLASH, textureFolder(textureId) + "pose_prepare.png"),
                avatarHud(textureId));
    }

    /** 立绘（已登场动作）。 */
    public static String splashReady(String textureId) {
        return firstExisting(
                pathOf(textureId, CharacterResourceSlot.SPLASH_READY, textureFolder(textureId) + "pose_already.png"),
                avatarHud(textureId));
    }

    private static String textureFolder(String textureId) {
        return GenshinAssets.CHARACTER_ROOT + "/" + textureId + "/textures/";
    }

    /**
     * 逐个看哪张贴图真的在资源包里，返回第一个存在的。
     *
     * <p>立绘是「只有本 MOD 才有的资源」，联动角色刚接进来时常常还没画：
     * 这时候退到小头像，至少界面上有个东西，不至于空一块。
     */
    private static String firstExisting(String preferred, String fallback) {
        if (exists(preferred)) {
            return preferred;
        }
        return exists(fallback) ? fallback : preferred;
    }

    private static boolean exists(String namespacedPath) {
        Source source = parse(namespacedPath);
        Minecraft minecraft = FMLEnvironment.dist == Dist.CLIENT ? Minecraft.getInstance() : null;
        ResourceManager manager = minecraft == null ? null : minecraft.getResourceManager();
        if (source == null || manager == null) {
            return false;
        }

        return manager.getResource(ResourceLocation.fromNamespaceAndPath(source.namespace(), source.path())).isPresent();
    }

    /**
     * 模型整体缩放：来源表里的 {@code scale} 优先，其次对方 {@code basics.json} 里声明的 {@code BodyScale}。
     *
     * <p>对方的模型不是照本 MOD 的体型做的，缩放常常要按角色调，所以放在这一层读。
     */
    public static float scale(String characterId, float fallback) {
        JsonObject json = declared(characterId);
        if (json != null && json.has(SCALE_KEY) && json.get(SCALE_KEY).isJsonPrimitive()) {
            return json.get(SCALE_KEY).getAsFloat();
        }

        if (!usesIb(json)) {
            return fallback;
        }

        IBRenderDefinition definition = IBLink.definition(ibCharacterId(characterId));
        return definition != null && definition.bodyScale() > 0 ? definition.bodyScale() : fallback;
    }

    /** 这个角色的骨骼约定（本体根骨骼 / 武器骨骼 / 额外隐藏）。 */
    public static CharacterBoneSpec bones(String characterId) {
        ResourceManager manager = clientResourceManager();
        if (manager == null) {
            return CharacterBoneSpec.DEFAULT;
        }

        refresh(manager);

        CharacterBoneSpec cached = boneSpecs.get(characterId);
        if (cached != null) {
            return cached;
        }

        JsonObject json = declared(characterId);
        CharacterBoneSpec spec = json != null && json.has(BONES_KEY) && json.get(BONES_KEY).isJsonObject()
                ? CharacterBoneSpec.parse(json.getAsJsonObject(BONES_KEY))
                : CharacterBoneSpec.DEFAULT;

        Map<String, CharacterBoneSpec> extended = new LinkedHashMap<>(boneSpecs);
        extended.put(characterId, spec);
        boneSpecs = Map.copyOf(extended);
        return spec;
    }

    // ==================== 内部 ====================

    /** 来源表里有没有用到 {@code @ib} 系列取值 —— 没有的话就不去问对方，免得白发一次「对方没这个角色」的日志。 */
    private static boolean usesIb(@Nullable JsonObject json) {
        if (json == null || !json.has(SOURCES_KEY) || !json.get(SOURCES_KEY).isJsonObject()) {
            return false;
        }

        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(SOURCES_KEY).entrySet()) {
            if (entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsString().trim().toLowerCase().startsWith(FROM_IB)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Source fromIb(String characterId, CharacterResourceSlot slot, boolean itemIcon) {
        IBRenderDefinition definition = IBLink.definition(ibCharacterId(characterId));
        if (definition == null) {
            return null;
        }

        ResourceLocation resolved = switch (slot) {
            case MODEL -> definition.model();
            case ANIMATION -> definition.animation();
            case TEXTURE -> definition.texture();
            case AVATAR, AVATAR_HUD -> definition.itemTexture();
            case SPLASH, SPLASH_READY -> null;
        };

        if (itemIcon && slot != CharacterResourceSlot.AVATAR && slot != CharacterResourceSlot.AVATAR_HUD) {
            return null;
        }

        return resolved == null ? null : new Source(resolved.getNamespace(), resolved.getPath());
    }

    /** {@code namespace:path} 或纯相对路径 → 来源。空串返回 null。 */
    @Nullable
    private static Source parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        int colon = text.indexOf(':');
        if (colon <= 0) {
            return new Source(GenshinAssets.MOD_ID, text);
        }

        String namespace = text.substring(0, colon).trim();
        String path = text.substring(colon + 1).trim();
        return namespace.isEmpty() || path.isEmpty() ? null : new Source(namespace, path);
    }

    private static String stripSuffix(String path, CharacterResourceSlot slot) {
        if (slot == CharacterResourceSlot.MODEL) {
            return path.endsWith(".geo.json") ? path.substring(0, path.length() - ".geo.json".length()) : path;
        }
        if (slot == CharacterResourceSlot.ANIMATION) {
            for (String suffix : new String[]{".animation.json", ".animations.json", ".json"}) {
                if (path.endsWith(suffix)) {
                    return path.substring(0, path.length() - suffix.length());
                }
            }
        }
        return path;
    }

    @Nullable
    private static JsonObject declared(String characterId) {
        if (characterId == null || characterId.isEmpty()) {
            return null;
        }

        ResourceManager manager = clientResourceManager();
        if (manager == null) {
            return null;
        }

        refresh(manager);
        return load(manager, characterId);
    }

    /**
     * 资源管理器换了一茬（游戏启动 / F3+T 重载）就把读到的来源表全丢掉 ——
     * 文件是可能被整合包改的，缓存必须跟着资源重载走。
     */
    private static void refresh(ResourceManager manager) {
        if (manager == lastManager) {
            return;
        }

        synchronized (CharacterResourceSources.class) {
            if (manager != lastManager) {
                declared = Map.of();
                boneSpecs = Map.of();
                lastManager = manager;
            }
        }
    }

    private static JsonObject read(ResourceManager manager, String characterId) {
        ResourceLocation location = GenshinAssets.id(
                GenshinAssets.CHARACTER_ROOT + "/" + characterId + "/" + SOURCE_FILE);

        Optional<Resource> found = manager.getResource(location);
        if (found.isEmpty()) {
            return new JsonObject();
        }

        try (Reader reader = found.get().openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                LOGGER.warn("[CharacterResourceSources] {} 不是对象，按未声明处理", location);
                return new JsonObject();
            }
            return parsed.getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("[CharacterResourceSources] 读取 {} 失败，按未声明处理：{}", location, e.toString());
            return new JsonObject();
        }
    }

    /** 取（并缓存）某个角色的来源表；没写这个文件时缓存成空对象。 */
    private static JsonObject load(ResourceManager manager, String characterId) {
        synchronized (CharacterResourceSources.class) {
            JsonObject cached = declared.get(characterId);
            if (cached != null) {
                return cached;
            }

            JsonObject read = read(manager, characterId);
            Map<String, JsonObject> extended = new LinkedHashMap<>(declared);
            extended.put(characterId, read);
            declared = Map.copyOf(extended);
            return read;
        }
    }

    @Nullable
    private static ResourceManager clientResourceManager() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        ResourceManager manager = minecraft.getResourceManager();
        return manager == null ? null : manager;
    }
}
