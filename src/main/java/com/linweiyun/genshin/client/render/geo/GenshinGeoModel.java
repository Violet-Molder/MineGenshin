// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.geo;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.asset.GeoAssetKind;
import com.linweiyun.genshin.asset.GeoPathOverrides;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class GenshinGeoModel<T extends GeoAnimatable> extends GeoModel<T> implements GenshinAssets.CharacterAssetOwner {
   private ResourceLocation defaultModel;
   private ResourceLocation defaultTexture;
   private ResourceLocation defaultAnimation;
   private ResourceLocation declaredModel;
   private ResourceLocation declaredTexture;
   private ResourceLocation declaredAnimation;
   private ResourceLocation sharedModel;
   private ResourceLocation sharedTexture;
   private ResourceLocation sharedAnimation;
   @Nullable
   private String characterId;
   private ResourceLocation[] animationFallbacks = new ResourceLocation[0];

   protected GenshinGeoModel() {
   }

   protected GenshinGeoModel(@Nullable String characterId) {
      this.setCharacterId(characterId);
   }

   public void setPaths(@Nullable ResourceLocation model, @Nullable ResourceLocation texture, @Nullable ResourceLocation animation) {
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

   public void setDeclaredPaths(@Nullable ResourceLocation model, @Nullable ResourceLocation texture, @Nullable ResourceLocation animation) {
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

   public void setSharedPaths(@Nullable ResourceLocation model, @Nullable ResourceLocation texture, @Nullable ResourceLocation animation) {
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
            .toArray(ResourceLocation[]::new);
      } else {
         this.animationFallbacks = new ResourceLocation[0];
      }
   }

   public ResourceLocation[] getAnimationResourceFallbacks(T animatable) {
      return this.animationFallbacks;
   }

   public ResourceLocation[] allAnimationFiles(T animatable) {
      ResourceLocation primary = this.getAnimationResource(animatable);
      ResourceLocation[] fallbacks = this.getAnimationResourceFallbacks(animatable);
      ResourceLocation[] all = new ResourceLocation[fallbacks.length + 1];
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
   public ResourceLocation defaultModelResource() {
      return this.defaultModel;
   }

   @Nullable
   public ResourceLocation defaultTextureResource() {
      return this.defaultTexture;
   }

   @Nullable
   public ResourceLocation defaultAnimationResource() {
      return this.defaultAnimation;
   }

   public ResourceLocation getModelResource(T animatable) {
      return AssetFallback.model(
         this.rewrite(GeoAssetKind.MODEL, this.defaultModel),
         this.rewrite(GeoAssetKind.MODEL, this.declaredModel),
         this.rewrite(GeoAssetKind.MODEL, this.sharedModel)
      );
   }

   public ResourceLocation getTextureResource(T animatable) {
      return AssetFallback.texture(
         this.rewrite(GeoAssetKind.TEXTURE, this.defaultTexture),
         this.rewrite(GeoAssetKind.TEXTURE, this.declaredTexture),
         this.rewrite(GeoAssetKind.TEXTURE, this.sharedTexture)
      );
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return AssetFallback.animation(
         this.rewrite(GeoAssetKind.ANIMATION, this.defaultAnimation),
         this.rewrite(GeoAssetKind.ANIMATION, this.declaredAnimation),
         this.rewrite(GeoAssetKind.ANIMATION, this.sharedAnimation)
      );
   }

   @Nullable
   private ResourceLocation rewrite(GeoAssetKind kind, @Nullable ResourceLocation original) {
      return original == null ? null : GeoPathOverrides.resolve(kind, this, original);
   }

   public BakedGeoModel getBakedModel(ResourceLocation location) {
      BakedGeoModel own = GenshinGeoCache.model(location);
      return own != null ? own : super.getBakedModel(location);
   }

   @Nullable
   public Animation getBakedAnimation(T animatable, String name) throws RuntimeException {
      Animation own = GenshinGeoCache.animation(this.getAnimationResource(animatable), this.getAnimationResourceFallbacks(animatable), name);
      return own != null ? own : super.getAnimation(animatable, name);
   }
}
