package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.content.skill_node.ElementalOrbSpawner;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 普通攻击产球，由伤害管线在确认「造成实际伤害」之后直接调用。
 *
 * <p>规则：只认普通攻击；必须是当前出战角色打出；元素为空或物理系不产；每次调用掷一次 50%，产 1 颗元素微粒。
 */
public final class NormalAttackOrbProducer {

    /** 产球概率。 */
    private static final float ORB_CHANCE = 0.5f;

    private NormalAttackOrbProducer() {
    }

    /**
     * 一次伤害确认「真的扣到血」之后由伤害管线直接调用。
     *
     * @param level            发生伤害的服务端世界
     * @param spec             这一下伤害的规格（只用它判断攻击类型）
     * @param attacker         直接造成伤害的实体
     * @param attackerCharacter 攻击者角色，怪物 / 环境伤害时为 {@code null}
     */
    public static void tryProduce(ServerLevel level, ModDamageSpec spec,
                                  @Nullable Entity attacker,
                                  @Nullable PGCharacter attackerCharacter) {
        // 热路径：先做最便宜的判断
        if (spec.getAttackType() != AttackType.NORMAL_ATTACK) {
            return;
        }
        if (attackerCharacter == null || !attackerCharacter.spawnsNormalAttackParticle()) {
            return;
        }
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        // 后台角色（不在场上）的普攻不产球
        if (CharacterHelper.getCurrentCharacter(player) != attackerCharacter) {
            return;
        }
        GenshinElement element = attackerCharacter.getElemental();
        if (element == null || element == ModElements.FYSIKOS.get()) {
            return;
        }
        if (!(level.getRandom().nextFloat() < ORB_CHANCE)) {
            return;
        }
        new ElementalOrbSpawner(level, element, 1, true, player.position()).execute();
    }
}