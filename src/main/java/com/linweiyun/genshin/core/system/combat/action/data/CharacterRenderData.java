// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.combat.action.data;

import com.linweiyun.genshin.asset.GenshinAssets;
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
      this(id, modelPath, texturePath, animationPath, extraAnimationPaths, animMapping, bodyScale, translucentBones, boneMounts, null, null);
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
      String modelAuthorUrl
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
      return this.extraAnimationPaths;
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
            this.modelAuthorUrl
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

   public List<CharacterBoneMount> boneMounts() {
      return this.boneMounts;
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
            this.modelAuthorUrl
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
         url
      );
   }

   public String modelAuthor() {
      return this.modelAuthor;
   }

   public String modelAuthorUrl() {
      return this.modelAuthorUrl;
   }

   public ResourceLocation modelIdentifier() {
      return GenshinAssets.fromModelPath(this.modelPath);
   }

   public ResourceLocation textureIdentifier() {
      return GenshinAssets.fromTexturePath(this.texturePath);
   }

   public ResourceLocation animationIdentifier() {
      return GenshinAssets.fromAnimationPath(this.animationPath);
   }

   public Map<String, String> animMapping() {
      return this.animMapping;
   }

   public float bodyScale() {
      return this.bodyScale;
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
