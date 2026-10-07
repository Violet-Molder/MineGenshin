// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset.pack;

import com.linweiyun.genshin.asset.ModAssetPaths;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class GeoPackSource {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
   private static final String NAMESPACE = "minegenshin";
   private static final ResourceLocation PACK_ID = ResourceLocation.fromNamespaceAndPath("minegenshin", "geo/georesources.minegenshin");
   private static volatile boolean explained;
   private static volatile boolean notReadyExplained;
   private static volatile int managersSeen;
   private static volatile ResourceManager lastManager;
   private static volatile Map<ResourceLocation, byte[]> lastEntries = Map.of();

   private GeoPackSource() {
   }

   public static Map<ResourceLocation, byte[]> entries(@Nullable ResourceManager manager) {
      return entries(manager, false);
   }

   public static Map<ResourceLocation, byte[]> entriesQuiet(@Nullable ResourceManager manager) {
      return entries(manager, true);
   }

   private static Map<ResourceLocation, byte[]> entries(@Nullable ResourceManager manager, boolean quiet) {
      if (manager == null) {
         return Map.of();
      }

      if (manager == lastManager) {
         return lastEntries;
      }

      synchronized (GeoPackSource.class) {
         if (manager == lastManager) {
            return lastEntries;
         }

         Map<ResourceLocation, byte[]> loaded = load(manager, quiet);
         if (loaded == null) {
            return Map.of();
         }

         if (!quiet || !loaded.isEmpty()) {
            lastEntries = loaded;
            lastManager = manager;
         }

         return loaded;
      }
   }

   public static boolean contains(@Nullable ResourceManager manager, @Nullable ResourceLocation filePath) {
      return filePath != null && entries(manager).containsKey(filePath);
   }

   public static int size(@Nullable ResourceManager manager) {
      return entries(manager).size();
   }

   public static boolean usePacked(@Nullable ResourceLocation raw, boolean available) {
      return available && raw != null ? !ModAssetPaths.isLocalFile(raw) : false;
   }

   @Nullable
   private static Map<ResourceLocation, byte[]> load(ResourceManager manager, boolean quiet) {
      int seen = managersSeen++;
      if (!manager.getNamespaces().contains("minegenshin")) {
         if (!quiet && !notReadyExplained) {
            notReadyExplained = true;
            LOGGER.debug("[GeoPackSource] 这次查询的资源管理器里还没有本 MOD 的命名空间（资源加载之前），等加载完成后再读整包");
         }

         return null;
      } else {
         Optional<Resource> found = manager.getResource(PACK_ID);
         if (found.isEmpty()) {
            if (!quiet) {
               if (seen == 0) {
                  LOGGER.debug("[GeoPackSource] 启动早期的资源管理器里还没有整包文件，等资源加载后再读");
               } else {
                  explainOnce("资源管理器里找不到整包文件（" + PACK_ID + "）：本次构建没有打包内置资源；模型 / 动画 / 贴图将不会显示");
               }
            }

            return Map.of();
         } else {
            byte[] raw;
            try (InputStream in = found.get().open()) {
               raw = in.readAllBytes();
            } catch (IOException e) {
               if (!quiet) {
                  explainOnce("读取整包文件失败：" + PACK_ID, e);
               }

               return Map.of();
            }

            Map<String, byte[]> items;
            try {
               items = GeoPack.load(raw, "geo/georesources.minegenshin");
            } catch (IllegalStateException e) {
               if (!quiet) {
                  explainOnce("整包文件内容残缺（被截断或改动过）：" + PACK_ID, e);
               }

               return Map.of();
            }

            if (items == null) {
               if (!quiet) {
                  if (GeoPackReaders.isBaseline()) {
                     explainOnce("这个整包需要读取器才能打开，当前环境没有读取器，读不出来（属预期）");
                  } else {
                     explainOnce("整包打不开：内容对不上：" + PACK_ID);
                  }
               }

               return Map.of();
            } else {
               Map<ResourceLocation, byte[]> entries = new LinkedHashMap<>(Math.max(4, items.size() * 2));
               long bytes = 0L;

               for (Entry<String, byte[]> item : items.entrySet()) {
                  entries.put(ResourceLocation.fromNamespaceAndPath("minegenshin", item.getKey()), item.getValue());
                  bytes += ((byte[])item.getValue()).length;
               }

               LOGGER.info("[GeoPackSource] 已读入整包：{} 条资源（共 {} 字节）", entries.size(), bytes);
               return Map.copyOf(entries);
            }
         }
      }
   }

   private static void explainOnce(String message) {
      if (!explained) {
         explained = true;
         LOGGER.warn("[GeoPackSource] {}", message);
      }
   }

   private static void explainOnce(String message, Throwable cause) {
      if (!explained) {
         explained = true;
         LOGGER.warn("[GeoPackSource] {}", message, cause);
      }
   }
}
