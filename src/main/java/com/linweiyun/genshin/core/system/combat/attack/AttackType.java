package com.linweiyun.genshin.core.system.combat.attack;

/**
 * 攻击类型枚举 —— 定义原神中所有攻击的分类
 *
 * 每个攻击类型拥有一个衰减标签（decayTag）：
 * - 衰减标签决定该攻击是否参与附着冷却，以及哪些攻击共用同一套计时计数器
 * - 同标签 + 同组别 = 共用一套计时计数器
 * - 不同标签 = 独立计算计时计数器
 * - 无标签（null）= 不参与附着冷却系统
 *
 * 衰减组别（DecayGroup）不在此枚举中定义，由具体角色/技能在创建伤害容器时指定
 * 如果未指定，则使用默认组别（DecayGroup.DEFAULT）
 *
 * 计时计数器存储在目标实体身上，按 (目标, 攻击者, 角色, 标签) 独立计算
 */
public enum AttackType {

    // 普通攻击 —— 角色的基础攻击
    // 默认标签 "normal_attack"：同角色所有普攻段共用一套计时计数器
    NORMAL_ATTACK("normal_attack", "attack_type.normal"),

    // 重击 —— 长按攻击键释放的蓄力攻击
    // 默认标签 "charged_attack"：独立于普攻的计时计数器
    CHARGED_ATTACK("charged_attack", "attack_type.charged"),

    // 下落攻击 —— 从空中下落的攻击
    // 默认标签 "plunging_attack"：独立于普攻和重击
    // 注意：空中下落阶段元素量为0（无法附着），落地冲击波元素量为1（可附着）
    PLUNGING_ATTACK("plunging_attack", "attack_type.plunging"),

    // 元素战技 —— 角色的E技能
    // 默认标签 "elemental_skill"：独立于普攻的计时计数器
    // 部分角色的战技和普攻可能共用标签（如可莉），由具体技能配置决定
    ELEMENTAL_SKILL("elemental_skill", "attack_type.skill"),

    // 元素爆发 —— 角色的Q技能
    // 默认标签 "elemental_burst"：完全独立的计时计数器
    ELEMENTAL_BURST("elemental_burst", "attack_type.burst"),

    // 特殊/环境伤害 —— 非角色直接造成的伤害
    // 无标签（null）：不参与附着冷却系统
    SPECIAL(null, "attack_type.special"),

    //怪物伤害
    MONSTER(null, "attack_type.monster"),

    // 月感电 —— 雷暴云造成的月曜反应伤害
    LUNAR_CHARGED(null, "attack_type.lunar_charged"),

    // 扩散 —— 风元素造成的剧变反应范围伤害
    ELECTRO_CHARGED(null, "attack_type.electro_charged"),
    SWIRL(null, "attack_type.swirl"),

    // 星扩散 —— 冰扩散的星烁反应变体
    STELLAR_SWIRL(null, "attack_type.stellar_swirl"),
    STELLAR_CONDUCE(null, "attack_type.stellar_conduce");

    // 衰减标签 —— 决定该攻击类型的附着冷却共用关系
    // null表示不使用附着冷却系统（如特殊/环境伤害）
    private final String decayTag;

    // 翻译键 —— 用于多语言显示
    private final String translationKey;

    /**
     * 构造函数
     * @param decayTag 衰减标签（null=不使用附着冷却）
     * @param translationKey 翻译键
     */
    AttackType(String decayTag, String translationKey) {
        this.decayTag = decayTag;                   // 设置衰减标签
        this.translationKey = translationKey;       // 设置翻译键
    }

    /** 获取衰减标签 */
    public String getDecayTag() { return decayTag; }

    /** 获取翻译键 */
    public String getTranslationKey() { return translationKey; }

    /**
     * 判断该攻击类型是否使用附着冷却系统
     * @return true=使用衰减标签，参与附着冷却；false=无标签，不参与
     */
    public boolean hasDecayTag() {
        return decayTag != null;                    // 标签非空即参与冷却
    }

    /**
     * 判断两种攻击类型是否共用同一套计时计数器
     * 共用条件：同标签 + 同组别（组别由DecayGroup决定，这里只判断标签）
     * @param other 另一个攻击类型
     * @return true=可能共用（标签相同），false=一定不共用
     */
    public boolean sharesTimerWith(AttackType other) {
        if (this.decayTag == null || other.decayTag == null) return false;  // 任一方无标签则不共用
        return this.decayTag.equals(other.decayTag);                        // 标签相同则可能共用
    }
}
