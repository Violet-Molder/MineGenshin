// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.content.items.artifact.inventory.AllWeaponArtifactInventory;
import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.BoneMountSource;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;

public final class LinweiyunResources {
   public static final String MODEL_AUTHOR = "下一只风筝";
   public static final String MODEL_AUTHOR_URL = "https://space.bilibili.com/281665959";

   /**
    * 渲染数据 —— 模型 / 贴图 / 动画都走共用目录，另外给五把武器各挂一个<b>骨骼替换点</b>：
    *
    * <pre>
   * sword    ← 剑槽位（slotFor(SWORD)）里的物品
   * bow      ← 弓槽位里的物品
   * long     ← 长柄槽位里的物品
   * polearm_fly ← 同上，但挂在身上（Waist）—— 飞行时武器脱离手、当扫帚用的那一根
   * claymore ← 大剑槽位里的物品
   * magic    ← 法器槽位里的物品
    * </pre>
    *
    * <p>语义就是用户要的「替换」：槽位里有武器 → 隐藏这根骨骼自带的几何体、把**装备的那把武器**
    * 画在它的位姿上；槽位空着 → 什么都不挂，骨骼保持原样（模型自带的那把）。
    *
    * <p>当前形态之外的武器骨骼本来就被外观项缩成 0（{@code CharacterAppearanceOptionBones}），
    * 而挂点画的是「骨骼已捕获的位姿」，缩 0 就等于画不出来 —— 所以不会出现五把叠在一起。
    * 也就是说：**先在配置页选形态 + 打开「常态显示武器」，对应槽位里的那把才会出现在身上**。
    */
   public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character(
            "linweiyun",
            CharacterRenderData.defaultAnimMapping(),
            1.0F,
            CharacterBoneMount.of("sword", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.SWORD))),
            CharacterBoneMount.of("bow", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.BOW))),
            CharacterBoneMount.of("long", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.POLEARM))),
            // 长柄的第二根：飞行时用（同槽位、挂在 Waist 上的 `polearm_fly`）。
            // 常态藏、飞行时亮 —— 见 AllWeaponAppearanceData#flightBones 与
            // CharacterAppearanceOptionBones 的飞行分支。
            CharacterBoneMount.of("polearm_fly", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.POLEARM))),
            CharacterBoneMount.of("claymore", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.CLAYMORE))),
            CharacterBoneMount.of("magic", BoneMountSource.ofSlot(
                    AllWeaponArtifactInventory.slotFor(WeaponAppearance.CATALYST))))
      .withModelAuthor("下一只风筝", "https://space.bilibili.com/281665959");

   private LinweiyunResources() {
   }
}
