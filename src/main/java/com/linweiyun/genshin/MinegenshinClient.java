package com.linweiyun.genshin;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import com.linweiyun.genshin.client.render.character.FirstPersonCharacterRenderer;
import com.linweiyun.genshin.client.render.geo.GenshinGeoCache;
import com.linweiyun.genshin.client.render.geo.AssetGeoCache;
import com.linweiyun.genshin.client.render.geo.CategoryGeoModel;
import com.linweiyun.genshin.client.damage.DamageIndicatorClientBridge;
import com.linweiyun.genshin.client.performance.IndicatorPerfReloadListener;
import com.linweiyun.genshin.client.performance.SkinningPerfReloadListener;
import com.linweiyun.genshin.asset.AssetCategory;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.content.entities.teyvat.monster.slime.LargeCryoSlime;
import com.linweiyun.genshin.client.combat.action.CharacterAnimationRegistry;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectileRenderer;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaSpiritSwordRenderer;
import com.linweiyun.genshin.client.render.entity.ElementalOrbRenderer;
import com.linweiyun.genshin.client.render.entity.FieldTalismanSpiritRender;
import com.linweiyun.genshin.client.render.entity.IceBlockProjectileRenderer;
import com.linweiyun.genshin.client.render.entity.StellarVortexRenderer;
import com.linweiyun.genshin.client.render.entity.ThunderCloudRenderer;
import com.linweiyun.genshin.client.render.gui.screen.ScreenArtifactInfo;
import com.linweiyun.genshin.core.attachment.ClientAttachmentSync;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.core.system.registry.register.ModMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
    public static void addReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GenshinGeoCache());
        event.registerReloadListener(new AssetGeoCache());
        event.registerReloadListener(new IndicatorPerfReloadListener());
        event.registerReloadListener(new SkinningPerfReloadListener());
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHARACTER_INFO_MENU.get(), ScreenArtifactInfo::new);
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
                ModEntities.VESNA_ATTACK_PROJECTILE.get(), VesnaAttackProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.VESNA_SPIRIT_SWORD.get(), VesnaSpiritSwordRenderer::new);
        // 冰块投射物：原版方块模型，不需要 GeckoLib
        event.registerEntityRenderer(ModEntities.ICE_BLOCK.get(), IceBlockProjectileRenderer::new);
    }



}
