package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.elementlib.api.ReactionCategory;
import com.linweiyun.genshin.Minegenshin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组的反应类型 —— 注册进 elementlib 的反应类型注册表（minegenshin 命名空间）。
 */
public final class ModReactionTypes {

    public static final DeferredRegister<ElementalReactionType> REACTION_TYPES =
            DeferredRegister.create(
                    com.linweiyun.elementlib.core.system.registry.ModRegistries.REACTION_TYPE_REGISTRY,
                    Minegenshin.MOD_ID);

    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> MELT =
            register("melt", ReactionCategory.AMPLIFYING);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> VAPORIZE =
            register("vaporize", ReactionCategory.AMPLIFYING);

    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> SHATTERED =
            register("shattered", ReactionCategory.SPECIAL);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> SUPERCONDUCT =
            register("superconduct", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> SWIRL =
            register("swirl", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> ELECTRO_CHARGED =
            register("electro_charged", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> OVERLOAD =
            register("overload", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> BURNING =
            register("burning", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> BLOOM =
            register("bloom", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> HYPERBLOOM =
            register("hyperbloom", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> BURGEON =
            register("burgeon", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> QUICKEN =
            register("quicken", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> AGGRAVATE =
            register("aggravate", ReactionCategory.TRANSFORMATIVE);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> SPREAD =
            register("spread", ReactionCategory.TRANSFORMATIVE);

    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> STELLAR_SWIRL =
            register("stellar_swirl", ReactionCategory.STELLAR);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> STELLAR_CONDUCE =
            register("stellar_conduce", ReactionCategory.STELLAR);

    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> LUNAR_CHARGED =
            register("lunar_charged", ReactionCategory.LUNAR);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> LUNAR_BLOOM =
            register("lunar_bloom", ReactionCategory.LUNAR);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> LUNAR_CRYSTALLIZE =
            register("lunar_crystallize", ReactionCategory.LUNAR);

    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> FROZEN =
            register("frozen", ReactionCategory.SPECIAL);
    public static final DeferredHolder<ElementalReactionType, ElementalReactionType> CRYSTALLIZE =
            register("crystallize", ReactionCategory.SPECIAL);

    private ModReactionTypes() {
    }

    private static DeferredHolder<ElementalReactionType, ElementalReactionType> register(
            String path, ReactionCategory category) {
        return REACTION_TYPES.register(path,
                () -> new ElementalReactionType("reaction.minegenshin." + path, category));
    }

    /** null 安全的类型比对。 */
    public static boolean is(@Nullable ElementalReactionType type,
                             DeferredHolder<ElementalReactionType, ElementalReactionType> holder) {
        return type != null && holder.isBound() && type == holder.get();
    }

    public static void register(IEventBus bus) {
        REACTION_TYPES.register(bus);
    }
}
