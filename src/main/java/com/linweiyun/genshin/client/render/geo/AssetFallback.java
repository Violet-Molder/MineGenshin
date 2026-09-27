// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.geo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

public final class AssetFallback {
   private AssetFallback() {
   }

   public static Identifier model(@Nullable Identifier own, @Nullable Identifier... candidates) {
      if (own == null) {
         return firstNonNull(candidates);
      }

      if (GenshinGeoCache.model(own) != null) {
         return own;
      }

      for (Identifier candidate : candidates) {
         if (candidate != null && !own.equals(candidate) && GenshinGeoCache.model(candidate) != null) {
            return candidate;
         }
      }

      return own;
   }

   public static Identifier animation(@Nullable Identifier own, @Nullable Identifier... candidates) {
      if (own == null) {
         return firstNonNull(candidates);
      }

      if (GenshinGeoCache.animationFile(own) != null) {
         return own;
      }

      for (Identifier candidate : candidates) {
         if (candidate != null && !own.equals(candidate) && GenshinGeoCache.animationFile(candidate) != null) {
            return candidate;
         }
      }

      return own;
   }

   public static Identifier texture(@Nullable Identifier own, @Nullable Identifier... candidates) {
      if (own == null) {
         return firstNonNull(candidates);
      }

      if (exists(own)) {
         return own;
      }

      for (Identifier candidate : candidates) {
         if (candidate != null && !own.equals(candidate) && exists(candidate)) {
            return candidate;
         }
      }

      return own;
   }

   @Nullable
   private static Identifier firstNonNull(@Nullable Identifier[] candidates) {
      for (Identifier candidate : candidates) {
         if (candidate != null) {
            return candidate;
         }
      }

      return null;
   }

   private static boolean exists(Identifier location) {
      Minecraft minecraft = Minecraft.getInstance();
      ResourceManager manager = minecraft == null ? null : minecraft.getResourceManager();
      return manager == null ? true : manager.getResource(location).isPresent();
   }
}
