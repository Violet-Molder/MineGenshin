package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.elementlib.core.system.reaction.ElementalReaction;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.reaction.builtin.ElectroChargedReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.FreezeReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.LunarChargedReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.MeltReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.SuperConductReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.SwirlReaction;
import com.linweiyun.genshin.core.system.reaction.builtin.VaporizeReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的元素反应 —— 注册进 elementlib 的反应注册表。
 */
public class ModElementalReactions {

    public static final DeferredRegister<ElementalReaction> ELEMENTAL_REACTIONS =
            DeferredRegister.create(
                    com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENTAL_REACTIONS_REGISTRY,
                    Minegenshin.MOD_ID);

    public static final DeferredHolder<ElementalReaction, VaporizeReaction> VAPORIZE = ELEMENTAL_REACTIONS.register(
            "vaporize",
            () -> new VaporizeReaction(
                    ModReactionTypes.VAPORIZE,
                    "minegenshin:hydro", "minegenshin:pyro",
                    1f, 2f,
                    0));

    public static final DeferredHolder<ElementalReaction, MeltReaction> MELT = ELEMENTAL_REACTIONS.register(
            "melt",
            () -> new MeltReaction(
                    ModReactionTypes.MELT,
                    "minegenshin:pyro", "minegenshin:cyro",
                    1f, 2f,
                    0));

    public static final DeferredHolder<ElementalReaction, FreezeReaction> FREEZE = ELEMENTAL_REACTIONS.register(
            "freeze",
            () -> new FreezeReaction(
                    ModReactionTypes.FROZEN,
                    "minegenshin:hydro", "minegenshin:cyro",
                    1f, 1f,
                    0));

    // 月感电：水:雷 = 1:1；优先级与原「默认顺序表」等价（水/雷/冰在表中的下标都在 0 与 5 之间）
    public static final DeferredHolder<ElementalReaction, LunarChargedReaction> LUNAR_CHARGED = ELEMENTAL_REACTIONS.register(
            "lunar_charged",
            () -> new LunarChargedReaction(
                    ModReactionTypes.LUNAR_CHARGED,
                    "minegenshin:hydro", "minegenshin:electro",
                    1f, 1f,
                    2));

    public static final DeferredHolder<ElementalReaction, SwirlReaction> SWIRL = ELEMENTAL_REACTIONS.register(
            "swirl",
            () -> new SwirlReaction(
                    ModReactionTypes.SWIRL,
                    ModElements.PYRO.getId().toString(), ModElements.ANEMO.getId().toString(),
                    1f, 2f,
                    5));

    public static final DeferredHolder<ElementalReaction, ElectroChargedReaction> ELECTRO_CHARGED = ELEMENTAL_REACTIONS.register(
            "electro_charged",
            () -> new ElectroChargedReaction(
                    ModReactionTypes.ELECTRO_CHARGED,
                    "minegenshin:hydro", "minegenshin:electro",
                    1f, 1f,
                    0));

    public static final DeferredHolder<ElementalReaction, SuperConductReaction> SUPERCONDUCT = ELEMENTAL_REACTIONS.register(
            "superconduct",
            () -> new SuperConductReaction(
                    ModReactionTypes.SUPERCONDUCT,
                    "minegenshin:electro", "minegenshin:cyro",
                    1f, 1f,
                    0));

    public static void register(IEventBus eventBus) {
        ELEMENTAL_REACTIONS.register(eventBus);
    }
}
