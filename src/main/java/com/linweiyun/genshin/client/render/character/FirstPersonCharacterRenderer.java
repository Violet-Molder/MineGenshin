// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPuppetBones;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.FirstPersonAnims;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.slf4j.Logger;

public final class FirstPersonCharacterRenderer {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final String HEAD_BONE = "head";
   private static final BoneUpdater<GeoRenderState> HIDE_HEAD = (renderPassInfo, snapshots) -> snapshots.ifPresent("head", head -> {
      head.skipRender(true);
      head.skipChildrenRender(true);
   });
   private static final Set<String> LOGGED = new HashSet<>();

   private FirstPersonCharacterRenderer() {
   }

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player != null && minecraft.level != null) {
         if (minecraft.options.getCameraType().isFirstPerson()) {
            String charId = CharacterHelper.getActiveCharacterId(player);
            if (charId != null && !charId.isEmpty() && AttachmentHelper.isGenshinMode(player)) {
               CharacterAnimations animations = CharacterActions.animationsFor(player);
               FirstPersonAnims firstPerson = animations == null ? FirstPersonAnims.DISABLED : animations.firstPerson();
               if (firstPerson != null && firstPerson.enabled()) {
                  if (!vanillaHandMatters(player)) {
                     event.setCanceled(true);
                     if (event.getHand() == InteractionHand.MAIN_HAND) {
                        CharacterRenderData data = CharacterRenderRepository.get(charId);
                        if (data != null) {
                           try {
                              render(player, charId, data, firstPerson, event);
                           } catch (Exception e) {
                              LOGGER.error("[FirstPersonCharacterRenderer] 第一人称渲染角色 '{}' 失败", charId, e);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void render(LocalPlayer player, String charId, CharacterRenderData data, FirstPersonAnims firstPerson, RenderHandEvent event) {
      CharacterRenderDispatcher.RenderTarget target = CharacterRenderDispatcher.targetFor(player, charId, data);
      if (target != null) {
         FirstPersonAnims.FirstPersonCamera camera = firstPerson.camera() == null ? FirstPersonAnims.FirstPersonCamera.DEFAULT : firstPerson.camera();
         float scale = camera.scale() <= 0.0F ? 1.0F : camera.scale() * data.bodyScale();
         float eyeHeight = player.getEyeHeight();
         PoseStack poseStack = event.getPoseStack();
         poseStack.pushPose();
         poseStack.translate(-camera.offsetX(), -(eyeHeight + camera.offsetY()), camera.offsetZ());
         if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
         }

         if (camera.pitch() != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.pitch()));
         }

         if (camera.yaw() != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(camera.yaw()));
         }

         if (camera.roll() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(camera.roll()));
         }

         CameraRenderState cameraState = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         target.renderer()
            .performRenderPass(
               target.animatable(),
               player,
               poseStack,
               event.getSubmitNodeCollector(),
               cameraState,
               event.getPackedLight(),
               event.getPartialTick(),
               CharacterRenderDispatcher.combine(
                  HIDE_HEAD,
                  CharacterRenderDispatcher.combine(
                     CharacterPropBones.hideAllUpdater(),
                     CharacterRenderDispatcher.combine(
                        CharacterFaceBones.updaterFor(player),
                        CharacterRenderDispatcher.combine(
                           CharacterPuppetBones.updaterFor(player),
                           CharacterAppearanceOptionBones.updaterFor(player, CharacterHelper.getCurrentCharacter(player))
                        )
                     )
                  )
               )
            );
         poseStack.popPose();
         if (LOGGED.add(charId)) {
            LOGGER.info(
               "[FirstPersonCharacterRenderer] 角色 '{}' 第一人称渲染中：眼高={}, 机位=({}, {}, {}) 旋转=({}, {}, {}) 缩放={}",
               new Object[]{charId, eyeHeight, camera.offsetX(), camera.offsetY(), camera.offsetZ(), camera.pitch(), camera.yaw(), camera.roll(), scale}
            );
         }
      }
   }

   private static boolean vanillaHandMatters(LocalPlayer player) {
      if (!player.isUsingItem() && !player.isScoping()) {
         ItemStack mainHand = player.getMainHandItem();
         return mainHand.isEmpty()
            ? false
            : mainHand.getItem() instanceof MapItem
               || mainHand.is(Items.BOW)
               || mainHand.is(Items.CROSSBOW)
               || mainHand.is(Items.SPYGLASS)
               || mainHand.is(Items.TRIDENT)
               || mainHand.is(Items.SHIELD);
      } else {
         return true;
      }
   }
}
