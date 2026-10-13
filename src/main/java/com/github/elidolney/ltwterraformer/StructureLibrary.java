package com.github.elidolney.ltwterraformer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class StructureLibrary {
    /** Largest selection or blueprint, in blocks including air (for example 80 x 80 x 80). */
    public static final int MAX_VOLUME = TerrainOperations.MAX_AREA_CHANGES;
    public static final int MAX_LISTED_BLUEPRINTS = 200;
    public static final int MAX_MATERIAL_LINES = 64;
    public static final String CLIPBOARD_NAME = "__clipboard";
    private static final int FORMAT_VERSION = 2;

    private StructureLibrary() {
    }

    /** The shared blueprint folder: every world and every player on this installation sees the same files. */
    public static Path blueprintFolder() {
        return FMLPaths.GAMEDIR.get().resolve("ltwterraformer").resolve("blueprints");
    }

    public static boolean captureClipboard(ServerPlayer player, ItemStack stack) {
        StoredStructure structure = captureSelection(player, stack);
        if (structure == null || !write(player, CLIPBOARD_NAME, structure)) {
            return false;
        }
        activate(stack, CLIPBOARD_NAME, structure);
        player.displayClientMessage(Component.translatable(
                "message.ltwterraformer.structure_copied",
                structure.size().getX(), structure.size().getY(), structure.size().getZ(), solidBlockCount(structure)), true);
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
        activate(stack, name, structure);
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
        activate(stack, name, structure);
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
            boolean deleted = Files.deleteIfExists(sharedPath(name));
            deleted |= Files.deleteIfExists(playerPath(player, name));
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

    /**
     * Rebuilds the tool's blueprint list from disk so files dropped into the blueprint folder show up in the
     * library. Returns the number of blueprints found.
     */
    public static int refreshNames(ServerPlayer player, ItemStack stack) {
        Set<String> names = new TreeSet<>();
        collectNames(blueprintFolder(), names, true);
        collectNames(playerFolder(player), names, false);
        List<String> listed = new ArrayList<>(names);
        if (listed.size() > MAX_LISTED_BLUEPRINTS) {
            listed = listed.subList(0, MAX_LISTED_BLUEPRINTS);
        }
        ToolData.setStructureNames(stack, listed);
        return listed.size();
    }

    private static void collectNames(Path folder, Set<String> names, boolean tidyFileNames) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.nbt")) {
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String base = fileName.substring(0, fileName.length() - ".nbt".length());
                if (base.equals(CLIPBOARD_NAME)) {
                    continue;
                }
                String normalized = normalizeName(base);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (!normalized.equals(base)) {
                    if (!tidyFileNames) {
                        continue;
                    }
                    // "My Castle.nbt" becomes "my_castle.nbt" so commands and the library can address it.
                    Path renamed = folder.resolve(normalized + ".nbt");
                    if (Files.exists(renamed)) {
                        if (!Files.isSameFile(file, renamed)) {
                            continue;
                        }
                    } else {
                        Files.move(file, renamed);
                    }
                }
                names.add(normalized);
            }
        } catch (IOException exception) {
            LTWTerraformerTool.LOGGER.error("Failed to list blueprints in {}", folder, exception);
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

        int rotation = ToolData.pasteRotation(stack);
        Rotation stateRotation = Rotation.values()[rotation];
        boolean survival = !player.getAbilities().instabuild;
        boolean skipAir = survival || ToolData.pasteSkipAir(stack);
        Vec3i source = structure.size();
        boolean quarterTurn = (rotation & 1) == 1;
        BlockPos end = origin.offset(
                (quarterTurn ? source.getZ() : source.getX()) - 1,
                source.getY() - 1,
                (quarterTurn ? source.getX() : source.getZ()) - 1);
        ServerLevel level = player.serverLevel();
        if (!level.hasChunksAt(origin, end)) {
            player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_chunks")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }

        List<BlockEntry> planned = new ArrayList<>();
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BlockEntry entry : structure.blocks()) {
            BlockState state = rotation == 0 ? entry.state() : entry.state().rotate(stateRotation);
            if (state.isAir() && skipAir) {
                continue;
            }
            BlockPos relative = new BlockPos(
                    TerrainMath.rotatedX(entry.relative().getX(), entry.relative().getZ(), source.getX(), source.getZ(), rotation),
                    entry.relative().getY(),
                    TerrainMath.rotatedZ(entry.relative().getX(), entry.relative().getZ(), source.getX(), source.getZ(), rotation));
            BlockPos target = origin.offset(relative);
            BlockState old = level.getBlockState(target);
            if (survival) {
                // Survival never digs or overwrites: it only adds blocks the player can pay for into open space.
                if (!old.isAir() && !old.canBeReplaced() || level.getBlockEntity(target) != null) {
                    continue;
                }
                Item cost = costItem(state);
                if (cost == null && !isSecondHalf(state)) {
                    continue;
                }
                if (cost != null) {
                    needed.merge(cost, costCount(state), Integer::sum);
                }
            }
            if (!level.getWorldBorder().isWithinBounds(target) || !level.mayInteract(player, target)
                    || old.getDestroySpeed(level, target) < 0) {
                player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_blocked",
                        target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.RED), true);
                return 0;
            }
            planned.add(new BlockEntry(relative, state, survival ? null : entry.blockEntityData()));
        }

        if (survival) {
            String missing = describeMissing(player, needed);
            if (!missing.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.ltwterraformer.structure_missing_materials",
                        missing).withStyle(ChatFormatting.RED), false);
                return 0;
            }
        }

        List<UndoManager.Change> changes = new ArrayList<>();
        planned.sort(Comparator.comparingInt(entry -> placementPriority(level, origin.offset(entry.relative()), entry)));
        List<BlockEntry> placed = new ArrayList<>();
        for (BlockEntry entry : planned) {
            if (TerrainOperations.setCopiedBlock(level, player, origin.offset(entry.relative()), entry.state(),
                    changes, entry.blockEntityData() != null)) {
                placed.add(entry);
            }
        }
        for (BlockEntry entry : placed) {
            if (entry.blockEntityData() == null) {
                continue;
            }
            BlockPos target = origin.offset(entry.relative());
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity != null) {
                CompoundTag data = entry.blockEntityData().copy();
                data.putInt("x", target.getX());
                data.putInt("y", target.getY());
                data.putInt("z", target.getZ());
                blockEntity.loadWithComponents(data, level.registryAccess());
                blockEntity.setChanged();
            }
        }
        for (int i = placed.size() - 1; i >= 0; i--) {
            BlockEntry entry = placed.get(i);
            level.blockUpdated(origin.offset(entry.relative()), entry.state().getBlock());
        }
        if (survival) {
            Map<Item, Integer> spent = new HashMap<>();
            for (BlockEntry entry : placed) {
                Item cost = costItem(entry.state());
                if (cost != null) {
                    spent.merge(cost, costCount(entry.state()), Integer::sum);
                }
            }
            spent.forEach((item, count) -> consume(player, item, count));
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

    private static void activate(ItemStack stack, String name, StoredStructure structure) {
        ToolData.setActiveStructure(stack, name, structure.size());
        ToolData.setActiveMaterials(stack, materialLines(structure), solidBlockCount(structure));
    }

    private static int solidBlockCount(StoredStructure structure) {
        int count = 0;
        for (BlockEntry entry : structure.blocks()) {
            if (!entry.state().isAir()) {
                count++;
            }
        }
        return count;
    }

    /** The shopping list for a blueprint: one {@code item_id=count} line per item, most-used first. */
    private static List<String> materialLines(StoredStructure structure) {
        Map<Item, Integer> counts = new HashMap<>();
        for (BlockEntry entry : structure.blocks()) {
            Item cost = costItem(entry.state());
            if (cost != null) {
                counts.merge(cost, costCount(entry.state()), Integer::sum);
            }
        }
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((first, second) -> {
            int byCount = Integer.compare(second.getValue(), first.getValue());
            return byCount != 0 ? byCount : BuiltInRegistries.ITEM.getKey(first.getKey()).toString()
                    .compareTo(BuiltInRegistries.ITEM.getKey(second.getKey()).toString());
        });
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : sorted) {
            if (lines.size() >= MAX_MATERIAL_LINES) {
                break;
            }
            lines.add(BuiltInRegistries.ITEM.getKey(entry.getKey()) + "=" + entry.getValue());
        }
        return lines;
    }

    /** The item a survival player pays for this block, or null when it has no item or is the free half of a pair. */
    private static Item costItem(BlockState state) {
        if (state.isAir() || isSecondHalf(state)) {
            return null;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    private static int costCount(BlockState state) {
        return state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
    }

    /** Upper door/plant halves and bed heads come with the item that pays for their other half. */
    private static boolean isSecondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    private static int inventoryCount(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consume(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.getItem() == item) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    private static String describeMissing(ServerPlayer player, Map<Item, Integer> needed) {
        List<String> missing = new ArrayList<>();
        int more = 0;
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int shortBy = entry.getValue() - inventoryCount(player, entry.getKey());
            if (shortBy <= 0) {
                continue;
            }
            if (missing.size() < 5) {
                missing.add(shortBy + " x " + new ItemStack(entry.getKey()).getHoverName().getString());
            } else {
                more++;
            }
        }
        if (missing.isEmpty()) {
            return "";
        }
        return String.join(", ", missing) + (more > 0 ? ", +" + more + " more" : "");
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

        // Stored bottom layer first, then by Z, then by X: index = x + z * sizeX + y * sizeX * sizeZ.
        List<BlockEntry> blocks = new ArrayList<>((int) volume);
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    CompoundTag blockEntityData = blockEntity == null
                            ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
                    blocks.add(new BlockEntry(new BlockPos(x, y, z), level.getBlockState(pos), blockEntityData));
                }
            }
        }
        return new StoredStructure(size, List.copyOf(blocks));
    }

    /** Writes the compact palette format: one palette of block states plus an index per block. */
    private static boolean write(ServerPlayer player, String name, StoredStructure structure) {
        CompoundTag root = new CompoundTag();
        root.putInt("format", FORMAT_VERSION);
        root.putInt("size_x", structure.size().getX());
        root.putInt("size_y", structure.size().getY());
        root.putInt("size_z", structure.size().getZ());
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag palette = new ListTag();
        ListTag blockEntities = new ListTag();
        int[] states = new int[structure.blocks().size()];
        for (int i = 0; i < states.length; i++) {
            BlockEntry block = structure.blocks().get(i);
            Integer index = paletteIndex.get(block.state());
            if (index == null) {
                index = palette.size();
                paletteIndex.put(block.state(), index);
                palette.add(NbtUtils.writeBlockState(block.state()));
            }
            states[i] = index;
            if (block.blockEntityData() != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("index", i);
                entry.put("data", block.blockEntityData().copy());
                blockEntities.add(entry);
            }
        }
        root.put("palette", palette);
        root.putIntArray("states", states);
        root.put("block_entities", blockEntities);
        try {
            Path target = CLIPBOARD_NAME.equals(name) ? playerPath(player, name) : sharedPath(name);
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
        Path source = CLIPBOARD_NAME.equals(name) ? playerPath(player, name) : sharedPath(name);
        if (!Files.isRegularFile(source)) {
            // Presets saved before the shared blueprint folder existed live in the world's per-player folder.
            source = playerPath(player, name);
            if (!Files.isRegularFile(source)) {
                return null;
            }
        }
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.create(256L * 1024L * 1024L));
            var blockLookup = player.serverLevel().holderLookup(Registries.BLOCK);
            if (root.getInt("format") == FORMAT_VERSION) {
                return readPaletteFormat(root, blockLookup);
            }
            if (root.contains("palette", Tag.TAG_LIST) && root.contains("size", Tag.TAG_LIST)) {
                return readVanillaTemplate(root, blockLookup);
            }
            return readLegacyFormat(root, blockLookup);
        } catch (IOException | RuntimeException exception) {
            LTWTerraformerTool.LOGGER.error("Failed to load structure {}", name, exception);
            return null;
        }
    }

    private static StoredStructure readPaletteFormat(CompoundTag root,
                                                     net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {
        Vec3i size = new Vec3i(root.getInt("size_x"), root.getInt("size_y"), root.getInt("size_z"));
        if (!validSize(size)) {
            return null;
        }
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        int[] states = root.getIntArray("states");
        if (states.length != size.getX() * size.getY() * size.getZ()) {
            return null;
        }
        Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        ListTag blockEntityTag = root.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockEntityTag.size(); i++) {
            CompoundTag entry = blockEntityTag.getCompound(i);
            blockEntities.put(entry.getInt("index"), entry.getCompound("data").copy());
        }
        List<BlockEntry> blocks = new ArrayList<>(states.length);
        int layer = size.getX() * size.getZ();
        for (int i = 0; i < states.length; i++) {
            if (states[i] < 0 || states[i] >= palette.size()) {
                return null;
            }
            BlockPos relative = new BlockPos(i % size.getX(), i / layer, (i / size.getX()) % size.getZ());
            blocks.add(new BlockEntry(relative, palette.get(states[i]), blockEntities.get(i)));
        }
        return new StoredStructure(size, List.copyOf(blocks));
    }

    /** Reads files saved by vanilla structure blocks (and tools that export the same format). */
    private static StoredStructure readVanillaTemplate(CompoundTag root,
                                                       net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() != 3) {
            return null;
        }
        Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        if (!validSize(size)) {
            return null;
        }
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        ListTag blockTag = root.getList("blocks", Tag.TAG_COMPOUND);
        List<BlockEntry> blocks = new ArrayList<>(blockTag.size());
        for (int i = 0; i < blockTag.size(); i++) {
            CompoundTag entry = blockTag.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int state = entry.getInt("state");
            if (pos.size() != 3 || state < 0 || state >= palette.size()) {
                return null;
            }
            BlockPos relative = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            if (!inside(relative, size)) {
                return null;
            }
            CompoundTag blockEntityData = entry.contains("nbt", Tag.TAG_COMPOUND) ? entry.getCompound("nbt").copy() : null;
            blocks.add(new BlockEntry(relative, palette.get(state), blockEntityData));
        }
        return new StoredStructure(size, List.copyOf(blocks));
    }

    /** Reads presets written by versions before 0.6.0: one compound per block. */
    private static StoredStructure readLegacyFormat(CompoundTag root,
                                                    net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {
        Vec3i size = new Vec3i(root.getInt("size_x"), root.getInt("size_y"), root.getInt("size_z"));
        if (!validSize(size)) {
            return null;
        }
        ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        if (list.size() != size.getX() * size.getY() * size.getZ()) {
            return null;
        }
        List<BlockEntry> blocks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos relative = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            if (!inside(relative, size)) {
                return null;
            }
            BlockState state = NbtUtils.readBlockState(blockLookup, entry.getCompound("state"));
            CompoundTag blockEntityData = entry.contains("block_entity", Tag.TAG_COMPOUND)
                    ? entry.getCompound("block_entity").copy() : null;
            blocks.add(new BlockEntry(relative, state, blockEntityData));
        }
        return new StoredStructure(size, List.copyOf(blocks));
    }

    private static boolean validSize(Vec3i size) {
        long volume = (long) size.getX() * size.getY() * size.getZ();
        return size.getX() >= 1 && size.getY() >= 1 && size.getZ() >= 1 && volume <= MAX_VOLUME;
    }

    private static boolean inside(BlockPos relative, Vec3i size) {
        return relative.getX() >= 0 && relative.getY() >= 0 && relative.getZ() >= 0
                && relative.getX() < size.getX() && relative.getY() < size.getY() && relative.getZ() < size.getZ();
    }

    private static Path sharedPath(String name) {
        String safeName = normalizeName(name);
        return blueprintFolder().resolve((safeName.isEmpty() ? "__invalid" : safeName) + ".nbt").normalize();
    }

    private static Path playerFolder(ServerPlayer player) {
        return player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("ltwterraformer")
                .resolve("structures")
                .resolve(player.getUUID().toString());
    }

    private static Path playerPath(ServerPlayer player, String name) {
        String safeName = CLIPBOARD_NAME.equals(name) ? CLIPBOARD_NAME : normalizeName(name);
        safeName = safeName.isEmpty() ? "__invalid" : safeName;
        return playerFolder(player).resolve(safeName + ".nbt").normalize();
    }

    private static int placementPriority(ServerLevel level, BlockPos target, BlockEntry entry) {
        if (entry.blockEntityData() != null) {
            return 1;
        }
        return !entry.state().isSolidRender(level, target)
                && !entry.state().isCollisionShapeFullBlock(level, target) ? 2 : 0;
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

    private record BlockEntry(BlockPos relative, BlockState state, CompoundTag blockEntityData) {
    }

    private record StoredStructure(Vec3i size, List<BlockEntry> blocks) {
    }
}
