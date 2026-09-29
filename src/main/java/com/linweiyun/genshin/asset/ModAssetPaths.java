// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset;

import com.linweiyun.genshin.Minegenshin;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ModAssetPaths {
   public static final String JSON_SUFFIX = ".json";
   public static final String GEO_SUFFIX = ".geo.json";
   public static final String ANIMATION_SUFFIX = ".animation.json";
   public static final String ANIMATIONS_SUFFIX = ".animations.json";
   public static final String PNG_SUFFIX = ".png";
   public static final String BLOCKSTATE_FILE = "blockstate.json";
   public static final String BLOCK_ITEM_DIR = "blockitem";
   public static final String TEXTURE_DIR = "textures";
   public static final String LOCAL_DIR = "local";
   public static final String DEFINITION_FILE = "definition.json";
   public static final String MODEL_FILE = "model.json";
   public static final String TEXTURE_FILE = "texture.png";

   private ModAssetPaths() {
   }

   public static Identifier itemDefinition(String itemId) {
      return inDir(dir(AssetCategory.ITEM, itemId), "definition.json", "");
   }

   public static Identifier itemModel(String itemId) {
      return inDir(dir(AssetCategory.ITEM, itemId), "model.json", "");
   }

   public static Identifier itemTexture(String itemId) {
      return textureIn(dir(AssetCategory.ITEM, itemId), "texture.png");
   }

   public static Identifier blockModel(String blockId) {
      return inDir(dir(AssetCategory.BLOCK, blockId), "model.json", "");
   }

   public static Identifier blockTexture(String blockId) {
      return textureIn(dir(AssetCategory.BLOCK, blockId), "texture.png");
   }

   public static Identifier blockItemDefinition(String blockId) {
      return inDir(blockItemDir(blockId), "definition.json", "");
   }

   public static Identifier blockItemModel(String blockId) {
      return inDir(blockItemDir(blockId), "model.json", "");
   }

   public static Identifier blockItemTexture(String blockId) {
      return textureIn(blockItemDir(blockId), "texture.png");
   }

   public static String textureDir(String objectDir) {
      return objectDir + "/textures";
   }

   public static Identifier textureIn(String objectDir, String fileName) {
      return Minegenshin.id(textureDir(objectDir) + "/" + fileName);
   }

   @Nullable
   public static String objectDirOf(@Nullable String dir) {
      if (dir == null) {
         return null;
      }

      String result = dir;
      boolean stripped = true;

      while (stripped) {
         stripped = false;

         for (String layer : new String[]{"textures", "local"}) {
            String suffix = "/" + layer;
            if (result.endsWith(suffix)) {
               result = result.substring(0, result.length() - suffix.length());
               stripped = true;
            }
         }
      }

      return result;
   }

   public static boolean isLocalDir(@Nullable String dir) {
      return dir != null && dir.endsWith("/local");
   }

   public static boolean isLocalFile(@Nullable Identifier raw) {
      return raw != null && raw.getPath().contains("/local/");
   }

   public static Identifier withoutLocalDir(@Nullable Identifier raw) {
      if (raw == null) {
         return null;
      }

      String marker = "/local/";
      String path = raw.getPath();
      int at = path.indexOf(marker);
      return at < 0 ? raw : Identifier.fromNamespaceAndPath(raw.getNamespace(), path.substring(0, at + 1) + path.substring(at + marker.length()));
   }

   public static String dir(AssetCategory category, String id) {
      return category.folder() + "/" + id;
   }

   public static String blockItemDir(String blockId) {
      return dir(AssetCategory.BLOCK, blockId) + "/blockitem";
   }

   public static Identifier file(AssetCategory category, String id, String relative) {
      return Minegenshin.id(dir(category, id) + "/" + relative);
   }

   public static Identifier inDir(String directory, String baseName, String suffix) {
      return Minegenshin.id(directory + "/" + baseName + suffix);
   }

   public static Identifier geoModel(AssetCategory category, String id) {
      return file(category, id, id + ".json");
   }

   public static Identifier geoModelWithSuffix(AssetCategory category, String id) {
      return file(category, id, id + ".geo.json");
   }

   public static Identifier animation(AssetCategory category, String id) {
      return file(category, id, id + ".animation.json");
   }

   public static Identifier texture(AssetCategory category, String id) {
      return file(category, id, id + ".png");
   }

   public static Identifier blockState(String blockId) {
      return file(AssetCategory.BLOCK, blockId, "blockstate.json");
   }

   public static Identifier blockItemGeoModel(String blockId, String itemName) {
      return inDir(blockItemDir(blockId), itemName, ".json");
   }

   public static Identifier blockItemGeoModelWithSuffix(String blockId, String itemName) {
      return inDir(blockItemDir(blockId), itemName, ".geo.json");
   }

   public static Identifier blockItemAnimation(String blockId, String itemName) {
      return inDir(blockItemDir(blockId), itemName, ".animation.json");
   }

   public static Identifier blockItemTexture(String blockId, String itemName) {
      return inDir(blockItemDir(blockId), itemName, ".png");
   }

   public static Identifier assetKey(AssetCategory category, String id) {
      return Minegenshin.id(dir(category, id) + "/" + id);
   }

   public static Identifier blockItemKey(String blockId, String itemName) {
      return Minegenshin.id(blockItemDir(blockId) + "/" + itemName);
   }

   public static boolean isGeoModelFile(Identifier raw) {
      if (raw == null) {
         return false;
      }

      String path = raw.getPath();
      return path.endsWith(".json") && !path.endsWith(".animation.json") && !path.endsWith(".animations.json") ? !path.endsWith("/blockstate.json") : false;
   }

   public static boolean isAnimationFile(Identifier raw) {
      if (raw == null) {
         return false;
      }

      String path = raw.getPath();
      return path.endsWith(".animation.json") || path.endsWith(".animations.json");
   }

   public static Identifier modelKeyOf(Identifier raw) {
      return strip(raw, ".geo.json", ".json");
   }

   public static Identifier animationKeyOf(Identifier raw) {
      return strip(raw, ".animation.json", ".animations.json", ".json");
   }

   private static Identifier strip(@Nullable Identifier raw, String... suffixes) {
      if (raw == null) {
         return null;
      }

      String path = raw.getPath();

      for (String suffix : suffixes) {
         if (path.endsWith(suffix)) {
            path = path.substring(0, path.length() - suffix.length());
            break;
         }
      }

      return Identifier.fromNamespaceAndPath(raw.getNamespace(), path);
   }

   @Nullable
   public static AssetCategory categoryOf(@Nullable Identifier raw) {
      if (raw != null && "minegenshin".equals(raw.getNamespace())) {
         String path = raw.getPath();

         for (AssetCategory category : AssetCategory.values()) {
            if (category.matchesPath(path)) {
               return category;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   public static String idOf(@Nullable Identifier raw) {
      AssetCategory category = categoryOf(raw);
      if (category == null) {
         return null;
      }

      String remainder = raw.getPath().substring(category.folder().length() + 1);
      int slash = remainder.indexOf(47);
      String id = slash < 0 ? remainder : remainder.substring(0, slash);
      return id.isEmpty() ? null : id;
   }

   @Nullable
   public static String roleOf(@Nullable Identifier raw) {
      AssetCategory category = categoryOf(raw);
      if (category == null) {
         return null;
      }

      String remainder = raw.getPath().substring(category.folder().length() + 1);
      int slash = remainder.indexOf(47);
      if (slash < 0) {
         return null;
      }

      String role = remainder.substring(slash + 1);
      return role.isEmpty() ? null : role;
   }

   public static boolean isBlockState(@Nullable Identifier raw) {
      return raw != null && AssetCategory.BLOCK.matchesPath(raw.getPath()) && raw.getPath().endsWith("/blockstate.json");
   }

   public static boolean isBlockItem(@Nullable Identifier raw) {
      return raw != null && AssetCategory.BLOCK.matchesPath(raw.getPath()) && raw.getPath().contains("/blockitem/");
   }

   @Nullable
   public static String dirOf(@Nullable Identifier raw) {
      if (raw == null) {
         return null;
      }

      String path = raw.getPath();
      int slash = path.lastIndexOf(47);
      return slash <= 0 ? null : path.substring(0, slash);
   }

   @Nullable
   public static String baseNameOf(@Nullable Identifier raw) {
      if (raw == null) {
         return null;
      }

      String path = raw.getPath();
      int slash = path.lastIndexOf(47);
      String file = slash < 0 ? path : path.substring(slash + 1);

      for (String suffix : new String[]{".animation.json", ".animations.json", ".geo.json", ".json", ".png"}) {
         if (file.endsWith(suffix)) {
            return file.substring(0, file.length() - suffix.length());
         }
      }

      return file;
   }

   @Nullable
   public static String dirNameOf(@Nullable String dir) {
      if (dir == null) {
         return null;
      }

      int slash = dir.lastIndexOf(47);
      return slash < 0 ? dir : dir.substring(slash + 1);
   }
}
