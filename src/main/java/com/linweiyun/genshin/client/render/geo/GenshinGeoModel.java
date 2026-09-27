// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.geo;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.asset.GeoAssetKind;
import com.linweiyun.genshin.core.asset.GeoPathOverrides;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public abstract class GenshinGeoModel<T extends GeoAnimatable> extends GeoModel<T> implements GenshinAssets.CharacterAssetOwner {
   private Identifier defaultModel;
   private Identifier defaultTexture;
   private Identifier defaultAnimation;
   private Identifier declaredModel;
   private Identifier declaredTexture;
   private Identifier declaredAnimation;
   private Identifier sharedModel;
   private Identifier sharedTexture;
   private Identifier sharedAnimation;
   @Nullable
   private String characterId;
   private Identifier[] animationFallbacks = new Identifier[0];

   protected GenshinGeoModel() {
   }

   protected GenshinGeoModel(@Nullable String characterId) {
      this.setCharacterId(characterId);
   }

   public void setPaths(@Nullable Identifier model, @Nullable Identifier texture, @Nullable Identifier animation) {
      if (model != null) {
         this.defaultModel = model;
      }

      if (texture != null) {
         this.defaultTexture = texture;
      }

      if (animation != null) {
         this.defaultAnimation = animation;
      }
   }

   public void setDeclaredPaths(@Nullable Identifier model, @Nullable Identifier texture, @Nullable Identifier animation) {
      if (model != null) {
         this.declaredModel = model;
      }

      if (texture != null) {
         this.declaredTexture = texture;
      }

      if (animation != null) {
         this.declaredAnimation = animation;
      }
   }

   public void setSharedPaths(@Nullable Identifier model, @Nullable Identifier texture, @Nullable Identifier animation) {
      if (model != null) {
         this.sharedModel = model;
      }

      if (texture != null) {
         this.sharedTexture = texture;
      }

      if (animation != null) {
         this.sharedAnimation = animation;
      }
   }

   public void setCharacterId(@Nullable String characterId) {
      this.characterId = characterId;
      if (characterId != null && !characterId.isEmpty()) {
         this.setPaths(
            GenshinAssets.characterModel(characterId, GenshinAssets.defaultModelFile(characterId)),
            GenshinAssets.characterTexture(characterId, GenshinAssets.defaultTextureFile(characterId)),
            GenshinAssets.characterAnimation(characterId, GenshinAssets.defaultAnimationFile(characterId))
         );
      }
   }

   public void setAnimationFallbackPaths(@Nullable List<String> relativePaths) {
      if (relativePaths != null && !relativePaths.isEmpty()) {
         this.animationFallbacks = relativePaths.stream()
            .filter(p -> p != null && !p.isEmpty())
            .map(GenshinAssets::fromAnimationPath)
            .toArray(Identifier[]::new);
      } else {
         this.animationFallbacks = new Identifier[0];
      }
   }

   public Identifier[] getAnimationResourceFallbacks(T animatable) {
      return this.animationFallbacks;
   }

   public Identifier[] allAnimationFiles(T animatable) {
      Identifier primary = this.getAnimationResource(animatable);
      Identifier[] fallbacks = this.getAnimationResourceFallbacks(animatable);
      Identifier[] all = new Identifier[fallbacks.length + 1];
      all[0] = primary;
      System.arraycopy(fallbacks, 0, all, 1, fallbacks.length);
      return all;
   }

   @Nullable
   @Override
   public String assetCharacterId() {
      return this.characterId;
   }

   @Nullable
   public Identifier defaultModelResource() {
      return this.defaultModel;
   }

   @Nullable
   public Identifier defaultTextureResource() {
      return this.defaultTexture;
   }

   @Nullable
   public Identifier defaultAnimationResource() {
      return this.defaultAnimation;
   }

   public Identifier getModelResource(GeoRenderState renderState) {
      return AssetFallback.model(
         this.rewrite(GeoAssetKind.MODEL, this.defaultModel),
         this.rewrite(GeoAssetKind.MODEL, this.declaredModel),
         this.rewrite(GeoAssetKind.MODEL, this.sharedModel)
      );
   }

   public Identifier getTextureResource(GeoRenderState renderState) {
      return AssetFallback.texture(
         this.rewrite(GeoAssetKind.TEXTURE, this.defaultTexture),
         this.rewrite(GeoAssetKind.TEXTURE, this.declaredTexture),
         this.rewrite(GeoAssetKind.TEXTURE, this.sharedTexture)
      );
   }

   public Identifier getAnimationResource(T animatable) {
      return AssetFallback.animation(
         this.rewrite(GeoAssetKind.ANIMATION, this.defaultAnimation),
         this.rewrite(GeoAssetKind.ANIMATION, this.declaredAnimation),
         this.rewrite(GeoAssetKind.ANIMATION, this.sharedAnimation)
      );
   }

   @Nullable
   private Identifier rewrite(GeoAssetKind kind, @Nullable Identifier original) {
      return original == null ? null : GeoPathOverrides.resolve(kind, this, original);
   }

   public BakedGeoModel getBakedModel(Identifier location) {
      BakedGeoModel own = GenshinGeoCache.model(location);
      return own != null ? own : super.getBakedModel(location);
   }

   @Nullable
   public Animation getBakedAnimation(T animatable, String name) throws RuntimeException {
      Animation own = GenshinGeoCache.animation(this.getAnimationResource(animatable), this.getAnimationResourceFallbacks(animatable), name);
      return own != null ? own : super.getBakedAnimation(animatable, name);
   }
}
