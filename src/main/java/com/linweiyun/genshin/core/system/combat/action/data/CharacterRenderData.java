// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.combat.action.data;

import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.asset.source.CharacterBoneSpec;
import com.linweiyun.genshin.asset.source.CharacterResourceSlot;
import com.linweiyun.genshin.asset.source.CharacterResourceSources;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class CharacterRenderData {
   private final String id;
   private final String modelPath;
   private final String texturePath;
   private final String animationPath;
   private final List<String> extraAnimationPaths;
   private final Map<String, String> animMapping;
   private final float bodyScale;
   private final List<CharacterBoneMount> boneMounts;
   private final List<String> translucentBones;
   private final String modelAuthor;
   private final String modelAuthorUrl;
   /**
    * 非 null = 这个角色的资源按来源表（{@code character/<id>/resources.json}）解析。
    *
    * <p>存 id 而不是存解析结果：来源表要读资源管理器，而 {@code XxxResources.RENDER_DATA} 是
    * 静态字段，初始化可能早于资源就绪。解析推到 {@link #modelIdentifier()} 这类查询里做，
    * 顺带也跟着资源重载走。
    */
   private final String sourceId;

   public static CharacterRenderData character(String id, Map<String, String> animMapping, float bodyScale, CharacterBoneMount... boneMounts) {
      return new CharacterRenderData(
         id,
         GenshinAssets.characterModelPath(id),
         GenshinAssets.characterTexturePath(id),
         GenshinAssets.characterAnimationPath(id),
         animMapping,
         bodyScale,
         boneMounts
      );
   }

   /**
    * 资源来源表驱动的角色 —— 模型 / 动画 / 贴图 / 缩放 / 武器骨骼都可以在
    * {@code character/<id>/resources.json} 里逐项改指向（联动角色走这条）。
    *
    * <p>这里给的三条路径是<b>兜底</b>：没写来源表、或来源表里那一项解析不出来时，
    * 行为与 {@link #character} 完全一致。
    */
   public static CharacterRenderData sourced(String id, Map<String, String> animMapping, float bodyScale, CharacterBoneMount... boneMounts) {
      return character(id, animMapping, bodyScale, boneMounts).withSourceId(id);
   }

   public CharacterRenderData(String id, String modelPath, String texturePath, String animationPath, Map<String, String> animMapping, float bodyScale) {
      this(id, modelPath, texturePath, animationPath, animMapping, bodyScale, List.of());
   }

   public CharacterRenderData(
      String id,
      String modelPath,
      String texturePath,
      String animationPath,
      Map<String, String> animMapping,
      float bodyScale,
      List<CharacterBoneMount> boneMounts
   ) {
      this(
         id,
         modelPath,
         texturePath,
         animationPath,
         animMapping,
         bodyScale,
         boneMounts == null ? new CharacterBoneMount[0] : boneMounts.toArray(new CharacterBoneMount[0])
      );
   }

   public CharacterRenderData(
      String id, String modelPath, String texturePath, String animationPath, Map<String, String> animMapping, float bodyScale, CharacterBoneMount... boneMounts
   ) {
      this(id, modelPath, texturePath, animationPath, List.of(), animMapping, bodyScale, boneMounts == null ? new CharacterBoneMount[0] : boneMounts);
   }

   public CharacterRenderData(
      String id,
      String modelPath,
      String texturePath,
      String animationPath,
      List<String> extraAnimationPaths,
      Map<String, String> animMapping,
      float bodyScale,
      CharacterBoneMount... boneMounts
   ) {
      this(id, modelPath, texturePath, animationPath, extraAnimationPaths, animMapping, bodyScale, List.of(), boneMounts);
   }

   private CharacterRenderData(
      String id,
      String modelPath,
      String texturePath,
      String animationPath,
      List<String> extraAnimationPaths,
      Map<String, String> animMapping,
      float bodyScale,
      List<String> translucentBones,
      CharacterBoneMount... boneMounts
   ) {
      this(id, modelPath, texturePath, animationPath, extraAnimationPaths, animMapping, bodyScale, translucentBones, boneMounts, null, null, null);
   }

   private CharacterRenderData(
      String id,
      String modelPath,
      String texturePath,
      String animationPath,
      List<String> extraAnimationPaths,
      Map<String, String> animMapping,
      float bodyScale,
      List<String> translucentBones,
      CharacterBoneMount[] boneMounts,
      String modelAuthor,
      String modelAuthorUrl,
      String sourceId
   ) {
      this.id = id;
      this.modelPath = modelPath;
      this.texturePath = texturePath;
      this.animationPath = animationPath;
      this.extraAnimationPaths = extraAnimationPaths == null ? List.of() : List.copyOf(extraAnimationPaths);
      this.animMapping = animMapping == null ? Collections.emptyMap() : animMapping;
      this.bodyScale = bodyScale;
      this.translucentBones = translucentBones == null ? List.of() : List.copyOf(translucentBones);
      this.boneMounts = boneMounts == null ? List.of() : Arrays.stream(boneMounts).filter(m -> m != null && m.isValid()).toList();
      this.modelAuthor = blankToNull(modelAuthor);
      this.modelAuthorUrl = blankToNull(modelAuthorUrl);
      this.sourceId = blankToNull(sourceId);
   }

   private static String blankToNull(String value) {
      return value != null && !value.isBlank() ? value : null;
   }

   public String id() {
      return this.id;
   }

   public String modelPath() {
      return this.modelPath;
   }

   public String texturePath() {
      return this.texturePath;
   }

   public String animationPath() {
      return this.animationPath;
   }

   public List<String> extraAnimationPaths() {
      return this.sourceId == null
         ? this.extraAnimationPaths
         : CharacterResourceSources.extraAnimations(this.sourceId, this.extraAnimationPaths);
   }

   public CharacterRenderData withAnimationFile(String relativePath) {
      if (relativePath != null && !relativePath.isEmpty()) {
         List<String> merged = new ArrayList<>(this.extraAnimationPaths);
         merged.add(relativePath);
         return new CharacterRenderData(
            this.id,
            this.modelPath,
            this.texturePath,
            this.animationPath,
            merged,
            this.animMapping,
            this.bodyScale,
            this.translucentBones,
            this.boneMounts.toArray(new CharacterBoneMount[0]),
            this.modelAuthor,
            this.modelAuthorUrl,
            this.sourceId
         );
      } else {
         return this;
      }
   }

   public List<String> allAnimationPaths() {
      List<String> all = new ArrayList<>(this.extraAnimationPaths.size() + 1);
      all.add(this.animationPath);
      all.addAll(this.extraAnimationPaths);
      return all;
   }

   /**
    * 武器骨骼挂点 —— 来源表里 {@code bones.weapon} 声明的，接在代码里声明的后面。
    *
    * <p>联动角色的模型是对方的，剑挂在哪根骨骼上只有模型作者知道，所以这一项必须能声明。
    */
   public List<CharacterBoneMount> boneMounts() {
      if (this.sourceId == null) {
         return this.boneMounts;
      }

      CharacterBoneSpec spec = CharacterResourceSources.bones(this.sourceId);
      if (spec.weaponBones().isEmpty()) {
         return this.boneMounts;
      }

      List<CharacterBoneMount> merged = new ArrayList<>(this.boneMounts.size() + spec.weaponBones().size());
      merged.addAll(this.boneMounts);
      for (CharacterBoneMount mount : spec.weaponBones()) {
         if (!merged.contains(mount)) {
            merged.add(mount);
         }
      }
      return List.copyOf(merged);
   }

   public List<String> translucentBones() {
      return this.translucentBones;
   }

   public CharacterRenderData withTranslucentBones(String... boneNames) {
      return boneNames != null && boneNames.length != 0
         ? new CharacterRenderData(
            this.id,
            this.modelPath,
            this.texturePath,
            this.animationPath,
            this.extraAnimationPaths,
            this.animMapping,
            this.bodyScale,
            Arrays.asList(boneNames),
            this.boneMounts.toArray(new CharacterBoneMount[0]),
            this.modelAuthor,
            this.modelAuthorUrl,
            this.sourceId
         )
         : this;
   }

   public CharacterRenderData withModelAuthor(String author, String url) {
      return new CharacterRenderData(
         this.id,
         this.modelPath,
         this.texturePath,
         this.animationPath,
         this.extraAnimationPaths,
         this.animMapping,
         this.bodyScale,
         this.translucentBones,
         this.boneMounts.toArray(new CharacterBoneMount[0]),
         author,
         url,
         this.sourceId
      );
   }

   private CharacterRenderData withSourceId(String sourceId) {
      return new CharacterRenderData(
         this.id,
         this.modelPath,
         this.texturePath,
         this.animationPath,
         this.extraAnimationPaths,
         this.animMapping,
         this.bodyScale,
         this.translucentBones,
         this.boneMounts.toArray(new CharacterBoneMount[0]),
         this.modelAuthor,
         this.modelAuthorUrl,
         sourceId
      );
   }

   public String modelAuthor() {
      return this.modelAuthor;
   }

   public String modelAuthorUrl() {
      return this.modelAuthorUrl;
   }

   public ResourceLocation modelIdentifier() {
      ResourceLocation own = GenshinAssets.fromModelPath(this.modelPath);
      return this.sourceId == null ? own : CharacterResourceSources.identifier(this.sourceId, CharacterResourceSlot.MODEL, own);
   }

   public ResourceLocation textureIdentifier() {
      ResourceLocation own = GenshinAssets.fromTexturePath(this.texturePath);
      return this.sourceId == null ? own : CharacterResourceSources.identifier(this.sourceId, CharacterResourceSlot.TEXTURE, own);
   }

   public ResourceLocation animationIdentifier() {
      ResourceLocation own = GenshinAssets.fromAnimationPath(this.animationPath);
      return this.sourceId == null ? own : CharacterResourceSources.identifier(this.sourceId, CharacterResourceSlot.ANIMATION, own);
   }

   public Map<String, String> animMapping() {
      return this.animMapping;
   }

   public float bodyScale() {
      return this.sourceId == null ? this.bodyScale : CharacterResourceSources.scale(this.sourceId, this.bodyScale);
   }

   public boolean isValid() {
      return this.id != null && !this.id.isEmpty() && this.modelPath != null && this.texturePath != null && this.animationPath != null;
   }

   public static Map<String, String> defaultAnimMapping() {
      return Map.ofEntries(
         Map.entry("idle", "idle"),
         Map.entry("walk", "walk"),
         Map.entry("run", "run"),
         Map.entry("walk_back", "walk_back"),
         Map.entry("crouch", "crouch"),
         Map.entry("crouch_walk", "crouch_walk"),
         Map.entry("jump", "jump"),
         Map.entry("jump_down", "jump_down"),
         Map.entry("air_idle", "idle"),
         Map.entry("air_move", "walk"),
         Map.entry("air_sprint", "run"),
         Map.entry("swim", "swim"),
         Map.entry("climb", "climb"),
         Map.entry("sleep", "sleep")
      );
   }
}
