package com.linweiyun.genshin.client.render.optimize;

/**
 * 几何提交入口。
 *
 * <p>{@link #trySubmit} 的返回值表示「本次有没有自己提交几何」：{@code true} 表示已经提交、
 * 调用方应跳过默认提交；{@code false} 表示没有提交、调用方按常规路径渲染。
 * {@link #submitDefault} 用于补一次默认提交。</p>
 *
 * <p>两个方法的参数都不参与计算，调用方可以传 {@code null}。</p>
 *
 * <p><b>调用约束</b>：这两个方法都不产生几何 —— 需要渲染时，调用方必须走 GeckoLib 的
 * 渲染入口，不能只靠这里返回的「没有提交」就认为几何已经被画出来。</p>
 */
public final class GeoRenderIntercept {
    private GeoRenderIntercept() {
    }

    /**
     * 是否有自己提交的几何。
     *
     * @return 恒为 {@code false}（本实现不自己提交任何几何）
     */
    public static boolean trySubmit(Object renderPassInfo, Object renderTasks, Object renderType) {
        return false;
    }

    /**
     * 补一次默认几何提交。本实现为空方法。
     */
    public static void submitDefault(Object renderPassInfo, Object renderTasks, Object renderType) {
    }
}
