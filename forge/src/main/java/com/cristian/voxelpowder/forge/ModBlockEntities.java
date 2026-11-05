package com.cristian.voxelpowder.forge;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private ModBlockEntities() {}
    public static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,VoxelPowder3D.MOD_ID);
    public static final RegistryObject<BlockEntityType<ChamberCoreBlockEntity>> CHAMBER=TYPES.register(
        "chamber_core",()->BlockEntityType.Builder.of(ChamberCoreBlockEntity::new,ModBlocks.CORE.get()).build(null));
}
