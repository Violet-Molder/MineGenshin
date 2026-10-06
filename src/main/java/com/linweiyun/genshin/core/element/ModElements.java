package com.linweiyun.genshin.core.element;

import com.linweiyun.elementlib.api.ElementRoles;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.Minegenshin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组元素注册中心 —— 注册进 elementlib 的元素注册表（minegenshin 命名空间）。
 */
public class ModElements {

    public static final DeferredRegister<GenshinElement> ELEMENTS =
            DeferredRegister.create(
                    com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY,
                    Minegenshin.MOD_ID);

    // ======== 主元素 ========
    public static final DeferredHolder<GenshinElement, GenshinElement> FYSIKOS = ELEMENTS.register(
            "fysikos", () -> new GenshinElement(false, false, "elemental.gim.fysikos"));

    public static final DeferredHolder<GenshinElement, GenshinElement> PYRO = ELEMENTS.register(
            "pyro", () -> new GenshinElement(true, false, "elemental.gim.pyro"));

    public static final DeferredHolder<GenshinElement, GenshinElement> HYDRO = ELEMENTS.register(
            "hydro", () -> new GenshinElement(false, false, "elemental.gim.hydro"));

    public static final DeferredHolder<GenshinElement, GenshinElement> ANEMO = ELEMENTS.register(
            "anemo", () -> new GenshinElement(false, true, "elemental.gim.anemo"));

    public static final DeferredHolder<GenshinElement, GenshinElement> ELECTRO = ELEMENTS.register(
            "electro", () -> new GenshinElement(false, false, "elemental.gim.electro"));

    public static final DeferredHolder<GenshinElement, GenshinElement> DENDRO = ELEMENTS.register(
            "dendro", () -> new GenshinElement(false, false, "elemental.gim.dendro"));

    /** 冰 —— 只负责附着本身；「寒冷」减速由 {@link ColdElement}（寒）承载。 */
    public static final DeferredHolder<GenshinElement, GenshinElement> CYRO = ELEMENTS.register(
            "cyro", () -> new GenshinElement(false, false, "elemental.gim.cyro"));

    public static final DeferredHolder<GenshinElement, GenshinElement> GEO = ELEMENTS.register(
            "geo", () -> new GenshinElement(false, true, "elemental.gim.geo"));

    // ======== 类元素（关联主元素，注册后调用 setupSubElements 设置） ========
    /** 冻 —— 冻结反应生成物，只负责附着本身；「禁 AI」由 {@link ColdElement}（寒）承载。 */
    public static final DeferredHolder<GenshinElement, GenshinElement> FROZEN = ELEMENTS.register(
            "frozen", () -> new GenshinElement(false, false, "elemental.gim.frozen"));

    /**
     * 寒 —— 冰/冻的附加效果载体（减速、禁 AI）。
     *
     * <p>独立注册、不并入冰参与反应配对；伴随关系（有冰/冻就有寒）由 elementlib 的 ColdAura 每 tick 同步。
     */
    public static final DeferredHolder<GenshinElement, ColdElement> COLD = ELEMENTS.register(
            "cold", () -> new ColdElement("elemental.gim.cold"));

    public static final DeferredHolder<GenshinElement, GenshinElement> AGGRAVATE = ELEMENTS.register(
            "aggravate", () -> new GenshinElement(true, false, "elemental.gim.aggravate"));

    public static final DeferredHolder<GenshinElement, GenshinElement> BURNING = ELEMENTS.register(
            "burning", () -> new GenshinElement(true, false, "elemental.gim.burning"));

    public static final DeferredHolder<GenshinElement, GenshinElement> WOOD = ELEMENTS.register(
            "wood", () -> new GenshinElement(false, false, "elemental.gim.wood"));

    /**
     * 在所有元素注册完成后调用：设置类元素的主元素关联，并把元素绑定到 elementlib 的角色表。
     */
    public static void setupSubElements() {
        FROZEN.get().setMainElement(CYRO.get());
        AGGRAVATE.get().setMainElement(ELECTRO.get());
        BURNING.get().setMainElement(PYRO.get());
        WOOD.get().setMainElement(DENDRO.get());
        bindRoles();
    }

    /** 把本模组的元素绑定到 elementlib 的框架角色，框架据此识别「冰 / 冻 / 寒 / 水」等。 */
    public static void bindRoles() {
        ElementRoles.bind(ElementRoles.FYSIKOS, FYSIKOS.get());
        ElementRoles.bind(ElementRoles.PYRO, PYRO.get());
        ElementRoles.bind(ElementRoles.HYDRO, HYDRO.get());
        ElementRoles.bind(ElementRoles.ANEMO, ANEMO.get());
        ElementRoles.bind(ElementRoles.ELECTRO, ELECTRO.get());
        ElementRoles.bind(ElementRoles.DENDRO, DENDRO.get());
        ElementRoles.bind(ElementRoles.CYRO, CYRO.get());
        ElementRoles.bind(ElementRoles.GEO, GEO.get());
        ElementRoles.bind(ElementRoles.FROZEN, FROZEN.get());
        ElementRoles.bind(ElementRoles.COLD, COLD.get());
        ElementRoles.bind(ElementRoles.AGGRAVATE, AGGRAVATE.get());
        ElementRoles.bind(ElementRoles.BURNING, BURNING.get());
        ElementRoles.bind(ElementRoles.WOOD, WOOD.get());
    }

    public static void register(IEventBus eventBus) {
        ELEMENTS.register(eventBus);
    }
}
