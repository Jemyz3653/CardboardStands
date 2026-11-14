package studio.detemkur.cardboardstands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CardboardStands implements ModInitializer {
    public static final String ID = "cardboardstands";
    static final Logger LOG = LoggerFactory.getLogger(ID);
    public static Block STAND;
    public static BlockEntityType<StandEntity> STAND_ENTITY;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        var blockKey = ResourceKey.create(Registries.BLOCK, id("stand"));
        STAND = Registry.register(BuiltInRegistries.BLOCK, blockKey, new StandBlock(
                BlockBehaviour.Properties.of().setId(blockKey).strength(0.4F).sound(SoundType.WOOD)
                        .noOcclusion().dynamicShape().ignitedByLava()));
        var itemKey = ResourceKey.create(Registries.ITEM, id("stand"));
        Item item = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(STAND, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        STAND_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("stand"),
                FabricBlockEntityTypeBuilder.create(StandEntity::new, STAND).build());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(item));
        StandPackets.register();
        StandServer.register();
    }
}
