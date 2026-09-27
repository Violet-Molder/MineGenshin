// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class CharacterAppearanceOptionBones {

   private static final Logger LOGGER = ModLog.getLogger(LogGroup.ANIMATION);

   /** 排查用：上一次打日志的判定串（变了才打，避免刷屏）。 */
   private static String lastProbeKey = null;

   /** 排查用：骨骼状态那半段也要等骨骼真的取到了再打，所以分两步。 */
   private static String pendingProbe = null;

   private CharacterAppearanceOptionBones() {
   }

   @Nullable
   public static BoneUpdater<GeoRenderState> updaterFor(@Nullable Player player, @Nullable PGCharacter character) {
      if (player != null && character != null) {
         String state = AnimationStateSync.stateOf(player);
         boolean normalState = "default".equals(state);
         int mask = character.getAppearance();
         Map<String, Boolean> bones = character.appearanceData().normalStateBones(mask, character.currentWeaponType());

         // 排查用：把「这一次的判定」原样打出来 —— 掩码 / 形态 / 状态 / 开关位 / 名单。
         // 只在判定变化时打，所以正常游戏不会刷屏。用户复现一次（开开关、站着）就能看到。
         if (player == Minecraft.getInstance().player && LOGGER.isInfoEnabled()) {
            String key = character.getTextureId() + "|" + mask + "|" + character.currentWeaponType() + "|" + state;
            if (!key.equals(lastProbeKey)) {
               lastProbeKey = key;
               boolean showBit = character.appearanceData().showWeapon(mask);
               LOGGER.info("武器显隐 probe：角色={} 掩码=0x{} 形态={} 状态={} 常态={} 显示武器位={} 名单={}",
                       character.getTextureId(), Integer.toHexString(mask), character.currentWeaponType(),
                       state, normalState, showBit, bones);
               pendingProbe = key;
            }
         }

         return updater(bones, normalState);
      } else {
         return null;
      }
   }

   public static BoneUpdater<GeoRenderState> updaterForPreview(int mask, PGCharacter character) {
      return updater(character.appearanceData().normalStateBones(mask, character.currentWeaponType()), true);
   }

   private static BoneUpdater<GeoRenderState> updater(Map<String, Boolean> bones, boolean normalState) {
      if (!normalState || bones.isEmpty()) {
         return (renderPassInfo, snapshots) -> {};
      }
      return (renderPassInfo, snapshots) -> {
         bones.forEach((bone, show) -> {
            float scale = show ? 1.0F : 0.0F;
            snapshots.ifPresent(bone, snapshot -> {
               snapshot.setScale(scale, scale, scale);
               if (show) {
                  snapshot.skipRender(false);
                  snapshot.skipChildrenRender(false);
               }
            });
         });

         // 排查用：紧接着把「骨骼到底在不在、现在缩成多少」也打一行 ——
         // 名单对了但骨骼名对不上（或还有别的东西在压它）时，这一行会立刻暴露。
         if (pendingProbe != null) {
            pendingProbe = null;
            Map<String, String> actual = new LinkedHashMap<>();
            bones.keySet().forEach(bone -> snapshots.get(bone).ifPresentOrElse(
                    snapshot -> actual.put(bone, String.format("%.1f/%s", snapshot.getScaleX(),
                            snapshot.isHidden() ? "hidden" : "shown")),
                    () -> actual.put(bone, "骨骼不存在")));
            LOGGER.info("武器显隐 probe 骨骼实况：{}", actual);
         }
      };
   }
}
