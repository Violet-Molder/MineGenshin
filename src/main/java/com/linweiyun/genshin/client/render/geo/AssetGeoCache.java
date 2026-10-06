// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.geo;

import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.math.MathParser;
import com.google.gson.JsonObject;
import com.linweiyun.genshin.asset.AssetCategory;
import com.linweiyun.genshin.asset.ModAssetPaths;
import com.linweiyun.genshin.asset.pack.GenshinGsonLoader;
import com.linweiyun.genshin.asset.pack.GeoJsonReader;
import com.linweiyun.genshin.asset.pack.GeoPackSource;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class AssetGeoCache implements PreparableReloadListener {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final GenshinGsonLoader LOADER = new GenshinGsonLoader();
   private static final String[] ROOTS = new String[]{AssetCategory.ITEM.folder(), AssetCategory.BLOCK.folder(), AssetCategory.ENTITY.folder()};
   private static final int MAX_EMPTY_SCANS = 5;
   private static final String[] VANILLA_ENTRY_FILES = new String[]{"blockstate.json", "definition.json", "model.json"};
   private static volatile Map<Identifier, BakedGeoModel> models = Map.of();
   private static volatile Map<Identifier, BakedAnimations> animations = Map.of();
   private static volatile Map<String, AssetGeoCache.DirFiles> index = Map.of();
   private static volatile boolean reloaded = false;
   private static int emptyScans = 0;

   @Nullable
   public static BakedGeoModel model(@Nullable Identifier location) {
      if (location == null) {
         return null;
      }

      ensureLoaded();
      return models.get(location);
   }

   @Nullable
   public static Animation animation(@Nullable Identifier animationKey, @Nullable String name) {
      if (animationKey != null && name != null) {
         ensureLoaded();
         BakedAnimations baked = animations.get(animationKey);
         if (baked == null) {
            return null;
         }

         Animation exact = baked.getAnimation(name);
         if (exact != null) {
            return exact;
         }

         String matched = fuzzyAnimationName(baked, name);
         if (matched == null) {
            return null;
         }

         LOGGER.info("[AssetGeoCache] 动画名宽松匹配：{} 里没有 '{}'，改用 '{}'", new Object[]{animationKey, name, matched});
         return baked.getAnimation(matched);
      } else {
         return null;
      }
   }

   public static Set<String> animationNames(@Nullable Identifier animationKey) {
      if (animationKey == null) {
         return Set.of();
      }

      ensureLoaded();
      BakedAnimations baked = animations.get(animationKey);
      return baked == null ? Set.of() : baked.animations().keySet();
   }

   @Nullable
   private static String fuzzyAnimationName(BakedAnimations baked, String wanted) {
      String suffix = "." + wanted;
      String candidate = null;

      for (String available : baked.animations().keySet()) {
         if (available.equals(wanted) || available.endsWith(suffix)) {
            if (candidate != null) {
               return null;
            }

            candidate = available;
         }
      }

      return candidate;
   }

   public static AssetGeoCache.DirFiles files(@Nullable String dir) {
      if (dir == null) {
         return AssetGeoCache.DirFiles.EMPTY;
      }

      ensureLoaded();
      return index.getOrDefault(dir, AssetGeoCache.DirFiles.EMPTY);
   }

   public static int dirCount() {
      return index.size();
   }

   public static void warmUp() {
      ensureLoaded();
   }

   public static int modelCount() {
      return models.size();
   }

   public static Set<String> knownDirs() {
      ensureLoaded();
      return index.keySet();
   }

   private static void ensureLoaded() {
      if (!reloaded) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null) {
            ResourceManager resourceManager = minecraft.getResourceManager();
            if (resourceManager != null) {
               synchronized (AssetGeoCache.class) {
                  if (!reloaded) {
                     LOGGER.info("[AssetGeoCache] 首次查询时同步补扫一次统一布局资源");
                     AssetGeoCache instance = new AssetGeoCache();
                     instance.apply(instance.scan(resourceManager));
                  }
               }
            }
         }
      }
   }

   public CompletableFuture<Void> reload(SharedState sharedState, Executor prepExecutor, PreparationBarrier barrier, Executor applyExecutor) {
      ResourceManager resourceManager = sharedState.resourceManager();
      return CompletableFuture.<AssetGeoCache.Scanned>supplyAsync(() -> this.scan(resourceManager), prepExecutor)
         .<AssetGeoCache.Scanned>thenCompose(barrier::wait)
         .thenAcceptAsync(this::apply, applyExecutor)
         .exceptionally(t -> {
            LOGGER.error("[AssetGeoCache] 重载监听器执行失败，保持上一次的索引（不影响其它资源重载）", t);
            return null;
         });
   }

   private AssetGeoCache.Scanned scan(ResourceManager resourceManager) {
      try {
         return this.scanUnsafe(resourceManager);
      } catch (Throwable t) {
         LOGGER.error("[AssetGeoCache] 扫描失败，本次跳过统一布局资源（不影响其它资源重载）", t);
         return new AssetGeoCache.Scanned(Map.of(), Map.of(), Map.of());
      }
   }

   private AssetGeoCache.Scanned scanUnsafe(ResourceManager resourceManager) {
      Map<Identifier, BakedGeoModel> foundModels = new HashMap<>(models);
      Map<Identifier, BakedAnimations> foundAnimations = new HashMap<>(animations);
      Map<String, AssetGeoCache.DirFiles> foundIndex = new HashMap<>(index);
      MathParser mathParser = MathParser.createWithDeduplication();
      Map<Identifier, byte[]> packedEntries = GeoPackSource.entries(resourceManager);

      for (String root : ROOTS) {
         Map<Identifier, Resource> resources;
         try {
            resources = resourceManager.listResources(root, id -> id.getNamespace().equals("minegenshin"));
         } catch (Exception e) {
            LOGGER.warn("[AssetGeoCache] 扫描根 '{}' 失败：{}", root, e.toString());
            continue;
         }

         Set<Identifier> candidates = new LinkedHashSet<>(resources.keySet());

         for (Identifier packed : packedEntries.keySet()) {
            if (packed.getPath().startsWith(root + "/") && !resources.containsKey(packed)) {
               candidates.add(packed);
            }
         }

         int before = foundModels.size() + foundAnimations.size();
         int fromDisk = 0;
         int fromPack = 0;

         for (Identifier raw : candidates) {
            String path = raw.getPath();
            if (path.startsWith(root + "/") && !isVanillaEntryFile(raw)) {
               Resource onDisk = resources.get(raw);
               byte[] packed = GeoPackSource.usePacked(raw, packedEntries.containsKey(raw)) ? packedEntries.get(raw) : null;
               if (onDisk != null || packed != null) {
                  if (packed != null) {
                     fromPack++;
                  } else if (onDisk != null) {
                     fromDisk++;
                  }

                  String dir = ModAssetPaths.objectDirOf(ModAssetPaths.dirOf(raw));
                  if (dir != null) {
                     if (path.endsWith(".png")) {
                        if (onDisk != null || packed != null) {
                           foundIndex.merge(dir, new AssetGeoCache.DirFiles(null, null, raw), AssetGeoCache::preferTexture);
                        }
                     } else if (path.endsWith(".json") && !ModAssetPaths.isBlockState(raw)) {
                        AssetGeoCache.ContentKind kind = classify(packed == null ? onDisk : null, packed, raw);
                        if (kind == AssetGeoCache.ContentKind.UNKNOWN) {
                           kind = ModAssetPaths.isAnimationFile(raw) ? AssetGeoCache.ContentKind.ANIMATION : AssetGeoCache.ContentKind.MODEL;
                           LOGGER.info("[AssetGeoCache] {} 的内容判不出类型，按文件名当作 {}", raw, kind);
                        }

                        try {
                           if (kind == AssetGeoCache.ContentKind.ANIMATION) {
                              JsonObject json = packed == null ? LOADER.deserializeGeckoLibAnimationFile(raw, onDisk) : LOADER.readPacked(raw, packed);
                              BakedAnimations baked = LOADER.bakeGeckoLibAnimationsFile(raw, json, mathParser);
                              if (baked != null) {
                                 foundAnimations.put(ModAssetPaths.animationKeyOf(raw), baked);
                                 foundIndex.merge(dir, new AssetGeoCache.DirFiles(null, raw, null), AssetGeoCache::preferAnimation);
                              } else {
                                 LOGGER.warn("[AssetGeoCache] 动画烘培返回 null，跳过：{}", raw);
                              }
                           } else {
                              JsonObject json = packed == null ? LOADER.deserializeGeckoLibModelFile(raw, onDisk) : LOADER.readPacked(raw, packed);
                              BakedGeoModel baked = LOADER.bakeGeckoLibModelFile(raw, json);
                              if (baked != null) {
                                 foundModels.put(ModAssetPaths.modelKeyOf(raw), baked);
                                 foundIndex.merge(dir, new AssetGeoCache.DirFiles(raw, null, null), AssetGeoCache::preferModel);
                              } else {
                                 LOGGER.warn("[AssetGeoCache] 模型烘培返回 null，跳过：{}", raw);
                              }
                           }
                        } catch (Exception e) {
                           LOGGER.error("[AssetGeoCache] 烘培失败（文件读到了，但 GeckoLib 解析不了；这种情况下运行时会报「Unable to find animation file」）：{}", raw, e);
                        }
                     }
                  }
               }
            }
         }

         LOGGER.info(
            "[AssetGeoCache] 扫描根 '{}'：命中资源 {} 个（磁盘 {} / 资源包 {}），新烘培 {} 个",
            new Object[]{root, candidates.size(), fromDisk, fromPack, foundModels.size() + foundAnimations.size() - before}
         );
      }

      return new AssetGeoCache.Scanned(copyNonNull(foundModels), copyNonNull(foundAnimations), copyNonNull(foundIndex));
   }

   private static <K, V> Map<K, V> copyNonNull(Map<K, V> source) {
      Map<K, V> clean = new HashMap<>(source.size());
      source.forEach((key, value) -> {
         if (key != null && value != null) {
            clean.put((K)key, (V)value);
         }
      });
      return Map.copyOf(clean);
   }

   private static boolean isVanillaEntryFile(Identifier raw) {
      String path = raw.getPath();
      int slash = path.lastIndexOf(47);
      String file = slash < 0 ? path : path.substring(slash + 1);

      for (String name : VANILLA_ENTRY_FILES) {
         if (name.equals(file)) {
            return true;
         }
      }

      return false;
   }

   private static AssetGeoCache.ContentKind classify(@Nullable Resource resource, @Nullable byte[] packed, Identifier id) {
      try {
         JsonObject json = GeoJsonReader.read(resource, packed, id);
         if (json.has("animations")) {
            return AssetGeoCache.ContentKind.ANIMATION;
         } else {
            return !json.has("minecraft:geometry") && !json.has("geometry") ? AssetGeoCache.ContentKind.UNKNOWN : AssetGeoCache.ContentKind.MODEL;
         }
      } catch (Exception e) {
         LOGGER.warn("[AssetGeoCache] 读取 {} 失败：{}", id, e.toString());
         return AssetGeoCache.ContentKind.UNKNOWN;
      }
   }

   private static AssetGeoCache.DirFiles preferTexture(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
      if (isPlain(incoming.texture()) != isPlain(existing.texture())) {
         return new AssetGeoCache.DirFiles(existing.model(), existing.animation(), isPlain(incoming.texture()) ? incoming.texture() : existing.texture());
      } else if (existing.texture() == null) {
         return new AssetGeoCache.DirFiles(existing.model(), existing.animation(), incoming.texture());
      } else {
         return matchesDirName(incoming.texture()) && !matchesDirName(existing.texture())
            ? new AssetGeoCache.DirFiles(existing.model(), existing.animation(), incoming.texture())
            : existing;
      }
   }

   private static AssetGeoCache.DirFiles preferAnimation(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
      if (isPlain(incoming.animation()) != isPlain(existing.animation())) {
         return new AssetGeoCache.DirFiles(existing.model(), isPlain(incoming.animation()) ? incoming.animation() : existing.animation(), existing.texture());
      } else if (existing.animation() == null) {
         return new AssetGeoCache.DirFiles(existing.model(), incoming.animation(), existing.texture());
      } else {
         return matchesDirName(incoming.animation()) && !matchesDirName(existing.animation())
            ? new AssetGeoCache.DirFiles(existing.model(), incoming.animation(), existing.texture())
            : existing;
      }
   }

   private static AssetGeoCache.DirFiles preferModel(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
      if (isPlain(incoming.model()) != isPlain(existing.model())) {
         return new AssetGeoCache.DirFiles(isPlain(incoming.model()) ? incoming.model() : existing.model(), existing.animation(), existing.texture());
      }

      if (existing.model() == null) {
         return new AssetGeoCache.DirFiles(incoming.model(), existing.animation(), existing.texture());
      }

      boolean incomingNamed = matchesDirName(incoming.model());
      boolean existingNamed = matchesDirName(existing.model());
      if (incomingNamed != existingNamed) {
         return new AssetGeoCache.DirFiles(incomingNamed ? incoming.model() : existing.model(), existing.animation(), existing.texture());
      }

      boolean incomingPlain = !incoming.model().getPath().endsWith(".geo.json");
      boolean existingPlain = !existing.model().getPath().endsWith(".geo.json");
      return incomingPlain != existingPlain
         ? new AssetGeoCache.DirFiles(incomingPlain ? incoming.model() : existing.model(), existing.animation(), existing.texture())
         : existing;
   }

   private static boolean matchesDirName(@Nullable Identifier file) {
      String dir = ModAssetPaths.dirOf(file);
      String base = ModAssetPaths.baseNameOf(file);
      return dir != null && base != null && base.equals(ModAssetPaths.dirNameOf(dir));
   }

   private static boolean isPlain(@Nullable Identifier file) {
      return ModAssetPaths.isLocalFile(file);
   }

   private void apply(AssetGeoCache.Scanned scanned) {
      models = scanned.models();
      animations = scanned.animations();
      index = scanned.index();
      boolean empty = index.isEmpty() && models.isEmpty() && animations.isEmpty();
      if (empty && ++emptyScans < 5) {
         LOGGER.warn("[AssetGeoCache] 本次一个文件都没扫到（资源可能还没就绪），保留重扫机会：第 {} / {} 次", new Object[]{emptyScans, 5});
      } else {
         emptyScans = 0;
         reloaded = true;
      }
   }

   private static String describe(@Nullable Identifier location) {
      return location == null ? "MISSING" : location.toString();
   }

   private enum ContentKind {
      MODEL,
      ANIMATION,
      UNKNOWN;
   }

   public record DirFiles(@Nullable Identifier model, @Nullable Identifier animation, @Nullable Identifier texture) {
      public static final AssetGeoCache.DirFiles EMPTY = new AssetGeoCache.DirFiles(null, null, null);
   }

   private record Scanned(Map<Identifier, BakedGeoModel> models, Map<Identifier, BakedAnimations> animations, Map<String, AssetGeoCache.DirFiles> index) {
   }
}
