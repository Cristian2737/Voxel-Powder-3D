package com.cristian.voxelpowder.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Creative SPRK injector; the target's underlying material is preserved. */
public final class SparkToolItem extends Item {
    public SparkToolItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if(!(ctx.getLevel() instanceof ServerLevel))return InteractionResult.SUCCESS;
        return ChamberCoreBlockEntity.trySparkAt((ServerLevel)ctx.getLevel(),ctx.getClickedPos())
            ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
