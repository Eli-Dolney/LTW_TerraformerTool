package com.github.elidolney.ltwterraformer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StructureLibrary {
    public static final int MAX_VOLUME = TerrainOperations.MAX_CHANGES;
    public static final String CLIPBOARD_NAME = "__clipboard";

    private StructureLibrary() {
    }

    public static boolean captureClipboard(ServerPlayer player, ItemStack stack) {
        StoredStructure structure = captureSelection(player, stack);
        if (structure == null || !write(player, CLIPBOARD_NAME, structure)) {
            return false;
        }
        ToolData.setActiveStructure(stack, CLIPBOARD_NAME, structure.size());
        player.displayClientMessage(Component.translatable(
                "message.ltwterraformer.structure_copied",
                structure.size().getX(), structure.size().getY(), structure.size().getZ(), structure.blocks().size()), true);
        return true;
    }

    public static boolean savePreset(ServerPlayer player, ItemStack stack, String requestedName) {
        String name = normalizeName(requestedName);
        if (name.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_invalid_name")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        StoredStructure structure = captureSelection(player, stack);
        if (structure == null || !write(player, name, structure)) {
            return false;
        }
        ToolData.addStructureName(stack, name);
        ToolData.setActiveStructure(stack, name, structure.size());
        player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_saved", name), false);
        return true;
    }

    public static boolean loadPreset(ServerPlayer player, ItemStack stack, String requestedName) {
        String name = normalizeName(requestedName);
        StoredStructure structure = read(player, name);
        if (structure == null) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_missing", name)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ToolData.addStructureName(stack, name);
        ToolData.setActiveStructure(stack, name, structure.size());
        ToolData.setEditorMode(stack, StructureEditorMode.PASTE);
        player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_loaded", name), false);
        return true;
    }

    public static boolean deletePreset(ServerPlayer player, ItemStack stack, String requestedName) {
        String name = normalizeName(requestedName);
        if (name.isEmpty()) {
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(path(player, name));
            ToolData.removeStructureName(stack, name);
            if (ToolData.activeStructure(stack).equals(name)) {
                ToolData.clearActiveStructure(stack);
                ToolData.setEditorMode(stack, StructureEditorMode.SELECT);
            }
            player.displayClientMessage(Component.translatable(
                    deleted ? "message.ltwterraformer.structure_deleted" : "message.ltwterraformer.structure_missing", name), false);
            return deleted;
        } catch (IOException exception) {
            LTWTerraformerTool.LOGGER.error("Failed to delete structure preset {}", name, exception);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_io_error")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
    }

    public static int paste(ServerPlayer player, ItemStack stack, BlockPos origin) {
        String name = ToolData.activeStructure(stack);
        if (name.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_nothing_loaded")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        StoredStructure structure = read(player, name);
        if (structure == null) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_missing", displayName(name))
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }

        BlockPos end = origin.offset(
                structure.size().getX() - 1,
                structure.size().getY() - 1,
                structure.size().getZ() - 1);
        ServerLevel level = player.serverLevel();
        if (!level.hasChunksAt(origin, end)) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_chunks")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }

        for (BlockEntry entry : structure.blocks()) {
            BlockPos target = origin.offset(entry.relative());
            BlockState old = level.getBlockState(target);
            if (!level.getWorldBorder().isWithinBounds(target) || !level.mayInteract(player, target)
                    || level.getBlockEntity(target) != null || old.getDestroySpeed(level, target) < 0) {
                player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_blocked")
                        .withStyle(ChatFormatting.RED), true);
                return 0;
            }
        }

        List<UndoManager.Change> changes = new ArrayList<>();
        for (BlockEntry entry : structure.blocks()) {
            TerrainOperations.setCopiedBlock(
                    level, player, origin.offset(entry.relative()), entry.state(), changes);
        }
        if (!changes.isEmpty()) {
            UndoManager.remember(player, level, changes);
        }
        player.displayClientMessage(Component.translatable(
                "message.ltwterraformer.structure_pasted", displayName(name), changes.size()), true);
        return changes.size();
    }

    public static String normalizeName(String input) {
        String normalized = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
        }
        return normalized.equals(CLIPBOARD_NAME) ? "" : normalized;
    }

    public static String displayName(String name) {
        return CLIPBOARD_NAME.equals(name) ? "Clipboard" : name;
    }

    private static StoredStructure captureSelection(ServerPlayer player, ItemStack stack) {
        Optional<BlockPos> first = ToolData.cornerOne(stack);
        Optional<BlockPos> second = ToolData.cornerTwo(stack);
        if (first.isEmpty() || second.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_select_two")
                    .withStyle(ChatFormatting.YELLOW), true);
            return null;
        }
        String selectedDimension = ToolData.selectionDimension(stack);
        if (!selectedDimension.equals(player.level().dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_wrong_dimension")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }

        BlockPos min = min(first.get(), second.get());
        BlockPos max = max(first.get(), second.get());
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume > MAX_VOLUME) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_too_large", volume, MAX_VOLUME)
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }

        ServerLevel level = player.serverLevel();
        if (!level.hasChunksAt(min, max)) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_chunks")
                    .withStyle(ChatFormatting.YELLOW), true);
            return null;
        }

        List<BlockEntry> blocks = new ArrayList<>((int) volume);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) != null) {
                player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_block_entities")
                        .withStyle(ChatFormatting.RED), true);
                return null;
            }
            blocks.add(new BlockEntry(pos.subtract(min).immutable(), level.getBlockState(pos)));
        }
        return new StoredStructure(size, List.copyOf(blocks));
    }

    private static boolean write(ServerPlayer player, String name, StoredStructure structure) {
        CompoundTag root = new CompoundTag();
        root.putInt("size_x", structure.size().getX());
        root.putInt("size_y", structure.size().getY());
        root.putInt("size_z", structure.size().getZ());
        ListTag blocks = new ListTag();
        for (BlockEntry block : structure.blocks()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", block.relative().getX());
            entry.putInt("y", block.relative().getY());
            entry.putInt("z", block.relative().getZ());
            entry.put("state", NbtUtils.writeBlockState(block.state()));
            blocks.add(entry);
        }
        root.put("blocks", blocks);
        try {
            Path target = path(player, name);
            Files.createDirectories(target.getParent());
            NbtIo.writeCompressed(root, target);
            return true;
        } catch (IOException exception) {
            LTWTerraformerTool.LOGGER.error("Failed to save structure {}", name, exception);
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_io_error")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
    }

    private static StoredStructure read(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            return null;
        }
        Path source = path(player, name);
        if (!Files.isRegularFile(source)) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.create(64L * 1024L * 1024L));
            Vec3i size = new Vec3i(root.getInt("size_x"), root.getInt("size_y"), root.getInt("size_z"));
            long volume = (long) size.getX() * size.getY() * size.getZ();
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || volume > MAX_VOLUME) {
                return null;
            }
            ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
            if (list.size() != volume) {
                return null;
            }
            List<BlockEntry> blocks = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockPos relative = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                if (relative.getX() < 0 || relative.getY() < 0 || relative.getZ() < 0
                        || relative.getX() >= size.getX() || relative.getY() >= size.getY() || relative.getZ() >= size.getZ()) {
                    return null;
                }
                BlockState state = NbtUtils.readBlockState(
                        player.serverLevel().holderLookup(Registries.BLOCK), entry.getCompound("state"));
                blocks.add(new BlockEntry(relative, state));
            }
            return new StoredStructure(size, List.copyOf(blocks));
        } catch (IOException | RuntimeException exception) {
            LTWTerraformerTool.LOGGER.error("Failed to load structure {}", name, exception);
            return null;
        }
    }

    private static Path path(ServerPlayer player, String name) {
        Path root = player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("ltwterraformer")
                .resolve("structures")
                .resolve(player.getUUID().toString());
        String safeName = CLIPBOARD_NAME.equals(name) ? CLIPBOARD_NAME : normalizeName(name);
        safeName = safeName.isEmpty() ? "__invalid" : safeName;
        return root.resolve(safeName + ".nbt").normalize();
    }

    private static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    private record BlockEntry(BlockPos relative, BlockState state) {
    }

    private record StoredStructure(Vec3i size, List<BlockEntry> blocks) {
    }
}
