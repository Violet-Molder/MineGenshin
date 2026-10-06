package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.elementlib.api.ElibAttackAction;
import com.linweiyun.elementlib.api.ElibAttackTrigger;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.attack.ElibAttackPipeline;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害驱动的方块附着：任何带元素的伤害都顺手把元素留给落点周围的环境。
 *
 * <p>领域实体的持续伤害、下落攻击、召唤物伤害都不经过 ActionState 的伤害点，走这里。
 */
public final class DamageBlockAttack {

    private static final double RADIUS = 2.0;

    private DamageBlockAttack() {
    }

    public static void onElementalDamage(ServerLevel level, LivingEntity target,
                                         @Nullable Entity sourceEntity,
                                         GenshinElement element,
                                         AttachmentProfile profile,
                                         float elementAmount) {
        if (level == null || target == null || element == null) {
            return;
        }
        Entity attacker = sourceEntity != null ? sourceEntity : target;
        Vec3 center = target.position();
        ElibAttackAction action = ElibAttackAction.aimed(attacker, element,
                ElibAttackTrigger.ACTION_DAMAGE_POINT, AttachmentSource.NORMAL_ATTACK, profile,
                center, Vec3.ZERO, RADIUS).withElementAmount(elementAmount);
        ElibAttackPipeline.dispatchAround(action, center, RADIUS, false);
    }
}
