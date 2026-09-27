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
import com.linweiyun.genshin.core.asset.AssetCategory;
import com.linweiyun.genshin.core.asset.GenshinAssets;
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

        // 伤害飘字：客户端安装表现实现（公共侧只依赖 api 契约）
        DamageIndicatorClientBridge.install();

        // 资产路径规则：默认把本 MOD 的 character 资源解析到 character/<角色id>/ 下
        GenshinAssets.installDefaults();

        // 角色动作系统：加角色在 CharacterAnimationRegistry 里加一行
        CharacterAnimationRegistry.registerAll();

        // 第一人称接管原版手臂。
        // RenderHandEvent 是游戏总线事件（NeoForge.EVENT_BUS），不是 mod 总线事件，
        // 所以这里显式注册，而不是靠 @EventBusSubscriber 去猜总线。
        NeoForge.EVENT_BUS.addListener(FirstPersonCharacterRenderer::onRenderHand);

        // 统一布局模型解析缓存（AssetGeoCache）的预热。
        // 它同时也作为客户端资源重载监听器注册（见 addReloadListeners），这里只是保证
        // 「第一次查询之前就已经扫好」。客户端初始化阶段资源管理器可能还是空的，
        // 那一轮空扫描不会落锚，会在首次查询或下一次资源重载时补扫（见 AssetGeoCache#apply）。
        event.enqueueWork(AssetGeoCache::warmUp);
    }

    /**
     * 本 MOD 自己的 GeckoLib 资源缓存（两套）。
     *
     * <p>GeckoLib 只扫 {@code assets/<ns>/geckolib/{models,animations}}，而我们要把角色资源
     * 集中在 {@code assets/minegenshin/character/<角色id>/}，所以自己扫、自己烘培；
     * 取值走 {@code GenshinGeoModel} 覆盖的两个方法，<b>不动 GeckoLib 任何全局行为</b>。
     *
     * <p>{@code AssetGeoCache} 是统一布局（{@code item/}、{@code block/}、{@code entity/}）
     * 的索引，同样在这里注册；它的 {@code reload} 已经把异常兜住，不会把整个资源重载带崩。
     */
    @SubscribeEvent
    public static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Minegenshin.id("genshin_geo_cache"), new GenshinGeoCache());
        event.addListener(Minegenshin.id("asset_geo_cache"), new AssetGeoCache());
        // 飘字性能系统：字形缓存抓着排版当时的图集 UV，而 Minecraft#font 实例在资源重载后
        // 并不会换（FontManager 就地重建 FontSet），所以必须显式挂重载钩子清缓存，
        // 否则 F3+T / 换资源包后飘字会花屏。
        event.addListener(Minegenshin.id("indicator_perf_cache"), new IndicatorPerfReloadListener());
        // GPU 蒙皮的显存缓存：F3+T / 换资源包后模型实例会换掉，必须显式 close 旧顶点缓冲与常量缓冲环，
        // 否则显存会随着每次重载一路涨。
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
     *
     * <p>这个事件在 {@code FeatureRenderDispatcher} 构造时发出，所以这里只注册一个空壳；
     * 显存分配留到真正提交的那一帧（见 {@code SkinnedFeatureRenderer}）。</p>
     */
    @SubscribeEvent
    public static void registerFeatureRenderers(RegisterFeatureRenderersEvent event) {
        // 走渲染器自己的注册入口：注册动作与它内部「已注册」标记的置位必须成对，别在这里手抄一行。
        SkinnedFeatureRenderer.register(event);
    }

    /**
     * GPU 蒙皮：一帧结束，骨骼常量缓冲环翻到下一块。
     *
     * <p>和 {@code RenderSystem} 里的 dynamic uniforms 同一个时机 —— 这一帧提交的所有模型都已经
     * 写完各自的矩阵，指针才允许被下一次提交覆盖。</p>
     */
    @SubscribeEvent
    public static void onFlipFrame(FlipFrameEvent event) {
        SkinDataStorage.endFrame();

        // 帧时间采样：F3 那条「1% low」需要最近若干帧的完整历史，所以每帧都采一次，
        // 而不是等玩家按 F3 才开始攒。采样本身只是一次数组写入。
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
        // 飘字性能系统：把「每帧规划 / 顶点生产耗时、提交条数、排版缓存条数、合并命中率」
        // 挂进 F3 调试屏。这是 Neo 26.2 给调试屏的正式扩展点（条目注册表），
        // 不需要 mixin 改调试屏本身；挂在默认档案的 IN_OVERLAY，按 F3 就能看到，
        // 也可以在 F3 的调试选项菜单里单独关掉。
        Identifier id = Minegenshin.id("indicator_perf");
        event.register(id, new IndicatorDebugEntry());
        event.includeInProfile(id, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);

        // 角色几何优化系统：把「本帧走了优化路径的模型数 / 骨骼遍历与写顶点耗时 / 顶点数」
        // 挂成第二条。要看优化到底快了多少，就在同一场景下把 performance.toml 里
        // render-optimize.character-geometry 关掉再开，对比这一行的 walk 与 v。
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
                ModEntities.VESNA_ATTACK_PROJECTILE.get(), VesnaAttackProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.VESNA_SPIRIT_SWORD.get(), VesnaSpiritSwordRenderer::new);
        // 冰块投射物：原版方块模型，不需要 GeckoLib
        event.registerEntityRenderer(ModEntities.ICE_BLOCK.get(), IceBlockProjectileRenderer::new);
    }



}
