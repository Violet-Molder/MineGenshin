package com.linweiyun.genshin.client.performance;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.Level;

/**
 * 血条「受伤拖尾」的状态机（渲染优化模块）。
 *
 * <h2>它修的两件事</h2>
 *
 * <p><b>一、进战第一刀没有拖尾。</b>血条只在「战斗中 / 有元素附着」时才画，
 * 脱战的怪在 {@code MobHealthBarHud} 里那一帧就被 {@code continue} 掉了，
 * 连带拖尾状态也停在原地。等它被第一刀打进战斗时，<b>这一帧读到的血量已经是掉完的了</b>：
 * 状态里既没有「这一刀之前它有多少血」的记录，也就只能从当前值起步 —— 第一刀没有拖尾。
 * 而「见到新实体就按满血起步」那种补法更糟：它把<b>不属于这一刀</b>的旧伤害一并拖出来，
 * 于是每次不显示→显示都从满血往下滑。</p>
 *
 * <p>所以这里把<b>采样</b>与<b>是否显示</b>拆开：只要实体在本帧的渲染列表里、
 * 又大致在可视距离内，就把真实血量喂进状态机推进，<b>不管这帧画不画血条</b>。
 * 脱战的怪在被砍之前，状态里记着的就是它当时的真实血量（未受伤就是满血）；
 * 第一刀落下时拖尾从这一笔之前的值开始收，画出来的正好是这一刀。</p>
 *
 * <p><b>二、拖尾速度跟着帧率跑。</b>原先每帧固定减 {@code 0.005}：
 * 60fps 是每秒 0.3，240fps 就是每秒 1.2，同一刀在高刷屏上几乎看不出拖尾。
 * 现在按真实流逝时间衰减（{@link #DECAY_PER_SECOND}），与刷新率无关。</p>
 *
 * <h2>为什么自己带一张表</h2>
 *
 * <p>原实现是 {@code HashMap<Integer, Float>}：每次 {@code put} 都要把 float 装箱成新的
 * {@code Float}，实体 id 一旦超出 {@code Integer} 的缓存范围，连键都是新建的 ——
 * 每条血条每帧两次小对象分配。这里换成 fastutil 的原始类型表（Minecraft 自带该库），
 * 命中路径零分配；每条状态是可变对象，只在实体第一次出现时建一次。</p>
 *
 * <p>整张表只有渲染主线程读写，所以不加同步。</p>
 */
public final class HealthBarTrailTracker {

    /** 每实体一条拖尾状态。可变 → 稳态下不产生垃圾。 */
    private static final class State {
        /** 本帧该显示的拖尾比例（≥ 当前血量比例） */
        float trail;
        /** 上次被采样到的游戏刻 */
        long lastSeen;
        /** 上次推进到的帧号：同一帧内被问两次只推进一次 */
        int lastFrame;
    }

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    /** 拖尾每秒衰减的比例。0.3/s 正等于旧实现「每帧 0.005 × 60fps」，观感不变 */
    private static final float DECAY_PER_SECOND = 0.3f;

    /** 采样断档超过这么多刻（1s）就认为本地历史不可信：重新从当前血量起步，不补旧账 */
    private static final long STALE_TICKS = 20L;

    /** 单帧最多推进多少秒：卡顿/切窗口回来后不要让拖尾一次跳完 */
    private static final float MAX_FRAME_SECONDS = 1.0f;

    /** 一游戏刻 = 1/20 秒 */
    private static final float SECONDS_PER_TICK = 0.05f;

    /** 过期清理间隔（帧）。表里常态只有几十条，不必每帧全扫 */
    private static final int SWEEP_INTERVAL_FRAMES = 40;

    /** 表条数兜底上限，越过就整表丢弃（异常场景，正常到不了） */
    private static final int MAX_ENTRIES = 512;

    /** 每轮清理复用的待删 id 表 */
    private static final IntArrayList STALE_IDS = new IntArrayList();

    /** 帧号，只用来判断「同一帧内是否已推进过」 */
    private static int frame;
    /** 本帧的游戏刻 */
    private static long frameGameTime;
    /** 本帧真实流逝的秒数 */
    private static float frameSeconds;

    private static long lastGameTime;
    private static float lastPartialTick;
    private static boolean clockPrimed;
    private static Level lastLevel;

    private HealthBarTrailTracker() {}

    /**
     * 每帧开头调一次：推进帧号、算本帧流逝了多少秒、顺手清理过期状态。
     *
     * <p>时间不取 {@code System.nanoTime()}，而是用「游戏刻 + 本帧 partialTick」：
     * 绝对值 {@code gameTime + partialTick} 之差就是真实流逝的游戏刻数。
     * 暂停时游戏刻不走，所以暂停不会让拖尾继续衰减；同一帧被问两次差值也是 0。</p>
     *
     * @param level       当前客户端世界；换了世界（重进存档 / 换维度）就整表作废，
     *                    否则新世界里的实体 id 会命中旧世界的状态
     * @param gameTime    {@code level.getGameTime()}
     * @param partialTick 本帧的 {@code DeltaTracker#getGameTimeDeltaPartialTick(false)}
     */
    public static void beginFrame(Level level, long gameTime, float partialTick) {
        frame++;
        frameGameTime = gameTime;

        if (level != lastLevel) {
            lastLevel = level;
            clear();
        }

        if (clockPrimed) {
            float ticks = (float) (gameTime - lastGameTime) + (partialTick - lastPartialTick);
            frameSeconds = Math.max(0f, Math.min(MAX_FRAME_SECONDS, ticks * SECONDS_PER_TICK));
        } else {
            // 第一帧没有「上一帧」可减，按 0 处理：不让刚进世界那一帧凭空衰减
            clockPrimed = true;
            frameSeconds = 0f;
        }
        lastGameTime = gameTime;
        lastPartialTick = partialTick;

        if (frame % SWEEP_INTERVAL_FRAMES == 0) {
            sweep();
        }
    }

    /**
     * 采样一次某个实体的血量比例，并返回本帧该显示的拖尾比例。
     *
     * <p>每实体每帧调一次即可（同一帧内重复调用直接返回上次结果，不会重复扣速度）。</p>
     *
     * @param entityId    {@code Entity#getId()}
     * @param healthRatio 当前血量比例（0~1），由调用方 clamp
     */
    public static float observe(int entityId, float healthRatio) {
        State state = STATES.get(entityId);
        if (state == null) {
            // 第一次见到这个实体（刚进渲染范围 / 刚进世界）：没有它「上一帧是多少」的记录，
            // 就不补历史，直接从当前值起步 —— 宁可这一帧不画拖尾，也不拖不属于它的旧伤害
            state = new State();
            state.trail = healthRatio;
            state.lastSeen = frameGameTime;
            state.lastFrame = frame;
            STATES.put(entityId, state);
            return healthRatio;
        }
        if (state.lastFrame == frame) {
            return state.trail;
        }

        float trail = state.trail;
        if (frameGameTime - state.lastSeen > STALE_TICKS) {
            // 中间断过采样（离屏太久 / 实体刚从别处过来），本地历史不可信，重新起步
            trail = healthRatio;
        } else if (trail > healthRatio) {
            trail = Math.max(healthRatio, trail - DECAY_PER_SECOND * frameSeconds);
        } else {
            // 满血、回血、护盾回填：直接跟上，不留反向拖尾
            trail = healthRatio;
        }

        state.trail = trail;
        state.lastSeen = frameGameTime;
        state.lastFrame = frame;
        return trail;
    }

    /**
     * 清掉采样断档超过 {@link #STALE_TICKS} 的状态。
     *
     * <p>这些条目的历史已经不可信，再读到也只会走「重新起步」，留着纯占内存。
     * 注意清理条件用的是「多久没被采样」，所以正在持续采样的实体一条都不会被误删。</p>
     */
    private static void sweep() {
        if (STATES.isEmpty()) {
            return;
        }
        STALE_IDS.clear();
        for (Int2ObjectMap.Entry<State> entry : STATES.int2ObjectEntrySet()) {
            if (frameGameTime - entry.getValue().lastSeen > STALE_TICKS) {
                STALE_IDS.add(entry.getIntKey());
            }
        }
        for (int i = 0; i < STALE_IDS.size(); i++) {
            STATES.remove(STALE_IDS.getInt(i));
        }
        if (STATES.size() > MAX_ENTRIES) {
            STATES.clear();
        }
    }

    /** 退出世界 / 换世界时整表作废（表很小，直接丢最省事）。 */
    public static void clear() {
        STATES.clear();
        clockPrimed = false;
    }

    /** 当前状态条数（调试用）。 */
    public static int size() {
        return STATES.size();
    }
}
