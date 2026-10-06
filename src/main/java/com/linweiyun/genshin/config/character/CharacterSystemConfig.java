package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 角色系统开关（临时脚手架）。
 *
 * <p>当前的模型是借来的，只为了验证这套动画/动作系统；等自有模型做好之后这两个开关就不需要了。
 * 两个开关<b>按角色独立控制</b>，用角色 ID（{@code PGCharacter.getTextureId()}，如 {@code "vesna"}）登记。
 *
 * <h2>custom_model_characters —— 是否启用特殊模型</h2>
 * <ul>
 *   <li>在名单里：这个角色走模型替换 + GeckoLib 动画（和 vesna 现在一样）。</li>
 *   <li>不在名单里：不做模型替换（照常原版渲染），只保留攻击延迟 ——
 *       按键之后，在<b>实际造成伤害的那一刻</b>播一次摆臂。</li>
 * </ul>
 *
 * <h2>action_system_characters —— 是否启用动作系统</h2>
 * <ul>
 *   <li>在名单里：完整动作系统，有前摇 / 硬直 / 后摇、移动封锁、延迟伤害。</li>
 *   <li>不在名单里：所有按键<b>及时响应</b>，不再采用前后摇与延迟伤害。</li>
 * </ul>
 */
public final class CharacterSystemConfig {

    public static ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_MODEL_CHARACTERS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> ACTION_SYSTEM_CHARACTERS;
    public static ModConfigSpec.BooleanValue LOAD_CHARACTER_GEO;

    private CharacterSystemConfig() {
    }

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("character_system");

        LOAD_CHARACTER_GEO = builder
                .comment("总门禁：是否加载角色 Geo 模型与动画。",
                        "设为 false 时角色不加载 Geo 模型和动画（走原版渲染兜底），",
                        "但物品和实体的 Geo 不受影响。改完后需要重启游戏 / 重载资源。")
                .define("load_character_geo", false);

        CUSTOM_MODEL_CHARACTERS = builder
                .comment("使用专属模型 + GeckoLib 动画的角色 ID 名单。",
                        "不在名单里的角色不做模型替换，只在伤害结算那一刻播一次摆臂。",
                        "load_character_geo 为 false 时本名单不生效。")
                .defineListAllowEmpty("custom_model_characters",
                        List.of(),
                        () -> "vesna",
                        CharacterSystemConfig::isCharacterId);

        ACTION_SYSTEM_CHARACTERS = builder
                .comment("启用完整动作系统（前摇 / 硬直 / 移动封锁 / 延迟伤害）的角色 ID 名单。",
                        "不在名单里的角色所有按键及时响应，不采用前后摇与延迟伤害。")
                .defineListAllowEmpty("action_system_characters",
                        List.of(),
                        () -> "vesna",
                        CharacterSystemConfig::isCharacterId);

        builder.pop();
    }

    private static boolean isCharacterId(Object value) {
        return value instanceof String;
    }

    /** 这个角色是否启用专属模型 + 动画。 */
    public static boolean customModel(String characterId) {
        return inList(CUSTOM_MODEL_CHARACTERS, characterId);
    }

    /** 这个角色是否启用完整动作系统。 */
    public static boolean actionSystem(String characterId) {
        return inList(ACTION_SYSTEM_CHARACTERS, characterId);
    }

    /** 总门禁：是否加载角色 Geo 模型与动画。false 时 Geo缓存不扫 character/ 根。 */
    public static boolean loadCharacterGeo() {
        if (LOAD_CHARACTER_GEO == null) {
            return true;
        }
        try {
            return LOAD_CHARACTER_GEO.get();
        } catch (IllegalStateException notLoadedYet) {
            return LOAD_CHARACTER_GEO.getDefault();
        }
    }

    private static boolean inList(ModConfigSpec.ConfigValue<List<? extends String>> config, String characterId) {
        if (characterId == null || characterId.isEmpty() || config == null) {
            return false;
        }
        List<? extends String> ids;
        try {
            ids = config.get();
        } catch (IllegalStateException notLoadedYet) {
            // 配置还没加载（早期构造期）：按默认名单判断
            ids = config.getDefault();
        }
        return ids != null && ids.contains(characterId);
    }
}