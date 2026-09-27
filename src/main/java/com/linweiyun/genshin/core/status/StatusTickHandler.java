package com.linweiyun.genshin.core.status;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.system.about.ColdAura;
import com.linweiyun.genshin.core.system.poise.PoiseFreezeBreak;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.phys.Vec3;

/**
 * <b>状态的每 tick 落点</b> —— 推进容器，然后做两件由「容器里有什么」决定的事：
 * 寒元素的伴随同步（{@link ColdAura}）与冻结期间的位移锁。
 *
 * <p>两件事都读同一份判据（同一 tick 的容器扫描结果），所以不会出现「减速还在、冻结没了」
 * 这类两份真相；具体理由见 {@link ColdAura} 的注释。
 */
@EventBusSubscriber
public class StatusTickHandler {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.level().isClientSide()) return;

        StatusContainer c = living.getData(AttachmentRegistration.CONTAINER);
        c.tick();

        living.setData(AttachmentRegistration.CONTAINER.get(), c);

        // 寒的伴随 + 效果（减速 / 禁 AI）都在这一处重算；返回值即「有寒且有冻」
        boolean frozenWithCold = ColdAura.tick(living, c);

        freezeMotion(living, frozenWithCold);

        // 冻结的直接破韧：只在「刚冻上」那一 tick 破一次，冻着的时候把破韧驻留按住。
        // 放在这里而不是元素系统内部，是因为这是韧性系统的副作用，不是冻结规则本身。
        PoiseFreezeBreak.onFreezeTick(living, frozenWithCold);
    }

    /**
     * <b>冻结期间不许继续积累位移</b>。
     *
     * <p>冻结只做了 {@code setNoAi(true)}：AI 目标停了，但<b>物理还在跑</b> ——
     * 水流推动、流体里的浮力都会继续改服务端的 deltaMovement。客户端因为 NoAI 不预测这段位移，
     * 于是两边位置分叉；一旦解冻，客户端要追上服务端 → 表现为「解冻瞬间目标猛地弹回水里本应在的位置」。
     *
     * <p>所以冻结期间每 tick 把速度清零，让服务端和客户端看到的是同一个静止事实。
     *
     * <p>判据用「有寒且有冻」（{@link ColdAura#tick} 的返回值）：冻元素没被宿主接受寒时
     * （冰史莱姆这种免疫冰的元素生物）它本就不该被冻住，位移自然也不该锁。
     */
    private static void freezeMotion(LivingEntity living, boolean frozenWithCold) {
        if (!frozenWithCold) {
            return;
        }
        living.setDeltaMovement(Vec3.ZERO);
        living.setSprinting(false);
        living.setDeltaMovement(Vec3.ZERO);
    }
}
