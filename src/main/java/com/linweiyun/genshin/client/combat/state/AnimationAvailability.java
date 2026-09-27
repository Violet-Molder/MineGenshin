// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.combat.state;

import com.geckolib.cache.animation.BakedAnimations;
import com.linweiyun.genshin.client.render.geo.AssetFallback;
import com.linweiyun.genshin.client.render.geo.GenshinGeoCache;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class AnimationAvailability {
   private static final Map<String, AnimationAvailability.Cached> CACHE = new ConcurrentHashMap<>();

   private AnimationAvailability() {
   }

   public static boolean existsFor(Player player, @Nullable String animationName) {
      return exists(CharacterHelper.getActiveCharacterId(player), animationName);
   }

   public static boolean exists(@Nullable String characterId, @Nullable String animationName) {
      if (animationName != null && !animationName.isEmpty()) {
         Set<String> names = namesFor(characterId);
         return names == null || names.contains(animationName);
      } else {
         return false;
      }
   }

   public static void invalidate() {
      CACHE.clear();
   }

   @Nullable
   private static Set<String> namesFor(@Nullable String characterId) {
      if (characterId != null && !characterId.isEmpty()) {
         AnimationAvailability.Cached cached = CACHE.get(characterId);
         if (cached != null && stillFresh(cached)) {
            return cached.names();
         }

         CharacterRenderData data = CharacterRenderRepository.get(characterId);
         if (data == null) {
            return null;
         }

         List<Identifier> files = new ArrayList<>(data.allAnimationPaths().size() + 1);
         files.add(AssetFallback.animation(data.animationIdentifier(), GenshinAssets.defaultAnimation()));

         for (String extra : data.extraAnimationPaths()) {
            files.add(GenshinAssets.fromAnimationPath(extra));
         }

         Set<String> names = new LinkedHashSet<>();
         BakedAnimations[] baked = new BakedAnimations[files.size()];

         for (int i = 0; i < files.size(); i++) {
            baked[i] = GenshinGeoCache.animationFile(files.get(i));
            GenshinGeoCache.collectAnimationNames(files.get(i), null, names);
         }

         boolean anyLoaded = false;

         for (BakedAnimations file : baked) {
            if (file != null) {
               anyLoaded = true;
               break;
            }
         }

         if (anyLoaded && !names.isEmpty()) {
            CACHE.put(characterId, new AnimationAvailability.Cached(files.getFirst(), baked, Set.copyOf(names)));
            return Set.copyOf(names);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean stillFresh(AnimationAvailability.Cached cached) {
      BakedAnimations[] baked = cached.baked();
      if (baked != null && baked.length != 0) {
         for (BakedAnimations file : baked) {
            if (file != null) {
               return GenshinGeoCache.animationFile(cached.primary()) == file;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private record Cached(Identifier primary, @Nullable BakedAnimations[] baked, Set<String> names) {
   }
}
