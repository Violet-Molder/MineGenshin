package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModAttributes {
    public static final DeferredRegister<AttributeType> ATTRIBUTES = ModRegistries.ATTRIBUTE_TYPES;

    // Base Stats
    public static final DeferredHolder<AttributeType, AttributeType> MAX_HP =
            ATTRIBUTES.register("max_hp", () -> new AttributeType("max_hp", "attribute.minegenshin.max_hp", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ATK =
            ATTRIBUTES.register("atk", () -> new AttributeType("atk", "attribute.minegenshin.atk", 0));
    public static final DeferredHolder<AttributeType, AttributeType> DEF =
            ATTRIBUTES.register("def", () -> new AttributeType("def", "attribute.minegenshin.def", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ELEMENTAL_MASTERY =
            ATTRIBUTES.register("elemental_mastery", () -> new AttributeType("elemental_mastery", "attribute.minegenshin.elemental_mastery", 0));
    public static final DeferredHolder<AttributeType, AttributeType> MAX_STAMINA =
            ATTRIBUTES.register("max_stamina", () -> new AttributeType("max_stamina", "attribute.minegenshin.max_stamina", 0));

    // Advanced Stats
    public static final DeferredHolder<AttributeType, AttributeType> CR =
            ATTRIBUTES.register("crit_rate", () -> new AttributeType("crit_rate", "attribute.minegenshin.crit_rate", 0.05f));
    public static final DeferredHolder<AttributeType, AttributeType> CDG =
            ATTRIBUTES.register("crit_dmg", () -> new AttributeType("crit_dmg", "attribute.minegenshin.crit_dmg", 0.5f));
    public static final DeferredHolder<AttributeType, AttributeType> HB =
            ATTRIBUTES.register("healing_bonus", () -> new AttributeType("healing_bonus", "attribute.minegenshin.healing_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> IHB =
            ATTRIBUTES.register("ihb", () -> new AttributeType("ihb", "attribute.minegenshin.ihb", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ER =
            ATTRIBUTES.register("energy_recharge", () -> new AttributeType("energy_recharge", "attribute.minegenshin.energy_recharge", 1.0f));
    public static final DeferredHolder<AttributeType, AttributeType> CDR =
            ATTRIBUTES.register("cd_reduction", () -> new AttributeType("cd_reduction", "attribute.minegenshin.cd_reduction", 0));
    public static final DeferredHolder<AttributeType, AttributeType> SS =
            // 翻译键必须和 lang 文件里的条名一致（attribute.minegenshin.shield_strength）。
            // 原来写成 attribute.minegenshin.ss，面板上就原样显示了一串键名。
            ATTRIBUTES.register("shield_strength", () -> new AttributeType("shield_strength", "attribute.minegenshin.shield_strength", 0));

    // Elemental Type
    public static final DeferredHolder<AttributeType, AttributeType> PYRO_BONUS =
            ATTRIBUTES.register("pyro_bonus", () -> new AttributeType("pyro_bonus", "attribute.minegenshin.pyro_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> PYRO_RES =
            ATTRIBUTES.register("pyro_res", () -> new AttributeType("pyro_res", "attribute.minegenshin.pyro_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> HYDRO_BONUS =
            ATTRIBUTES.register("hydro_bonus", () -> new AttributeType("hydro_bonus", "attribute.minegenshin.hydro_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> HYDRO_RES =
            ATTRIBUTES.register("hydro_res", () -> new AttributeType("hydro_res", "attribute.minegenshin.hydro_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> DENDRO_BONUS =
            ATTRIBUTES.register("dendro_bonus", () -> new AttributeType("dendro_bonus", "attribute.minegenshin.dendro_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> DENDRO_RES =
            ATTRIBUTES.register("dendro_res", () -> new AttributeType("dendro_res", "attribute.minegenshin.dendro_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ELECTRO_BONUS =
            ATTRIBUTES.register("electro_bonus", () -> new AttributeType("electro_bonus", "attribute.minegenshin.electro_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ELECTRO_RES =
            ATTRIBUTES.register("electro_res", () -> new AttributeType("electro_res", "attribute.minegenshin.electro_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ANEMO_BONUS =
            ATTRIBUTES.register("anemo_bonus", () -> new AttributeType("anemo_bonus", "attribute.minegenshin.anemo_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> ANEMO_RES =
            ATTRIBUTES.register("anemo_res", () -> new AttributeType("anemo_res", "attribute.minegenshin.anemo_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> CYRO_BONUS =
            ATTRIBUTES.register("cyro_bonus", () -> new AttributeType("cyro_bonus", "attribute.minegenshin.cyro_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> CYRO_RES =
            ATTRIBUTES.register("cyro_res", () -> new AttributeType("cyro_res", "attribute.minegenshin.cyro_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> GEO_BONUS =
            ATTRIBUTES.register("geo_bonus", () -> new AttributeType("geo_bonus", "attribute.minegenshin.geo_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> GEO_RES =
            ATTRIBUTES.register("geo_res", () -> new AttributeType("geo_res", "attribute.minegenshin.geo_res", 0));
    public static final DeferredHolder<AttributeType, AttributeType> PHYSICAL_BONUS =
            ATTRIBUTES.register("physical_bonus", () -> new AttributeType("physical_bonus", "attribute.minegenshin.physical_bonus", 0));
    public static final DeferredHolder<AttributeType, AttributeType> PHYSICAL_RES =
            ATTRIBUTES.register("physical_res", () -> new AttributeType("physical_res", "attribute.minegenshin.physical_res", 0));

    // Special Attributes
    // 生命之契
    public static final DeferredHolder<AttributeType, AttributeType> BOL =
            // 同上：注册名是 bond_of_life，翻译键也要用 attribute.minegenshin.bond_of_life
            ATTRIBUTES.register("bond_of_life", () -> new AttributeType("bol", "attribute.minegenshin.bond_of_life", 0));

    /**
     * 韧性：暂时只分 4 档，值就是档位（1 最低、4 最高），不是绝对的韧性条长度。
     *
     * <p>默认 1 —— 原版普通生物那一档。每只怪具体给几档由
     * {@link com.linweiyun.genshin.core.system.poise.PoiseTiers#poiseTier} 派生，
     * 想单独调就在怪物自己的属性表里覆盖。
     */
    public static final DeferredHolder<AttributeType, AttributeType> POISE =
            ATTRIBUTES.register("poise", () -> new AttributeType("poise", "attribute.minegenshin.poise", 1));

    /**
     * 韧性上限（这条韧性条有多长）—— 默认 0 = <b>不覆盖</b>，按档位长度算。
     *
     * <p>用户口径（2026-09-25）：「韧性上限应该是一个可变属性。对于已经出现的实体基本上是固定的，
     * 但是对一个注册表单例比如僵尸，不同环境下的韧性可能不一样。」
     * 所以长度不再只由档位推出来，而是<b>可以逐实例给</b>：
     * 同一只僵尸在普通地区与副本里可以有不同的上限，同一 registrations 单例不代表同一个数值。
     *
     * <p>分工：{@link #POISE 档位}决定「这条韧性怎么恢复」（每秒衰减、重置时间），
     * 本属性决定「它有多长」；两者都可以不配，不配就各走各自的默认。
     * 语义：配了（{@code > 0}）就是基准长度，仍然要乘联机系数；
     * {@code 0} / 没配 = 用 {@link com.linweiyun.genshin.core.system.poise.PoiseTiers.TierProfile#length()}。
     *
     * <p>属性变了要让它生效：上限变小会把当前值一起钳下来
     * （{@link com.linweiyun.genshin.core.system.poise.PoiseState#refreshMax}），
     * 所以「进副本时把上限调高」这类做法只要在下一次结算前写上属性就行。
     */
    public static final DeferredHolder<AttributeType, AttributeType> POISE_MAX =
            ATTRIBUTES.register("poise_max", () -> new AttributeType("poise_max", "attribute.minegenshin.poise_max", 0));

    /**
     * 霸体强度 —— 默认 1，<b>越大越不容易被打断</b>。
     *
     * <p>这是给玩家/技能书看的名字（用户口径：「霸体系数堆高就不怕」）。
     * 文献那套是「系数越小越耐削、0 = 完全免疫」的反方向，两者的换算只在
     * {@link com.linweiyun.genshin.core.system.poise.PoiseService#poiseFactorOf} 一处：
     * {@code 实际削韧 = 攻击削韧 ÷ 霸体强度}。
     *
     * <p>0 或负数按 1 处理 —— 免得一个没配准的 0 变成全场无敌（真正的免疫走显式门控）。
     */
    public static final DeferredHolder<AttributeType, AttributeType> SUPER_ARMOR =
            ATTRIBUTES.register("super_armor", () -> new AttributeType("super_armor", "attribute.minegenshin.super_armor", 1));

    /**
     * 重量 —— 冲击判定用的质量量级，默认 100。
     *
     * <p>文献只给了「击飞门槛 = 竖直力 ≥ 5.5 × 重量」「击退门槛 = 水平力 ≥ 2 × 重量」，
     * 以及「挣扎状态的作用对象是重量 ≤ 100」，没有给出每种生物的完整重量表。
     * 所以默认 100 作为典型量级；要逐怪覆盖时用这张属性，
     * 或者走 {@link com.linweiyun.genshin.core.system.poise.impact.ImpactSolver#overrideWeight} 的按 id 覆盖口。
     */
    public static final DeferredHolder<AttributeType, AttributeType> WEIGHT =
            ATTRIBUTES.register("weight", () -> new AttributeType("weight", "attribute.minegenshin.weight", 100));

    public static void register(IEventBus bus) {

        ATTRIBUTES.register(bus);
    }
}
