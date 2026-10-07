package com.linweiyun.genshin.core.system.combat.action.data;


import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 骨骼挂点的「内容从哪来」。
 *
 * <p>骨骼替换分两步：<b>挂点</b>（{@link CharacterBoneMount}，说明骨头在哪、怎么摆）
 * 和<b>内容</b>（这个接口，说明往上面放什么）。默认内容是当前角色武器槽里的整个物品模型，
 * 换别的来源只要实现这个接口并交给 {@link CharacterBoneMount#withSource}。
 *
 * <h2>内置来源</h2>
 * <ul>
 *   <li>{@link #WEAPON_SLOT} —— 角色武器槽的整个模型（默认）</li>
 *   <li>{@link #ofSlot(int)} —— 角色装备栏任意槽位的整个模型</li>
 *   <li>{@link #fixed(ItemStack)} —— 写死一个物品</li>
 *   <li>{@link #of(Function)} —— 自定义逻辑（读配置、读别的实体……）</li>
 *   <li>{@link #weaponSubBone(String)} —— <b>武器槽物品的某一根骨骼</b>（拆剑身/剑鞘用）</li>
 *   <li>{@link #subModel(ResourceLocation, ResourceLocation, String)} —— 任意 geo 模型的一根骨骼</li>
 * </ul>
 *
 * <p>返回 {@code null} 或 {@link BoneMountContent#isEmpty()} 表示这次不挂东西：
 * 那根骨骼会保持原样渲染，不会被隐藏，所以「没装备 → 用模型自带的部件」是自动成立的。
 */
@FunctionalInterface
public interface BoneMountSource {

    /**
     * @param player    正在渲染的玩家（本地或远端都可能是）
     * @param character 他当前的出战角色；可能为 null（还没同步到 / 没戴饰品）
     * @return 要挂到骨骼上的内容；{@code null} 表示不挂
     */
    @Nullable
    BoneMountContent resolve(Player player, PGCharacter character);

    // ==================== 整个物品模型 ====================

    /** 当前角色的武器槽。默认来源。 */
    BoneMountSource WEAPON_SLOT = (player, character) ->
            character == null ? null : BoneMountContent.whole(character.getData().getWeapon());

    /** 角色装备栏里的任意槽位，用 {@code ArtifactInventory.SLOT_*} 常量。 */
    static BoneMountSource ofSlot(int slot) {
        return (player, character) -> character == null
                ? null
                : BoneMountContent.whole(character.getData().getArtifactInventory().getItem(slot));
    }

    /** 固定物品：不管装备什么，这根骨骼永远显示这一个模型。 */
    static BoneMountSource fixed(ItemStack stack) {
        BoneMountContent content = BoneMountContent.whole(stack == null ? ItemStack.EMPTY : stack.copy());
        return (player, character) -> content;
    }

    /** 自定义逻辑，结果是「整个物品模型」。 */
    static BoneMountSource of(Function<PGCharacter, ItemStack> resolver) {
        return (player, character) ->
                character == null ? null : BoneMountContent.whole(resolver.apply(character));
    }

    // ==================== 源模型里的一根骨骼 ====================

    /**
     * 武器槽物品的<b>某一根骨骼</b>。
     *
     * <p>武器槽只能放一个物品，但那个物品的 geo 模型里可以有很多根骨骼。
     * 用这个来源就能把「一把有剑身和剑鞘的剑」拆开，分别挂到角色模型的两根骨骼上：
     * <pre>
     * CharacterBoneMount.of("blade_right", BoneMountSource.weaponSubBone("blade"))
     * CharacterBoneMount.of("shealth",     BoneMountSource.weaponSubBone("sheath"))
     * </pre>
     *
     * @param sourceBone 武器 geo 模型里的骨骼名（取它和它的子树）
     */
    static BoneMountSource weaponSubBone(String sourceBone) {
        return (player, character) -> character == null
                ? null
                : BoneMountContent.subBone(character.getData().getWeapon(), sourceBone);
    }

    /**
     * 任意 geo 模型里的一根骨骼，不依赖任何物品。
     *
     * <p>用于「拿 C 模型的一根骨骼去替换 B 的某根骨骼」，或者做固定外观。
     *
     * @param modelId    geo 模型 id，如 {@code minegenshin:item/test_sword}
     *                   （即 {@code assets/minegenshin/geckolib/models/item/test_sword.geo.json}）
     * @param textureId  贴图 id，如 {@code minegenshin:textures/item/test_sword.png}
     * @param sourceBone 取哪根骨骼
     */
    static BoneMountSource subModel(ResourceLocation modelId, ResourceLocation textureId, String sourceBone) {
        BoneMountContent content = BoneMountContent.model(modelId, textureId, sourceBone);
        return (player, character) -> content;
    }

    /** 槽位物品的某一根骨骼，槽位可自选。 */
    static BoneMountSource slotSubBone(int slot, String sourceBone) {
        return (player, character) -> character == null
                ? null
                : BoneMountContent.subBone(
                        character.getData().getArtifactInventory().getItem(slot), sourceBone);
    }
}
