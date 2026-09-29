package com.linweiyun.genshin.core.system.combat.animation.action;

import com.linweiyun.genshin.core.character.util.CharacterHelper;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.DefaultCharacterAnimations;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色 → 动作编排 / 动画配置 的查表注册表。
 *
 * <p>移植自参考2 的同名类。参考2 按 {@code Character_Now}（一个同步变量）查表，
 * 本项目按玩家当前出战角色的 ID（{@code PGCharacter.getTextureId()}，如 {@code "vesna"}）查表，
 * 查不到返回兜底项 —— 没戴角色饰品 / 不认识的角色走默认动画 + 空动作。
 */
public final class CharacterActions {

    private static final Map<String, CharacterActionHandler> HANDLERS = new HashMap<>();
    private static final Map<String, CharacterAnimations> ANIMATIONS = new HashMap<>();

    private CharacterActions() {
    }

    /**
     * 注册一个角色的动作编排与动画配置（两者放一起，状态机切状态时要顺手取音效）。
     *
     * @param characterKey 角色 ID（{@code PGCharacter.getTextureId()}）
     */
    public static void register(String characterKey, CharacterActionHandler handler, CharacterAnimations animations) {
        if (characterKey == null || characterKey.isEmpty()) return;
        HANDLERS.put(characterKey, handler);
        ANIMATIONS.put(characterKey, animations);
    }

    @Nullable
    public static CharacterActionHandler get(@Nullable String characterKey) {
        return characterKey == null ? null : HANDLERS.get(characterKey);
    }

    @Nullable
    public static CharacterAnimations animations(@Nullable String characterKey) {
        return characterKey == null ? null : ANIMATIONS.get(characterKey);
    }

    /** 取某个玩家当前角色的动作编排；没登记就返回兜底编排（什么都不做）。 */
    public static CharacterActionHandler getFor(Player player) {
        CharacterActionHandler handler = get(CharacterHelper.getActiveCharacterId(player));
        return handler == null ? CharacterActionHandler.EMPTY : handler;
    }

    /** 取某个玩家当前角色的动画配置；没登记就返回兜底配置（只有通用常态动画）。 */
    public static CharacterAnimations animationsFor(Player player) {
        CharacterAnimations animations = animations(CharacterHelper.getActiveCharacterId(player));
        return animations == null ? DefaultCharacterAnimations.INSTANCE : animations;
    }
}
