// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.optimize;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.character.CharacterRenderDispatcher;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.linweiyun.genshin.client.render.optimize.geo.GeoCompileCache;
import com.linweiyun.genshin.client.render.optimize.gpu.BoneMatrixPalette;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedFeatureRenderer;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedMesh;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedMeshCache;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelines;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedSubmit;
import com.linweiyun.genshin.client.render.optimize.walk.BoneWalker;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class GeoRenderIntercept {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final Logger ANIM_LOGGER = ModLog.getLogger(LogGroup.ANIMATION);
   private static int probeFrame = -1;
   private static final Map<String, Integer> PROBE_SUBMITS = new HashMap<>();
   private static final String[] PROBE_BONES = new String[]{"Root", "Waist", "UpBody", "UpperBody", "Robot_Root", "RightSword", "LeftSword"};
   private static boolean gpuSubmitWarned;

   private static void probeDuplicateSubmit(BakedGeoModel model) {
      if (ANIM_LOGGER.isInfoEnabled()) {
         int frame = CharacterRenderDispatcher.renderFrame();
         if (frame != probeFrame) {
            probeFrame = frame;
            PROBE_SUBMITS.clear();
         }

         String id = String.valueOf(model.properties().identifier());
         int count = PROBE_SUBMITS.merge(id, 1, Integer::sum);
         if (count == 2) {
            StringBuilder stack = new StringBuilder();

            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
               if (stack.length() > 0) {
                  stack.append(" ← ");
               }

               String clazz = element.getClassName();
               stack.append(clazz.substring(clazz.lastIndexOf(46) + 1)).append('.').append(element.getMethodName()).append(':').append(element.getLineNumber());
               if (stack.length() > 400) {
                  break;
               }
            }

            if (stack.toString().contains("CharacterRenderDispatcher")) {
               ANIM_LOGGER.info("同一帧第二次提交角色模型 {}（frame={}）调用栈：{}", new Object[]{id, frame, stack});
            }
         }
      }
   }

   private static String poseSignature(RenderPassInfo<?> renderPassInfo) {
      if (!ANIM_LOGGER.isInfoEnabled()) {
         return "";
      }

      StringBuilder sb = new StringBuilder();

      for (String bone : PROBE_BONES) {
         GeoBone target = (GeoBone)renderPassInfo.model().getBone(bone).orElse(null);
         BoneSnapshot snapshot = target == null ? null : target.frameSnapshot;
         if (snapshot != null) {
            sb.append(bone)
               .append('=')
               .append(Math.round(snapshot.getRotY() * 10.0F) / 10.0F)
               .append('/')
               .append(Math.round(snapshot.getTranslateY() * 100.0F) / 100.0F)
               .append('/')
               .append(Math.round(snapshot.getScaleX() * 100.0F) / 100.0F)
               .append(' ');
         }
      }

      return sb.toString();
   }

   private static void probeDrawTimePose(RenderPassInfo<?> renderPassInfo, String submitPose) {
      if (!submitPose.isEmpty()) {
         String drawPose = poseSignature(renderPassInfo);
         if (!drawPose.equals(submitPose)) {
            ANIM_LOGGER.info(
               "画的时候姿势变了！frame={}\n   提交时 {}\n   画的时候 {}",
               new Object[]{ANIM_LOGGER.isInfoEnabled() ? CharacterRenderDispatcher.renderFrame() : -1, submitPose, drawPose}
            );
         }
      }
   }

   private GeoRenderIntercept() {
   }

   public static boolean trySubmit(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks, @Nullable RenderType renderType) {
      if (renderType == null) {
         return true;
      }

      BakedGeoModel model = renderPassInfo.model();
      if (model.isMissingno()) {
         return false;
      }

      probeDuplicateSubmit(model);
      int flags = RenderOptimize.characterFlags();
      CompiledGeoModel compiled = flags == 0 ? null : GeoCompileCache.get(model);
      if (compiled == null && flags != 0) {
         RenderOptimizeStats.recordFallback();
      }

      int packedLight = renderPassInfo.packedLight();
      int packedOverlay = renderPassInfo.packedOverlay();
      int renderColor = renderPassInfo.renderColor();
      if (compiled != null
         && canSkinOnGpu(renderType)
         && submitSkinnedRender(renderPassInfo, renderTasks, renderType, compiled, packedLight, packedOverlay, renderColor)) {
         return true;
      }

      submitGeometry(renderPassInfo, renderTasks, renderType, compiled, packedLight, packedOverlay, renderColor, flags);
      return true;
   }

   public static void submitDefault(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks, @Nullable RenderType renderType) {
      if (renderType != null) {
         BakedGeoModel model = renderPassInfo.model();
         if (model.isMissingno()) {
            submitMissingModel(renderPassInfo, renderTasks);
         } else {
            submitGeometry(
               renderPassInfo, renderTasks, renderType, null, renderPassInfo.packedLight(), renderPassInfo.packedOverlay(), renderPassInfo.renderColor(), 0
            );
         }
      }
   }

   private static void submitGeometry(
      RenderPassInfo<?> renderPassInfo,
      OrderedSubmitNodeCollector renderTasks,
      RenderType renderType,
      @Nullable CompiledGeoModel compiled,
      int packedLight,
      int packedOverlay,
      int renderColor,
      int flags
   ) {
      String submitPose = poseSignature(renderPassInfo);
      renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
         PoseStack poseStack = renderPassInfo.poseStack();
         probeDrawTimePose(renderPassInfo, submitPose);
         poseStack.pushPose();
         poseStack.last().set(pose);
         renderPassInfo.renderPosed(() -> {
            if (compiled == null) {
               renderPassInfo.model().render(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor);
            } else {
               long began = System.nanoTime();
               int written = BoneWalker.render(compiled, poseStack, renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor, flags);
               RenderOptimizeStats.record(System.nanoTime() - began, written);
            }
         });
         poseStack.popPose();
      });
   }

   private static void submitMissingModel(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks) {
      renderTasks.submitCustomGeometry(
         renderPassInfo.poseStack(),
         RenderTypes.entityCutout(MissingTextureAtlasSprite.getLocation()),
         (pose, vertexConsumer) -> {
            PoseStack poseStack = renderPassInfo.poseStack();
            poseStack.pushPose();
            poseStack.last().set(pose);
            renderPassInfo.renderPosed(
               () -> renderPassInfo.model()
                  .render(renderPassInfo, vertexConsumer, renderPassInfo.packedLight(), renderPassInfo.packedOverlay(), renderPassInfo.renderColor())
            );
            poseStack.popPose();
         }
      );
   }

   private static boolean canSkinOnGpu(RenderType renderType) {
      return RenderOptimize.gpuSkinningEnabled() && !SkinnedFeatureRenderer.isDisabled() && SkinnedPipelines.skinnedFor(renderType.pipeline()) != null;
   }

   private static boolean submitSkinnedRender(
      RenderPassInfo<?> renderPassInfo,
      OrderedSubmitNodeCollector renderTasks,
      RenderType renderType,
      CompiledGeoModel compiled,
      int packedLight,
      int packedOverlay,
      int renderColor
   ) {
      RenderPipeline pipeline = SkinnedPipelines.skinnedFor(renderType.pipeline());
      if (pipeline == null) {
         return false;
      }

      try {
         SkinnedMesh mesh = SkinnedMeshCache.get(compiled);
         if (mesh == null) {
            return false;
         }

         PoseStack poseStack = renderPassInfo.poseStack();
         Pose pose = poseStack.last().copy();
         int[] runs = new int[Math.max(2, compiled.boneCount * 2)];
         int[] runCount = new int[1];
         GpuBufferSlice[] palette = new GpuBufferSlice[1];
         poseStack.pushPose();
         poseStack.last().set(pose);

         try {
            renderPassInfo.renderPosed(() -> {
               long began = System.nanoTime();
               GpuBufferSlice slice = BoneMatrixPalette.compute(compiled, poseStack, renderPassInfo, packedLight, packedOverlay, renderColor, runs, runCount);
               if (slice != null) {
                  RenderOptimizeStats.recordGpu(System.nanoTime() - began, visibleVertices(runs, runCount[0]));
               }

               palette[0] = slice;
            });
         } finally {
            poseStack.popPose();
         }

         if (palette[0] != null && runCount[0] != 0) {
            renderTasks.submitSpecial(phaseFor(renderType), new SkinnedSubmit(mesh, palette[0], runs, runCount[0], renderType, pipeline));
            return true;
         } else {
            return false;
         }
      } catch (Throwable t) {
         if (!gpuSubmitWarned) {
            gpuSubmitWarned = true;
            LOGGER.warn("[RenderOptimize] GPU 蒙皮提交失败，该模型本帧回退 CPU 路径（后续同样情况不再刷屏）", t);
         }

         RenderOptimizeStats.recordFallback();
         return false;
      }
   }

   private static RenderPhaseKey<SubmitNode> phaseFor(RenderType renderType) {
      if (renderType.isOutline()) {
         return RenderPhaseKeys.OUTLINE;
      } else {
         return renderType.hasBlending() ? RenderPhaseKeys.TRANSLUCENT_CUSTOM_GEOMETRY : RenderPhaseKeys.SOLID;
      }
   }

   private static int visibleVertices(int[] runs, int runCount) {
      int visible = 0;

      for (int i = 0; i < runCount; i++) {
         visible += runs[2 * i + 1];
      }

      return visible;
   }
}
