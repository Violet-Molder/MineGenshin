// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.render.geo.GenshinGeoModel;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import lombok.Generated;
import org.slf4j.Logger;

public class CharacterPlayerModel extends GenshinGeoModel<GenshinReplacedPlayer> {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private CharacterRenderData renderData;

   public void updateRenderData(CharacterRenderData data) {
      if (data != null) {
         this.renderData = data;
         this.setCharacterId(data.id());
         this.setDeclaredPaths(data.modelIdentifier(), data.textureIdentifier(), data.animationIdentifier());
         this.setSharedPaths(GenshinAssets.defaultModel(), GenshinAssets.defaultTexture(), GenshinAssets.defaultAnimation());
         this.setAnimationFallbackPaths(data.extraAnimationPaths());
         LOGGER.info(
            "[CharacterPlayerModel] 角色 '{}' 路径: 自己={}/{}/{}，声明={}/{}/{}，共用={}/{}/{}，额外动画={}",
            new Object[]{
               data.id(),
               this.defaultModelResource(),
               this.defaultTextureResource(),
               this.defaultAnimationResource(),
               data.modelIdentifier(),
               data.textureIdentifier(),
               data.animationIdentifier(),
               GenshinAssets.defaultModel(),
               GenshinAssets.defaultTexture(),
               GenshinAssets.defaultAnimation(),
               data.extraAnimationPaths()
            }
         );
      }
   }

   @Generated
   public CharacterRenderData getRenderData() {
      return this.renderData;
   }
}
