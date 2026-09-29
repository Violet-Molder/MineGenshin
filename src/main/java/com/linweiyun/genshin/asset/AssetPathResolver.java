// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.asset;

import com.linweiyun.genshin.asset.pack.GeoPackSource;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

public final class AssetPathResolver {
   private AssetPathResolver() {
   }

   public static boolean existing(@Nullable ResourceManager resourceManager, @Nullable Identifier location) {
      return resourceManager != null && location != null
         ? resourceManager.getResource(location).isPresent() || GeoPackSource.contains(resourceManager, location)
         : false;
   }

   @Nullable
   public static Identifier firstExisting(@Nullable ResourceManager resourceManager, Identifier... candidates) {
      if (resourceManager != null && candidates != null) {
         for (Identifier candidate : candidates) {
            if (existing(resourceManager, candidate)) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   public static Identifier resolveModel(@Nullable ResourceManager resourceManager, AssetSet set) {
      return firstExisting(resourceManager, set.modelCandidates());
   }

   @Nullable
   public static Identifier resolveAnimation(@Nullable ResourceManager resourceManager, AssetSet set) {
      return firstExisting(resourceManager, set.animationCandidates());
   }

   @Nullable
   public static Identifier resolveTexture(@Nullable ResourceManager resourceManager, AssetSet set) {
      return firstExisting(resourceManager, set.textureCandidates());
   }

   public static Map<Identifier, Resource> listCategory(@Nullable ResourceManager resourceManager, AssetCategory category) {
      if (resourceManager == null) {
         return Map.of();
      }

      String root = category.folder();
      return resourceManager.listResources(root, id -> category.matchesPath(id.getPath()));
   }

   public static Set<String> listIds(@Nullable ResourceManager resourceManager, AssetCategory category) {
      Set<String> ids = new LinkedHashSet<>();

      for (Identifier location : listCategory(resourceManager, category).keySet()) {
         String id = ModAssetPaths.idOf(location);
         if (id != null) {
            ids.add(id);
         }
      }

      return ids;
   }

   public static Set<String> listRoles(@Nullable ResourceManager resourceManager, AssetCategory category, String id) {
      Set<String> roles = new LinkedHashSet<>();

      for (Identifier location : listCategory(resourceManager, category).keySet()) {
         if (id.equals(ModAssetPaths.idOf(location))) {
            String role = ModAssetPaths.roleOf(location);
            if (role != null) {
               roles.add(role);
            }
         }
      }

      return roles;
   }

   public static String describe(@Nullable ResourceManager resourceManager, AssetSet set) {
      return set.describe()
         + " model="
         + presence(resolveModel(resourceManager, set))
         + " animation="
         + presence(resolveAnimation(resourceManager, set))
         + " texture="
         + presence(resolveTexture(resourceManager, set));
   }

   private static String presence(@Nullable Identifier location) {
      return location == null ? "MISSING" : location.toString();
   }

   public static Optional<Resource> resource(@Nullable ResourceManager resourceManager, @Nullable Identifier location) {
      return resourceManager != null && location != null ? resourceManager.getResource(location) : Optional.empty();
   }
}
