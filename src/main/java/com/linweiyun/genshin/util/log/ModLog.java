package com.linweiyun.genshin.util.log;

import com.linweiyun.genshin.Minegenshin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本模组的 logger 工厂：所有 {@code LOGGER} 字段都从这里拿。
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
