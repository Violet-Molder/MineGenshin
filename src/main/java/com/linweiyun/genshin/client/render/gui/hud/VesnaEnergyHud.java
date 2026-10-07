package com.linweiyun.genshin.client.render.gui.hud;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Vesna 风骑能量条。
 * <ul>
 *   <li>第一人称：HUD 层（普通 GUI 矩形）。</li>
 *   <li>第三人称：世界几何层（贴玩家模型左侧，随相机透视）。</li>
 * </ul>
 * 三个矩形竖直排列，从下往上按能量填充，每格 6 点（总 18 点）。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class VesnaEnergyHud {

    // ============================================================
    // 第三人称世界几何参数
    // ============================================================

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minegenshin", "gui/short_character_hp_bar_white.png");

    /** 每格尺寸（方块） */
    private static final float SEG_WIDTH  = 0.35f;
    private static final float SEG_HEIGHT = 0.35f;
    private static final float SEG_GAP    = 0.06f;
    private static final float TOTAL_HEIGHT = SEG_HEIGHT * 3 + SEG_GAP * 2;

    /** 相对玩家位置的偏移：RIGHT 负 = 屏幕左 */
    private static final double OFFSET_RIGHT = -0.7;
    private static final double OFFSET_UP    =  0.0;

    /** 第三人称能量条几何用的顶点缓冲块大小（字节） */
    private static final int BUFFER_SIZE = 4096;

    // ============================================================
    // 第一人称 HUD 参数
    // ============================================================

    private static final int FP_SEG_W = 20;
    private static final int FP_SEG_H = 14;
    private static final int FP_GAP   = 2;

    /** HUD 位置：屏幕宽 29% 处，底部往上 34 像素 */
    private static final float FP_LEFT_PERCENT = 0.29f;
    private static final int   FP_BOTTOM_PX    = 34;

    private static final int FP_BG_COLOR   = 0xC0000000;
    private static final int FP_FILL_COLOR = 0xFFFFCF5D;

    /** 每格能量值 */
    private static final float SEGMENT_SIZE = 6f;

    /** 本模组自己的顶点缓冲，只有渲染主线程会碰 */
    private static ByteBufferBuilder buffer;
    private static MultiBufferSource.BufferSource bufferSource;

    private static MultiBufferSource.BufferSource bufferSource() {
        if (bufferSource == null) {
            buffer = new ByteBufferBuilder(BUFFER_SIZE);
            bufferSource = MultiBufferSource.immediate(buffer);
        }
        return bufferSource;
    }

    // ============================================================
    // 第一人称：HUD 层
    // ============================================================

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Minegenshin.id("vesna_energy_hud_first_person"),
                (GuiGraphics graphics, DeltaTracker deltaTracker) ->
                        renderFirstPersonHud(graphics));
    }

    private static void renderFirstPersonHud(GuiGraphics g) {
        if (!isVisible()) return;
        if (!isFirstPerson()) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment.getCurrentCharacter();
        if (!(character instanceof Vesna vesna)) return;

        float energy = vesna.getVesnaEnergy();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int totalH = FP_SEG_H * 3 + FP_GAP * 2;
        int baseX = (int) (screenW * FP_LEFT_PERCENT);
        int topY  = screenH - FP_BOTTOM_PX - totalH;

        for (int i = 0; i < 3; i++) {
            int yTop = topY + (2 - i) * (FP_SEG_H + FP_GAP);
            int yBot = yTop + FP_SEG_H;

            g.fill(baseX, yTop, baseX + FP_SEG_W, yBot, FP_BG_COLOR);

            float segLow = i * SEGMENT_SIZE;
            float ratio = Math.max(0f, Math.min(1f, (energy - segLow) / SEGMENT_SIZE));
            if (ratio > 0.001f) {
                int fillH = (int) (FP_SEG_H * ratio);
                g.fill(baseX, yBot - fillH, baseX + FP_SEG_W, yBot, FP_FILL_COLOR);
            }
        }
    }

    // ============================================================
    // 第三人称：世界几何层
    // ============================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!isVisible()) return;
        if (isFirstPerson()) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment.getCurrentCharacter();
        if (!(character instanceof Vesna vesna)) return;

        float energy = vesna.getVesnaEnergy();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        Vec3 playerPos = player.getPosition(partialTick);
        float yaw = camera.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 camRight = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        Vec3 anchor = playerPos
                .add(camRight.scale(OFFSET_RIGHT))
                .add(0, player.getBbHeight() * 0.5 + OFFSET_UP, 0);

        Vec3 relative = anchor.subtract(camPos);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource collector = bufferSource();

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));

        RenderType type = RenderType.entityTranslucent(WHITE_TEXTURE);
        Matrix4f matrix = poseStack.last().pose();

        float halfW = SEG_WIDTH * 0.5f;
        float baseY = -TOTAL_HEIGHT * 0.5f;

        for (int i = 0; i < 3; i++) {
            float yLow = baseY + i * (SEG_HEIGHT + SEG_GAP);
            float yHigh = yLow + SEG_HEIGHT;

            drawRect(collector.getBuffer(type), matrix, 0,
                    -halfW, yLow, halfW, yHigh,
                    0.10f, 0.10f, 0.15f, 0.60f);

            float segLow = i * SEGMENT_SIZE;
            float fillRatio = Math.max(0f, Math.min(1f, (energy - segLow) / SEGMENT_SIZE));
            if (fillRatio > 0.001f) {
                float fillYHigh = yLow + SEG_HEIGHT * fillRatio;
                drawRect(collector.getBuffer(type), matrix, -0.001f,
                        -halfW, yLow, halfW, fillYHigh,
                        1.0f, 0.82f, 0.35f, 1.0f);
            }
        }

        poseStack.popPose();
        collector.endBatch();
    }

    private static void drawRect(VertexConsumer consumer, Matrix4f matrix, float z,
                                 float x1, float y1, float x2, float y2,
                                 float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y1, z).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y2, z).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x1, y2, z).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
    }

    // ============================================================
    // 公共判定
    // ============================================================

    private static boolean isVisible() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!TeyvatWorldInvasion.isClientInvaded()) return false;
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment.getCurrentCharacter();
        return character instanceof Vesna v && v.isWindriderActive();
    }

    private static boolean isFirstPerson() {
        return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }
}
