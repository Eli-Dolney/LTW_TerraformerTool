package com.github.elidolney.ltwterraformer;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(LTWTerraformerTool.MOD_ID)
public final class LTWTerraformerTool {
    public static final String MOD_ID = "ltwterraformer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LTWTerraformerTool(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabItems);
        NeoForge.EVENT_BUS.addListener(ModCommands::register);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SURVIVAL_BUILDER_WAND);
            event.accept(ModItems.CREATIVE_TERRAFORMER);
            event.accept(ModItems.STRUCTURE_EDITOR);
        }
    }
}
