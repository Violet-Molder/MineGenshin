// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

public final class GeoPackResources {
   private static final String NAMESPACE = "minegenshin";
   private static final String TEXTURE_SUFFIX = ".png";
   private static final PackResources SOURCE = new GeoPackResources.BundleSource();

   private GeoPackResources() {
   }

   @Nullable
   public static Resource resolve(@Nullable ResourceManager manager, @Nullable Identifier location) {
      if (manager == null || location == null) {
         return null;
      }

      if ("minegenshin".equals(location.getNamespace()) && isTexture(location)) {
         Map<Identifier, byte[]> entries = GeoPackSource.entriesQuiet(manager);
         if (!GeoPackSource.usePacked(location, entries.containsKey(location))) {
            return null;
         }

         byte[] data = entries.get(location);
         return data == null ? null : of(data);
      } else {
         return null;
      }
   }

   public static Map<Identifier, Resource> under(@Nullable ResourceManager manager, @Nullable String directory, @Nullable Predicate<Identifier> filter) {
      if (manager != null && directory != null) {
         String prefix = directory + "/";
         Map<Identifier, Resource> found = new LinkedHashMap<>();

         for (Entry<Identifier, byte[]> entry : GeoPackSource.entriesQuiet(manager).entrySet()) {
            Identifier location = entry.getKey();
            if ("minegenshin".equals(location.getNamespace())
               && isTexture(location)
               && location.getPath().startsWith(prefix)
               && (filter == null || filter.test(location))) {
               found.put(location, of(entry.getValue()));
            }
         }

         return found;
      } else {
         return Map.of();
      }
   }

   private static boolean isTexture(Identifier location) {
      return location.getPath().endsWith(".png");
   }

   private static Resource of(byte[] data) {
      return new Resource(SOURCE, () -> new ByteArrayInputStream(data));
   }

   private static final class BundleSource implements PackResources {
      private static final PackLocationInfo LOCATION = new PackLocationInfo(
         "minegenshin/geo-bundle", Component.literal("Minegenshin geo bundle"), PackSource.BUILT_IN, Optional.empty()
      );

      @Nullable
      public IoSupplier<InputStream> getRootResource(String... path) {
         return null;
      }

      @Nullable
      public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
         return null;
      }

      public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
      }

      public Set<String> getNamespaces(PackType type) {
         return Set.of();
      }

      @Nullable
      public <T> T getMetadataSection(MetadataSectionType<T> metadataSerializer) {
         return null;
      }

      public PackLocationInfo location() {
         return LOCATION;
      }

      public void close() {
      }
   }
}
