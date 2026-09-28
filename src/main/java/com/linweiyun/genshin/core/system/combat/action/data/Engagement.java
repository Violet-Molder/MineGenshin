package com.linweiyun.genshin.core.system.combat.action.data;



    public final class Engagement {

        /** 该字段不覆盖，用全局默认。 */
        public static final double USE_DEFAULT = -1.0;

        /** 是不是远程招式：不突进 + 索敌距离跟着攻击距离走。 */
        public final boolean ranged;
        /** 允不允许突进（远程招式里这个开关无效，远程永远不突进）。 */
        public final boolean dash;
        /** 索敌距离（格）；{@link #USE_DEFAULT} = 近战 6 格 / 远程 = 攻击距离。 */
        public final double acquireRange;
        /** 保持锁定的距离（格）；{@link #USE_DEFAULT} = 近战 9 格 / 远程 = 索敌 + 2。 */
        public final double keepRange;
        /** 索敌视角半角（度）；{@link #USE_DEFAULT} = 全局默认。 */
        public final double acquireAngle;
        /** 保持锁定的视角半角（度）；{@link #USE_DEFAULT} = 全局默认。 */
        public final double keepAngle;
        /** 突进停到离目标几格；{@link #USE_DEFAULT} = 全局默认。 */
        public final double stopDistance;
        /** 突进速度（格/刻）；{@link #USE_DEFAULT} = 全局默认。 */
        public final double dashSpeed;
        /** 突进最多几刻；&lt;= 0 表示用全局默认。 */
        public final int maxApproachTicks;
        /** 转向最大角速度（度/刻）；{@link #USE_DEFAULT} = 全局默认。 */
        public final float turnSpeed;
        /**
         * 吸附带（格）：目标在「攻击距离 + 这个」以内时，出手瞬间朝它推一小步。
         *
         * <p>{@link #USE_DEFAULT}（-1）= 全局 {@code AttackApproach.ADHESION_BAND}；
         * <b>0 = 这一招不吸附</b>（和 {@link #adhesionStep} 任一为 0 都会关掉吸附）。
         */
        public final double adhesionBand;
        /**
         * 出手那一下的推力（冲量）；{@link #USE_DEFAULT}（-1）= 全局
         * {@code AttackApproach.ADHESION_STEP_SPEED}；<b>0 = 这一招不吸附</b>。
         *
         * <p>大剑这类「一刀就要贴上去」的角色调大，轻武器/远程调小或直接
         * {@link #withAdhesion(double, double) withAdhesion(0, 0)} 关掉。
         */
        public final double adhesionStep;

        /**
         * 近距离时是否朝目标迈一步吸附。{@code true}（默认）= 出手时朝目标推一小步；
         * {@code false} = 只转向不移动（不跟磁铁一样）。
         */
        public final boolean approachStep;

        public Engagement(boolean ranged, boolean dash,
                          double acquireRange, double keepRange,
                          double acquireAngle, double keepAngle,
                          double stopDistance, double dashSpeed,
                          int maxApproachTicks, float turnSpeed) {
            this(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, USE_DEFAULT, USE_DEFAULT, true);
        }

        public Engagement(boolean ranged, boolean dash,
                          double acquireRange, double keepRange,
                          double acquireAngle, double keepAngle,
                          double stopDistance, double dashSpeed,
                          int maxApproachTicks, float turnSpeed,
                          double adhesionBand, double adhesionStep) {
            this(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed,
                    adhesionBand, adhesionStep, true);
        }

        public Engagement(boolean ranged, boolean dash,
                          double acquireRange, double keepRange,
                          double acquireAngle, double keepAngle,
                          double stopDistance, double dashSpeed,
                          int maxApproachTicks, float turnSpeed,
                          double adhesionBand, double adhesionStep,
                          boolean approachStep) {
            this.ranged = ranged;
            this.dash = dash;
            this.acquireRange = acquireRange;
            this.keepRange = keepRange;
            this.acquireAngle = acquireAngle;
            this.keepAngle = keepAngle;
            this.stopDistance = stopDistance;
            this.dashSpeed = dashSpeed;
            this.maxApproachTicks = maxApproachTicks;
            this.turnSpeed = turnSpeed;
            this.adhesionBand = adhesionBand;
            this.adhesionStep = adhesionStep;
            this.approachStep = approachStep;
        }

        /** 近战默认：索敌 6 / 保持 9 / 攻击距离外突进贴脸。 */
        public static Engagement melee() {
            return new Engagement(false, true, USE_DEFAULT, USE_DEFAULT, USE_DEFAULT, USE_DEFAULT,
                    USE_DEFAULT, USE_DEFAULT, 0, (float) USE_DEFAULT);
        }

        /** 远程：不突进、索敌 = 攻击距离、只转向。 */
        public static Engagement ranged() {
            return new Engagement(true, false, USE_DEFAULT, USE_DEFAULT, USE_DEFAULT, USE_DEFAULT,
                    USE_DEFAULT, USE_DEFAULT, 0, (float) USE_DEFAULT);
        }

        /** 这一招实际会不会突进。 */
        public boolean wantsDash() {
            return !ranged && dash;
        }

        /** 装备/刻数全量重建（内部用；加字段时只改这一处）。 */
        private Engagement copy(boolean ranged, boolean dash, double acquireRange, double keepRange,
                                double acquireAngle, double keepAngle, double stopDistance,
                                double dashSpeed, int maxApproachTicks, float turnSpeed,
                                double adhesionBand, double adhesionStep) {
            return new Engagement(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed,
                    adhesionBand, adhesionStep, this.approachStep);
        }

        /**
         * 近距离时是否朝目标迈一步吸附。
         *
         * <p>{@code true}（默认）= 出手时朝目标推一小步；{@code false} = 只转向不移动。
         */
        public Engagement withApproachStep(boolean enabled) {
            return new Engagement(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed,
                    adhesionBand, adhesionStep, enabled);
        }

        public Engagement withDash(boolean enabled) {
            return copy(ranged, enabled, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, adhesionBand, adhesionStep);
        }

        public Engagement withAcquireRange(double range) {
            return copy(ranged, dash, range, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, adhesionBand, adhesionStep);
        }

        public Engagement withKeepRange(double range) {
            return copy(ranged, dash, acquireRange, range, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, adhesionBand, adhesionStep);
        }

        /** 索敌与保持的视角半角（度）。 */
        public Engagement withAngles(double acquire, double keep) {
            return copy(ranged, dash, acquireRange, keepRange, acquire, keep,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, adhesionBand, adhesionStep);
        }

        /** 突进手感三件套：停几格 / 多快 / 最多几刻。 */
        public Engagement withDashProfile(double stopDistance, double dashSpeed, int maxApproachTicks) {
            return copy(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, adhesionBand, adhesionStep);
        }

        /** 转向最大角速度（度/刻）——想让人物转得更急/更从容就调这个。 */
        public Engagement withTurnSpeed(float degreesPerTick) {
            return copy(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, degreesPerTick, adhesionBand, adhesionStep);
        }

        /**
         * 吸附（每次出手朝目标推一小步）：推多远生效 / 一步多大力。
         *
         * <pre>
         * 大剑：   .withAdhesion(2.0, 0.30)   // 带得更宽、一步更沉
         * 短刀：   .withAdhesion(1.2, 0.16)   // 贴身才吸、步子轻
         * 不要吸附：.withAdhesion(0, 0)        // 0 = 关掉（两个里任一为 0 就关）
         * 用默认：  .withAdhesion(-1, -1)      // -1 = USE_DEFAULT
         * </pre>
         */
        public Engagement withAdhesion(double band, double step) {
            return copy(ranged, dash, acquireRange, keepRange, acquireAngle, keepAngle,
                    stopDistance, dashSpeed, maxApproachTicks, turnSpeed, band, step);
        }
    }