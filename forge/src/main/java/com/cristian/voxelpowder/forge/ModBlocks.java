package com.cristian.voxelpowder.forge;

import com.cristian.voxelpowder.core.Matter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    private ModBlocks() {}
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,VoxelPowder3D.MOD_ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,VoxelPowder3D.MOD_ID);
    public static final RegistryObject<Block> CORE=BLOCKS.register("chamber_core",()->new ChamberCoreBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)));
    public static final RegistryObject<Item> CORE_ITEM=ITEMS.register("chamber_core",()->new BlockItem(CORE.get(),new Item.Properties()));
    public static final RegistryObject<Item> SPARK_TOOL=ITEMS.register("tpt_spark_tool",
        ()->new SparkToolItem(new Item.Properties().stacksTo(1)));
    public static final Map<Matter,RegistryObject<Block>> MATTER_BLOCKS;
    public static final Map<Matter,RegistryObject<Item>> MATTER_ITEMS;
    static {
        Map<Matter,RegistryObject<Block>> blocks=new LinkedHashMap<>();
        Map<Matter,RegistryObject<Item>> items=new LinkedHashMap<>();
        for(Matter m:Matter.values()) {
            if(m==Matter.AIR || m==Matter.WALL || m==Matter.SPRK)continue;
            String name="tpt_"+m.name().toLowerCase(java.util.Locale.ROOT);
            RegistryObject<Block> block=BLOCKS.register(name,()->new MatterBlock(m));
            blocks.put(m,block);
            items.put(m,ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties())));
        }
        MATTER_BLOCKS=Collections.unmodifiableMap(blocks);
        MATTER_ITEMS=Collections.unmodifiableMap(items);
    }
    public static Matter fromMinecraftBlock(Block block) {
        for(var entry:MATTER_BLOCKS.entrySet()) if(entry.getValue().get()==block)return entry.getKey();
        return null;
    }
    public static Block toMinecraftBlock(Matter m) {
        var ref=MATTER_BLOCKS.get(m);
        return ref==null ? null : ref.get();
    }
}
