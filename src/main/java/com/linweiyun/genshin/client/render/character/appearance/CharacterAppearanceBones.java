// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import com.linweiyun.genshin.core.character.util.appearance.EarBoneRules;
import com.linweiyun.genshin.core.character.util.appearance.LegBoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

public final class CharacterAppearanceBones {
   private static final int MASK_BITS = 20;
   private static final int MASK_MASK = 1048575;
   private static final Map<Integer, BoneUpdater<BoneRenderState>> CACHE = new ConcurrentHashMap<>();

   private CharacterAppearanceBones() {
   }

   public static BoneUpdater<BoneRenderState> forMask(int mask) {
      int key = mask & 1048575;
      BoneUpdater<BoneRenderState> cached = CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      BoneUpdater<BoneRenderState> created = updaterOf(hiddenBones(key));
      BoneUpdater<BoneRenderState> previous = CACHE.putIfAbsent(key, created);
      return previous != null ? previous : created;
   }

   private static List<String> hiddenBones(int mask) {
      List<String> hidden = new ArrayList<>(LegBoneRules.hiddenBones(mask));
      hidden.addAll(EarBoneRules.hiddenBones(mask));
      return hidden;
   }

   private static BoneUpdater<BoneRenderState> updaterOf(List<String> hidden) {
      return (renderPassInfo, snapshots) -> {
         for (String bone : hidden) {
            snapshots.ifPresent(bone, snapshot -> {
               snapshot.skipRender(true);
               snapshot.skipChildrenRender(true);
            });
         }
      };
   }

   @Nullable
   public static BoneUpdater<BoneRenderState> forMaskOrNull(@Nullable Integer mask) {
      return mask == null ? null : forMask(mask);
   }
}
