package com.linweiyun.genshin.core.system.combat.action.data;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 一个骨骼挂点这次要挂上去的「内容」。
 *
 * <p>两种形态，覆盖三种用法：
 *
 * <h2>1. 整个物品模型 —— {@link #whole(ItemStack)}</h2>
 * 在目标骨骼上画出这件物品的完整模型（普通物品模型和 GeckoLib 的 geo 物品都行）。
 * 适合「一把剑整体握在手里」。
 *
 * <h2>2. 物品 geo 模型里的一根骨骼 —— {@link #subBone(ItemStack, String)}</h2>
 * <b>这是拆分剑身/剑鞘用的形态。</b> 武器槽只有一个物品，但这个物品的 geo 模型里
 * 剑身和剑鞘是两根独立骨骼，于是：
 * <pre>
 * 挂点 blade_right ← 武器槽物品的 "blade" 骨骼
 * 挂点 shealth     ← 武器槽物品的 "sheath" 骨骼
 * </pre>
 * 同一个物品源，拆成两根骨骼分别挂到角色模型的两根骨骼上。
 *
 * <h2>3. 直接指定 geo 模型 —— {@link #model(ResourceLocation, ResourceLocation, String)}</h2>
 * 内容不来自物品，而是来自任意一个 geo 模型文件。适合「拿 C 模型的一根骨骼去替换 B 的某根骨骼」，
 * 或者做固定外观。
 *
 * @param stack      物品源；形态 3 时为 {@link ItemStack#EMPTY}
 * @param sourceBone 从源模型里取哪根骨骼；{@code null} 表示整个模型
 * @param modelId    直接指定的 geo 模型 id；为 {@code null} 时从 {@link #stack} 反解
 * @param textureId  配合 {@link #modelId} 的贴图
 */
public record BoneMountContent(
        ItemStack stack,
        @Nullable String sourceBone,
        @Nullable ResourceLocation modelId,
        @Nullable ResourceLocation textureId) {

    /** 整个物品模型。 */
    public static BoneMountContent whole(ItemStack stack) {
        return new BoneMountContent(stack == null ? ItemStack.EMPTY : stack, null, null, null);
    }

    /** 物品 geo 模型里的一根骨骼（含它的子树）。 */
    public static BoneMountContent subBone(ItemStack stack, String sourceBone) {
        return new BoneMountContent(stack == null ? ItemStack.EMPTY : stack, sourceBone, null, null);
    }

    /** 直接指定 geo 模型与贴图里的一根骨骼。 */
    public static BoneMountContent model(ResourceLocation modelId, ResourceLocation textureId, String sourceBone) {
        return new BoneMountContent(ItemStack.EMPTY, sourceBone, modelId, textureId);
    }

    /** 这次有没有东西可挂。 */
    public boolean isEmpty() {
        return stack.isEmpty() && modelId == null;
    }

    /** 是不是「从某个 geo 模型里取一根骨骼」。 */
    public boolean isSubBone() {
        return sourceBone != null && !sourceBone.isEmpty();
    }
}
