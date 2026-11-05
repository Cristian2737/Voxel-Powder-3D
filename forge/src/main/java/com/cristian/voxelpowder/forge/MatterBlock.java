package com.cristian.voxelpowder.forge;

import com.cristian.voxelpowder.core.Matter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Minecraft-native block behavior for the Forge chamber's material placeholders. */
public final class MatterBlock extends Block {
    public static final BooleanProperty POWERED=BooleanProperty.create("powered");
    public final Matter material;
    private static boolean intangible(Matter m) {
        return m.kind==Matter.Kind.LIQUID || m.kind==Matter.Kind.GAS;
    }
    private static BlockBehaviour.Properties properties(Matter material) {
        // Solids/powders have Minecraft's usual full-cube collision and occlusion;
        // liquid and gas placeholders have neither. The TPT simulation still controls motion.
        BlockBehaviour.Properties prop=BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY)
            .strength(material==Matter.STONE || material==Matter.METAL?1.5f:0.55f)
            .lightLevel(state -> state.getValue(POWERED) && material==Matter.LCRY ? 13 : 0)
            .sound(material==Matter.WOOD?SoundType.WOOD:material==Matter.METAL?SoundType.METAL:SoundType.SAND);
        if(intangible(material))prop=prop.noCollission().noOcclusion();
        return prop;
    }
    public MatterBlock(Matter material) {
        super(properties(material));
        this.material=material;
        registerDefaultState(defaultBlockState().setValue(POWERED,Boolean.FALSE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(POWERED);
    }
}
