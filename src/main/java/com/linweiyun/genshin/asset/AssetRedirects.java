// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class AssetRedirects {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
   private static final String NS = "minegenshin";
   private static final Set<String> VANILLA_ROOTS = Set.of("blockstates", "items", "models", "textures");
   private static final Map<String, String[]> MIRROR_DIRECTORIES = Map.of(
      "models",
      new String[]{AssetCategory.ITEM.folder(), AssetCategory.BLOCK.folder()},
      "textures/item",
      new String[]{AssetCategory.ITEM.folder()},
      "textures/block",
      new String[]{AssetCategory.BLOCK.folder()}
   );
   private static final Set<String> NON_MODEL_FILES = Set.of("blockstate.json", "definition.json", "sounds.json");
   private static final String GEO_SUFFIX = ".geo.json";
   private static final String ANIMATION_SUFFIX = ".animation.json";
   private static final String ANIMATIONS_SUFFIX = ".animations.json";
   private static final Map<Identifier, Identifier> BLOCK_ITEM_OVERRIDES = new ConcurrentHashMap<>();
   private static final Map<String, Integer> LAST_LOGGED = new ConcurrentHashMap<>();

   private AssetRedirects() {
   }

   public static void registerBlockItem(Identifier blockId, Identifier itemId) {
      if (blockId != null && itemId != null) {
         BLOCK_ITEM_OVERRIDES.put(itemId, blockId);
      }
   }

   public static Map<Identifier, Resource> resolve(@Nullable String vanillaDirectory, @Nullable ResourceManager manager) {
      if (vanillaDirectory != null && manager != null) {
         String directory = trimSlashes(vanillaDirectory);
         int slash = directory.indexOf(47);
         String root = slash < 0 ? directory : directory.substring(0, slash);
         if (!VANILLA_ROOTS.contains(root)) {
            return Map.of();
         }

         Map<Identifier, Identifier> plan = switch (root) {
            case "blockstates" -> blockStateEntries();
            case "items" -> itemDefinitionEntries();
            case "models" -> mirrorEntries(manager, directory, ".json", true, "models/", false);
            case "textures" -> mirrorEntries(manager, directory, ".png", false, "textures/", true);
            default -> Map.of();
         };
         return materialize(directory, plan, manager);
      } else {
         return Map.of();
      }
   }

   private static Map<Identifier, Identifier> blockStateEntries() {
      Map<Identifier, Identifier> plan = new LinkedHashMap<>();

      for (Identifier blockId : BuiltInRegistries.BLOCK.keySet()) {
         if (isOurs(blockId)) {
            plan.put(id("blockstates/" + blockId.getPath() + ".json"), id(ModAssetPaths.dir(AssetCategory.BLOCK, blockId.getPath()) + "/blockstate.json"));
         }
      }

      return plan;
   }

   private static Map<Identifier, Identifier> itemDefinitionEntries() {
      Map<Identifier, Identifier> plan = new LinkedHashMap<>();

      for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
         if (isOurs(itemId)) {
            plan.put(id("items/" + itemId.getPath() + ".json"), id(ModAssetPaths.dir(AssetCategory.ITEM, itemId.getPath()) + "/definition.json"));
         }
      }

      for (Identifier blockId : BuiltInRegistries.BLOCK.keySet()) {
         if (isOurs(blockId)) {
            plan.putIfAbsent(id("items/" + blockId.getPath() + ".json"), id(ModAssetPaths.blockItemDir(blockId.getPath()) + "/definition.json"));
         }
      }

      BLOCK_ITEM_OVERRIDES.forEach(
         (itemId, blockIdx) -> plan.put(id("items/" + itemId.getPath() + ".json"), id(ModAssetPaths.blockItemDir(blockIdx.getPath()) + "/definition.json"))
      );
      return plan;
   }

   private static Map<Identifier, Identifier> mirrorEntries(
      ResourceManager manager, String directory, String suffix, boolean models, String vanillaPrefix, boolean stripLayoutTextureDir
   ) {
      String[] roots = MIRROR_DIRECTORIES.get(directory);
      if (roots != null && roots.length != 0) {
         Map<Identifier, Identifier> plan = new LinkedHashMap<>();

         for (Identifier file : listLayout(manager, suffix, roots)) {
            String path = file.getPath();
            if (!models || !isNonModel(path)) {
               Identifier vanillaId = id(vanillaPrefix + (stripLayoutTextureDir ? stripTextureDir(path) : path));
               Identifier mirrored = id(path);
               if (!vanillaId.equals(mirrored)) {
                  Identifier previous = plan.get(vanillaId);
                  if (previous == null || ModAssetPaths.isLocalFile(file) && !ModAssetPaths.isLocalFile(previous)) {
                     plan.put(vanillaId, mirrored);
                  }
               }

               // 物品的「默认模型」：统一布局里只有 item/<id>/model.json，
               // 而原版按物品 id 找 models/item/<id>.json —— 让默认 id 也指到同一个文件。
               String defaultItemModel = models ? defaultItemModelFile(path) : null;
               if (defaultItemModel != null) {
                  plan.putIfAbsent(id("models/" + defaultItemModel), mirrored);
               }
            }
         }

         return plan;
      } else {
         return Map.of();
      }
   }

   private static boolean isNonModel(String path) {
      if (!path.endsWith(".geo.json") && !path.endsWith(".animation.json") && !path.endsWith(".animations.json")) {
         String file = path.substring(path.lastIndexOf(47) + 1);
         return NON_MODEL_FILES.contains(file);
      } else {
         return true;
      }
   }

   /**
    * {@code item/<id>/model.json} → {@code item/<id>.json}（原版物品默认模型在 {@code models/} 下的文件名）。
    *
    * <p>不是「物品目录里的 model.json」时返回 null。
    */
   @Nullable
   private static String defaultItemModelFile(String path) {
      String prefix = AssetCategory.ITEM.folder() + "/";
      String suffix = "/" + ModAssetPaths.MODEL_FILE;
      if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
         return null;
      }

      String id = path.substring(prefix.length(), path.length() - suffix.length());
      return id.isEmpty() || id.indexOf(47) >= 0 ? null : prefix + id + ".json";
   }

   private static String stripTextureDir(String path) {
      return stripLayer(stripLayer(path, "textures"), "local");
   }

   private static String stripLayer(String path, String layer) {
      String marker = "/" + layer + "/";
      int at = path.indexOf(marker);
      return at < 0 ? path : path.substring(0, at) + "/" + path.substring(at + marker.length());
   }

   private static List<Identifier> listLayout(ResourceManager manager, String suffix, String... roots) {
      List<Identifier> found = new ArrayList<>();

      for (String root : roots) {
         manager.listResources(root, candidate -> candidate.getPath().startsWith(root + "/") && candidate.getPath().endsWith(suffix))
            .keySet()
            .forEach(candidate -> {
               if (isOurs(candidate)) {
                  found.add(candidate);
               }
            });
      }

      return found;
   }

   private static Map<Identifier, Resource> materialize(String directory, Map<Identifier, Identifier> plan, ResourceManager manager) {
      if (plan.isEmpty()) {
         return Map.of();
      }

      Map<Identifier, Resource> resolved = new LinkedHashMap<>();

      for (Entry<Identifier, Identifier> entry : plan.entrySet()) {
         Resource resource = (Resource)manager.getResource(entry.getValue()).orElse(null);
         if (resource != null) {
            resolved.put(entry.getKey(), resource);
         }
      }

      logInjection(directory, resolved);
      return resolved;
   }

   private static void logInjection(String directory, Map<Identifier, Resource> resolved) {
      Integer previous = LAST_LOGGED.put(directory, resolved.size());
      if (!resolved.isEmpty() && (previous == null || previous != resolved.size())) {
         List<String> samples = new ArrayList<>(3);

         for (Entry<Identifier, Resource> entry : resolved.entrySet()) {
            if (samples.size() >= 3) {
               break;
            }

            samples.add(entry.getKey() + "（实读 " + entry.getValue().sourcePackId() + "）");
         }

         LOGGER.info("[AssetRedirects] 原版目录 '{}' 注入 {} 条虚拟入口：{}", new Object[]{directory, resolved.size(), String.join("、", samples)});
      }
   }

   private static boolean isOurs(@Nullable Identifier id) {
      return id != null && "minegenshin".equals(id.getNamespace());
   }

   private static String trimSlashes(String value) {
      String trimmed = value;

      while (trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }

      while (trimmed.endsWith("/")) {
         trimmed = trimmed.substring(0, trimmed.length() - 1);
      }

      return trimmed;
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("minegenshin", path);
   }
}
