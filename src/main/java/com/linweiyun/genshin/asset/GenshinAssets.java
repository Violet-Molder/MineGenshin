// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class GenshinAssets {
   public static final String MOD_ID = "minegenshin";
   public static final String CHARACTER_ROOT = "character";
   public static final String ITEM_ROOT = "item";
   public static final String ENTITY_ROOT = "entity";
   public static final String ICON_ROOT = "icon";
   public static final String SOUNDS_DIR = "sounds";
   public static final String SOUND_DEFINITION_FILE = "sounds.json";
   public static final String DEFAULT_ASSET_DIR = "character/linweiyun";
   public static final String DEFAULT_MODEL_FILE = "linweiyun.geo.json";
   public static final String DEFAULT_TEXTURE_FILE = "linweiyun.png";
   public static final String DEFAULT_ANIMATION_FILE = "linweiyun.animation.json";

   private GenshinAssets() {
   }

   public static String defaultModelPath() {
      return "character/linweiyun/linweiyun.geo.json";
   }

   public static String defaultTexturePath() {
      return "character/linweiyun/textures/linweiyun.png";
   }

   public static String defaultAnimationPath() {
      return "character/linweiyun/linweiyun.animation.json";
   }

   public static Identifier defaultModel() {
      return fromModelPath(defaultModelPath());
   }

   public static Identifier defaultTexture() {
      return fromTexturePath(defaultTexturePath());
   }

   public static Identifier defaultAnimation() {
      return fromAnimationPath(defaultAnimationPath());
   }

   public static String characterAnimationPath(String characterId) {
      return "character/" + characterId + "/" + characterId + ".animation.json";
   }

   public static String characterModelPath(String characterId) {
      return "character/" + characterId + "/" + characterId + ".geo.json";
   }

   public static String characterTexturePath(String characterId) {
      return "character/" + characterId + "/textures/" + characterId + ".png";
   }

   public static String defaultModelFile(String characterId) {
      return characterId + ".geo.json";
   }

   public static String defaultAnimationFile(String characterId) {
      return characterId + ".animation.json";
   }

   public static String defaultTextureFile(String characterId) {
      return characterId + ".png";
   }

   public static Identifier fromModelPath(String relativePath) {
      return idOrNamespaced(stripSuffix(relativePath, ".geo.json"));
   }

   public static Identifier fromAnimationPath(String relativePath) {
      return idOrNamespaced(stripAnimationSuffix(relativePath));
   }

   public static Identifier fromTexturePath(String relativePath) {
      return idOrNamespaced(relativePath);
   }

   public static Identifier characterModel(String characterId, String fileName) {
      return id("character/" + characterId + "/" + stripSuffix(fileName, ".geo.json"));
   }

   public static Identifier characterAnimation(String characterId, String fileName) {
      return id("character/" + characterId + "/" + stripAnimationSuffix(fileName));
   }

   public static Identifier characterTexture(String characterId, String fileName) {
      return ModAssetPaths.textureIn("character/" + characterId, fileName);
   }

   public static String defaultItemModelFile(String itemName) {
      return itemName + ".geo.json";
   }

   public static String defaultItemAnimationFile(String itemName) {
      return itemName + ".animation.json";
   }

   public static String defaultItemTextureFile(String itemName) {
      return itemName + ".png";
   }

   public static Identifier itemModel(String itemName, String fileName) {
      return id("item/" + itemName + "/" + stripSuffix(fileName, ".geo.json"));
   }

   public static Identifier itemAnimation(String itemName, String fileName) {
      return id("item/" + itemName + "/" + stripAnimationSuffix(fileName));
   }

   public static Identifier itemTexture(String itemName, String fileName) {
      return ModAssetPaths.textureIn("item/" + itemName, fileName);
   }

   public static Identifier entityModel(String entityName, String fileName) {
      return id("entity/" + entityName + "/" + stripSuffix(fileName, ".geo.json"));
   }

   public static Identifier entityAnimation(String entityName, String fileName) {
      return id("entity/" + entityName + "/" + stripAnimationSuffix(fileName));
   }

   public static Identifier entityTexture(String entityName, String fileName) {
      return ModAssetPaths.textureIn("entity/" + entityName, fileName);
   }

   public static String characterSoundsDir(String characterId) {
      return "character/" + characterId + "/sounds";
   }

   public static String characterSoundDefinitionPath(String characterId) {
      return "character/" + characterId + "/sounds.json";
   }

   public static Identifier characterSound(String characterId, String fileName) {
      return id(characterSoundsDir(characterId) + "/" + fileName.replace('\\', '/'));
   }

   public static Identifier soundAssetPath(Identifier location) {
      if (location == null) {
         return null;
      }

      String path = location.getPath();
      return path.endsWith(".ogg") ? location : Identifier.fromNamespaceAndPath(location.getNamespace(), path + ".ogg");
   }

   public static boolean isCharacterSound(@Nullable Identifier location) {
      if (location == null) {
         return false;
      }

      String path = location.getPath();
      return path.startsWith("character/") && path.contains("/sounds/");
   }

   public static Identifier characterSoundEvent(String characterId, String key) {
      if (key == null || key.isEmpty()) {
         return null;
      }

      if (key.indexOf(58) >= 0) {
         return Identifier.tryParse(key);
      }

      String path = key.startsWith(characterId + "_") ? key : characterId + "_" + key;
      return id(path);
   }

   @Nullable
   public static String characterIdOfSoundDefinition(@Nullable Identifier definitionFile) {
      if (definitionFile != null && "minegenshin".equals(definitionFile.getNamespace())) {
         String path = definitionFile.getPath();
         String prefix = "character/";
         String suffix = "/sounds.json";
         if (path.startsWith(prefix) && path.endsWith(suffix)) {
            String id = path.substring(prefix.length(), path.length() - suffix.length());
            return !id.isEmpty() && id.indexOf(47) < 0 ? id : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Identifier icon(String category, String name) {
      return id("icon/" + category + "/" + stripSuffix(name, ".png") + ".png");
   }

   public static Identifier itemIcon(String itemName) {
      return icon("item", itemName);
   }

   public static void installDefaults() {
      GeoPathOverrides.register((kind, owner, original) -> {
         if (!"minegenshin".equals(original.getNamespace())) {
            return null;
         }

         String path = original.getPath();
         if (path.startsWith("character/") || path.startsWith("item/") || path.startsWith("entity/")) {
            return null;
         }

         if (owner instanceof GenshinAssets.CharacterAssetOwner charOwner) {
            String characterId = charOwner.assetCharacterId();
            if (characterId != null && !characterId.isEmpty()) {
               String file = lastSegment(path);
               if (file != null && !file.isEmpty()) {
                  return switch (kind) {
                     case MODEL -> characterModel(characterId, ensureSuffix(file, ".geo.json"));
                     case ANIMATION -> characterAnimation(characterId, ensureSuffix(file, ".animation.json"));
                     case TEXTURE -> characterTexture(characterId, ensureSuffix(file, ".png"));
                  };
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      });
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("minegenshin", path);
   }

   /**
    * 相对路径 → 资源路径，<b>允许写成 {@code namespace:path}</b>。
    *
    * <p>不带冒号就是本 MOD（和 {@link #id} 一样）；带冒号时用写死的命名空间 ——
    * 联动角色的资源在别的模组里，它的路径要能指过去，不能再被套上 {@code minegenshin:}。
    */
   public static Identifier idOrNamespaced(String path) {
      int colon = path.indexOf(':');
      return colon <= 0
              ? id(path)
              : Identifier.fromNamespaceAndPath(path.substring(0, colon), path.substring(colon + 1));
   }

   @Nullable
   private static String lastSegment(String path) {
      if (path != null && !path.isEmpty()) {
         int slash = path.lastIndexOf(47);
         return slash < 0 ? path : path.substring(slash + 1);
      } else {
         return null;
      }
   }

   private static String stripSuffix(String value, String suffix) {
      return value != null && value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
   }

   private static String stripAnimationSuffix(String value) {
      if (value == null) {
         return null;
      }

      for (String suffix : new String[]{".animation.json", ".animations.json", ".json"}) {
         if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
         }
      }

      return value;
   }

   private static String ensureSuffix(String value, String suffix) {
      return value.endsWith(suffix) ? value : value + suffix;
   }

   public interface CharacterAssetOwner {
      @Nullable
      String assetCharacterId();
   }
}
