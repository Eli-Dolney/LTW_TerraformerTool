package com.github.elidolney.ltwterraformer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

public final class SurvivalBuilderWandItem extends Item {
    public SurvivalBuilderWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack wand = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            WandRestriction next = ToolData.restriction(wand).next();
            ToolData.setRestriction(wand, next);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.restriction", next.displayName()), true);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        WandMaterialSource material = WandMaterialSource.select(serverPlayer, level.getBlockState(context.getClickedPos()));
        if (material == null) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.no_blocks").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        WandRestriction restriction = ToolData.restriction(wand);
        int remainingDurability = wand.getMaxDamage() - wand.getDamageValue();
        int placementLimit = Math.min(material.availableCount(serverPlayer), remainingDurability);
        int placed = placePattern(level, serverPlayer, context, material, restriction, placementLimit);
        if (placed > 0) {
            EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            wand.hurtAndBreak(placed, serverPlayer, slot);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.placed", placed), true);
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(Component.translatable(
                placementLimit == 0 ? "message.ltwterraformer.wand_exhausted" : "message.ltwterraformer.no_space"), true);
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            WandRestriction next = ToolData.restriction(wand).next();
            ToolData.setRestriction(wand, next);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.restriction", next.displayName()), true);
            return InteractionResultHolder.sidedSuccess(wand, level.isClientSide());
        }
        return InteractionResultHolder.pass(wand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ltwterraformer.survival_builder_wand.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.restriction", ToolData.restriction(stack).displayName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.survival_builder_wand.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ltwterraformer.survival_builder_wand.3").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int placePattern(ServerLevel level, ServerPlayer player, UseOnContext context,
                                    WandMaterialSource material, WandRestriction restriction, int placementLimit) {
        BlockPos origin = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        List<BlockPos> planned = WandPlacementPlanner.plan(
                level, origin, clickedFace, restriction, player.getDirection());
        List<UndoManager.Change> changes = new ArrayList<>();

        Direction placementFace = restriction == WandRestriction.HOLE_FILL ? Direction.UP : clickedFace;
        for (BlockPos target : planned) {
            if (changes.size() >= placementLimit || !material.available(player)) {
                break;
            }

            BlockPos support = target.relative(placementFace.getOpposite());
            BlockState old = level.getBlockState(target);
            if (level.getWorldBorder().isWithinBounds(target) && level.mayInteract(player, target)
                    && player.mayUseItemAt(target, placementFace, context.getItemInHand())
                    && level.getBlockEntity(target) == null && (old.isAir() || old.canBeReplaced())) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), placementFace, support, false);
                BlockPlaceContext placementContext = new BlockPlaceContext(
                        player, context.getHand(), material.currentStack(player), hit);
                BlockState placedState = material.blockItem().getBlock().getStateForPlacement(placementContext);
                if (placedState != null && placedState.canSurvive(level, target)
                        && level.isUnobstructed(placedState, target, CollisionContext.of(player))) {
                    BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target, 3);
                    if (!level.setBlock(target, placedState, 3)) {
                        continue;
                    }
                    if (EventHooks.onBlockPlace(player, snapshot, placementFace)) {
                        snapshot.restore(3);
                        continue;
                    }
                    changes.add(new UndoManager.Change(target.immutable(), old));
                    material.consume(player);
                }
            }
        }

        if (!changes.isEmpty()) {
            UndoManager.remember(player, level, changes);
            BlockState soundState = material.blockItem().getBlock().defaultBlockState();
            level.playSound(null, origin, soundState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        return changes.size();
    }
}
