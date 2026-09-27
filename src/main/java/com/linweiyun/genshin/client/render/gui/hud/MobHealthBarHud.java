package com.linweiyun.genshin.client.render.gui.hud;

import com.linweiyun.genshin.client.performance.HealthBarTrailTracker;
import com.linweiyun.genshin.client.performance.HudRenderCaches;
import com.linweiyun.genshin.content.entities.teyvat.ITeyvatBoss;
import com.linweiyun.genshin.content.entities.teyvat.NonTeyvatEntity;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatFriendly;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatHostile;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatLiving;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.about.FrozenDecayState;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.shield.ShieldService;
import com.linweiyun.genshin.core.system.poise.PoiseService;
import com.linweiyun.genshin.core.system.poise.PoiseState;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT)
public class MobHealthBarHud {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    private static final Identifier HP_BAR_BG_TEXTURE =
            Identifier.fromNamespaceAndPath("minegenshin", "gui/short_character_hp_green.png");
    private static final Identifier HP_BAR_FILL_TEXTURE =
            Identifier.fromNamespaceAndPath("minegenshin", "gui/short_character_hp_bar_white.png");

    /**
     * 血条用的 {@link RenderType}，一类贴图只取一次。
     *
     * <p>{@code RenderTypes.entityTranslucent(texture)} 内部是
     * {@code Util.memoize(BiFunction)}，<b>每次调用都要新建一个缓存键对象</b>；
     * 而一条血条要取 4~6 次 RenderType（背景 / 拖尾 / 填充 / 盾条背景 / 盾条填充），
     * 也就是每实体每帧白造同样数量的对象。这里提前取好，之后只是读一个静态字段。</p>
     *
     * <p>懒初始化而不是静态初始化：本类是客户端专属，但静态初始化跑在类加载时，
     * 放到「第一次真的要画血条」那一刻更稳妥。</p>
     */
    private static RenderType barBgType;
    private static RenderType barFillType;

    /** 每实体复用的「主元素 → 是否低量」临时表，避免每实体每帧新建 LinkedHashMap */
    private static final Map<GenshinElement, Boolean> MAIN_ELEMENT_SCRATCH = new LinkedHashMap<>();

    private static RenderType barBgType() {
        RenderType type = barBgType;
        if (type == null) {
            type = RenderTypes.entityTranslucent(HP_BAR_BG_TEXTURE);
            barBgType = type;
        }
        return type;
    }

    private static RenderType barFillType() {
        RenderType type = barFillType;
        if (type == null) {
            type = RenderTypes.entityTranslucent(HP_BAR_FILL_TEXTURE);
            barFillType = type;
        }
        return type;
    }

    private static final float BAR_WIDTH = 1.0f;
    private static final float BAR_HEIGHT = 0.08f;
    private static final float Y_OFFSET = 0.5f;
    private static final double MAX_DISTANCE = 24.0;

    private static final float FILL_Z_OFFSET = -0.001f;
    private static final float TRAIL_Z_OFFSET = -0.0005f;

    /**
     * 拖尾采样的距离上限（格）。
     *
     * <p>血条最远画到 {@link #MAX_DISTANCE}（24 格），这里多留 8 格余量给「刚被打所以远处也显示」
     * 的那种情况。超出这个距离的实体不喂拖尾：它连血条都画不出来，喂了也只是白算，
     * 等它进到范围内会按「采样断档」重新起步，不会拖出旧账。</p>
     */
    private static final double TRAIL_SAMPLE_DISTANCE = 32.0;

    // 罩型元素盾：压在血条正上方一条更细的条
    private static final float SHIELD_BAR_HEIGHT = 0.0336f;
    private static final float SHIELD_BAR_Y = 0.068f;
    // 槽用 0.0f、填充用这个，错开共面避免 z-fighting（血条那边同理）
    private static final float SHIELD_FILL_Z_OFFSET = -0.001f;

    // 削韧条：压在血条<b>正下方</b>一条更细的条（护盾在上面、韧性在下面，两条不打架）
    private static final float POISE_BAR_HEIGHT = 0.0336f;
    private static final float POISE_BAR_Y = -0.068f;
    private static final float POISE_FILL_Z_OFFSET = -0.001f;

    private static final float[] COLOR_POISE = { 0.95f, 0.82f, 0.35f };
    private static final float[] COLOR_POISE_BROKEN = { 1.00f, 1.00f, 1.00f };
    private static final float[] COLOR_POISE_SLOT = { 0.22f, 0.26f, 0.30f };

    private static final float LEVEL_TEXT_Y = 0.3f;
    private static final float LEVEL_TEXT_SCALE = 0.025f;
    private static final float LEVEL_TEXT_Y_BIG = 0.225f;
    private static final float LEVEL_TEXT_SCALE_BIG = 0.05f;

    // 图标尺寸
    private static final float ICON_SIZE = 0.30f;
    private static final float ICON_SPACING = 0.04f;
    private static final float ICON_PADDING = 0.02f;

    // 字体行高（mc.font.lineHeight），用于计算文字顶部
    private static final float FONT_LINE_HEIGHT = 9.0f;

    // 图标闪烁：剩余衰减时间低于此阈值（秒）开始闪烁
    private static final float BLINK_THRESHOLD_SECONDS = 2.0f;
    // 闪烁周期（tick）
    private static final long BLINK_PERIOD = 10L;

    private static final float RECENT_HIT_THRESHOLD = 0.8f;

    private static final float[] COLOR_HOSTILE = { 1.00f, 0.20f, 0.20f };
    private static final float[] COLOR_FRIENDLY = { 0.30f, 0.90f, 0.30f };
    private static final float[] COLOR_TRAIL = { 0.70f, 0.50f, 0.10f };


    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!TeyvatWorldInvasion.isClientInvaded()) return;

        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        PoseStack poseStack = event.getPoseStack();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // 相机在本帧的实体循环里是常量：位置、朝向各取一次就够
        // （朝向早先是写在 pushPose 里逐实体取的）
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Quaternionf camRot = camera.rotation();
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        long gameTime = mc.level.getGameTime();
        boolean blinkVisible = (gameTime % BLINK_PERIOD) < (BLINK_PERIOD / 2);

        // 拖尾状态机开帧：推进帧号、算这一帧真实流逝了多少秒（与刷新率无关）
        HealthBarTrailTracker.beginFrame(mc.level, gameTime, partialTick);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof Player) continue;
            if (living instanceof ITeyvatBoss) continue;
            if (!living.isAlive()) continue;
            if (!(living instanceof TeyvatLiving teyvat)) continue;

            // 相机相对坐标直接用基本量算：getPosition(partialTick) 每次都会建一个 Vec3，
            // 下面那次 subtract(camPos) 再建一个，一实体一帧两个纯属白造
            double relX = Mth.lerp(partialTick, living.xo, living.getX()) - camX;
            double relY = Mth.lerp(partialTick, living.yo, living.getY()) - camY;
            double relZ = Mth.lerp(partialTick, living.zo, living.getZ()) - camZ;
            double distance = Math.sqrt(relX * relX + relY * relY + relZ * relZ);

            // 元素附着独立于战斗状态
            StatusContainer container = living.getData(AttachmentRegistration.CONTAINER);
            boolean hasElements = container != null && hasActiveElements(container, living);

            boolean inCombat = teyvat.isInCombat();
            boolean barEligible = inCombat || hasElements;

            // 远到不会画、又不在战斗/没有元素附着的实体，连血量都不必读
            boolean trailSampled = distance <= TRAIL_SAMPLE_DISTANCE;
            if (!trailSampled && !barEligible) continue;

            float maxHealth = living.getMaxHealth();
            if (maxHealth <= 0) continue;
            float healthRatio = Math.max(0f, Math.min(1f, living.getHealth() / maxHealth));

            // —— 拖尾采样：与「这一帧画不画血条」解耦 ——
            // 脱战的怪这一帧通常不画血条，但正因为它在被砍之前一直在这里采样，
            // 第一刀落下时表里存的才是「这一刀之前」的真实血量，第一刀才有拖尾。
            if (trailSampled) {
                HealthBarTrailTracker.observe(living.getId(), healthRatio);
            }

            // 只是来采样的（脱战、无元素）：这帧不画任何东西
            if (!barEligible) continue;

            float[] fillColor = resolveColor(living);

            boolean recentlyHit = teyvat.getCombatTicks() > teyvat.getCombatDuration() * RECENT_HIT_THRESHOLD;
            boolean showBar = inCombat && (distance <= MAX_DISTANCE || recentlyHit);

            if (distance > MAX_DISTANCE && !recentlyHit && !inCombat && !hasElements) continue;

            float r, g, b;
            if (fillColor == null) {
                r = 1.0f; g = 1.0f; b = 1.0f;
            } else {
                r = fillColor[0]; g = fillColor[1]; b = fillColor[2];
            }

            double baseY = relY + living.getBbHeight() + Y_OFFSET;

            poseStack.pushPose();
            poseStack.translate((float) relX, (float) baseY, (float) relZ);
            poseStack.mulPose(camRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));

            int level = teyvat.getMonsterLevel();

            if (showBar) {
                // 采样在上面就做过了（距离内），这里拿回同一帧的结果；
                // 只有「远到不采样、但因为刚被打所以照样显示」的实体才在这里补采一次，
                // 那种情况下采样已经断档，状态机会按「重新起步」处理，不会拖出旧账。
                float trailRatio = HealthBarTrailTracker.observe(living.getId(), healthRatio);

                // ============ 空槽背景 ============
                RenderType bgType = barBgType();
                collector.submitCustomGeometry(poseStack, bgType, (pose, buffer) -> {
                    Matrix4f matrix = pose.pose();
                    drawTexturedQuad(buffer, matrix, 0.0f,
                            -BAR_WIDTH / 2, -BAR_HEIGHT / 2, BAR_WIDTH / 2, BAR_HEIGHT / 2,
                            0.0f, 0.0f, 1.0f, 1.0f,
                            1.0f, 1.0f, 1.0f, 1.0f);
                });

                // ============ 拖尾 ============
                if (trailRatio > healthRatio) {
                    float trailWidth = BAR_WIDTH * trailRatio;
                    RenderType trailType = barFillType();
                    collector.submitCustomGeometry(poseStack, trailType, (pose, buffer) -> {
                        Matrix4f matrix = pose.pose();
                        drawTexturedQuad(buffer, matrix, TRAIL_Z_OFFSET,
                                BAR_WIDTH / 2 - trailWidth, -BAR_HEIGHT / 2,
                                BAR_WIDTH / 2, BAR_HEIGHT / 2,
                                trailRatio, 0.0f, 0.0f, 1.0f,
                                COLOR_TRAIL[0], COLOR_TRAIL[1], COLOR_TRAIL[2], 1.0f);
                    });
                }

                // ============ 血条填充 ============
                float fillWidth = BAR_WIDTH * healthRatio;
                RenderType barType = barFillType();
                collector.submitCustomGeometry(poseStack, barType, (pose, buffer) -> {
                    Matrix4f matrix = pose.pose();
                    drawTexturedQuad(buffer, matrix, FILL_Z_OFFSET,
                            BAR_WIDTH / 2 - fillWidth, -BAR_HEIGHT / 2,
                            BAR_WIDTH / 2, BAR_HEIGHT / 2,
                            healthRatio, 0.0f, 0.0f, 1.0f,
                            r, g, b, 1.0f);
                });

                // ============ 护盾条 ============
                // 血条在画（showBar）且有罩型护盾时，在血条正上方叠一条更细的盾条；颜色取护盾元素
                if (ShieldService.hasAuraShield(living)) {
                    float shieldRatio = ShieldService.get(living).ratio();
                    GenshinElement shieldElement = ShieldService.shieldElement(living);
                    float sr = 1.0f;
                    float sg = 1.0f;
                    float sb = 1.0f;
                    if (shieldElement != null) {
                        int color = DamageIndicatorFactory.getColorForElement(shieldElement);
                        sr = (color >> 16 & 0xFF) / 255.0f;
                        sg = (color >> 8 & 0xFF) / 255.0f;
                        sb = (color & 0xFF) / 255.0f;
                    }

                    poseStack.pushPose();
                    poseStack.translate(0.0f, SHIELD_BAR_Y, 0.0f);
                    RenderType shieldType = barFillType();

                    // 空槽
                    collector.submitCustomGeometry(poseStack, shieldType, (pose, buffer) -> {
                        Matrix4f matrix = pose.pose();
                        drawTexturedQuad(buffer, matrix, 0.0f,
                                -BAR_WIDTH / 2, -SHIELD_BAR_HEIGHT / 2, BAR_WIDTH / 2, SHIELD_BAR_HEIGHT / 2,
                                0.0f, 0.0f, 1.0f, 1.0f,
                                0.22f, 0.26f, 0.3f, 1.0f);
                    });

                    // 填充：按剩余护盾比例从右往左收
                    // sr/sg/sb 在 if 里赋值过，不是 effectively final，lambda 捕获不了，先拷一份
                    float shieldWidth = BAR_WIDTH * shieldRatio;
                    float fillR = sr;
                    float fillG = sg;
                    float fillB = sb;
                    collector.submitCustomGeometry(poseStack, shieldType, (pose, buffer) -> {
                        Matrix4f matrix = pose.pose();
                        drawTexturedQuad(buffer, matrix, SHIELD_FILL_Z_OFFSET,
                                BAR_WIDTH / 2 - shieldWidth, -SHIELD_BAR_HEIGHT / 2,
                                BAR_WIDTH / 2, SHIELD_BAR_HEIGHT / 2,
                                shieldRatio, 0.0f, 0.0f, 1.0f,
                                fillR, fillG, fillB, 1.0f);
                    });
                    poseStack.popPose();
                }

                // ============ 削韧条 ============
                // 血条正下方一条更细的条：没破韧是金色、按已攒削韧从右往左长；
                // 破韧期间条是空的，改为闪白 —— 一眼能看出「现在这个破绽窗口是开着的」。
                PoiseState poise = PoiseService.peek(living);
                if (poise != null && poise.isEngaged()) {
                    boolean poiseBroken = poise.isBroken();
                    float poiseRatio = poiseBroken ? 1.0f : poise.ratio();

                    poseStack.pushPose();
                    poseStack.translate(0.0f, POISE_BAR_Y, 0.0f);
                    RenderType poiseType = barFillType();

                    // 空槽
                    collector.submitCustomGeometry(poseStack, poiseType, (pose, buffer) -> {
                        Matrix4f matrix = pose.pose();
                        drawTexturedQuad(buffer, matrix, 0.0f,
                                -BAR_WIDTH / 2, -POISE_BAR_HEIGHT / 2, BAR_WIDTH / 2, POISE_BAR_HEIGHT / 2,
                                0.0f, 0.0f, 1.0f, 1.0f,
                                COLOR_POISE_SLOT[0], COLOR_POISE_SLOT[1], COLOR_POISE_SLOT[2], 1.0f);
                    });

                    // 填充：破韧时按闪烁相位整条亮/灭，普通时按比例
                    if (poiseRatio > 0.0001f && (!poiseBroken || blinkVisible)) {
                        float[] poiseColor = poiseBroken ? COLOR_POISE_BROKEN : COLOR_POISE;
                        float poiseWidth = BAR_WIDTH * poiseRatio;
                        float fillR = poiseColor[0];
                        float fillG = poiseColor[1];
                        float fillB = poiseColor[2];
                        collector.submitCustomGeometry(poseStack, poiseType, (pose, buffer) -> {
                            Matrix4f matrix = pose.pose();
                            drawTexturedQuad(buffer, matrix, POISE_FILL_Z_OFFSET,
                                    BAR_WIDTH / 2 - poiseWidth, -POISE_BAR_HEIGHT / 2,
                                    BAR_WIDTH / 2, POISE_BAR_HEIGHT / 2,
                                    poiseRatio, 0.0f, 0.0f, 1.0f,
                                    fillR, fillG, fillB, 1.0f);
                        });
                    }
                    poseStack.popPose();
                }

                // ============ 等级文字 ============
                if (level > 0) {
                    submitLevelText(poseStack, collector, mc, level, LEVEL_TEXT_Y, LEVEL_TEXT_SCALE);
                }
            } else if (inCombat) {
                if (level > 0) {
                    submitLevelText(poseStack, collector, mc, level, LEVEL_TEXT_Y_BIG, LEVEL_TEXT_SCALE_BIG);
                }
            }

            // ============ 元素图标 ============
            if (hasElements) {
                float iconY = computeIconY(showBar, level > 0, inCombat);
                renderElementalIcons(poseStack, collector, container, iconY, blinkVisible);
            }

            poseStack.popPose();
        }
    }

    private static boolean hasActiveElements(StatusContainer container, LivingEntity living) {
        if (container == null) {
            return false;
        }

        int total = container.getAll().size();
        int active = 0;
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance)) continue;
            active++;
        }

        return active > 0;
    }

    /**
     * 图标中心 Y 坐标（相对血条中心，Y 轴向上）：
     * - 有血条 + 有等级：在等级文字上方
     * - 有血条 + 无等级：在血条上方
     * - 无血条 + 有大等级（远距）：在大等级文字上方
     * - 无血条 + 无等级（仅元素）：占血条位置（Y=0）
     */
    private static float computeIconY(boolean showBar, boolean hasLevel, boolean inCombat) {
        if (showBar && hasLevel) {
            float levelTop = LEVEL_TEXT_Y + FONT_LINE_HEIGHT * LEVEL_TEXT_SCALE;
            return levelTop + ICON_PADDING + ICON_SIZE / 2.0f;
        }
        if (showBar) {
            return BAR_HEIGHT / 2.0f + ICON_PADDING + ICON_SIZE / 2.0f;
        }
        if (inCombat && hasLevel) {
            float levelTop = LEVEL_TEXT_Y_BIG + FONT_LINE_HEIGHT * LEVEL_TEXT_SCALE_BIG;
            return levelTop + ICON_PADDING + ICON_SIZE / 2.0f;
        }
        return 0.0f;
    }

    /**
     * 渲染元素图标：
     * - 类元素映射到主元素（FROZEN → CYRO），按主元素去重
     * - 剩余衰减时间 ≤ 2s 的元素闪烁
     */
    private static void renderElementalIcons(PoseStack poseStack, SubmitNodeCollector collector,
                                             StatusContainer container, float yOffset,
                                             boolean blinkVisible) {
        if (container == null) return;

        FrozenDecayState frozenState = container.getFrozenDecayState();

        // 收集主元素 -> 是否 low（表复用：这段每实体每帧都要跑，重建表就是每实体每帧一次白分配）
        MAIN_ELEMENT_SCRATCH.clear();
        Map<GenshinElement, Boolean> mainElementMap = MAIN_ELEMENT_SCRATCH;
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            GenshinElement e = ea.getElement();
            if (e == null || e == ModElements.FYSIKOS.get()) continue;
            // 效果载体（寒）不占图标：它伴随冰/冻存在，显示的应该是冰/冻本身
            if (e.isEffectCarrier()) continue;

            GenshinElement main = e.getMainElement();

            float rate;
            if (e == ModElements.FROZEN.get() && frozenState != null) {
                rate = frozenState.getCurrentDecayRate();
            } else {
                rate = ea.getCurrentDecayPerSecond();
            }

            boolean isLow = false;
            if (rate > 0.0001f) {
                float remainSeconds = ea.getUnit() / rate;
                isLow = remainSeconds <= BLINK_THRESHOLD_SECONDS;
            }

            mainElementMap.merge(main, isLow, (a, b) -> a || b);
        }
        if (mainElementMap.isEmpty()) return;

        int count = mainElementMap.size();
        float totalWidth = count * ICON_SIZE + (count - 1) * ICON_SPACING;
        float startX = -totalWidth / 2.0f;

        int i = 0;
        for (Map.Entry<GenshinElement, Boolean> entry : mainElementMap.entrySet()) {
            GenshinElement element = entry.getKey();
            boolean isLow = entry.getValue();

            // 闪烁：处于低量状态且当前相位不可见时跳过渲染
            if (isLow && !blinkVisible) {
                i++;
                continue;
            }

            float x1 = startX + i * (ICON_SIZE + ICON_SPACING);
            float x2 = x1 + ICON_SIZE;
            float yTop = yOffset + ICON_SIZE / 2.0f;
            float yBottom = yOffset - ICON_SIZE / 2.0f;

            // 图标 RenderType 按元素查表：早先这里是「拼字符串 → new Identifier → 查 RenderType」，
            // 每个图标、每个实体、每一帧都来一遍
            RenderType type = HudRenderCaches.elementIcon(element);
            if (type == null) {
                i++;
                continue;
            }
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                Matrix4f matrix = pose.pose();
                drawTexturedQuad(buffer, matrix, 0.0f,
                        x1, yBottom, x2, yTop,
                        1.0f, 0.0f, 0.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f);
            });
            i++;
        }
    }

    private static float[] resolveColor(LivingEntity living) {
        if (living instanceof TeyvatFriendly) return COLOR_FRIENDLY;
        if (living instanceof TeyvatHostile) return COLOR_HOSTILE;
        if (living instanceof NonTeyvatEntity) {
            MobCategory cat = living.getType().getCategory();
            if (cat == MobCategory.MISC) return null;
            return cat.isFriendly() ? COLOR_FRIENDLY : COLOR_HOSTILE;
        }
        return null;
    }

    private static void submitLevelText(PoseStack poseStack, SubmitNodeCollector collector,
                                        Minecraft mc, int level, float yOffset, float scale) {
        // 文案与宽度都按等级缓存：「Lv.N」的拼接与 Font#width 的排版早先是每实体每帧重跑
        String text = HudRenderCaches.levelLabel(level);
        int width = HudRenderCaches.levelLabelWidth(mc.font, level);

        poseStack.pushPose();
        poseStack.translate(0.0f, yOffset, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(scale, -scale, scale);

        collector.submitText(
                poseStack,
                -width / 2.0f,
                0.0f,
                Component.literal(text).getVisualOrderText(),
                true,
                Font.DisplayMode.SEE_THROUGH,
                0xF000F0,
                0xFFFFFFFF,
                0,
                0
        );

        poseStack.popPose();
    }

    private static void drawTexturedQuad(VertexConsumer consumer, Matrix4f matrix, float z,
                                         float x1, float y1, float x2, float y2,
                                         float u1, float v1, float u2, float v2,
                                         float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z).setColor(r, g, b, a).setUv(u1, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y1, z).setColor(r, g, b, a).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, y2, z).setColor(r, g, b, a).setUv(u2, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x1, y2, z).setColor(r, g, b, a).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
    }
}
