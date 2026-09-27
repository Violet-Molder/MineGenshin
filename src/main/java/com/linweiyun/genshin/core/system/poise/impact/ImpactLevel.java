package com.linweiyun.genshin.core.system.poise.impact;

/**
 * 硬直等级 —— 文献「常用冲击类型」那张 0–9 表的代码化。
 *
 * <h2>它是什么</h2>
 * 一次攻击除了削多少韧，还带一个「这一下打出去是什么效果」的等级：
 * 无影响 → 微颤 → 轻击 → 击退 → 击飞。等级越高，越能打断动作、越能把目标推走。
 * 具体的「推不推得动」还要看目标重量，判定在 {@link ImpactSolver}。
 *
 * <h2>为什么不是枚举</h2>
 * 文献表里既有固定的 0–9 档，也有 {@code （击退，300，0）}、{@code （击飞，0，1050）}
 * 这种「硬直等级 + 自定水平/竖直冲击力」的写法。所以这里把
 * <b>硬直等级</b>与<b>力值</b>拆开：{@link #ofLevel(int)} 取标准档，
 * {@link #custom(Hardiness, float, float)} 表达自定力值，两者共用同一套判定。
 *
 * <h2>数值来源</h2>
 * {@code 知识库/数据/原神-韧性力学.json → 角色数据-常用冲击类型}（11 行：默认 + 等级 0–9）。
 * 表里等级 2 叫「轻击」、等级 3/4 叫「击退」、等级 5–9 叫「击飞」。
 *
 * <p>本类只描述「这一下是什么」。判定与施加冲量在 {@link ImpactSolver}；
 * 不在这里读实体、不在这里改世界。
 */
public final class ImpactLevel {

    /** 文献表最小等级（无影响）。 */
    public static final int MIN_LEVEL = 0;

    /** 文献表最大等级。 */
    public static final int MAX_LEVEL = 9;

    /**
     * 硬直等级名 —— 表里的「硬直等级」列。
     *
     * <p>{@code defaultLevel} 是这个等级名在 0–9 表里的代表档：
     * 击退取 3（轻击档之上那一档），击飞取 5（击飞的第一档）。
     * 自定力值只借用等级名时，用它决定「算不算打断动作」。
     */
    public enum Hardiness {
        /** 无影响 —— 既不打断也不推。 */
        NONE("无影响", 0),
        /** 微颤 —— 最轻的一档；破韧期间对普通敌人照样能打断动作（见 {@link #interruptStrength()}）。 */
        TREMBLE("微颤", 1),
        /** 轻击 —— 大部分武器普攻的档位。 */
        LIGHT("轻击", 2),
        /** 击退 —— 水平推开。 */
        KNOCKBACK("击退", 3),
        /** 击飞 —— 水平 + 竖直。 */
        LAUNCH("击飞", 5);

        private final String displayName;
        private final int defaultLevel;

        Hardiness(String displayName, int defaultLevel) {
            this.displayName = displayName;
            this.defaultLevel = defaultLevel;
        }

        /** 中文名（日志用）。 */
        public String displayName() {
            return displayName;
        }

        /** 该等级名在 0–9 表里的代表档。 */
        public int defaultLevel() {
            return defaultLevel;
        }
    }

    // ==================== 文献表的 10 个标准档 ====================

    /** 默认（= 等级 0）：无影响，0 / 0。 */
    public static final ImpactLevel DEFAULT = new ImpactLevel(0, Hardiness.NONE, 0f, 0f);

    /** 等级 0：无影响，0 / 0。 */
    public static final ImpactLevel NONE = DEFAULT;

    /** 等级 1：微颤，0 / 0。 */
    public static final ImpactLevel TREMBLE = new ImpactLevel(1, Hardiness.TREMBLE, 0f, 0f);

    /** 等级 2：轻击，水平 200。 */
    public static final ImpactLevel LIGHT = new ImpactLevel(2, Hardiness.LIGHT, 200f, 0f);

    /** 等级 3：击退，水平 200。 */
    public static final ImpactLevel KNOCKBACK_WEAK = new ImpactLevel(3, Hardiness.KNOCKBACK, 200f, 0f);

    /** 等级 4：击退，水平 800。 */
    public static final ImpactLevel KNOCKBACK = new ImpactLevel(4, Hardiness.KNOCKBACK, 800f, 0f);

    /** 等级 5：击飞，水平 480 / 竖直 600。 */
    public static final ImpactLevel LAUNCH_5 = new ImpactLevel(5, Hardiness.LAUNCH, 480f, 600f);

    /** 等级 6：击飞，水平 655 / 竖直 800。 */
    public static final ImpactLevel LAUNCH_6 = new ImpactLevel(6, Hardiness.LAUNCH, 655f, 800f);

    /** 等级 7：击飞，水平 0 / 竖直 800。 */
    public static final ImpactLevel LAUNCH_7 = new ImpactLevel(7, Hardiness.LAUNCH, 0f, 800f);

    /** 等级 8：击飞，水平 795 / 竖直 900。 */
    public static final ImpactLevel LAUNCH_8 = new ImpactLevel(8, Hardiness.LAUNCH, 795f, 900f);

    /** 等级 9：击飞，水平 1200 / 竖直 600。 */
    public static final ImpactLevel LAUNCH_9 = new ImpactLevel(9, Hardiness.LAUNCH, 1200f, 600f);

    /** 按等级直接取标准档；下标就是等级。 */
    private static final ImpactLevel[] BY_LEVEL = {
            NONE, TREMBLE, LIGHT, KNOCKBACK_WEAK, KNOCKBACK,
            LAUNCH_5, LAUNCH_6, LAUNCH_7, LAUNCH_8, LAUNCH_9
    };

    private final int level;
    private final Hardiness hardiness;
    private final float horizontalForce;
    private final float verticalForce;

    private ImpactLevel(int level, Hardiness hardiness, float horizontalForce, float verticalForce) {
        this.level = level;
        this.hardiness = hardiness;
        this.horizontalForce = Math.max(0f, horizontalForce);
        this.verticalForce = Math.max(0f, verticalForce);
    }

    /**
     * 取标准档。
     *
     * @param level 0–9，超出会被钳进区间（配错不崩）
     */
    public static ImpactLevel ofLevel(int level) {
        return BY_LEVEL[Math.clamp(level, MIN_LEVEL, MAX_LEVEL)];
    }

    /**
     * 自定力值 —— 保留某个等级名的语义，但水平/竖直力按调用方给。
     *
     * <p>例：反应表里的 {@code 击退，240，300} 用
     * {@code custom(Hardiness.KNOCKBACK, 240f, 300f)}。
     */
    public static ImpactLevel custom(Hardiness hardiness, float horizontalForce, float verticalForce) {
        if (hardiness == null) {
            hardiness = Hardiness.NONE;
        }
        return new ImpactLevel(hardiness.defaultLevel(), hardiness, horizontalForce, verticalForce);
    }

    /**
     * 自定力值 —— 以 0–9 里的某一档决定硬直等级，力值按调用方给。
     *
     * <p>例：{@code 击飞，0，1050} 用 {@code custom(5, 0f, 1050f)}。
     */
    public static ImpactLevel custom(int level, float horizontalForce, float verticalForce) {
        ImpactLevel base = ofLevel(level);
        return new ImpactLevel(base.level, base.hardiness, horizontalForce, verticalForce);
    }

    /** 0–9 等级。 */
    public int level() {
        return level;
    }

    /** 硬直等级名。 */
    public Hardiness hardiness() {
        return hardiness;
    }

    /** 水平冲击力（文献原始刻度）。 */
    public float horizontalForce() {
        return horizontalForce;
    }

    /** 竖直冲击力（文献原始刻度）。 */
    public float verticalForce() {
        return verticalForce;
    }

    /** 无影响：既不打断也不推。 */
    public boolean isNone() {
        return hardiness == Hardiness.NONE;
    }

    /** 击飞档。 */
    public boolean isLaunch() {
        return hardiness == Hardiness.LAUNCH;
    }

    /**
     * 这一下的<b>打断强度</b> —— 就是硬直等级本身：无影响 0 / 微颤 1 / 轻击 2 /
     * 击退 3–4 / 击飞 5–9。
     *
     * <p>它不和某个常数比，而是和目标的<b>抗打断系数</b>比大小：
     * 打断强度 ≥ 抗打断系数 才打断动作，判定在
     * {@code core.system.control.Controllable#applyHitControl}。
     * 普通敌人的系数是 1，所以破韧期间连微颤都能打断；
     * 首领的系数高过最强的一档，只能靠破绽窗口打断一次。
     *
     * <p>⚠️ 口径更正（2026-09-25）：早先这里是一个「等级 ≥ 2 才打断」的布尔
     * （{@code canInterruptAction()}），理由是「微颤只是晃一下、不该取消动作」。
     * 用户更正为<b>破韧期间敌人处于几乎无控制抗性、随便攻击都能打断其目前的动作</b>，
     * 于是判据改成「和对方的抗性比大小」—— 门槛落在目标身上，不再是一个常数。
     */
    public int interruptStrength() {
        return level;
    }

    /** 换一组力值，保留当前等级。 */
    public ImpactLevel withForces(float horizontalForce, float verticalForce) {
        return new ImpactLevel(level, hardiness, horizontalForce, verticalForce);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImpactLevel that)) {
            return false;
        }
        return level == that.level
                && hardiness == that.hardiness
                && Float.compare(horizontalForce, that.horizontalForce) == 0
                && Float.compare(verticalForce, that.verticalForce) == 0;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(level);
        result = 31 * result + hardiness.hashCode();
        result = 31 * result + Float.hashCode(horizontalForce);
        result = 31 * result + Float.hashCode(verticalForce);
        return result;
    }

    @Override
    public String toString() {
        return "ImpactLevel{等级 " + level + " " + hardiness.displayName()
                + ", 水平 " + horizontalForce + ", 竖直 " + verticalForce + "}";
    }
}
