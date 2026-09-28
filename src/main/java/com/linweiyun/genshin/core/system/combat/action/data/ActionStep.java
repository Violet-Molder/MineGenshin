package com.linweiyun.genshin.core.system.combat.action.data;

import java.util.Collections;

import java.util.List;

    public final class ActionStep {
        public final String animation;           // 动画名
        public final int duration;               // 动画总时长（刻）

        /**
         * <b>执行期结束刻</b>：{@code [prepareTicks, protectDuration)} 这一段是执行期，期间不可打断。
         *
         * <h2>一段动作的三个窗口</h2>
         * <pre>
         * 0                  prepareTicks              protectDuration        duration
         * ├── 准备阶段（可打断）──┼──── 执行期（不可打断）────┼─── 后摇（可取消）───┤
         * </pre>
         * <ul>
         *   <li><b>准备阶段</b> {@code [0, prepareTicks)}：吟唱 / 读条。
         *       可以被打断 —— 挨打、走开、跳跃都会让这一招作废。</li>
         *   <li><b>执行期</b> {@code [prepareTicks, protectDuration)}：技能<b>真正在发生</b>的部分，
         *       位移、动画、伤害点（{@code hits[].delay}）都在这一段里。
         *       一旦进入就<b>不可打断</b>，否则就是「CD 扣了、能量没了、效果没出来」。</li>
         *   <li><b>后摇</b> {@code [protectDuration, duration)}：效果已经结算完，
         *       可以被移动 / 跳跃 / 下一招取消 —— 连招手感就靠它。</li>
         * </ul>
         */
        public final int protectDuration;

        /**
         * 准备阶段长度（刻）—— 三窗口图见 {@link #protectDuration}。
         */
        public int prepareTicks = 0;

        public final int priority;               // 优先级
        public final List<Move> moves;           // 位移列表
        public final List<Hit> hits;             // 伤害列表
        public final List<SoundRef> sounds;      // 音效列表（旧写法：每格固定播一条）
        /** 音效编排（新写法）：序列 + 随机可组合，见 {@link SoundCue}。 */
        public final List<SoundCue> soundCues = new java.util.ArrayList<>();
        public final float skillCharge;          // 战技能量回复
        public final float finalCharge;          // 大招能量回复
        public final int cooldown;               // 冷却（刻），0 表示无冷却
        public final int comboWindow;            // 连击窗口（刻），后摇结束后可接下一段的窗口
        public String comboEndAnim = null;       // 最后一段连招结束后的收尾动画（仅最终段生效）
        public int comboEndTicks = DEFAULT_COMBO_END_TICKS; // 收尾动画播多少刻

        /**
         * 这一段的「生效攻击距离」（格）。索敌距离通常大于它，差额就是突进要补的距离。
         */
        public float attackRange = 0f;

        /**
         * 突进时<b>从第几刻开始冻结动画</b>（刻）。默认 {@code 0} = 按下即冻结。
         */
        public int dashStartDelay = 0;

        /**
         * 这一招的<b>交战形态</b>：远程还是近战、要不要突进、索敌多远。
         */
        public Engagement engagement = Engagement.melee();

        /**
         * 这一段的 {@code moves} 位移<b>允不允许带 Y 轴</b>。
         *
         * <p>默认 {@code false}：位移只在<b>水平面</b>上给（视线方向投影到水平面再归一化）。
         */
        public boolean moveAllowsVertical = false;

        /**
         * 这一段的动画要不要<b>循环播</b>。
         */
        public boolean loopAnimation = false;

        public ActionBodyFacing bodyFacing = ActionBodyFacing.TARGET;

        /**
         * 大招「跃起 → 摆姿态 → 锁落点 → 下坠刺击」的可选配置；{@code null} = 普通大招。
         *
         * <h2>为什么位移要单独配</h2>
         * 这种大招的<b>动画只负责「跳起来 + 把身体转成下坠姿态」</b>，
         * 真正飞出去的过程由代码驱动 —— 因为落点要在「开始下坠」那一刻按双方位置算出来，
         * 而且<b>之后不再追踪</b>（敌人跑了就打空，这是设计）。
         *
         * <pre>
         * 0 ──── jumpTicks ──────── landTick ──────── duration
         * │  跃起 + 摆姿态（动画）  │   下坠（代码）   │  落地伤害 + 收招
         *                           ↑ 这一刻锁落点，并发给服务端
         * </pre>
         *
         * <p><b>伤害点必须配在 {@code jumpTicks + diveTicks} 那一刻</b>
         * （{@code hits[].delay = DiveBurst.landTick()}），落地伤害才会和下坠同步。
         *
         * <p>另外记得把 {@code engagement} 的突进关掉（{@code .withDash(false)}）——
         * 接近这件事由下坠本身完成，不需要再来一次贴脸突进。
         */
        public DiveBurst diveBurst = null;

        /**
         * 便捷写法：把这一段做成「跃起下坠刺击」大招。
         *
         * @param jumpTicks   跃起 + 摆姿态的刻数（动画演到这里为止）
         * @param diveTicks   下坠刻数（落点在这一段开始时锁定）
         * @param jumpHeight  跃起多高（格）
         * @param maxDistance 落点最远离自己几格（防止一口气飞到天边）
         */
        public ActionStep withDiveBurst(int jumpTicks, int diveTicks,
                                        double jumpHeight, double maxDistance) {
            this.diveBurst = new DiveBurst(jumpTicks, diveTicks, jumpHeight, maxDistance);
            return this;
        }

        /** 跃起下坠大招的时序与射程（见 {@link #diveBurst}）。 */
        public static final class DiveBurst {
            /** 跃起 + 摆姿态用多少刻（这一段动画正常播，位移由代码抬升）。 */
            public final int jumpTicks;
            /** 下坠多少刻。 */
            public final int diveTicks;
            /** 跃起高度（格）。 */
            public final double jumpHeight;
            /** 落点最远离自己多远（格）。 */
            public final double maxDistance;

            public DiveBurst(int jumpTicks, int diveTicks, double jumpHeight, double maxDistance) {
                this.jumpTicks = Math.max(1, jumpTicks);
                this.diveTicks = Math.max(1, diveTicks);
                this.jumpHeight = Math.max(0.5, jumpHeight);
                this.maxDistance = Math.max(1.0, maxDistance);
            }

            /** 落地时刻 —— {@code hits[].delay} 要写这个值。 */
            public int landTick() {
                return jumpTicks + diveTicks;
            }
        }

        /** 收尾动画的默认时长（刻）。 */
        public static final int DEFAULT_COMBO_END_TICKS = 60;

        /**
         * 默认攻击距离（格）—— 近战够得着的范围。
         *
         * <p>「索敌距离 &gt; 攻击距离」的差额就是突进要补的距离。
         */
        public static final float DEFAULT_MELEE_RANGE = 3.0f;

        public ActionStep(String animation, int duration, int protectDuration, int priority,
                          List<Move> moves, List<Hit> hits, List<SoundRef> sounds,
                          float skillCharge, float finalCharge, int cooldown, int comboWindow) {
            this.animation = animation;
            this.duration = duration;
            this.protectDuration = protectDuration;
            this.priority = priority;
            this.moves = moves == null ? Collections.emptyList() : moves;
            this.hits = hits == null ? Collections.emptyList() : hits;
            this.sounds = sounds == null ? Collections.emptyList() : sounds;
            this.skillCharge = skillCharge;
            this.finalCharge = finalCharge;
            this.cooldown = cooldown;
            this.comboWindow = comboWindow;
        }

        /** 便捷写法：设置收尾动画与它的时长。 */
        public ActionStep withComboEnd(String animation, int ticks) {
            this.comboEndAnim = animation;
            this.comboEndTicks = ticks > 0 ? ticks : DEFAULT_COMBO_END_TICKS;
            return this;
        }

        /**
         * 便捷写法：这一段有<b>准备阶段</b>（吟唱 / 读条）多少刻。
         *
         * <p>准备阶段里可以被打断，执行期（{@code prepareTicks ~ protectDuration}）不行 ——
         * 见 {@link #protectDuration} 的三窗口图。写 0 = 触发即执行（默认）。
         */
        public ActionStep withPrepareTicks(int ticks) {
            this.prepareTicks = Math.max(0, ticks);
            return this;
        }

        /** 便捷写法：显式指定这一段的生效攻击距离（格）。 */
        public ActionStep withAttackRange(float range) {
            this.attackRange = Math.max(0f, range);
            return this;
        }

        /**
         * 便捷写法：突进<b>延后到第几刻</b>才开始（前几刻先播「起跳 / 变身 / 蓄力」）。
         *
         * <p>见 {@link #dashStartDelay}：写 0（默认）= 按下即冻结冲出去；
         * 写 N = 先播 N 刻动画，第 N 刻才冻结并突进。
         */
        public ActionStep withDashStartDelay(int ticks) {
            this.dashStartDelay = Math.max(0, ticks);
            return this;
        }

        /** 便捷写法：换掉这一招的交战形态（远程/近战、突不进、索敌多远）。 */
        public ActionStep withEngagement(Engagement value) {
            if (value != null) {
                this.engagement = value;
            }
            return this;
        }

        /**
         * 便捷写法：这一段的位移要不要带 Y 轴。
         *
         * <p>{@code true} = 沿视线冲（抬头会起飞，上挑、跳劈这类要它）；
         * {@code false}（默认）= 只走水平面。
         */
        public ActionStep withVerticalMove(boolean allowVertical) {
            this.moveAllowsVertical = allowVertical;
            return this;
        }

        /**
         * 便捷写法：这一段的动画循环播（持续型招式用）。
         *
         * <p>见 {@link #loopAnimation}：{@code false}（默认）= 播一次停在最后一帧；
         * {@code true} = 一直循环到状态结束。
         */
        public ActionStep withLoopAnimation(boolean loop) {
            this.loopAnimation = loop;
            return this;
        }

        public ActionStep withBodyFacing(ActionBodyFacing value) {
            this.bodyFacing = value == null ? ActionBodyFacing.TARGET : value;
            return this;
        }

        public ActionStep withCameraFacing() {
            return withBodyFacing(ActionBodyFacing.CAMERA);
        }

        /**
         * 便捷写法：标记为<b>远程招式</b>。
         *
         * <p>等价于 {@code withEngagement(Engagement.ranged())}，效果：
         * <ul>
         *   <li>不突进（永远只转向）；</li>
         *   <li>索敌距离 = {@link #effectiveAttackRange()}（够得着才锁）。</li>
         * </ul>
         *
         * <p><b>远程招式记得配 {@link #withAttackRange(float)}</b> —— 不配的话
         * {@code effectiveAttackRange()} 会用近战默认的 3 格，索敌也就只有 3 格。
         */
        public ActionStep ranged() {
            this.engagement = Engagement.ranged();
            return this;
        }

        /**
         * 追加一格音效编排（序列 + 随机）。
         *
         * <p>和旧的 {@link #sounds} 是叠加关系：旧列表里每一条会当成「固定播一格」，
         * 这里追加的是额外编排。新代码建议只用这个。
         */
        public ActionStep withSoundCue(SoundCue cue) {
            if (cue != null) {
                this.soundCues.add(cue);
            }
            return this;
        }

        /**
         * 实际生效的攻击距离。
         *
         * <p><b>默认就是近战够得着的距离</b>，不需要每个动作都配 —— 大多数攻击只用默认值。
         * 想特调（比如长枪、大范围横斩）再用 {@link #withAttackRange(float)} 覆盖。
         *
         * <p>注意<b>不能拿 {@code hits[].scope} 当攻击距离</b>：那是 AoE 半径，
         * 不是「够得着多远」。早期版本用它算，结果薇斯娜会在 8 格外就停下不出手。
         */
        public float effectiveAttackRange() {
            return attackRange > 0f ? attackRange : DEFAULT_MELEE_RANGE;
        }
    }
