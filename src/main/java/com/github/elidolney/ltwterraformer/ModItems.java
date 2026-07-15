package com.github.elidolney.ltwterraformer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LTWTerraformerTool.MOD_ID);

    public static final DeferredItem<Item> CREATIVE_TERRAFORMER = ITEMS.register(
            "creative_terraformer",
            () -> new CreativeTerraformerItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
                    .attributes(creativeReach()))
    );

    public static final DeferredItem<Item> SURVIVAL_BUILDER_WAND = ITEMS.register(
            "survival_builder_wand",
            () -> new SurvivalBuilderWandItem(new Item.Properties().stacksTo(1).durability(4096))
    );

    private ModItems() {
    }

    private static ItemAttributeModifiers creativeReach() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.BLOCK_INTERACTION_RANGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(LTWTerraformerTool.MOD_ID, "creative_reach"),
                                59.5,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HAND)
                .build();
    }
}
