package com.linweiyun.genshin.core.system.poise;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 实体身上<b>那一条韧性条</b>的运行时状态 —— 攒了多少、破没破、驻留还剩多久。
 *
 * <p>和 {@link PoiseTiers.TierProfile}（不变的表）分开：这边只有几个数字，
 * 同步与存档都很便宜，档位参数不用过网络。
 *
 * <h2>存在哪</h2>
 * NeoForge 数据附件 {@code AttachmentRegistration.POISE}，对所有 LivingEntity 有效
 * （怪物和玩家同一条管线）。不用属性：属性只能表示一个数，
 * 而韧性有「攒了多少 / 破没破 / 驻留计时 / 暂停标记」好几项。
 *
 * <h2>几个数字都是什么意思</h2>
 * <ul>
 *   <li>{@code value} —— 当前攒到的削韧值。攒到上限就是破韧。</li>
 *   <li>{@code broken} —— 破没破。破了之后 {@code value} 锁在上限不再涨，也不再衰减。</li>
 *   <li>{@code residenceTicks} —— 破韧后已经驻留了几刻；走到「重置时间」就恢复。</li>
 *   <li>{@code pauseTicks} —— 强控（悬浮一类）把驻留计时<b>暂停</b>的剩余刻数。</li>
 * </ul>
 *
 * <h2>同步</h2>
 * {@code @Persisted} + {@link PersistedParser}，和
 * {@link com.linweiyun.genshin.core.system.shield.ShieldState} 同一套机制。
 * 客户端血条下面那条削韧条读的就是这里的 {@link #value()} / {@link #maxValue()}。
 *
 * <p>⚠️ NeoForge 的附件是「原地改对象不算改」—— 改完必须由 {@link PoiseService} 调一次
 * {@code setData} 推给客户端，否则客户端永远拿旧值。
 */
public class PoiseState implements IPersistedSerializable {

    /** 当前攒到的削韧值。 */
    @Persisted(key = "value")
    private float value;

    /** 破韧中。 */
    @Persisted(key = "broken")
    private boolean broken;

    /** 破韧后已经驻留的刻数。 */
    @Persisted(key = "residence")
    private int residenceTicks;

    /** 强控把驻留计时暂停的剩余刻数（>0 期间驻留不走）。 */
    @Persisted(key = "pause")
    private int pauseTicks;

    /**
     * 上限的快照 —— 给客户端算比例用。
     *
     * <p>上限是算出来的（档位长度 × 联机系数），客户端不一定能算出一模一样的值
     * （联机系数要问服务端的在线人数；而且上限本身是可变属性 {@code POISE_MAX}，
     * 逐实例给，客户端自己看不到）。把算好的上限随状态一起同步过去，
     * 客户端就不必自己再算一份。
     */
    @Persisted(key = "max")
    private float maxValue;

    /** 最近一次挨削韧的时刻，用来判断「这一条还要不要画」。 */
    @Persisted(key = "last_hit")
    private long lastHitGameTime;

    public static final Codec<PoiseState> CODEC = PersistedParser.createCodec(PoiseState::new);
    public static final StreamCodec<ByteBuf, PoiseState> STREAM_CODEC =
            PersistedParser.createStreamCodec(PoiseState::new);

    public PoiseState() {
    }

    // ==================== 读取 ====================

    public float value() {
        return this.value;
    }

    /** 上限；还没结算过就是 0。 */
    public float maxValue() {
        return this.maxValue;
    }

    public boolean isBroken() {
        return this.broken;
    }

    public int residenceTicks() {
        return this.residenceTicks;
    }

    public int pauseTicks() {
        return this.pauseTicks;
    }

    public boolean isResidencePaused() {
        return this.pauseTicks > 0;
    }

    public long lastHitGameTime() {
        return this.lastHitGameTime;
    }

    /** 这一条「有没有内容」：攒过削韧、正破着、或者刚挨过打。 */
    public boolean isEngaged() {
        return this.broken || this.value > 0f;
    }

    /**
     * 削韧条比例（0~1）：破韧期间恒为 0 —— 条被打空就是「破韧」最直观的样子，
     * 破绽窗口的剩余时间由表现层自己用 {@link #residenceTicks()} 表达。
     */
    public float ratio() {
        if (this.broken) {
            return 0f;
        }
        if (this.maxValue <= 0f) {
            return 0f;
        }
        return Math.clamp(this.value / this.maxValue, 0f, 1f);
    }

    // ==================== 写入（只由 PoiseService 调） ====================

    /** 攒一笔削韧，钳到上限。 */
    void add(float amount, float maxValue) {
        this.maxValue = maxValue;
        if (amount <= 0f || this.broken) {
            return;
        }
        this.value = Math.min(maxValue, this.value + amount);
    }

    /** 攒到上限了没有（上限 ≤ 0 视为永远攒不满，用来表达「不吃削韧」）。 */
    boolean isFull() {
        return this.maxValue > 0f && this.value >= this.maxValue;
    }

    /** 每刻的自然衰减（自恢复）；破韧期间不走这条。 */
    void decay(float amount) {
        if (amount <= 0f) {
            return;
        }
        this.value = Math.max(0f, this.value - amount);
    }

    /** 刷新上限快照（联机人数变了、档位属性被改过都会用到）。 */
    void refreshMax(float maxValue) {
        this.maxValue = maxValue;
        if (this.value > maxValue) {
            this.value = maxValue;
        }
    }

    /** 进入破韧态：值锁在上限，驻留计时从 0 开始。 */
    void markBroken() {
        this.broken = true;
        this.value = this.maxValue;
        this.residenceTicks = 0;
    }

    /** 驻留推进一刻。 */
    void advanceResidence() {
        this.residenceTicks++;
    }

    /** 驻留计时暂停若干刻（强控）；已有更长的暂停就不缩短。 */
    void pauseResidence(int ticks) {
        if (ticks > this.pauseTicks) {
            this.pauseTicks = ticks;
        }
    }

    /** 暂停计时走一刻；还有剩余就返回 true（本刻驻留不动）。 */
    boolean consumePause() {
        if (this.pauseTicks <= 0) {
            return false;
        }
        this.pauseTicks--;
        return true;
    }

    /** 韧性整个恢复：值归零、破韧解除、计时清空。 */
    void reset() {
        this.value = 0f;
        this.broken = false;
        this.residenceTicks = 0;
        this.pauseTicks = 0;
    }

    void markHit(long gameTime) {
        this.lastHitGameTime = gameTime;
    }
}
