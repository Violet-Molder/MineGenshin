package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 一次伤害的<b>计算记录</b>：缩写公式 + 展开公式（可多行）+ 数值（可多行）。
 *
 * <h2>单人伤害长什么样</h2>
 * <pre>
 * [伤害] 直伤 | 攻击类型=普通攻击 元素=风 攻击者=薇斯娜 目标=僵尸 反应=无
 *   缩写公式 伤害 = 基础区 × 暴击区 × 倍率区 × 增伤区 × 防御区 × 抗性区 × 衰减区
 *   展开公式 伤害 = 【攻击力 × 攻击力倍率 + 附加伤害】 × 【1 + 暴击伤害】 × 【1 + 倍率提升】 × 【1】 × 【(攻方等级×5+500)/(攻方等级×5+500+守方防御)】 × 【1 - 抗性】 × 【衰减伤害系数】
 *   数值　　 伤害 = 【1144.625 × 0.130 + 0】 × 【1 + 0.966】 × 【1 + 0】 × 【1】 × 【(90×5+500)/(90×5+500+815)】 × 【1 - 0.100】 × 【1】 = 141.714
 * </pre>
 *
 * <h2>多人参与的反应伤害长什么样</h2>
 * <pre>
 *   缩写公式 伤害 = 单人理论伤害 × 权重（逐名求和）
 *   展开公式 单人理论伤害 = 【攻击力 × 星辉系数】 × 【1 + 16×元素精通/(元素精通+2000) + 星烁加成】 × 【1 - 抗性】 × 【1 + 暴击伤害】 × 【擢升区】 × 【大权区】
 *   展开公式 伤害 = 【第1名】 × 0.6 + 【第2名】 × 0.3 + 【第3名】 × 0.05 + 【第4名】 × 0.05
 *   数值　　 单人1 = 【1144.625 × 0.5】 × 【1 + 16×200/(200+2000) + 0.140】 × 【1 - 0.100】 × 【1 + 0.966】 × 【1 + 0.200】 × 【1 + 0.300】 = 16.2
 *   数值　　 单人2 = 【1144.625 × 0.5】 × 【1 + 16×200/(200+2000) + 0.140】 × 【1 - 0.100】 × 【1 + 0.966】 × 【1 + 0.200】 × 【1 + 0.300】 = 16.2
 *   数值　　 伤害 = 【16.2】 × 0.6 + 【16.2】 × 0.3 = 14.58
 * </pre>
 *
 * <h2>规矩</h2>
 * <ol>
 *   <li><b>公式里不写逻辑判断</b>（没有 {@code ? :}）—— 只写表达式，实际结果看数值；</li>
 *   <li><b>每个乘区一个【】</b>，展开公式与数值的【】一一对应；</li>
 *   <li><b>只有「值为 0 的属性项」可以省略</b>（基础区那 4 个属性里没参与的那些）；
 *       其余项哪怕没赋额外值（0 或 1）也要写出来；</li>
 *   <li><b>数值行是把数字代进公式</b>，不是把算好的乘区结果填进去 ——
 *       乘区内部用 {@code × / + / ()} 把数字摆成和展开公式<b>一模一样的位置</b>，
 *       这样才可以把数字和公式里的名字一一对应起来；</li>
 *   <li><b>数值行只有数字与运算符</b>，不掺任何描述文字。</li>
 * </ol>
 */
public final class DamageTrace {

    /** 开发环境总开关：false 时 LOGGER 为无操作实现，所有日志完全静默。发布前改为 false。 */
    private static final boolean DEV_LOGGING = false;

    private static final Logger LOGGER = DEV_LOGGING ? ModLog.getLogger(LogGroup.COMBAT) : NOPLogger.NOP_LOGGER;

    /**
     * 总开关：false 时 {@link #log()} 什么都不做。
     *
     * <p>它只控制「收不收」，真正决定「要不要拼字符串」的是 {@link #active()}。</p>
     */
    public static boolean ENABLED = true;

    /**
     * <b>真正会输出吗</b> —— 总开关打开、开发日志打开、底层 logger 也允许 INFO 时才为 true。
     *
     * <p>这个判断存在的意义是<b>省掉构建本身</b>：公式日志的每一行都是调用方现场拼出来的
     * （{@code "1 + " + fmt(x)} 这种），{@code ENABLED} 只挡得住「收」挡不住「拼」。
     * 而 {@link #DEV_LOGGING} 为 false 时 {@link #LOGGER} 是 {@code NOPLogger}，
     * 拼出来的那一大串最后只会被丢掉 —— 每次伤害白拼一遍，攻速一高就是纯粹的 CPU 与 GC 开销。</p>
     *
     * <p>所以四个伤害管线里的日志块都包在 {@code if (DamageTrace.active())} 里；
     * {@code DEV_LOGGING} 是编译期常量 false，JIT 会把整块当死代码消掉。</p>
     */
    public static boolean active() {
        return ENABLED && DEV_LOGGING && LOGGER.isInfoEnabled();
    }

    /** 不记录时共用的空实现：所有写入口都是空操作，所以可以被所有伤害共享。 */
    private static final DamageTrace NOOP = new DamageTrace("");

    private final String pipeline;
    private final List<String> head = new ArrayList<>();

    private String slim;
    /** 由 {@link #zone} 累积的「伤害 = 【】 × 【】…」两行（单人伤害用）。 */
    private final StringBuilder zoneFormula = new StringBuilder();
    private final StringBuilder zoneValue = new StringBuilder();
    /** 额外的展开公式行 / 数值行（多人反应用）。 */
    private final List<String> fullLines = new ArrayList<>();
    private final List<String> valueLines = new ArrayList<>();
    private final List<String> valueLabels = new ArrayList<>();
    private final List<String> shortNames = new ArrayList<>();
    private String result;

    private DamageTrace(String pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * 开始记录一次伤害。
     *
     * <p>不记录时直接返回共享的空实现 —— 连这个对象的 2 个 StringBuilder、
     * 5 个 ArrayList 都不再为每次伤害各建一份。</p>
     */
    public static DamageTrace start(String pipeline) {
        return active() ? new DamageTrace(pipeline) : NOOP;
    }

    /**
     * 不记录时用的空实现。
     *
     * <p>给「连 {@code pipelineName(...)} 这种小参数都懒得算」的调用方用：
     * {@code trace = active() ? start(name) : none()}。</p>
     */
    public static DamageTrace none() {
        return NOOP;
    }

    public boolean isEmpty() {
        return shortNames.isEmpty() && fullLines.isEmpty();
    }

    // ==================== 头部 ====================

    public DamageTrace head(String key, Object value) {
        if (active()) {
            head.add(key + "=" + fmt(value));
        }
        return this;
    }

    public DamageTrace headAttack(Object attackType, Object element) {
        return head("攻击类型",
                attackType instanceof AttackType type ? DamageLabels.attackType(type) : attackType)
                .head("元素", element);
    }

    public DamageTrace headEntities(LivingEntity attacker, LivingEntity target, Object attackerName) {
        return head("攻击者", attackerName == null ? nameOf(attacker) : pretty(attackerName))
                .head("目标", nameOf(target));
    }

    // ==================== 公式与数值 ====================

    /** 缩写公式（只列参与的乘区）。不调的话用 {@link #zone} 收上来的乘区名拼。 */
    public DamageTrace slim(String formula) {
        if (active()) {
            this.slim = formula;
        }
        return this;
    }

    /**
     * 加一个乘区：展开公式与数值各追加一个【】。
     *
     * @param shortName 缩写公式里的名字（基础区 / 暴击区…）
     * @param formula   展开公式（只写表达式）
     * @param value     数值写法：<b>把数字代进上面那条公式</b>（只有数字与运算符，不加描述）
     */
    public DamageTrace zone(String shortName, String formula, String value) {
        if (!active()) {
            return this;
        }
        shortNames.add(shortName);
        if (zoneFormula.length() > 0) {
            zoneFormula.append(" × ");
            zoneValue.append(" × ");
        }
        zoneFormula.append('【').append(formula).append('】');
        zoneValue.append('【').append(value).append('】');
        return this;
    }

    /** 另起一行展开公式（多人反应用：单人公式 / 加权公式）。 */
    public DamageTrace fullLine(String formula) {
        if (active()) {
            fullLines.add(formula);
        }
        return this;
    }

    /** 另起一行数值（多人反应用：单人1 / 单人2 / 加权）。 */
    public DamageTrace valueLine(String label, String value) {
        if (active()) {
            valueLabels.add(label);
            valueLines.add(value);
        }
        return this;
    }

    /** 结果（追加到最后一行数值的末尾）。 */
    public DamageTrace result(Object value) {
        if (active()) {
            result = fmt(value);
        }
        return this;
    }

    // ==================== 输出 ====================

    public void log() {
        if (!active()) {
            return;
        }

        StringBuilder out = new StringBuilder(320);
        out.append("[伤害] ").append(pipeline);
        if (!head.isEmpty()) {
            out.append(" | ").append(String.join(" ", head));
        }

        String slimText = slim != null ? slim : "伤害 = " + String.join(" × ", shortNames);
        out.append("\n  缩写公式 ").append(slimText);

        if (zoneFormula.length() > 0) {
            out.append("\n  展开公式 伤害 = ").append(zoneFormula);
        }
        for (String line : fullLines) {
            out.append("\n  展开公式 ").append(line);
        }

        if (zoneValue.length() > 0) {
            out.append("\n  数值　　 伤害 = ").append(zoneValue);
            if (valueLines.isEmpty() && result != null) {
                out.append(" = ").append(result);
            }
        }
        for (int i = 0; i < valueLines.size(); i++) {
            String label = valueLabels.get(i) == null ? "伤害" : valueLabels.get(i);
            out.append("\n  数值　　 ").append(label).append(" = ").append(valueLines.get(i));
            if (i == valueLines.size() - 1 && result != null) {
                out.append(" = ").append(result);
            }
        }
        LOGGER.info(out.toString());
    }

    // ==================== 格式化 ====================

    /** 小数三位、整数不带小数点；元素拍平成中文名（anemo → 风）。 */
    public static String fmt(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Float f) {
            return trim(f);
        }
        if (value instanceof Double d) {
            return trim(d);
        }
        if (value instanceof GenshinElement element) {
            return DamageLabels.element(element);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return String.valueOf(value);
    }

    private static String trim(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return String.valueOf(d);
        }
        if (d == Math.rint(d) && Math.abs(d) < 1.0E9) {
            return String.valueOf((long) d);
        }
        return String.format(Locale.ROOT, "%.3f", d);
    }

    private static String nameOf(LivingEntity entity) {
        return entity == null ? "null" : entity.getName().getString();
    }

    private static String pretty(Object name) {
        if (name instanceof Component component) {
            return component.getString();
        }
        return String.valueOf(name);
    }
}
