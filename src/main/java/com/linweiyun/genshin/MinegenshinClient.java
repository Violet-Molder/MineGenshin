package com.linweiyun.genshin;

import com.geckolib.renderer.GeoEntityRenderer;
import com.linweiyun.genshin.client.render.character.FirstPersonCharacterRenderer;
import com.linweiyun.genshin.client.render.geo.GenshinGeoCache;
import com.linweiyun.genshin.client.render.geo.AssetGeoCache;
import com.linweiyun.genshin.client.render.geo.CategoryGeoModel;
import com.linweiyun.genshin.client.damage.DamageIndicatorClientBridge;
import com.linweiyun.genshin.client.performance.IndicatorDebugEntry;
import com.linweiyun.genshin.client.performance.IndicatorPerfReloadListener;
import com.linweiyun.genshin.client.performance.SkinningPerfReloadListener;
import com.linweiyun.genshin.client.render.optimize.RenderOptimizeDebugEntry;
import com.linweiyun.genshin.client.render.optimize.FrameTimeStats;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinDataStorage;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedFeatureRenderer;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelines;
import com.linweiyun.genshin.asset.AssetCategory;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.content.entities.teyvat.monster.slime.LargeCryoSlime;
import com.linweiyun.genshin.client.combat.action.CharacterAnimationRegistry;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectileRenderer;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaSpiritSwordRenderer;
import com.linweiyun.genshin.content.entities.teyvat.skill.miyabi.MiyabiSlashEffectRenderer;
import com.linweiyun.genshin.client.render.entity.ElementalOrbRenderer;
import com.linweiyun.genshin.client.render.entity.FieldTalismanSpiritRender;
import com.linweiyun.genshin.client.render.entity.IceBlockProjectileRenderer;
import com.linweiyun.genshin.client.render.entity.StellarVortexRenderer;
import com.linweiyun.genshin.client.render.entity.StellarPrismRenderer;
import com.linweiyun.genshin.client.render.entity.ThunderCloudRenderer;
import com.linweiyun.genshin.client.render.gui.screen.ScreenArtifactInfo;
import com.linweiyun.genshin.core.attachment.ClientAttachmentSync;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.core.system.registry.register.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.FlipFrameEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;


@Mod(value = Minegenshin.MOD_ID, dist = Dist.CLIENT)

@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public class MinegenshinClient {
    public MinegenshinClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new ConfigurationScreen(container, parent));
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ClientAttachmentSync.init(() -> Minecraft.getInstance().player);
        DamageIndicatorClientBridge.install();
        GenshinAssets.installDefaults();
        CharacterAnimationRegistry.registerAll();
        NeoForge.EVENT_BUS.addListener(FirstPersonCharacterRenderer::onRenderHand);
        event.enqueueWork(AssetGeoCache::warmUp);
    }
    @SubscribeEvent
    public static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Minegenshin.id("genshin_geo_cache"), new GenshinGeoCache());
        event.addListener(Minegenshin.id("asset_geo_cache"), new AssetGeoCache());
        event.addListener(Minegenshin.id("indicator_perf_cache"), new IndicatorPerfReloadListener());
        event.addListener(Minegenshin.id("skinning_perf_cache"), new SkinningPerfReloadListener());
    }

    /**
     * GPU 蒙皮：注册自己的着色器管线（顶点格式多带一个骨骼索引属性，另外挂一组骨骼矩阵常量缓冲）。
     */
    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        SkinnedPipelines.registerPipelines(event);
    }

    /**
     * GPU 蒙皮：把提交节点真正画出来的渲染器。
     */
    @SubscribeEvent
    public static void registerFeatureRenderers(RegisterFeatureRenderersEvent event) {
        // 走渲染器自己的注册入口：注册动作与它内部「已注册」标记的置位必须成对，别在这里手抄一行。
        SkinnedFeatureRenderer.register(event);
    }

    /**
     * GPU 蒙皮：一帧结束，骨骼常量缓冲环翻到下一块。
     */
    @SubscribeEvent
    public static void onFlipFrame(FlipFrameEvent event) {
        SkinDataStorage.endFrame();
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            FrameTimeStats.sample(minecraft.getFrameTimeNs());
        }
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHARACTER_INFO_MENU.get(), ScreenArtifactInfo::new);
    }

    @SubscribeEvent
    public static void registerDebugEntries(RegisterDebugEntriesEvent event) {
        Identifier id = Minegenshin.id("indicator_perf");
        event.register(id, new IndicatorDebugEntry());
        event.includeInProfile(id, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        Identifier renderId = Minegenshin.id("render_optimize");
        event.register(renderId, new RenderOptimizeDebugEntry());
        event.includeInProfile(renderId, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.FIELD_TALISMAN_SPIRIT.get(), FieldTalismanSpiritRender::new);
        event.registerEntityRenderer(
                ModEntities.LARGE_CRYO_SLIME.get(), context -> new GeoEntityRenderer<>(context,
                        new CategoryGeoModel<LargeCryoSlime>(AssetCategory.ENTITY, "large_cryo_slime"))
        );
        event.registerEntityRenderer(
                ModEntities.ELEMENTAL_ORB.get(), ElementalOrbRenderer::new);
        event.registerEntityRenderer(
                ModEntities.THUNDER_CLOUD.get(), ThunderCloudRenderer::new);
        event.registerEntityRenderer(
                ModEntities.STELLAR_VORTEX.get(), StellarVortexRenderer::new);
        event.registerEntityRenderer(
                ModEntities.STELLAR_PRISM.get(), StellarPrismRenderer::new);
        event.registerEntityRenderer(
                ModEntities.VESNA_ATTACK_PROJECTILE.get(), VesnaAttackProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.VESNA_SPIRIT_SWORD.get(), VesnaSpiritSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.MIYABI_SLASH.get(), MiyabiSlashEffectRenderer::new);
        // 冰块投射物：原版方块模型，不需要 GeckoLib
        event.registerEntityRenderer(ModEntities.ICE_BLOCK.get(), IceBlockProjectileRenderer::new);
    }



}
