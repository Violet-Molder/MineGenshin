package com.linweiyun.genshin.content.items.weapon.sword;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.impl.DiebianForsakenWindEffect;
import com.linweiyun.genshin.content.effect.character.impl.DiebianLoyalWindEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 蝶变（五星单手剑）。
 *
 * <p>主词条：攻击力 47.54（= 五星 tier3 的 48 再 -0.46，见 {@link #MAIN_STAT_DELTA}）；
 * 副词条：暴击伤害 9.6%（五星 tier3 那一档）。
 *
 * <h2>武器效果：三种风轮换</h2>
 * <pre>
 * 施放元素战技 / 元素爆发 → 按固定顺序获得下一种风：
 *   ① 忠忱之风：暴击伤害 +56%，10 秒
 *   ② 叛弃之风：星扩散反应伤害 +36%，10 秒
 *   ③ 丰获之风：恢复 5 点元素能量（每 4 秒至多通过这种方式恢复 5 点）
 * 三种循环；装备者退场时移除这些效果并<b>重置顺序</b>（下次从忠忱之风重新开始）。
 * </pre>
 *
 * <p>状态存在<b>角色</b>上（`PGCharacterData.weaponPassiveStage` / `weaponPassiveGateTick`）：
 * 武器被动是「装备者」的属性，换人时要能重置；放武器物品 NBT 反而会在换装时漏掉重置。
 */
public class BeyondTheChrysalis extends Sword {

    public static final String NAME = "beyond_the_chrysalis";

    /** 主词条比五星 tier3 的基础攻击力(48)低这么多 → 47.54。 */
    public static final double MAIN_STAT_DELTA = -0.46;

    /** 丰获之风的回能：每次 5 点，每 4 秒至多一次。 */
    public static final float HARVEST_ENERGY = 5f;
    public static final int HARVEST_INTERVAL_TICKS = 4 * 20;

    /** 风的数量（三种轮换）。 */
    public static final int WIND_COUNT = 3;

    public BeyondTheChrysalis(Item.Properties properties) {
        super(properties);
        this.star = 5;
        // tier3 = 基础攻击力 48 / 副词条暴击伤害 9.6%（这一档就是他指定的 9.6%）
        this.tier = 3;
        this.subStatAttribute = ModAttributes.CDG;
        this.mainStatDelta = MAIN_STAT_DELTA;
    }

    // ==================== 武器被动 ====================

    /**
     * 装备者施放了元素战技 / 元素爆发 → 轮换到下一阵风。
     *
     * <p>由 {@code ActionManager} 在<b>服务端</b>受理成功那一刻调用（触发即生效，
     * 和「CD 已经转了但效果没出来」那种坑对齐）。
     */
    @Override
    public void onAbilityCast(Player player, PGCharacter character, ActionKind kind) {
        if (player.level().isClientSide()) return;
        if (kind != ActionKind.ELEMENTAL_SKILL_TAP
                && kind != ActionKind.ELEMENTAL_SKILL_HOLD
                && kind != ActionKind.ELEMENTAL_BURST) {
            return;
        }

        PGCharacterData data = character.getData();
        int stage = Math.floorMod(data.getWeaponPassiveStage(), WIND_COUNT);
        applyWind(player, character, stage);
        data.setWeaponPassiveStage((stage + 1) % WIND_COUNT);
    }

    private void applyWind(Player player, PGCharacter character, int stage) {
        switch (stage) {
            case 0 -> CharacterEffectHelper.addEffect(player, character, new CharacterEffectInstance(
                    ModCharacterEffects.DIEBIAN_LOYAL_WIND_EFFECT.get(),
                    DiebianLoyalWindEffect.DURATION_TICKS, 0, false));
            case 1 -> CharacterEffectHelper.addEffect(player, character, new CharacterEffectInstance(
                    ModCharacterEffects.DIEBIAN_FORSAKEN_WIND_EFFECT.get(),
                    DiebianForsakenWindEffect.DURATION_TICKS, 0, false));
            default -> {
                // 丰获之风：回 5 点能量，带 4 秒的间隔限制
                long now = player.level().getGameTime();
                if (now >= character.getData().getWeaponPassiveGateTick()) {
                    character.getData().addElementalEnergy(HARVEST_ENERGY);
                    character.getData().setWeaponPassiveGateTick(now + HARVEST_INTERVAL_TICKS);
                }
            }
        }
    }

    /**
     * 装备者退场：移除两种风的 buff，并把轮换顺序重置回「忠忱之风」。
     *
     * <p>⚠️ 必须走 {@link CharacterEffectHelper#removeEffect}（它会调 {@code onEffectRemoved}）。
     * 容器自己的 {@code removeEffectsOfType(...)} 只是把条目从列表里删掉、<b>不触发移除回调</b>
     * —— 用它的话，忠忱之风加在暴击伤害上的那 56% 会<b>永远留在角色身上</b>。
     */
    @Override
    public void onLeaveField(Player player, PGCharacter character) {
        if (player.level().isClientSide()) return;

        CharacterEffectHelper.removeEffect(player, character,
                ModCharacterEffects.DIEBIAN_LOYAL_WIND_EFFECT.get());
        CharacterEffectHelper.removeEffect(player, character,
                ModCharacterEffects.DIEBIAN_FORSAKEN_WIND_EFFECT.get());

        character.getData().setWeaponPassiveStage(0);
        character.getData().markDirty();
    }

    // ==================== 物品信息 ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, builder, flag);
        builder.add(Component.empty());
        builder.add(Component.translatable("item.minegenshin.beyond_the_chrysalis.passive").withStyle(ChatFormatting.YELLOW));
        builder.add(Component.empty());
        builder.add(Component.literal("溢彩流光的长剑，曾有人以之破除束缚，\n剑风轻盈如同翩跹的蝶舞。"));

    }
}
