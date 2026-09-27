package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.config.PoiseConfig;
import com.linweiyun.genshin.core.system.control.Controllable;
import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlService;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * <b>冻结 → 直接破韧</b>这条通道（文献口径 + 用户原话「冻结可以无视韧性直接冻结，
 * 或者说直接破韧也行」）。
 *
 * <h2>为什么需要一个「桥」</h2>
 * 冻结的判据（有寒且有冻）本来在 {@code ColdAura.tick} 里每 tick 算一次，
 * 而「冻结<b>刚开始</b>的那一瞬间」才是要破韧的时刻 —— 每 tick 都破一次
 * 会把破韧驻留反复重置，敌人一被冻住就永远在破韧状态。
 * 触发器又不该把「谁被冻过」记在结冰那套状态里（那是元素系统的状态，不是韧性的），
 * 所以这里单独记一份「这一轮冻结，我已经破过韧了」。
 *
 * <h2>一次冻结只破一次，但冻结期间破绽窗口不流逝</h2>
 * <ul>
 *   <li>冻住的那一 tick：{@link PoiseService#forceBreak}，目标进入破韧；</li>
 *   <li>冻着的每一 tick：给破韧驻留续一小段暂停（和聚怪牵引同一个做法）——
 *       于是「冻着的时候它一直是破绽」，解冻之后驻留才接着走完；</li>
 *   <li>解冻：把这一轮记的账消掉，下次再冻住就再破一次。</li>
 * </ul>
 *
 * <h2>只读语义、不反向影响冻结</h2>
 * 冻结本身照常生效（减速、禁 AI、锁位移都在元素系统那边）；
 * 这里只是它的<b>副作用</b>。开关关掉（{@code poise.toml → freeze.force-break}）
 * 就完全回到旧行为：冻得住，但韧性条不动。
 */
public final class PoiseFreezeBreak {

    /**
     * 冻结期间给破韧驻留续的暂停刻数。
     *
     * <p>取值同 {@code GatherPull.RESET_HOLD_TICKS}：略大于 1，
     * 每 tick 刷一次就能吸收 tick 顺序上的错位，冻完立刻接着走驻留。
     */
    private static final int RESET_HOLD_TICKS = 5;

    /** 这一轮冻结里已经破过韧的实体（按实体 id）。解冻时清掉。 */
    private static final Set<Long> BROKEN_EPISODE = new HashSet<>();

    private PoiseFreezeBreak() {
    }

    /**
     * 每 tick 由 {@code StatusTickHandler} 带着「此刻冻没冻着」调一次。
     *
     * @param frozenNow 「有寒且有冻」——{@code ColdAura.tick} 的返回值
     */
    public static void onFreezeTick(LivingEntity entity, boolean frozenNow) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        long key = entity.getId();

        if (!frozenNow) {
            // 解冻 / 从来没过：把这一轮的账消掉，下次冻住重新破一次
            // （绝大多数生物每刻都走这条，先问一句「账上有没有人」，
            //   省掉每只怪每刻一次 Long 装箱 + 哈希）
            if (!BROKEN_EPISODE.isEmpty()) {
                BROKEN_EPISODE.remove(key);
            }
            return;
        }
        if (!entity.isAlive()) {
            BROKEN_EPISODE.remove(key);
            return;
        }
        if (!PoiseConfig.isOn(PoiseConfig.FREEZE_FORCE_BREAK)) {
            // 开关关掉：冻结照常，但不碰韧性 —— 也不记账，免得开关中途打开时漏掉一次
            return;
        }
        // 实体自己不吃冻结（首领一类在 Controllable#blocksControl 里拦下）：它压根没被冻住，
        // 那就连「冻结直接破韧」也不该发生 —— 冻住了才谈得上破韧。
        Controllable controllable = ControlService.of(entity);
        if (controllable == null || !controllable.allowsControl(ControlRequest.freeze(true))) {
            return;
        }
        if (BROKEN_EPISODE.add(key)) {
            PoiseService.forceBreak(entity, "freeze");
        } else {
            // 已经在这一轮冻结里破过韧了：只把驻留按住，免得窗口在冻着的时候自己走完
            PoiseService.pauseReset(entity, RESET_HOLD_TICKS);
        }
    }

    /** 实体卸载 / 死亡时清掉记账（防止集合随刷怪缓慢增长）。 */
    public static void forget(LivingEntity entity) {
        if (entity != null) {
            BROKEN_EPISODE.remove(entity.getId());
        }
    }

    /** 现在记着多少条（诊断用）。 */
    public static int trackedCount() {
        return BROKEN_EPISODE.size();
    }
}
