package com.linweiyun.genshin;

import com.linweiyun.genshin.config.GenshinConfig;
import com.linweiyun.genshin.content.attribute.AttributeCapHandler;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.items.ModItems;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.character.ModCharacters;
import com.linweiyun.genshin.core.element.ElementLibBridge;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.combat.action.ServerTickScheduler;
import com.linweiyun.genshin.core.system.performance.DamageNumberThrottle;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import com.linweiyun.genshin.event.listener.server.GenshinEvents;
import com.linweiyun.genshin.core.system.registry.register.*;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(Minegenshin.MOD_ID)
public class Minegenshin {
    public static final String MOD_ID = "minegenshin";

    /**
     * 本模组所有日志的总开关。
     *
     * <p>{@code false} = 从 {@link ModLog} 拿的每一个 LOGGER 全部静默（连 error 也不出声），
     * 与各分组开关是「与」的关系：总开关关掉时分组开关怎么设都没用。</p>
     *
     * <p>运行期可改（{@code volatile}）：{@code Minegenshin.LOG_ENABLED = false;} 立刻生效，
     * 不需要重启。只静音某一块请用分组开关，见 {@link LogGroup} / {@link ModLog}。</p>
     */
    public static volatile boolean LOG_ENABLED = true;

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
    public Minegenshin(IEventBus modEventBus, ModContainer modContainer) {
        ElementLibBridge.install();
        GenshinEvents.init();
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.CHARACTER_SPEC, "minegenshin/character.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.WORLD_TEXT_COLOR_SPEC, "minegenshin/world-text-color.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.ENTITY_SPEC, "minegenshin/entity.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.WEAPON_SPEC, "minegenshin/weapon.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.ARTIFACT_SPEC, "minegenshin/artifact.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.REACTION_SPEC, "minegenshin/reaction.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.PERFORMANCE_SPEC, "minegenshin/performance.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.COMPATIBILITY_SPEC, "minegenshin/compatibility.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.POISE_SPEC, "minegenshin/poise.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GenshinConfig.GAMEPLAY_SPEC, "minegenshin/gameplay.toml");


        ModElements.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModItemGroups.register(modEventBus);
        ModEntities.register(modEventBus);
        ModDamageTypes.register(modEventBus);
        ModMobEffects.register(modEventBus);
        ModStatusDataComponents.register(modEventBus);
        ModElementalReactions.register(modEventBus);
        ModReactionTypes.register(modEventBus);
        ArtifactSets.register(modEventBus);
        ModDataComponents.register(modEventBus);

        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModCharacters.CHARACTERS.register(modEventBus);
        AttachmentRegistration.register(modEventBus);
        ModCharacterEffects.register(modEventBus);
        ModMenus.register(modEventBus);

        PlayerUIMenuType.register(
                ResourceLocation.fromNamespaceAndPath("minegenshin", "backpack"),
                player -> {
                    Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT.get());
                    return backpack::createUI;
                }
        );



    }
    private void commonSetup(FMLCommonSetupEvent event) {
        ModElements.setupSubElements();
        NetworkManager.init();
        event.enqueueWork(AttributeCapHandler::applyCapRelief);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("DecayCounter Worker started (elementlib)");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ServerTickScheduler.tick();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("DecayCounter Worker stopped (elementlib)");
        DamageNumberThrottle.clear();
        ReactionPriorityCalculator.clearSnapshots();
    }
}
