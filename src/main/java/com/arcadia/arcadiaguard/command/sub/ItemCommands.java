package com.arcadia.arcadiaguard.command.sub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ItemCommands {

    private ItemCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("item")
            .then(literal("block")
                .then(argument("item", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                    .executes(ItemCommands::block)))
            .then(literal("unblock")
                .then(argument("item", ResourceLocationArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ArcadiaGuard.dynamicItemBlockList().list(), builder))
                    .executes(ItemCommands::unblock)))
            .then(literal("list").executes(ItemCommands::list));
    }

    private static int block(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            ctx.getSource().sendFailure(Component.translatable("arcadiaguard.command.item.unknown", id.toString()));
            return 0;
        }
        boolean added = ArcadiaGuard.dynamicItemBlockList().add(id);
        String key = added ? "arcadiaguard.command.item.blocked" : "arcadiaguard.command.item.already_blocked";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, id.toString()), true);
        return added ? 1 : 0;
    }

    private static int unblock(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        boolean removed = ArcadiaGuard.dynamicItemBlockList().remove(id);
        String key = removed ? "arcadiaguard.command.item.unblocked" : "arcadiaguard.command.item.not_found";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, id.toString()), true);
        return removed ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<ResourceLocation> items = ArcadiaGuard.dynamicItemBlockList().list();
        if (items.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.item.none_blocked"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("arcadiaguard.command.item.list_header", String.valueOf(items.size())), false);
            for (ResourceLocation id : items) {
                ctx.getSource().sendSuccess(() -> Component.literal("  - " + id), false);
            }
        }
        return 1;
    }
}
