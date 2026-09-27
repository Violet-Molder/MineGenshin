// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.client.combat.state.BodyYawSync;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPuppetBones;
import com.linweiyun.genshin.config.PerformanceConfig;
import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "minegenshin", value = Dist.CLIENT)
public final class CharacterRenderDispatcher {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final Logger ANIM_LOGGER = ModLog.getLogger(LogGroup.ANIMATION);
   private static long lastSubmitFrameKey = Long.MIN_VALUE;
   private static String lastProbedProps = null;
   private static final String[] POSE_PROBE_BONES = new String[]{
      "Root", "root", "Waist", "UpBody", "UpperBody", "Body", "Robot_Root", "RightSword", "LeftSword"
   };
   private static int poseProbeFrame = -1;
   private static float poseProbeLast = Float.NaN;
   private static final boolean DEBUG_NO_SUPPORT_LAYERS = Boolean.getBoolean("minegenshin.debug.noSupportLayers");
   private static int renderFrameCounter;
   private static final Map<String, CharacterPlayerModel> MODELS = new HashMap<>();
   private static final Map<String, CharacterRenderer> RENDERERS = new HashMap<>();
   private static final Map<Player, GenshinReplacedPlayer> ANIMATABLES = new WeakHashMap<>();
   private static int timelineProbeFrame = -1;
   private static double timelineProbeLastAnimTime = Double.NaN;
   private static Object timelineProbeLastTimeline = null;
   private static final Map<String, Integer> RENDER_PASS_LOGGED = new HashMap<>();

   private static void probePose(Player player, BoneSnapshots snapshots, int frame) {
      if (frame != poseProbeFrame) {
         poseProbeFrame = frame;
         float sum = 0.0F;
         StringBuilder detail = new StringBuilder();

         for (String bone : POSE_PROBE_BONES) {
            BoneSnapshot snapshot = (BoneSnapshot)snapshots.get(bone).orElse(null);
            if (snapshot != null) {
               sum += snapshot.getRotX() * 0.9F
                  + snapshot.getRotY() * 1.7F
                  + snapshot.getRotZ() * 1.3F
                  + snapshot.getTranslateY() * 5.0F
                  + snapshot.getScaleX() * 11.0F
                  + snapshot.getScaleY() * 7.0F;
               if (detail.length() > 0) {
                  detail.append(' ');
               }

               detail.append(bone)
                  .append("=(")
                  .append(Math.round(snapshot.getRotX()))
                  .append(',')
                  .append(Math.round(snapshot.getRotY()))
                  .append(',')
                  .append(Math.round(snapshot.getRotZ()))
                  .append(")s")
                  .append(Math.round(snapshot.getScaleX() * 100.0F) / 100.0F)
                  .append(" tY=")
                  .append(Math.round(snapshot.getTranslateY() * 100.0F) / 100.0F)
                  .append(" tZ=")
                  .append(Math.round(snapshot.getTranslateZ() * 100.0F) / 100.0F);
            }
         }

         float delta = Float.isNaN(poseProbeLast) ? 0.0F : Math.abs(sum - poseProbeLast);
         poseProbeLast = sum;
         if (!(delta < 2.0F)) {
            ANIM_LOGGER.info(
               "姿态 frame={} Δ={} 状态={} 刻={} {}",
               new Object[]{frame, Math.round(delta * 10.0F) / 10.0F, AnimationStateSync.stateOf(player), player.tickCount, detail}
            );
         }
      }
   }

   private static boolean supportLayersDisabled() {
      return DEBUG_NO_SUPPORT_LAYERS || PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS != null && (Boolean)PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS.get();
   }

   @SubscribeEvent
   public static void onRenderFramePre(Pre event) {
      renderFrameCounter++;
   }

   public static int renderFrame() {
      return renderFrameCounter;
   }

   private static void probeProps(Player player, BoneSnapshots snapshots) {
      StringBuilder shown = new StringBuilder();

      for (String bone : new String[]{"FJO", "MFly", "ysmGlow_texiao", "tea", "RightSword", "LeftSword"}) {
         snapshots.get(bone)
            .ifPresent(
               snapshot -> {
                  boolean collapsed = Math.abs(snapshot.getScaleX()) < 1.0E-4F
                     && Math.abs(snapshot.getScaleY()) < 1.0E-4F
                     && Math.abs(snapshot.getScaleZ()) < 1.0E-4F;
                  if (!snapshot.isHidden() && !snapshot.areChildrenHidden() && !collapsed) {
                     shown.append('[').append(bone).append(']');
                  }
               }
            );
      }

      String key = shown.toString();
      if (!key.equals(lastProbedProps)) {
         lastProbedProps = key;
         ANIM_LOGGER.info("本帧会画出的道具 {}（状态={}，刻={}）", new Object[]{key.isEmpty() ? "（无）" : key, AnimationStateSync.stateOf(player), player.tickCount});
      }
   }

   private static void probeTimeline(Player player, GenshinReplacedPlayer animatable, BoneSnapshots snapshots, float partialTick) {
      int frame = renderFrameCounter;
      if (frame != timelineProbeFrame) {
         timelineProbeFrame = frame;
         AnimatableManager<GeoAnimatable> manager = animatable.getAnimatableInstanceCache().getManagerForId(player.getId());
         AnimationController<?> controller = manager == null ? null : (AnimationController)manager.getAnimationControllers().get("movement_controller");
         if (controller != null) {
            double timeline = controller.getCurrentTimelineTime();
            double animTime = controller.getCurrentAnimationTime();
            Object timelineObj = controller.getTimeline();
            AnimationPoint point = controller.getCurrentAnimationPoint();
            String name = point == null ? "无" : point.animation().name();
            double length = point == null ? 0.0 : point.animation().length();
            boolean rebuilt = timelineObj != null && timelineObj != timelineProbeLastTimeline;
            boolean jumped = !Double.isNaN(timelineProbeLastAnimTime) && Math.abs(animTime - timelineProbeLastAnimTime) > 0.12;
            boolean wrapped = length > 0.0 && !Double.isNaN(timelineProbeLastAnimTime) && timelineProbeLastAnimTime > length * 0.6 && animTime < length * 0.25;
            if (rebuilt || jumped || wrapped) {
               ANIM_LOGGER.info(
                  "时间轴 frame={} 动画={} 周期={} timeline={} animTime={} Δ={} 重建={} 跳={} 回卷={} partial={} 刻={} {}",
                  new Object[]{
                     frame,
                     name,
                     round2(length),
                     round2(timeline),
                     round2(animTime),
                     Double.isNaN(timelineProbeLastAnimTime) ? "-" : round2(animTime - timelineProbeLastAnimTime),
                     rebuilt,
                     jumped,
                     wrapped,
                     round2(partialTick),
                     player.tickCount,
                     poseBrief(snapshots)
                  }
               );
            }

            timelineProbeLastAnimTime = animTime;
            timelineProbeLastTimeline = timelineObj;
         }
      }
   }

   private static String poseBrief(BoneSnapshots snapshots) {
      StringBuilder out = new StringBuilder();

      for (String bone : new String[]{"Root", "Waist", "UpperBody", "Robot_Root", "LeftLeg", "RightLeg"}) {
         BoneSnapshot snapshot = (BoneSnapshot)snapshots.get(bone).orElse(null);
         if (snapshot != null) {
            out.append(' ')
               .append(bone)
               .append("(rx=")
               .append(round1(snapshot.getRotX()))
               .append(",tY=")
               .append(round2(snapshot.getTranslateY()))
               .append(",tZ=")
               .append(round2(snapshot.getTranslateZ()))
               .append(')');
         }
      }

      return out.toString();
   }

   private static float round1(float value) {
      return Math.round(value * 10.0F) / 10.0F;
   }

   private static double round2(double value) {
      return Math.round(value * 100.0) / 100.0;
   }

   private CharacterRenderDispatcher() {
   }

   public static boolean handleSubmit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraState) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }

      Player player = mc.level.getEntity(state.id) instanceof Player p ? p : null;
      if (player == null) {
         return false;
      }

      if (!AttachmentHelper.isGenshinMode(player)) {
         return false;
      }

      String charId = CharacterHelper.getActiveCharacterId(player);
      if (charId != null && !charId.isEmpty()) {
         CharacterRenderData data = CharacterRenderRepository.get(charId);
         if (data == null) {
            return false;
         }

         try {
            doRender(poseStack, submitNodeCollector, cameraState, player, charId, data, state.bodyRot);
         } catch (Exception e) {
            LOGGER.error("[CharacterRenderDispatcher] 渲染角色 '{}' 失败", charId, e);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void doRender(
      PoseStack poseStack,
      SubmitNodeCollector bufferSource,
      CameraRenderState cameraState,
      Player player,
      String charId,
      CharacterRenderData data,
      float stateBodyRot
   ) {
      CharacterRenderDispatcher.RenderTarget target = targetFor(player, charId, data);
      if (target != null) {
         float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
         Pose anchor = poseStack.last();
         poseStack.pushPose();

         try {
            renderCharacter(poseStack, bufferSource, cameraState, player, data, target, partialTick, stateBodyRot);
         } finally {
            while (!poseStack.isEmpty() && poseStack.last() != anchor) {
               poseStack.popPose();
            }
         }
      }
   }

   private static void renderCharacter(
      PoseStack poseStack,
      SubmitNodeCollector bufferSource,
      CameraRenderState cameraState,
      Player player,
      CharacterRenderData data,
      CharacterRenderDispatcher.RenderTarget target,
      float partialTick,
      float stateBodyRot
   ) {
      float bodyScale = data.bodyScale();
      if (bodyScale != 1.0F) {
         poseStack.scale(bodyScale, bodyScale, bodyScale);
      }

      float bodyYaw = player == Minecraft.getInstance().player ? stateBodyRot : BodyYawSync.renderYaw(player, partialTick);
      poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      PGCharacter character = CharacterHelper.getCurrentCharacter(player);
      BoneUpdater<GeoRenderState> appearanceUpdater = character == null ? null : CharacterAppearanceBones.forMask(character.getAppearance());
      BoneUpdater<GeoRenderState> propUpdater = CharacterPropBones.updaterFor(player);
      BoneUpdater<GeoRenderState> faceUpdater = CharacterFaceBones.updaterFor(player);
      BoneUpdater<GeoRenderState> puppetUpdater = CharacterPuppetBones.updaterFor(player);
      BoneUpdater<GeoRenderState> weaponUpdater = CharacterAppearanceOptionBones.updaterFor(player, character);
      BoneUpdater<GeoRenderState> boneUpdater = combine(combine(appearanceUpdater, propUpdater), combine(combine(faceUpdater, puppetUpdater), weaponUpdater));
      BoneUpdater<GeoRenderState> submitUpdater = boneUpdater;
      if (PerformanceConfig.DEBUG_DISABLE_BONE_UPDATERS != null && (Boolean)PerformanceConfig.DEBUG_DISABLE_BONE_UPDATERS.get()) {
         submitUpdater = null;
      }

      if (player == Minecraft.getInstance().player && ANIM_LOGGER.isInfoEnabled()) {
         int frameKey = renderFrameCounter;
         if (frameKey == lastSubmitFrameKey) {
            ANIM_LOGGER.info(
               "同一帧第二次提交角色模型（frame={}，状态={}，动画刻={}，partial={}）", new Object[]{frameKey, AnimationStateSync.stateOf(player), player.tickCount, partialTick}
            );
         }

         lastSubmitFrameKey = frameKey;
         submitUpdater = (info, snapshots) -> {
            boneUpdater.run(info, snapshots);
            probeProps(player, snapshots);
            probePose(player, snapshots, renderFrameCounter);
            probeTimeline(player, target.animatable(), snapshots, partialTick);
         };
      }

      target.renderer().performRenderPass(target.animatable(), player, poseStack, bufferSource, cameraState, 15728880, partialTick, submitUpdater);
   }

   @Nullable
   public static BoneUpdater<GeoRenderState> combine(@Nullable BoneUpdater<GeoRenderState> first, @Nullable BoneUpdater<GeoRenderState> second) {
      if (first == null) {
         return second;
      } else {
         return second == null ? first : (renderPassInfo, snapshots) -> {
            first.run(renderPassInfo, snapshots);
            second.run(renderPassInfo, snapshots);
         };
      }
   }

   @Nullable
   public static CharacterRenderDispatcher.RenderTarget targetFor(Player player, String charId, CharacterRenderData data) {
      if (player != null && charId != null && !charId.isEmpty() && data != null) {
         CharacterPlayerModel model = MODELS.computeIfAbsent(charId, k -> {
            CharacterPlayerModel m = new CharacterPlayerModel();
            m.updateRenderData(data);
            return m;
         });
         CharacterRenderer renderer = RENDERERS.computeIfAbsent(charId, k -> {
            CharacterRenderer created = new CharacterRenderer(model);
            if (!supportLayersDisabled()) {
               created.withRenderLayer(new BoneMountGeoLayer(created));
               created.withRenderLayer(new TranslucentBoneGeoLayer(created));
            }

            return created;
         });
         GenshinReplacedPlayer animatable = ANIMATABLES.computeIfAbsent(player, k -> new GenshinReplacedPlayer());
         animatable.setPlayerEntity(player);
         logRenderPassOncePerFrame(charId);
         return new CharacterRenderDispatcher.RenderTarget(model, renderer, animatable);
      } else {
         return null;
      }
   }

   private static void logRenderPassOncePerFrame(String charId) {
      if (ANIM_LOGGER.isInfoEnabled()) {
         int frame = renderFrameCounter;
         String path = callerPath();
         if (!"世界".equals(path)) {
            String key = frame + "|" + path;
            if (RENDER_PASS_LOGGED.put(key, 1) == null) {
               if (RENDER_PASS_LOGGED.size() > 64) {
                  RENDER_PASS_LOGGED.clear();
               }

               ANIM_LOGGER.info("渲染趟 {} 角色={} frame={}", new Object[]{path, charId, frame});
            }
         }
      }
   }

   private static String callerPath() {
      StringBuilder stack = new StringBuilder();

      for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
         stack.append(element.getClassName()).append('.');
      }

      String s = stack.toString();
      if (s.contains("CharacterConfigPage")) {
         return "K页预览";
      } else if (s.contains("CharacterEquipUI")) {
         return "U页预览";
      } else {
         return s.contains("FirstPersonCharacterRenderer") ? "第一人称" : "世界";
      }
   }

   public record RenderTarget(CharacterPlayerModel model, CharacterRenderer renderer, GenshinReplacedPlayer animatable) {
   }
}
