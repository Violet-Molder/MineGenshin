package com.linweiyun.genshin.command;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PrimogemCommand {

    /**
     * 只造「primogem」这一棵子树，交给 {@link CharacterCommand} 挂到同一个 {@code minegenshin} 根下面。
     *
     * <p>⚠️ 不能在这里自己 {@code dispatcher.register(Commands.literal("minegenshin")...)}：
     * Brigadier 的根子节点是 {@code putIfAbsent}，第二个同名的根会被<b>静默丢弃</b>
     * ——两个类各自注册一次的话，后注册的那棵树（也就是整棵 character）会直接消失。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildPrimogemNode() {
        return Commands.literal("primogem")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(PrimogemCommand::getPrimogem)
                                        )
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(PrimogemCommand::setPrimogem)
                                                )
                                        )
                                )
                                .then(Commands.literal("add")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(PrimogemCommand::addPrimogem)
                                                )
                                        )
                                );
    }

    private static int getPrimogem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int primogem = target.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);

        context.getSource().sendSuccess(
                () -> Component.translatable("command.minegenshin.primogem.get", target.getName().getString(), primogem),
                false);
        return primogem;
    }

    private static int setPrimogem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        target.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, amount);
        NetworkManager.setPrimogemToPlayer(target, amount);

        context.getSource().sendSuccess(
                () -> Component.translatable("command.minegenshin.primogem.set", target.getName().getString(), amount),
                true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addPrimogem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        int current = target.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
        int newAmount = current + amount;
        target.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, newAmount);
        NetworkManager.setPrimogemToPlayer(target, newAmount);

        int finalNewAmount = newAmount;
        context.getSource().sendSuccess(
                () -> Component.translatable("command.minegenshin.primogem.add", target.getName().getString(), amount, finalNewAmount),
                true);
        return Command.SINGLE_SUCCESS;
    }
}