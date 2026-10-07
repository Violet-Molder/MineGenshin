package com.linweiyun.genshin.client.sound;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色音效 —— 把 {@code character/<角色id>/sounds/} 接进原版声音系统。
 *
 * <h2>作者看到的布局</h2>
 * <pre>
 * assets/minegenshin/character/vesna/
 * ├── vesna.animation.json
 * ├── vesna.png
 * ├── sounds.json          ← 事件定义（本文档下面那个格式）
 * └── sounds/
 *         attack_1.ogg
 *         attack_2.ogg
 *         skill.ogg
 * </pre>
 *
 * <h2>为什么需要这么一层</h2>
 * 原版有两个写死的约定，缺一不可：
 * <ol>
 *   <li>事件定义只读 {@code assets/<命名空间>/sounds.json} —— <b>一个命名空间只有一个</b>，
 *       没法一个角色一个文件；</li>
 *   <li>{@code Sound#getPath()} 固定拼 {@code sounds/<名字>.ogg}，
 *       所以声音文件只能放 {@code assets/<命名空间>/sounds/} 下。</li>
 * </ol>
 * 于是这里做两件事：<b>自己扫角色目录</b>读事件定义（绕开第 1 条），
 * 并且用 {@link CharacterSound} 覆写 {@code getPath()}（绕开第 2 条）。
 * 两者都只影响本 MOD，不碰原版行为，也不需要动整合包里别人的音效。
 *
 * <h2>定义格式</h2>
 * 和原版 {@code sounds.json} 完全一致，只是 {@code name} 相对<b>本角色的 sounds 目录</b>：
 * <pre>
 * {
 *   "attack_1": { "subtitle": "character.vesna.attack_1", "sounds": ["attack_1"] },
 *   "attack_1_alt": {
 *     "sounds": [
 *       { "name": "attack_1", "pitch": 0.95, "weight": 3 },
 *       { "name": "attack_2", "weight": 1 }
 *     ]
 *   }
 * }
 * </pre>
 * <ul>
 *   <li>key → 事件名：短名自动带角色前缀（{@code attack_1} → {@code minegenshin:vesna_attack_1}），
 *       已经带了前缀或用 {@code 命名空间:名字} 写的就照用，见
 *       {@link GenshinAssets#characterSoundEvent(String, String)}。</li>
 *   <li>{@code name} → 文件：{@code attack_1} = {@code character/vesna/sounds/attack_1.ogg}，
 *       带 {@code :} 的写法按「命名空间根目录下的路径」理解
 *       （{@code minegenshin:character/common/sounds/whoosh} = 共用音效）；
 *       <b>原版事件名不能这样引用</b>（那是事件不是文件）。</li>
 *   <li>{@code volume / pitch / weight / stream / preload} 和原版同义。</li>
 *   <li>文件不存在就跳过那条并打日志 —— 只写定义、ogg 还没做进来时不会炸，
 *       补上文件重进一次（或 F3+T）就响。</li>
 * </ul>
 *
 * <h2>什么时候被调用</h2>
 * NeoForge 的 {@code SoundEngineLoadEvent}（{@link #onSoundEngineLoad}）。
 * 它在 {@code SoundManager#apply} 的最后一行 {@code soundEngine.reload()} 里触发，
 * 也就是<b>原版刚 clear + 填好自己的两张表之后</b> —— 我们只管往上加。
 *
 * <p><b>为什么不是注入 {@code SoundManager#apply} 的 TAIL</b>：那个方法的第一个参数
 * {@code SoundManager.Preparations} 是 protected 嵌套类，handler 写不出来，
 * 而 Mixin 要求签名逐参数完全一致（{@code Object} 顶替会启动崩溃）。
 * 改用事件后，mixin 那边只剩「把两张表暴露出来」这一件事，见
 * {@link CharacterSoundSink}。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class CharacterSounds {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
    private static final Gson GSON = new Gson();

    private CharacterSounds() {
    }

    /**
     * 声音引擎（重）加载完成 → 把角色音效补进原版事件表与文件表。
     *
     * <p>这条事件在「引擎构造」和「每次资源重载」时都会来一次，
     * 两次都做同样的事：扫角色目录 → 补表。表本身会被原版重载清空，
     * 所以每次都重新扫，不需要自己维护「上一轮加了哪些」。
     */
    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resources = minecraft.getResourceManager();
        SoundManager soundManager = minecraft.getSoundManager();

        if (resources == null || !(soundManager instanceof CharacterSoundSink sink)) {
            return;
        }

        Loaded loaded = scan(resources);
        if (loaded.isEmpty()) {
            return;
        }

        sink.genshin$soundEvents().putAll(loaded.events());
        sink.genshin$soundFiles().putAll(loaded.files());
    }

    /** 扫出来的结果：事件表 + 声音文件表（都要并进原版那两张表里）。 */
    public record Loaded(Map<ResourceLocation, WeighedSoundEvents> events, Map<ResourceLocation, Resource> files) {

        public static final Loaded EMPTY = new Loaded(Map.of(), Map.of());

        public boolean isEmpty() {
            return events.isEmpty() && files.isEmpty();
        }
    }

    /**
     * 扫一遍所有 {@code character/<角色id>/sounds/}，产出可以并进原版注册表的条目。
     *
     * <p>两条来源，<b>定义文件优先</b>：
     * <ol>
     *   <li><b>自动发现</b>：目录里每一个 {@code *.ogg} 直接登记成事件，
     *       事件名 = {@code <角色id>_<文件名>}（{@code character/vesna/sounds/attack_1.ogg}
     *       → {@code minegenshin:vesna_attack_1}）。<b>放进去就能用，不需要写任何定义文件。</b></li>
     *   <li>{@code character/<角色id>/sounds.json}（可选，进阶）：补字幕、多音色、音量/音高、
     *       权重这些自动发现表达不了的东西；同名事件会<b>覆盖</b>自动发现的那条。</li>
     * </ol>
     *
     * @param resources 当前资源管理器（重载时那一个）
     * @return 什么都没扫到时返回 {@link Loaded#EMPTY}
     */
    public static Loaded scan(@Nullable ResourceManager resources) {
        if (resources == null) {
            return Loaded.EMPTY;
        }

        Map<ResourceLocation, WeighedSoundEvents> events = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> files = new LinkedHashMap<>();

        int auto = scanSoundFiles(resources, events, files);
        int defined = scanDefinitions(resources, events, files);

        if (events.isEmpty() && files.isEmpty()) {
            return Loaded.EMPTY;
        }

        LOGGER.info("[MineGenshin] 角色音效：自动发现 {} 个 ogg，{} 个角色写了 sounds.json，共 {} 条事件",
                auto, defined, events.size());
        return new Loaded(events, files);
    }

    // ==================== 来源 1：目录里的 ogg ====================

    /** @return 登记了几个文件 */
    private static int scanSoundFiles(ResourceManager resources,
                                      Map<ResourceLocation, WeighedSoundEvents> events,
                                      Map<ResourceLocation, Resource> files) {
        Map<ResourceLocation, Resource> found = resources.listResources(
                GenshinAssets.CHARACTER_ROOT,
                id -> GenshinAssets.MOD_ID.equals(id.getNamespace())
                        && id.getPath().endsWith(".ogg")
                        && GenshinAssets.isCharacterSound(id));

        int count = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String characterId = characterIdOfSoundFile(entry.getKey());
            String fileName = fileNameOf(entry.getKey());
            if (characterId == null || fileName == null) {
                continue;
            }

            // character/vesna/sounds/attack_1.ogg → 声音位置 minegenshin:character/vesna/sounds/attack_1
            //                                   → 事件 minegenshin:vesna_attack_1
            ResourceLocation location = GenshinAssets.characterSound(characterId, fileName);
            ResourceLocation eventId = GenshinAssets.characterSoundEvent(characterId, fileName);
            if (location == null || eventId == null) {
                continue;
            }

            WeighedSoundEvents event = events.computeIfAbsent(eventId, id -> new WeighedSoundEvents(id, null));
            event.addSound(new CharacterSound(location, entry.getKey(), 1.0F, 1.0F, 1, false, false));
            files.put(entry.getKey(), entry.getValue());
            count++;
        }
        return count;
    }

    /**
     * 从音效文件路径反查角色 id。
     *
     * @param soundFile 形如 {@code minegenshin:character/vesna/sounds/attack_1.ogg}
     */
    @Nullable
    private static String characterIdOfSoundFile(ResourceLocation soundFile) {
        String path = soundFile.getPath();
        String prefix = GenshinAssets.CHARACTER_ROOT + "/";
        String marker = "/" + GenshinAssets.SOUNDS_DIR + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        int markerIndex = path.indexOf(marker, prefix.length());
        if (markerIndex < 0) {
            return null;
        }
        String id = path.substring(prefix.length(), markerIndex);
        return (id.isEmpty() || id.indexOf('/') >= 0) ? null : id;
    }

    /** 取文件名（不含目录与 {@code .ogg}）。 */
    @Nullable
    private static String fileNameOf(ResourceLocation soundFile) {
        String path = soundFile.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        if (name.endsWith(".ogg")) {
            name = name.substring(0, name.length() - ".ogg".length());
        }
        return name.isEmpty() ? null : name;
    }

    // ==================== 来源 2：sounds.json（可选） ====================

    /** @return 有几个角色写了定义文件 */
    private static int scanDefinitions(ResourceManager resources,
                                       Map<ResourceLocation, WeighedSoundEvents> events,
                                       Map<ResourceLocation, Resource> files) {
        Map<ResourceLocation, Resource> definitions = resources.listResources(
                GenshinAssets.CHARACTER_ROOT,
                id -> GenshinAssets.MOD_ID.equals(id.getNamespace())
                        && id.getPath().endsWith("/" + GenshinAssets.SOUND_DEFINITION_FILE));

        int count = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : definitions.entrySet()) {
            String characterId = GenshinAssets.characterIdOfSoundDefinition(entry.getKey());
            if (characterId == null) {
                continue;
            }
            readDefinition(characterId, entry.getValue(), resources, events, files);
            count++;
        }
        return count;
    }

    // ==================== 解析 ====================

    private static void readDefinition(String characterId, Resource definition, ResourceManager resources,
                                       Map<ResourceLocation, WeighedSoundEvents> events,
                                       Map<ResourceLocation, Resource> files) {
        try (Reader reader = definition.openAsReader()) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                LOGGER.warn("[MineGenshin] 角色 '{}' 的音效定义不是 JSON 对象，已跳过", characterId);
                return;
            }

            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                readEvent(characterId, entry.getKey(), entry.getValue(), resources, events, files);
            }
        } catch (Exception e) {
            LOGGER.warn("[MineGenshin] 角色 '{}' 的音效定义读取失败：{}", characterId, e.toString());
        }
    }

    private static void readEvent(String characterId, String key, JsonElement value,
                                  ResourceManager resources,
                                  Map<ResourceLocation, WeighedSoundEvents> events,
                                  Map<ResourceLocation, Resource> files) {
        if (value == null || !value.isJsonObject()) {
            return;
        }
        JsonObject object = value.getAsJsonObject();

        ResourceLocation eventId = GenshinAssets.characterSoundEvent(characterId, key);
        if (eventId == null) {
            return;
        }

        JsonElement soundsElement = object.get("sounds");
        if (soundsElement == null || !soundsElement.isJsonArray()) {
            LOGGER.warn("[MineGenshin] 角色 '{}' 的音效 '{}' 没有 sounds 数组，已跳过", characterId, key);
            return;
        }

        String subtitle = optString(object, "subtitle", null);
        WeighedSoundEvents event = new WeighedSoundEvents(eventId, subtitle);
        JsonArray array = soundsElement.getAsJsonArray();

        for (JsonElement element : array) {
            Sound sound = readSound(characterId, element, eventId, resources, files);
            if (sound == null) {
                continue;
            }
            event.addSound(sound);
        }

        if (event.getWeight() > 0) {
            // 覆盖自动发现的那条：写了定义就听定义的
            events.put(eventId, event);
        }
    }

    @Nullable
    private static Sound readSound(String characterId, JsonElement element, ResourceLocation eventId,
                                   ResourceManager resources, Map<ResourceLocation, Resource> files) {
        String name;
        float volume = 1.0F;
        float pitch = 1.0F;
        int weight = 1;
        boolean stream = false;
        boolean preload = false;

        if (element != null && element.isJsonPrimitive()) {
            name = element.getAsString();
        } else if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (!object.has("name")) {
                return null;
            }
            name = object.get("name").getAsString();
            volume = Math.max(0.01F, optFloat(object, "volume", 1.0F));
            pitch = Math.max(0.01F, optFloat(object, "pitch", 1.0F));
            weight = Math.max(1, optInt(object, "weight", 1));
            stream = optBoolean(object, "stream", false);
            preload = optBoolean(object, "preload", false);
        } else {
            return null;
        }

        if (name == null || name.isEmpty()) {
            return null;
        }

        ResourceLocation location = name.indexOf(':') >= 0
                ? ResourceLocation.tryParse(name)
                : GenshinAssets.characterSound(characterId, name);
        if (location == null) {
            LOGGER.warn("[MineGenshin] 角色 '{}' 的音效名 '{}' 非法，已跳过", characterId, name);
            return null;
        }

        ResourceLocation assetPath = GenshinAssets.soundAssetPath(location);
        Resource resource = resources.getResource(assetPath).orElse(null);
        if (resource == null) {
            LOGGER.warn("[MineGenshin] 声音文件 {} 不存在，事件 {} 少了一条（把 ogg 放进角色目录的 sounds/ 里即可）",
                    assetPath, eventId);
            return null;
        }

        files.put(assetPath, resource);
        return new CharacterSound(location, assetPath, volume, pitch, weight, stream, preload);
    }

    // ==================== JSON 小工具 ====================

    private static String optString(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static float optFloat(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int optInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean optBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    // ==================== 声音本体 ====================

    /**
     * 一个「文件在角色目录里」的 {@link Sound}。
     *
     * <p>原版 {@code Sound#getPath()} 固定拼 {@code sounds/<名字>.ogg}，
     * 这里把它覆写成我们记下的真实路径 —— 校验（文件在不在）和播放
     * （{@code SoundBufferLibrary} 取流）都走这个方法，所以只要改这一处，
     * 声音文件就可以待在角色目录里。
     */
    public static final class CharacterSound extends Sound {

        private final ResourceLocation assetPath;

        CharacterSound(ResourceLocation location, ResourceLocation assetPath, float volume, float pitch,
                       int weight, boolean stream, boolean preload) {
            super(location, ConstantFloat.of(volume), ConstantFloat.of(pitch), weight,
                    Sound.Type.FILE, stream, preload, 16);
            this.assetPath = assetPath;
        }

        @Override
        public ResourceLocation getPath() {
            return assetPath;
        }
    }
}
