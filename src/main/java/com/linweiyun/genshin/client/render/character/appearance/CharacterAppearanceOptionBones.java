// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
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

   /**
    * 起飞前摇那类动作状态的名字前缀（{@code fly_start} / {@code fly_start_polearm} …）。
    *
    * <p>这类状态里外观 updater 本来不插手（只有 {@code default} 才管），结果就是
    * <b>手里的武器还亮着、身下又冒出一把</b> —— 所以前摇这一段单独给一套显隐。
    */
   private static final String FLIGHT_START_PREFIX = "fly_start";

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
         // 飞行中（常态那颗状态里也在飞）：把手上的武器换成「放飞骨骼」上那份 ——
         // 长柄飞行是骑着武器当扫帚，武器不能再被手臂的动画拖着走
         boolean flying = GenshinFlight.isFlying(player);
         boolean starting = state != null && state.startsWith(FLIGHT_START_PREFIX);
         Map<String, Boolean> bones = flying ? flightBones(character)
                 : starting ? flightStartBones(character)
                 : character.appearanceData().normalStateBones(mask, character.currentWeaponType());

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

         return updater(bones, normalState || flying || starting);
      } else {
         return null;
      }
   }

   public static BoneUpdater<GeoRenderState> updaterForPreview(int mask, PGCharacter character) {
      return updater(character.appearanceData().normalStateBones(mask, character.currentWeaponType()), true);
   }

   /**
    * 飞行时的武器显隐：<b>手骨藏起来、放飞骨骼亮起来</b>。
    *
    * <p>只做「当前形态有放飞骨骼」这一档（现在是长柄），其它形态照用户设置走 ——
    * 免得飞行顺手改了别的武器显隐规则。放飞骨骼是功能性的（人骑在上面），
    * 所以这一档不看「常态显示武器」那个开关，一律亮着。
    */
   private static Map<String, Boolean> flightBones(PGCharacter character) {
      CharacterAppearanceData data = character.appearanceData();
      int mask = character.getAppearance();
      Map<String, Boolean> bones = new LinkedHashMap<>(
              data.normalStateBones(mask, character.currentWeaponType()));
      // 「显示武器」关着时也能认出当前形态是哪一根手骨，所以这里用强制打开的那份来认
      Map<String, Boolean> identified =
              data.normalStateBones(data.withShowWeapon(mask, true), character.currentWeaponType());
      data.flightBones().forEach((handBone, flightBone) -> {
         bones.put(flightBone, false);
         if (Boolean.TRUE.equals(identified.get(handBone))) {
            bones.put(handBone, false);
            bones.put(flightBone, true);
         }
      });
      return bones;
   }

   /**
    * 起飞前摇：<b>只把手上的那根藏掉</b>，放飞骨骼交给动画自己管。
    *
    * <p>为什么这半套：用户的 {@code fly_start_polearm} 里用 {@code polearm_fly.scale} 排了
    * 「枪先脱手 → 0.125 秒时才亮出飞行的那一根」的时间线；这里要是把放飞骨骼也一并点亮，
    * 那条时间线就被盖掉了。所以前摇这一档只做「藏手骨」。
    */
   private static Map<String, Boolean> flightStartBones(PGCharacter character) {
      CharacterAppearanceData data = character.appearanceData();
      Map<String, Boolean> bones = new LinkedHashMap<>(
              data.normalStateBones(character.getAppearance(), character.currentWeaponType()));
      Map<String, Boolean> identified =
              data.normalStateBones(data.withShowWeapon(character.getAppearance(), true),
                      character.currentWeaponType());
      data.flightBones().forEach((handBone, flightBone) -> {
         if (Boolean.TRUE.equals(identified.get(handBone))) {
            bones.put(handBone, false);
         }
      });
      return bones;
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
