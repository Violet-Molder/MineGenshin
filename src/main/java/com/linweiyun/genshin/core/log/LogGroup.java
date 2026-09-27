package com.linweiyun.genshin.core.log;

/**
 * 本模组日志的分组。
 *
 * <p>每个组自带一个开关（默认全开），和主类 {@code Minegenshin.LOG_ENABLED} 的总开关是
 * 「与」的关系：总开关关掉时所有组都不出声，总开关开着时各组各管各的。
 * 也就是说排查某一块时可以只留那一组，其余全静音。</p>
 *
 * <p>组是<b>写在创建 logger 的那一行</b>的（{@code ModLog.getLogger(LogGroup.COMBAT)}），
 * 不是按包名猜的：同一个包里需要分开管的类，各自选自己的组就行。</p>
 *
 * <p>运行期切换：{@code LogGroup.COMBAT.setEnabled(false)}；批量切换见 {@link ModLog}。</p>
 */
public enum LogGroup {

    /** 战斗动作、伤害结算、目标选择、攻击扫掠 */
    COMBAT("战斗（动作 / 伤害 / 目标选择）"),
    /** 元素附着宿主与元素反应 */
    ELEMENT("元素（附着 / 反应）"),
    /** 角色实体、天赋、命座与角色增益效果 */
    CHARACTER("角色（角色 / 角色效果）"),
    /** 物品、实体、技能节点等内容侧 */
    CONTENT("内容（物品 / 实体 / 技能节点）"),
    /** 客户端渲染、资源包、界面与飘字 */
    RENDER("渲染（几何 / 资源 / 界面 / 飘字）"),
    /** 其余核心服务：网络、存档附件、状态、资源包、刷怪、卡池、音效、主类自身 */
    CORE("核心服务（网络 / 存档 / 状态 / 资源包 / 刷怪 / 卡池 / 音效）"),
    /** 混入（含跨模组兼容的混入插件） */
    MIXIN("混入（Mixin）");

    /** 中文说明，只用于日志/文档里指认这个组是什么 */
    private final String label;

    /** 本组开关。{@code volatile}：切换可能发生在客户端/服务端之外的管理线程 */
    private volatile boolean enabled = true;

    LogGroup(String label) {
        this.label = label;
    }

    /** 这一组管的是什么 */
    public String label() {
        return label;
    }

    /** 本组自己的开关（不含主类总开关），默认 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /** 单独开关这一组 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
