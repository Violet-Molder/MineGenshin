package com.linweiyun.genshin.core.system.wish;

import com.google.gson.JsonParser;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.character.ModCharacters;
import com.linweiyun.genshin.content.items.ModItems;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class WishSystem {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
    private static final Random RANDOM = new Random();
    private static final int WISH_COST = 160;
    private static WishConfig cachedConfig;

    public static void performWish(ServerPlayer serverPlayer) {
        int primogem = serverPlayer.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
        if (primogem < WISH_COST) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.minegenshin.wish.not_enough_primogem"));
            return;
        }

        serverPlayer.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, primogem - WISH_COST);
        NetworkManager.setPrimogemToPlayer(serverPlayer, primogem - WISH_COST);

        WishConfig config = getConfig(serverPlayer);
        if (config == null || config.pools().isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.minegenshin.wish.no_reward"));
            return;
        }

        PlayerCharactersAttachment charactersAttachment =
                serverPlayer.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

        boolean gotSomething = false;
        for (WishPool pool : config.pools()) {
            for (int i = 0; i < pool.rolls(); i++) {
                WishEntry entry = rollEntry(pool.entries());
                if (entry == null) continue;

                if ("character".equals(entry.type())) {
                    gotSomething |= handleCharacterDrop(serverPlayer, charactersAttachment, entry);
                } else if ("item".equals(entry.type())) {
                    gotSomething |= handleItemDrop(serverPlayer, entry);
                }
            }
        }

        if (!gotSomething) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.minegenshin.wish.no_reward"));
        }

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, serverPlayer.registryAccess());
        charactersAttachment.serialize(output);
        NetworkManager.setPlayerCharactersToPlayer(serverPlayer, output.buildResult());
    }

    /**
     * 抽到角色。
     *
     * <ul>
     *   <li>还没有这个角色 → 直接授予；</li>
     *   <li>已有、命座 &lt; 6 → <b>命座 +1</b>；</li>
     *   <li>已有、且已经满命 → <b>满命补偿</b>：随机给<b>一整套</b>圣遗物
     *       （魔女套 / 血红之证套，各 5 件，词条已经 roll 好）。</li>
     * </ul>
     */
    private static boolean handleCharacterDrop(ServerPlayer serverPlayer,
                                               PlayerCharactersAttachment charactersAttachment,
                                               WishEntry entry) {
        Identifier charId = Identifier.parse(entry.id());
        PGCharacter template = ModCharacters.getById(charId);
        if (template == null) {
            LOGGER.warn("Unknown character in wish: {}", entry.id());
            return false;
        }

        // 必须用玩家实际持有的那一份实例：模板角色是每次新建的副本，改它不会生效。
        PGCharacter owned = charactersAttachment.getCharacterByUUID(template.getCharacterUUID());

        if (owned == null) {
            charactersAttachment.addCharacter(template, serverPlayer);
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.minegenshin.wish.draw_character",
                            template.getName().copy().withStyle(ChatFormatting.GOLD)));
            return true;
        }

        if (owned.addConstellation()) {
            LOGGER.info("[祈愿] {} 抽到重复角色 {} → 命座 {}",
                    serverPlayer.getName().getString(), entry.id(), owned.getConstellation());
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.minegenshin.wish.constellation_up",
                            owned.getName().copy().withStyle(ChatFormatting.GOLD),
                            Component.literal(String.valueOf(owned.getConstellation()))
                                    .withStyle(ChatFormatting.AQUA)));
            return true;
        }

        giveMaxConstellationCompensation(serverPlayer);
        return true;
    }

    /**
     * 满命之后的额外补偿：随机挑一套圣遗物，<b>整套 5 件</b>一起发。
     *
     * <p>要发新造出来的圣遗物就必须先 {@link ArtifactItem#initializeArtifactStackIfNeeded}——
     * 否则拿到的是「未激活空壳」，主副词条全是空的（现有祈愿物品那一支也有这个坑）。
     */
    private static void giveMaxConstellationCompensation(ServerPlayer serverPlayer) {
        int roll = RANDOM.nextInt(3);
        List<Supplier<? extends Item>> pieces = switch (roll) {
            case 0 -> List.of(ModItems.CRIMSON_FLOWER, ModItems.CRIMSON_PLUME, ModItems.CRIMSON_SANDS,
                    ModItems.CRIMSON_GOBLET, ModItems.CRIMSON_CIRCLET);
            case 1 -> List.of(ModItems.SCARLET_FLOWER, ModItems.SCARLET_PLUME, ModItems.SCARLET_SANDS,
                    ModItems.SCARLET_GOBLET, ModItems.SCARLET_CIRCLET);
            default -> List.of(ModItems.TENACITY_FLOWER, ModItems.TENACITY_PLUME, ModItems.TENACITY_SANDS,
                    ModItems.TENACITY_GOBLET, ModItems.TENACITY_CIRCLET);
        };

        // 套装名只有一份来源：语言键。日志和聊天栏都直接用它，避免再维护第二份中文名
        String setKey = switch (roll) {
            case 0 -> "artifact_set.crimson_witch";
            case 1 -> "artifact_set.scarlet_proof";
            default -> "artifact_set.tenacity_of_the_millelith";
        };

        for (Supplier<? extends Item> piece : pieces) {
            ItemStack stack = new ItemStack(piece.get());
            ArtifactItem.initializeArtifactStackIfNeeded(stack);
            NetworkManager.giveItemToPlayer(serverPlayer, stack);
        }

        LOGGER.info("[祈愿] {} 已满命 → 补偿一整套 {}", serverPlayer.getName().getString(), setKey);
        serverPlayer.sendSystemMessage(
                Component.translatable("message.minegenshin.wish.constellation_max_compensation",
                        Component.translatable(setKey).withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    private static boolean handleItemDrop(ServerPlayer serverPlayer, WishEntry entry) {
        Identifier itemId = Identifier.parse(entry.id());
        Item item = BuiltInRegistries.ITEM.getValue(
                ResourceKey.create(Registries.ITEM, itemId));
        if (item == null) {
            LOGGER.warn("Unknown item in wish: {}", entry.id());
            return false;
        }

        ItemStack stack = new ItemStack(item, entry.count());
        NetworkManager.giveItemToPlayer(serverPlayer, stack);
        serverPlayer.sendSystemMessage(
                Component.translatable("message.minegenshin.wish.draw_item",
                        stack.getDisplayName().copy().withStyle(ChatFormatting.GOLD),
                        stack.getCount()));
        return true;
    }

    private static WishEntry rollEntry(List<WishEntry> entries) {
        int totalWeight = 0;
        for (WishEntry entry : entries) {
            totalWeight += entry.weight();
        }
        if (totalWeight <= 0) return null;

        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;
        for (WishEntry entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private static WishConfig getConfig(ServerPlayer serverPlayer) {
        if (cachedConfig != null) return cachedConfig;

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return null;

        ResourceManager rm = server.getResourceManager();
        Identifier resourceId = Identifier.fromNamespaceAndPath("minegenshin", "wish/standard.json");

        try {
            var resource = rm.getResourceOrThrow(resourceId);
            try (var reader = resource.openAsReader()) {
                var json = JsonParser.parseReader(reader);
                var result = WishConfig.CODEC.parse(JsonOps.INSTANCE, json);
                cachedConfig = result.getOrThrow();
                LOGGER.info("Loaded wish config: {} pools", cachedConfig.pools().size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load wish config from {}", resourceId, e);
        }

        return cachedConfig;
    }

    public static void clearCache() {
        cachedConfig = null;
    }
}