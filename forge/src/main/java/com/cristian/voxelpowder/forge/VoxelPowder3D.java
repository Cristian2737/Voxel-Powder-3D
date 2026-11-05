package com.cristian.voxelpowder.forge;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VoxelPowder3D.MOD_ID)
public final class VoxelPowder3D {
    public static final String MOD_ID="tpt3d";
    public VoxelPowder3D() {
        IEventBus modBus=FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        modBus.addListener(this::addCreativeItems);
    }
    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if(!event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS))return;
        event.accept(ModBlocks.CORE_ITEM.get());
        event.accept(ModBlocks.SPARK_TOOL.get());
        for(var entry:ModBlocks.MATTER_ITEMS.values()) event.accept(entry.get());
    }
}
