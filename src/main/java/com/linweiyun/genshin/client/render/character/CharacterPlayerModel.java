// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.render.geo.GenshinGeoModel;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import lombok.Generated;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import org.jetbrains.annotations.Nullable;

public class CharacterPlayerModel extends GenshinGeoModel<GenshinReplacedPlayer> {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

   /**
    * 下落攻击把时间轴推到目标格的快慢：每刻推多少刻。
    *
    * <p>{@code 1} = 不跳，让动画自己从 0 刻正常往前播到目标格然后停住（用来判断
    * 「喂进去的刻到底有没有作用在姿态上」）；给大值（64）就是下一帧直接跳过去。
    */
   private static final int PLUNGE_SKIP_TICKS_PER_TICK = 1;

   /** 玩家 UUID → 这一次下落攻击的时间轴锚点（第一次看到 plunge 状态的那一游戏刻）。 */
   private static final Map<UUID, Integer> PLUNGE_ANCHOR = new HashMap<>();

   /** 下落攻击冻结用的骨骼快照（每个玩家一份，退出下落状态时清掉）。 */
   private static final Map<UUID, List<BonePose>> PLUNGE_POSE = new HashMap<>();

   private CharacterRenderData renderData;

   public void updateRenderData(CharacterRenderData data) {
      if (data != null) {
         this.renderData = data;
         this.setCharacterId(data.id());
         this.setDeclaredPaths(data.modelIdentifier(), data.textureIdentifier(), data.animationIdentifier());
         this.setSharedPaths(GenshinAssets.defaultModel(), GenshinAssets.defaultTexture(), GenshinAssets.defaultAnimation());
         this.setAnimationFallbackPaths(data.extraAnimationPaths());
         LOGGER.info(
            "[CharacterPlayerModel] 角色 '{}' 路径: 自己={}/{}/{}，声明={}/{}/{}，共用={}/{}/{}，额外动画={}",
            new Object[]{
               data.id(),
               this.defaultModelResource(),
               this.defaultTextureResource(),
               this.defaultAnimationResource(),
               data.modelIdentifier(),
               data.textureIdentifier(),
               data.animationIdentifier(),
               GenshinAssets.defaultModel(),
               GenshinAssets.defaultTexture(),
               GenshinAssets.defaultAnimation(),
               data.extraAnimationPaths()
            }
         );
      }
   }

   /**
    * 给 GeckoLib 喂「本帧的动画时间基准」—— 必须是<b>整数刻</b>。
    *
    * <p>GeckoLib 4.9.3 的 {@code GeoModel.handleAnimations} 先读 {@code DataTickets.TICK}；
    * 读不到时，对「实现了 {@code GeoReplacedEntity} 的动画对象」回退成
    * {@code RenderUtil.getCurrentTick() + partialTick}。而那个 {@code getCurrentTick()} 取的是<b>墙钟</b>
    * （本来就是连续值），再叠加一次 0→1 的 {@code partialTick} 就等于每刻多推进近一倍、
    * 到刻边界又倒回约 1 刻 —— 姿态在相邻两帧之间来回抖，并且整体偏慢，肉眼就是「一卡一卡」。
    *
    * <p>实体那条路没这个问题：GeckoLib 的实体渲染器会喂 {@code entity.tickCount}（整数），
    * {@code partialTick} 由 GeckoLib 自己加。我们走的是 {@code GeoObjectRenderer}，没人喂，所以在这里补；
    * 补完时间轴就是 {@code tickCount + partialTick} 的连续斜坡。
    */
   @Override
   public void handleAnimations(GenshinReplacedPlayer animatable, long instanceId,
                                AnimationState<GenshinReplacedPlayer> animationState, float partialTick) {
      Player player = animatable.getPlayerEntity();
      Minecraft minecraft = Minecraft.getInstance();

      double tick = player != null
              ? player.tickCount
              : minecraft.level != null ? minecraft.level.getGameTime() : 0.0;

      animationState.setData(DataTickets.TICK, tick);

      super.handleAnimations(animatable, instanceId, animationState, partialTick);

      applyPlungePose(animatable, player);
   }

   /**
    * 下落攻击：播到目标格就把整副骨骼拍张快照，之后每帧按快照写回去（钉住下劈姿态）。
    *
    * <p>不走「喂 tick 推时间轴」那条路 —— 那条路在这条角色模型上不作用到骨骼，
    * 快照里的姿势本身还是 GeckoLib 自然播放到那一刻算出来的，我们只负责拍下来 + 之后不许它动。
    */
   private void applyPlungePose(GenshinReplacedPlayer animatable, @Nullable Player player) {
      if (player == null) {
         return;
      }

      double holdTick = plungingHoldTick(player);
      if (holdTick < 0) {
         PLUNGE_ANCHOR.remove(player.getUUID());
         PLUNGE_POSE.remove(player.getUUID());
         return;
      }

      List<BonePose> pose = PLUNGE_POSE.get(player.getUUID());
      if (pose == null) {
         if (plungeElapsed(player) < holdTick) {
            // 还没播到目标格：这一小段（0.17 秒）让动画自己正常播过去
            return;
         }
         BakedGeoModel model = this.getBakedModel(this.getModelResource(animatable));
         if (model == null) {
            return;
         }
         pose = capturePose(model);
         PLUNGE_POSE.put(player.getUUID(), pose);
      }

      applyPose(pose);
   }

   /** 这一次下落攻击已经过了多少刻。 */
   private static int plungeElapsed(Player player) {
      Integer anchor = PLUNGE_ANCHOR.get(player.getUUID());
      if (anchor == null) {
         anchor = player.tickCount;
         PLUNGE_ANCHOR.put(player.getUUID(), anchor);
      }
      return Math.max(0, player.tickCount - anchor);
   }

   private static List<BonePose> capturePose(BakedGeoModel model) {
      List<BonePose> out = new ArrayList<>();
      for (GeoBone root : model.topLevelBones()) {
         captureBone(root, out);
      }
      return out;
   }

   private static void captureBone(GeoBone bone, List<BonePose> out) {
      out.add(new BonePose(bone, bone.getRotX(), bone.getRotY(), bone.getRotZ(),
              bone.getPosX(), bone.getPosY(), bone.getPosZ(),
              bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()));
      for (GeoBone child : bone.getChildBones()) {
         captureBone(child, out);
      }
   }

   private static void applyPose(List<BonePose> pose) {
      for (BonePose bone : pose) {
         GeoBone target = bone.bone();
         target.setRotX(bone.rotX());
         target.setRotY(bone.rotY());
         target.setRotZ(bone.rotZ());
         target.setPosX(bone.posX());
         target.setPosY(bone.posY());
         target.setPosZ(bone.posZ());
         target.setScaleX(bone.scaleX());
         target.setScaleY(bone.scaleY());
         target.setScaleZ(bone.scaleZ());
      }
   }

   /** 一根骨头在某一刻的姿势快照（旋转 / 位置 / 缩放）。 */
   private record BonePose(GeoBone bone,
                           float rotX, float rotY, float rotZ,
                           float posX, float posY, float posZ,
                           float scaleX, float scaleY, float scaleZ) {
   }

   /**
    * 这个玩家此刻该把动画时间轴钉在哪一刻；不用钉就返回 -1。
    *
    * <p>判据是「当前状态名 = 该角色的下落攻击状态名」：本地玩家读自己的动作状态机，
    * 别的玩家读同步过来的状态名，同一套判断两边都成立。
    */
   private static double plungingHoldTick(Player player) {
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = attachment == null ? null : attachment.getCurrentCharacter();
      if (character == null) {
         return -1.0;
      }

      double hold = character.getPlungingAnimationHoldTick();
      return hold >= 0 && character.getPlungingAnimation().equals(AnimationStateSync.stateOf(player)) ? hold : -1.0;
   }

   @Generated
   public CharacterRenderData getRenderData() {
      return this.renderData;
   }
}
