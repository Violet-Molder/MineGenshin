// 申鹤的「骨骼显隐」控制器。
//
// 直接放在申鹤自己的角色包里，与申鹤类同名前缀（Shenhe → ShenheBoneVisibility）。
// 基类 CharacterBoneVisibility.forCharacter() 会按出战角色的实际类自动在这发现本类，
// 所以不需要任何注册接线 —— 加一个新角色只要照这个模板在它的包里放一个文件就行。
//
// 目前没有独有定制：完全沿用基类默认（只渲染 allbody 子树、隐藏 weapon 及其它/装饰件）。
// 想给申鹤单独调整时，把 weaponUpdater 覆写成申鹤自己的规则即可。
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.client.render.character.appearance.CharacterBoneVisibility;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import net.minecraft.world.entity.player.Player;

public final class ShenheBoneVisibility extends CharacterBoneVisibility {

   public ShenheBoneVisibility() {
   }

   @Override
   public BoneUpdater<GeoRenderState> weaponUpdater(Player player, PGCharacter character) {
      // 现在没有独有定制：沿用基类默认即可。需要定制时把这里换成申鹤自己的武器显隐逻辑。
      return super.weaponUpdater(player, character);
   }
}