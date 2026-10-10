package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroup;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 星见雅各招共用的伤害结算 —— 都是风元素、都是范围伤害，差别只在「打哪一块」与倍率。
 *
 * <p>只跑服务端；倍率是占攻击力多少（1.0 = 100%）。
 */
public final class MiyabiDamage {

    private MiyabiDamage() {
    }

    /**
     * 对身前一段区域结算一次伤害。
     *
     * <p>扫描盒从玩家自己的碰撞箱起算、沿视线扫出去再外扩：这样「站在高一级方块上的敌人」
     * 与「脚下低一级的敌人」都在范围内 —— 只按脚底位置铺一条线的话，Y 轴会漏掉一大截。
     *
     * @param reach      向前多远（格）
     * @param width      左右 / 前后再外扩多少（格）
     * @param height     上下再外扩多少（格）
     * @param element    结算元素；{@link ModElements#FYSIKOS 物理}时不附着
     * @param multiplier 倍率（占攻击力）
     */
    public static void forward(Player player, PGCharacter character, AttackType type, GenshinElement element,
                               DecayGroup decayGroup, double reach, float width, float height, float multiplier) {
        Level level = player.level();
        if (level.isClientSide() || multiplier <= 0f) {
            return;
        }

        Vec3 direction = CombatAim.direction(player);
        AABB box = player.getBoundingBox()
                .expandTowards(direction.x * reach, direction.y * reach, direction.z * reach)
                .inflate(width, height, width);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e != player)) {
            hurt(player, character, target, type, element, decayGroup, multiplier);
        }
    }

    /**
     * 以某个点为中心结算一次伤害。
     *
     * @param radius 中心到边缘的距离（格）
     */
    public static void around(Player player, PGCharacter character, AttackType type, GenshinElement element,
                              DecayGroup decayGroup, Vec3 center, double radius, float multiplier) {
        Level level = player.level();
        if (level.isClientSide() || multiplier <= 0f) {
            return;
        }

        for (LivingEntity target : new AreaEntityCollector(level, center, center, (float) radius).execute()) {
            if (target != player) {
                hurt(player, character, target, type, element, decayGroup, multiplier);
            }
        }
    }

    private static void hurt(Player player, PGCharacter character, LivingEntity target,
                             AttackType type, GenshinElement element, DecayGroup decayGroup, float multiplier) {
        ModDamageSpec.Builder spec = ModDamageSpec.builder(type, element)
                .multiplier(multiplier)
                .decayGroup(decayGroup)
                .attackerCharacter(character);
        if (element != null && element != ModElements.FYSIKOS.get()) {
            spec.elementAmount(AttachmentType.WEAK.getInitialAmount());
        }

        target.hurt(ModDamageSource.from(spec.build(), player), 0f);
    }
}
