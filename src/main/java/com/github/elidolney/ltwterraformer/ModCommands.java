package com.github.elidolney.ltwterraformer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = LTWTerraformerTool.MOD_ID)
public final class ModCommands {
    private ModCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ltwterraformer")
                .then(Commands.literal("undo").executes(context -> undo(context.getSource())))
                .then(Commands.literal("cycle").executes(context -> cycleHeldTool(context.getSource())))
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (TerraformMode mode : TerraformMode.values()) {
                                        builder.suggest(mode.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ModCommands::setMode)))
                .then(Commands.literal("shape")
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("next");
                                    for (BrushShape shape : BrushShape.values()) {
                                        builder.suggest(shape.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ModCommands::setShape)))
                .then(Commands.literal("radius")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, TerrainOperations.MAX_RADIUS))
                                .executes(ModCommands::setRadius)))
                .then(Commands.literal("adjust_radius")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-1, 1))
                                .executes(ModCommands::adjustRadius)))
                .then(Commands.literal("restriction")
                        .then(Commands.argument("restriction", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (WandRestriction restriction : WandRestriction.values()) {
                                        builder.suggest(restriction.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ModCommands::setRestriction))));
    }

    private static int undo(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.ltwterraformer.players_only"));
            return 0;
        }
        int restored = UndoManager.undo(player);
        if (restored == 0) {
            source.sendFailure(Component.translatable("command.ltwterraformer.nothing_to_undo"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.ltwterraformer.undone", restored), false);
        return restored;
    }

    private static int cycleHeldTool(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }
        ItemStack stack = heldTool(player);
        if (stack.is(ModItems.CREATIVE_TERRAFORMER.get())) {
            TerraformMode next = ToolData.mode(stack).next();
            ToolData.setMode(stack, next);
            source.sendSuccess(() -> Component.translatable("message.ltwterraformer.mode", next.displayName()), false);
            return 1;
        }
        if (stack.is(ModItems.SURVIVAL_BUILDER_WAND.get())) {
            WandRestriction next = ToolData.restriction(stack).next();
            ToolData.setRestriction(stack, next);
            source.sendSuccess(() -> Component.translatable("message.ltwterraformer.restriction", next.displayName()), false);
            return 1;
        }
        source.sendFailure(Component.translatable("command.ltwterraformer.hold_tool"));
        return 0;
    }

    private static int setMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context.getSource());
        ItemStack stack = player == null ? ItemStack.EMPTY : creativeTool(player);
        String id = StringArgumentType.getString(context, "mode");
        TerraformMode mode = findMode(id);
        if (stack.isEmpty() || mode == null) {
            context.getSource().sendFailure(Component.translatable(
                    stack.isEmpty() ? "command.ltwterraformer.hold_creative" : "command.ltwterraformer.invalid_mode", id));
            return 0;
        }
        ToolData.setMode(stack, mode);
        context.getSource().sendSuccess(() -> Component.translatable("message.ltwterraformer.mode", mode.displayName()), false);
        return 1;
    }

    private static int setShape(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context.getSource());
        ItemStack stack = player == null ? ItemStack.EMPTY : creativeTool(player);
        String id = StringArgumentType.getString(context, "shape");
        BrushShape shape = id.equals("next") ? (stack.isEmpty() ? null : ToolData.shape(stack).next()) : findShape(id);
        if (stack.isEmpty() || shape == null) {
            context.getSource().sendFailure(Component.translatable(
                    stack.isEmpty() ? "command.ltwterraformer.hold_creative" : "command.ltwterraformer.invalid_shape", id));
            return 0;
        }
        ToolData.setShape(stack, shape);
        context.getSource().sendSuccess(() -> Component.translatable("message.ltwterraformer.shape", shape.displayName()), false);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context.getSource());
        ItemStack stack = player == null ? ItemStack.EMPTY : creativeTool(player);
        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.ltwterraformer.hold_creative"));
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(context, "radius");
        ToolData.setRadius(stack, radius);
        context.getSource().sendSuccess(() -> Component.translatable("message.ltwterraformer.radius", radius), false);
        return radius;
    }

    private static int adjustRadius(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context.getSource());
        ItemStack stack = player == null ? ItemStack.EMPTY : creativeTool(player);
        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.ltwterraformer.hold_creative"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int radius = Math.clamp(ToolData.radius(stack) + amount, 1, TerrainOperations.MAX_RADIUS);
        ToolData.setRadius(stack, radius);
        context.getSource().sendSuccess(() -> Component.translatable("message.ltwterraformer.radius", radius), false);
        return radius;
    }

    private static int setRestriction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context.getSource());
        ItemStack stack = player == null ? ItemStack.EMPTY : wand(player);
        String id = StringArgumentType.getString(context, "restriction");
        WandRestriction restriction = findRestriction(id);
        if (stack.isEmpty() || restriction == null) {
            context.getSource().sendFailure(Component.translatable(
                    stack.isEmpty() ? "command.ltwterraformer.hold_wand" : "command.ltwterraformer.invalid_restriction", id));
            return 0;
        }
        ToolData.setRestriction(stack, restriction);
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.ltwterraformer.restriction", restriction.displayName()), false);
        return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.ltwterraformer.players_only"));
            return null;
        }
    }

    private static ItemStack heldTool(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.CREATIVE_TERRAFORMER.get()) || main.is(ModItems.SURVIVAL_BUILDER_WAND.get())) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.CREATIVE_TERRAFORMER.get()) || offhand.is(ModItems.SURVIVAL_BUILDER_WAND.get())
                ? offhand : ItemStack.EMPTY;
    }

    private static ItemStack creativeTool(ServerPlayer player) {
        ItemStack stack = heldTool(player);
        return stack.is(ModItems.CREATIVE_TERRAFORMER.get()) ? stack : ItemStack.EMPTY;
    }

    private static ItemStack wand(ServerPlayer player) {
        ItemStack stack = heldTool(player);
        return stack.is(ModItems.SURVIVAL_BUILDER_WAND.get()) ? stack : ItemStack.EMPTY;
    }

    private static TerraformMode findMode(String id) {
        for (TerraformMode mode : TerraformMode.values()) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return null;
    }

    private static BrushShape findShape(String id) {
        for (BrushShape shape : BrushShape.values()) {
            if (shape.id().equals(id)) {
                return shape;
            }
        }
        return null;
    }

    private static WandRestriction findRestriction(String id) {
        for (WandRestriction restriction : WandRestriction.values()) {
            if (restriction.id().equals(id)) {
                return restriction;
            }
        }
        return null;
    }
}
