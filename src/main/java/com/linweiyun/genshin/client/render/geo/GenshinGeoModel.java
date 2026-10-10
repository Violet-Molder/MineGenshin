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
   /** 已经交给 GeckoLib 动画处理器登记过骨骼的那个模型，见 {@link #activateOwnModel}。 */
   @Nullable
   private BakedGeoModel processorModel;

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

   /**
    * 取烘焙模型：先问我们自己的缓存；<b>从自己缓存拿到的模型要替 GeckoLib 补一次骨骼登记</b>。
    *
    * <p>GeckoLib 4.9.3 的 {@code GeoModel.getBakedModel} 在命中它自己的缓存时会调用
    * {@code getAnimationProcessor().setActiveModel(model)}，把模型的骨骼登记进动画处理器；
    * 而 {@code GeoModel.handleAnimations} 推进动画的前置条件正是
    * {@code !processor.getRegisteredBones().isEmpty()}（骨骼没登记就直接跳过 tickAnimation）。
    *
    * <p>我们的模型都在自己的缓存里（不经过 GeckoLib 的缓存），如果这里直接从缓存返回，
    * 那次登记就永远不发生 —— 表现是「模型显示正常、所有动画都不播、日志一条报错都没有」，
    * 而且是角色 / 物品 / 实体一起中招。所以取到自己的模型后要手动补上这一步。
    */
   public BakedGeoModel getBakedModel(ResourceLocation location) {
      BakedGeoModel own = activateOwnModel(GenshinGeoCache.model(location));
      return own != null ? own : super.getBakedModel(location);
   }

   /**
    * 把「自己缓存里的烘焙模型」交给 GeckoLib 的动画处理器。
    *
    * <p>只在换了模型时才登记（{@code setActiveModel} 会清空并重新遍历骨骼），与 GeckoLib 自己的
    * {@code currentModel} 判断同一个语义。
    *
    * @return 传进来的模型（可能为 null），方便调用方串成一句
    */
   @Nullable
   protected BakedGeoModel activateOwnModel(@Nullable BakedGeoModel model) {
      if (model != null && model != this.processorModel) {
         this.getAnimationProcessor().setActiveModel(model);
         this.processorModel = model;
      }

      return model;
   }

   /**
    * 取一条动画 —— <b>先把我们自己的缓存问一遍，再退回 GeckoLib 的缓存</b>。
    *
    * <p>与 {@link #getBakedModel(ResourceLocation)} 对称：本 MOD（以及联动模组）的动画文件
    * 不在 GeckoLib 自己扫的 {@code animations/} 根下，所以 GeckoLib 的缓存里没有它们；
    * 模型那条路靠覆写 {@code getBakedModel} 解决，动画这条必须覆写 {@link #getAnimation}。
    *
    * <p><b>名字必须是 {@code getAnimation}</b>：GeckoLib 4.x 的 {@code GeoModel} 只声明了
    * {@code getAnimation(T, String)}；5.x 才改叫 {@code getBakedAnimation}。
    * 写成 5.x 的名字在本分支（GeckoLib 4.9.3）不是覆写、是死代码，表现就是
    * 「模型（IB 的）显示正常，动画一条都不播」。
    */
   @Nullable
   @Override
   public Animation getAnimation(T animatable, String name) throws RuntimeException {
      Animation own = GenshinGeoCache.animation(this.getAnimationResource(animatable), this.getAnimationResourceFallbacks(animatable), name);
      return own != null ? own : super.getAnimation(animatable, name);
   }
}
