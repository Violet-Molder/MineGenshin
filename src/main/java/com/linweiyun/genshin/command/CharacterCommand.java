package com.linweiyun.genshin.command;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.system.registry.register.ModCharacters;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class CharacterCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("minegenshin")
                        .then(Commands.literal("character")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("uuid", IntegerArgumentType.integer())
                                                        .executes(CharacterCommand::addCharacter)
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("uuid", IntegerArgumentType.integer())
                                                        .executes(CharacterCommand::removeCharacter)
                                                )
                                        )
                                )
                                // 天赋手动升级次数调试入口：
                                //   /minegenshin character talent <玩家> <uuid> <normal|skill|burst> <0-9>
                                // 用来验证「消耗按手动次数算」和「3/5 命 +3 级」——
                                // 手动次数和有效等级是分开的，所以这里设的是手动次数。
                                .then(Commands.literal("talent")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("uuid", IntegerArgumentType.integer())
                                                        .then(Commands.argument("kind",
                                                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                                                .suggests((ctx, b) -> {
                                                                    b.suggest("normal");
                                                                    b.suggest("skill");
                                                                    b.suggest("burst");
                                                                    return b.buildFuture();
                                                                })
                                                                .then(Commands.argument("manual", IntegerArgumentType.integer(
                                                                                0, PGCharacterData.MAX_MANUAL_TALENT_UPGRADES))
                                                                        .executes(CharacterCommand::setTalentManual)
                                                                )
                                                        )
                                                )
                                        )
                                )
                                // 命座调试入口：/minegenshin character constellation <玩家> <uuid> <0-6>
                                // 抽卡一次只 +1 命，想试 C2/C6 效果用这个直接设，不用真抽 7 次
                                .then(Commands.literal("constellation")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("uuid", IntegerArgumentType.integer())
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(
                                                                        0, PGCharacterData.MAX_CONSTELLATION))
                                                                .executes(CharacterCommand::setConstellation)
                                                        )
                                                )
                                        )
                                )
                        )
                        // 「primogem」那棵树在这里挂进同一个 minegenshin 根。
                        // 同一个根只能注册一次（Brigadier 根子节点是 putIfAbsent，重复的会被静默丢弃），
                        // 所以 PrimogemCommand 只提供 buildPrimogemNode()、不再自己 register。
                        .then(PrimogemCommand.buildPrimogemNode())
        );
    }

    private static int addCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int uuid = IntegerArgumentType.getInteger(context, "uuid");

        PGCharacter character = ModCharacters.getByUUID(uuid);
        if (character == null) {
            context.getSource().sendFailure(Component.translatable("command.minegenshin.character.not_found", uuid));
            return 0;
        }

        PlayerCharactersAttachment attachment = target.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment.hasCharacter(uuid)) {
            context.getSource().sendFailure(Component.translatable("command.minegenshin.character.already_owned"));
            return 0;
        }
        attachment.addCharacterToPlayer(target ,character);
        attachment.syncToPlayer(target);

        context.getSource().sendSuccess(
                () -> Component.translatable("command.minegenshin.character.add_success", target.getName().getString(), character.getName().getString()),
                true);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeCharacter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int uuid = IntegerArgumentType.getInteger(context, "uuid");

        PlayerCharactersAttachment attachment = target.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (!attachment.hasCharacter(uuid)) {
            context.getSource().sendFailure(Component.translatable("command.minegenshin.character.not_owned"));
            return 0;
        }

        PGCharacter definition = ModCharacters.getByUUID(uuid);
        String characterName = definition != null ? definition.getName().getString() : String.valueOf(uuid);

        attachment.removeCharacterToPlayer(target, uuid);
        attachment.syncToPlayer(target);

        context.getSource().sendSuccess(
                () -> Component.translatable("command.minegenshin.character.remove_success", target.getName().getString(), characterName),
                true);
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * 设置某个天赋的<b>手动升级次数</b>（不等于有效等级：有效等级 = 1 + 手动 + 命座加成）。
     *
     * <p>用来验证「消耗按手动次数算」：手动 3 次 + 3 命 → 显示 7 级，但下一次升级
     * 仍然消耗「4→5」那一档。
     */
    private static int setTalentManual(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int uuid = IntegerArgumentType.getInteger(context, "uuid");
        String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind");
        int manual = IntegerArgumentType.getInteger(context, "manual");

        PlayerCharactersAttachment attachment =
                target.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter owned = attachment.getCharacterByUUID(uuid);
        if (owned == null) {
            context.getSource().sendFailure(Component.translatable("command.minegenshin.character.not_owned"));
            return 0;
        }
        if (!owned.getData().setManualTalentUpgrades(kind, manual)) {
            context.getSource().sendFailure(Component.literal("kind 只能是 normal / skill / burst"));
            return 0;
        }
        attachment.syncSingleCharacterToPlayer(target, owned);

        final String k = kind.toLowerCase(java.util.Locale.ROOT);
        final int mLv = switch (k) {
            case "normal" -> owned.getData().getNormalAttackLevel();
            case "skill" -> owned.getData().getElementalSkillLevel();
            default -> owned.getData().getElementalBurstLevel();
        };
        final int mCap = switch (k) {
            case "normal" -> owned.getData().getNormalAttackLevelCap();
            case "skill" -> owned.getData().getElementalSkillLevelCap();
            default -> owned.getData().getElementalBurstLevelCap();
        };
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s 的天赋 %s：手动 %d 次 → 有效等级 %d/%d（命座 %d）",
                owned.getName().getString(), k, manual, mLv, mCap, owned.getConstellation())), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 直接设置某个角色（玩家实际持有的那一份）的命座。
     *
     * <p>一定要走 {@code attachment.getCharacterByUUID}，不能拿 {@code ModCharacters.getByUUID}
     * ——后者是模板实例，改了不会落到玩家身上。
     */
    private static int setConstellation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int uuid = IntegerArgumentType.getInteger(context, "uuid");
        int level = IntegerArgumentType.getInteger(context, "level");

        PlayerCharactersAttachment attachment =
                target.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter owned = attachment.getCharacterByUUID(uuid);
        if (owned == null) {
            context.getSource().sendFailure(Component.translatable("command.minegenshin.character.not_owned"));
            return 0;
        }

        owned.setConstellation(level);
        attachment.syncSingleCharacterToPlayer(target, owned);

        context.getSource().sendSuccess(
                () -> Component.translatable("message.minegenshin.constellation.set",
                        owned.getName().getString(), String.valueOf(owned.getConstellation())),
                true);
        return Command.SINGLE_SUCCESS;
    }
}