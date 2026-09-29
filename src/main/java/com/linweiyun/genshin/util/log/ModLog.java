package com.linweiyun.genshin.util.log;

import com.linweiyun.genshin.Minegenshin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本模组的 logger 工厂：所有 {@code LOGGER} 字段都从这里拿。
 *
 * <h2>为什么要有这一层</h2>
 * 原版写法 {@code LogUtils.getLogger()} 的调用点必须紧贴在字段初始化那一行——它靠栈帧找
 * 「谁在调我」，日志里那个 {@code [com.linweiyun.genshin.xxx.Yyy/]} 就是这个类的名字。
 * 我们想统一加开关，就不能简单地把它换成一个中间类：那样名字会全部变成中间类，
 * 日志里再也看不出是哪个类打的了。
 *
 * <p>所以这里的做法是：<b>名字照旧取自调用者类</b>（{@link #getLogger} 自己走一遍栈帧，
 * 跳过本类的帧），外层再套一个 {@link GroupLogger} 做闸门。对调用点来说只有创建那一行变了，
 * 打印出来的名字、级别、格式和从前一字不差。</p>
 *
 * <h2>开关怎么算</h2>
 * 一次日志要出声，必须同时满足：
 * <ol>
 *   <li>{@link Minegenshin#LOG_ENABLED} —— 总开关（在主类里）；</li>
 *   <li>{@link LogGroup#isEnabled()} —— 这条日志所属组的开关。</li>
 * </ol>
 * 开关是<b>每次调用现查</b>的，不是创建时定死，所以运行期改立刻生效：
 * <pre>{@code
 * LogGroup.COMBAT.setEnabled(false);   // 只静音战斗那一组
 * ModLog.enableOnly(LogGroup.RENDER);  // 只留渲染组，其余全静音
 * Minegenshin.LOG_ENABLED = false;     // 整个模组不再打任何日志
 * }</pre>
 *
 * <p>闸门关掉时连 {@code isInfoEnabled()} 这类探测也一并返回 {@code false}，
 * 调用点那些「先问再拼」的写法会连字符串拼装都省掉。</p>
 *
 * <p>本类不碰 Minecraft 的类型，只有 {@code Minegenshin} 的一个 {@code boolean} 字段，
 * 所以混入插件那种跑得比模组构造还早的代码也能安全地用它。</p>
 */
public final class ModLog {

    /** 找调用者类用。{@code RETAIN_CLASS_REFERENCE} 才能拿到 Class 而不是字符串 */
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private ModLog() {
    }

    /**
     * 给声明它的那个类拿一个 logger。
     *
     * <p>写法固定为字段初始化那一行：
     * {@code private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);}</p>
     *
     * @param group 这条日志属于哪一组，决定它跟哪个分组开关走
     * @return logger 名字 = 调用本方法的类，日志里显示的类名与从前一致
     */
    public static Logger getLogger(LogGroup group) {
        return new GroupLogger(LoggerFactory.getLogger(callerClass()), group);
    }

    /** 这一组现在会不会出声（总开关 + 组开关） */
    public static boolean isEnabled(LogGroup group) {
        return Minegenshin.LOG_ENABLED && group.isEnabled();
    }

    /** 单独开关一组 */
    public static void setEnabled(LogGroup group, boolean enabled) {
        group.setEnabled(enabled);
    }

    /** 只留一组出声，其余全部静音（总开关保持不动） */
    public static void enableOnly(LogGroup group) {
        for (LogGroup g : LogGroup.values()) {
            g.setEnabled(g == group);
        }
    }

    /** 所有组都打开（总开关保持不动） */
    public static void enableAllGroups() {
        for (LogGroup g : LogGroup.values()) {
            g.setEnabled(true);
        }
    }

    /** 所有组都静音（等价于只关总开关，但保留各自原来的开关状态） */
    public static void disableAllGroups() {
        for (LogGroup g : LogGroup.values()) {
            g.setEnabled(false);
        }
    }

    /**
     * 调用 {@link #getLogger} 的那个类。
     *
     * <p>栈里第一帧是本方法自己（可能还有一层 lambda），全部跳过后就是真正的调用者。
     * 取不到时退回本类，宁可名字难看也不要抛异常——日志工厂抛异常会把调用方的类初始化一起带崩。</p>
     */
    private static Class<?> callerClass() {
        return WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(c -> c != ModLog.class)
                .findFirst()
                .orElse(ModLog.class));
    }
}
