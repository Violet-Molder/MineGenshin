// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneSnapshot;
import com.linweiyun.genshin.client.render.character.bones.BoneSnapshots;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.client.combat.state.BodyYawSync;
import com.linweiyun.genshin.client.render.character.appearance.*;
import com.linweiyun.genshin.config.PerformanceConfig;
import com.linweiyun.genshin.config.character.CharacterSystemConfig;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 角色模型的渲染调度：原版渲染点（玩家实体、第一人称手部）把玩家与当前位姿交进来，
 * 这里解析出该角色要用的模型与骨骼规则，再交给 {@link CharacterRenderer} 画。
 *
 * <p>每个角色缓存一份模型与渲染器；同一玩家复用同一个 {@link GenshinReplacedPlayer}
 * 作为动画对象，动画状态因此能在两处渲染点之间保持一致。
 */
@EventBusSubscriber(modid = "minegenshin", value = Dist.CLIENT)
public final class CharacterRenderDispatcher {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final Logger ANIM_LOGGER = ModLog.getLogger(LogGroup.ANIMATION);
   private static long lastSubmitFrameKey = Long.MIN_VALUE;
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

   private static void probePose(Player player, BoneSnapshots snapshots, int frame) {
      if (frame != poseProbeFrame) {
         poseProbeFrame = frame;
         float sum = 0.0F;

         for (String bone : POSE_PROBE_BONES) {
            BoneSnapshot snapshot = snapshots.get(bone).orElse(null);
            if (snapshot != null) {
               sum += snapshot.getRotX() * 0.9F
                  + snapshot.getRotY() * 1.7F
                  + snapshot.getRotZ() * 1.3F
                  + snapshot.getTranslateY() * 5.0F
                  + snapshot.getScaleX() * 11.0F
                  + snapshot.getScaleY() * 7.0F;
            }
         }

         poseProbeLast = sum;
      }
   }

   private static boolean supportLayersDisabled() {
      return DEBUG_NO_SUPPORT_LAYERS || PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS != null && (Boolean)PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS.get();
   }

   @SubscribeEvent
   public static void onRenderFramePre(RenderFrameEvent.Pre event) {
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
   }

   private CharacterRenderDispatcher() {
   }

   /**
    * 玩家实体渲染入口：接管后就取消原版玩家模型。
    *
    * <p>用 {@link RenderPlayerEvent.Pre} 而不是改渲染器：事件触发时 PoseStack 已经 push 并平移到
    * 实体所在处（{@code EntityRenderDispatcher} 在调用渲染器之前做这两步），
    * 与后续自己摆位姿的约定一致；取消事件就等于「这个玩家不画原版模型」。
    */
   @SubscribeEvent
   public static void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
      if (event.isCanceled()) {
         return;
      }

      Player player = event.getEntity();
      float partialTick = event.getPartialTick();
      float bodyRot = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
      if (handleRender(player, bodyRot, event.getPoseStack(), event.getMultiBufferSource(),
              event.getPackedLight(), partialTick)) {
         event.setCanceled(true);
      }
   }

   /**
    * 玩家实体的渲染入口。
    *
    * <p>调用方应先 {@code pushPose} 并把位姿摆到实体所在处；本方法只负责模型选择与绘制，
    * 自己 push / pop 一层并在返回前恢复。
    *
    * @param bodyRot 实体本帧的身体朝向（角度）
    * @return 是否已经接管这次渲染；返回 {@code true} 时原版玩家模型不应再画
    */
   public static boolean handleRender(Player player, float bodyRot, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int packedLight, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || player == null) {
         return false;
      }

      if (!AttachmentHelper.isGenshinMode(player)) {
         return false;
      }

      // 总门禁：角色 Geo 关闭时不走 Geo 渲染，回退到原版玩家模型。
      if (!CharacterSystemConfig.loadCharacterGeo()) {
         return false;
      }

      String charId = CharacterHelper.getActiveCharacterId(player);
      if (charId == null || charId.isEmpty()) {
         return false;
      }

      CharacterRenderData data = CharacterRenderRepository.get(charId);
      if (data == null) {
         return false;
      }

      try {
         doRender(poseStack, bufferSource, player, charId, data, bodyRot, packedLight, partialTick);
      } catch (Exception e) {
         LOGGER.error("[CharacterRenderDispatcher] 渲染角色 '{}' 失败", charId, e);
      }

      return true;
   }

   private static void doRender(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      Player player,
      String charId,
      CharacterRenderData data,
      float stateBodyRot,
      int packedLight,
      float partialTick
   ) {
      CharacterRenderDispatcher.RenderTarget target = targetFor(player, charId, data);
      if (target != null) {
         poseStack.pushPose();

         try {
            renderCharacter(poseStack, bufferSource, player, data, target, partialTick, stateBodyRot, packedLight);
         } finally {
            poseStack.popPose();
         }
      }
   }

   private static void renderCharacter(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      Player player,
      CharacterRenderData data,
      CharacterRenderDispatcher.RenderTarget target,
      float partialTick,
      float stateBodyRot,
      int packedLight
   ) {
      float bodyScale = data.bodyScale();
      if (bodyScale != 1.0F) {
         poseStack.scale(bodyScale, bodyScale, bodyScale);
      }

      float bodyYaw = player == Minecraft.getInstance().player ? stateBodyRot : BodyYawSync.renderYaw(player, partialTick);
      poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      PGCharacter character = CharacterHelper.getCurrentCharacter(player);
      BoneUpdater<BoneRenderState> appearanceUpdater = character == null ? null : CharacterAppearanceBones.forMask(character.getAppearance());
      BoneUpdater<BoneRenderState> propUpdater = CharacterPropBones.updaterFor(player);
      BoneUpdater<BoneRenderState> faceUpdater = CharacterFaceBones.updaterFor(player);
      BoneUpdater<BoneRenderState> puppetUpdater = CharacterPuppetBones.updaterFor(player);
      BoneUpdater<BoneRenderState> physicsUpdater = CharacterBonePhysics.forCharacter(character).clothUpdater(player, character);
      BoneUpdater<BoneRenderState> weaponUpdater = CharacterBoneVisibility.forCharacter(character).weaponUpdater(player, character);
      BoneUpdater<BoneRenderState> boneUpdater = combine(combine(combine(appearanceUpdater, propUpdater), combine(combine(faceUpdater, puppetUpdater), weaponUpdater)), physicsUpdater);
      BoneUpdater<BoneRenderState> submitUpdater = boneUpdater;
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
         BoneUpdater<BoneRenderState> base = boneUpdater;
         submitUpdater = (info, snapshots) -> {
            base.run(info, snapshots);
            probeProps(player, snapshots);
            probePose(player, snapshots, renderFrameCounter);
         };
      }

      target.renderer().performRenderPass(target.animatable(), player, poseStack, bufferSource, packedLight, partialTick, submitUpdater);
   }

   @Nullable
   public static BoneUpdater<BoneRenderState> combine(@Nullable BoneUpdater<BoneRenderState> first, @Nullable BoneUpdater<BoneRenderState> second) {
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
               created.addRenderLayer(new BoneMountGeoLayer(created));
               created.addRenderLayer(new TranslucentBoneGeoLayer(created));
               // 只读骨骼位姿、不画东西：把「手上武器」的世界位姿喂给 Photon 特效用（见 WeaponAnchorCache）
               created.addRenderLayer(new WeaponAnchorGeoLayer(created));
            }

            return created;
         });
         GenshinReplacedPlayer animatable = ANIMATABLES.computeIfAbsent(player, k -> new GenshinReplacedPlayer());
         animatable.setPlayerEntity(player);
         return new CharacterRenderDispatcher.RenderTarget(model, renderer, animatable);
      } else {
         return null;
      }
   }

   public record RenderTarget(CharacterPlayerModel model, CharacterRenderer renderer, GenshinReplacedPlayer animatable) {
   }
}