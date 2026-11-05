package com.cristian.voxelpowder.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Place one chamber core; the bounded 16x12x16 simulation lives directly above it. */
public final class ChamberCoreBlock extends BaseEntityBlock {
    public ChamberCoreBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new ChamberCoreBlockEntity(pos,state);
    }
    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,ModBlockEntities.CHAMBER.get(),
            (l,p,s,be)->be.serverTick((ServerLevel)l,p));
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,
                                           InteractionHand hand,BlockHitResult hit) {
        if(!player.getItemInHand(hand).isEmpty() || player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof ChamberCoreBlockEntity core)
            core.seedDemo((ServerLevel)level,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
