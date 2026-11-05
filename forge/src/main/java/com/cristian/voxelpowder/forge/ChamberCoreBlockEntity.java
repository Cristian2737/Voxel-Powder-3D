package com.cristian.voxelpowder.forge;

import com.cristian.voxelpowder.core.Matter;
import com.cristian.voxelpowder.core.Electricity;
import com.cristian.voxelpowder.core.Simulator;
import com.cristian.voxelpowder.core.VoxelWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-owned chamber. Only registered TPT blocks/air are changed.
 * Foreign vanilla solid blocks become immutable barriers in the CA.
 * World changes are capped at 320 per simulation step for first-prototype safety.
 */
public final class ChamberCoreBlockEntity extends BlockEntity {
    private static final int W=16,H=12,D=16,MAX_CHANGES=160;
    private final VoxelWorld sim=new VoxelWorld(W,H,D);
    private final Simulator simulator=new Simulator(42);
    private boolean initialized=false;
    private int stepCounter=0;
    private final byte[] projected=new byte[W*H*D];
    private final byte[] projectedPowered=new byte[W*H*D];

    public ChamberCoreBlockEntity(BlockPos pos,BlockState state) {
        super(ModBlockEntities.CHAMBER.get(),pos,state);
    }
    private static BlockPos cellPos(BlockPos core,int x,int y,int z) {
        return core.offset(x-W/2,y+1,z-D/2);
    }
    private Matter materialAt(ServerLevel level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if(state.isAir())return Matter.AIR;
        Matter found=ModBlocks.fromMinecraftBlock(state.getBlock());
        return found==null?Matter.WALL:found;
    }
    private boolean loaded(ServerLevel level,BlockPos pos) {
        return level.hasChunksAt(cellPos(pos,0,0,0),cellPos(pos,W-1,H-1,D-1));
    }
    private void refreshFromWorld(ServerLevel level,BlockPos pos) {
        for(int y=0;y<H;y++)for(int z=0;z<D;z++)for(int x=0;x<W;x++) {
            int i=sim.index(x,y,z);
            Matter actual=materialAt(level,cellPos(pos,x,y,z));
            // Adopt external player changes without clobbering the chamber's saved state.
            if(!initialized || (projected[i]&0xff)!=actual.id)sim.set(i,actual);
            projected[i]=(byte)actual.id;
            BlockState state=level.getBlockState(cellPos(pos,x,y,z));
            projectedPowered[i]=(byte)(state.hasProperty(MatterBlock.POWERED) &&
                state.getValue(MatterBlock.POWERED)?1:0);
        }
        initialized=true;
    }
    public void serverTick(ServerLevel level,BlockPos pos) {
        if(level.getGameTime()%4!=0 || !loaded(level,pos))return;
        refreshFromWorld(level,pos);
        simulator.step(sim,MAX_CHANGES);
        boolean changed=false;
        for(int y=0;y<H;y++)for(int z=0;z<D;z++)for(int x=0;x<W;x++) {
            int i=sim.index(x,y,z);Matter updated=sim.at(i);
            boolean powered=sim.energized(i) || sim.switchOnAt(i);
            if((projected[i]&0xff)==updated.id && (projectedPowered[i]!=0)==powered)continue;
            Block block=updated==Matter.AIR ? Blocks.AIR : ModBlocks.toMinecraftBlock(updated);
            // Never overwrite a vanilla block (identified as WALL).
            if(updated==Matter.WALL || block==null)continue;
            BlockPos at=cellPos(pos,x,y,z);
            if(materialAt(level,at)==Matter.WALL)continue;
            BlockState updatedState=block.defaultBlockState();
            if(updatedState.hasProperty(MatterBlock.POWERED))
                updatedState=updatedState.setValue(MatterBlock.POWERED,powered);
            level.setBlock(at,updatedState,2);
            projected[i]=(byte)updated.id;
            projectedPowered[i]=(byte)(powered?1:0);
            changed=true;
        }
        stepCounter++;
        if(changed || stepCounter%10==0)setChanged();
    }
    /** Test tool injects SPRK at the existing material, not as a replacement block. */
    public static boolean trySparkAt(ServerLevel level,BlockPos target) {
        // Bounded scan done only when the user clicks an electronic block.
        for(int cy=target.getY()-H;cy<target.getY();cy++)
            for(int cx=target.getX()-(W-1-W/2);cx<=target.getX()+W/2;cx++)
                for(int cz=target.getZ()-(D-1-D/2);cz<=target.getZ()+D/2;cz++) {
                    BlockPos corePos=new BlockPos(cx,cy,cz);
                    if(!level.hasChunkAt(corePos) || !level.getBlockState(corePos).is(ModBlocks.CORE.get()))continue;
                    if(!(level.getBlockEntity(corePos) instanceof ChamberCoreBlockEntity))continue;
                    ChamberCoreBlockEntity chamber=(ChamberCoreBlockEntity)level.getBlockEntity(corePos);
                    int x=target.getX()-cx+W/2,y=target.getY()-cy-1,z=target.getZ()-cz+D/2;
                    if(!chamber.sim.inside(x,y,z))continue;
                    chamber.refreshFromWorld(level,corePos);
                    if(Electricity.spark(chamber.sim,x,y,z)) {
                        chamber.setChanged();return true;
                    }
                }
        return false;
    }
    /** Empty-hand right-click on the core to populate a sample sandbox. */
    public void seedDemo(ServerLevel level,BlockPos pos) {
        if(!loaded(level,pos))return;
        for(int x=2;x<6;x++)for(int z=2;z<6;z++)for(int y=7;y<11;y++)seed(level,pos,x,y,z,Matter.SAND);
        for(int x=10;x<14;x++)for(int z=2;z<6;z++)for(int y=7;y<11;y++)seed(level,pos,x,y,z,Matter.WATER);
        for(int x=2;x<6;x++)for(int z=10;z<14;z++)for(int y=1;y<4;y++)seed(level,pos,x,y,z,Matter.WOOD);
        for(int x=10;x<14;x++)for(int z=10;z<14;z++)for(int y=7;y<10;y++)seed(level,pos,x,y,z,Matter.LAVA);
        seed(level,pos,4,4,11,Matter.FIRE);
        setChanged();
    }
    private void seed(ServerLevel level,BlockPos pos,int x,int y,int z,Matter matter) {
        BlockPos at=cellPos(pos,x,y,z);
        if(!level.getBlockState(at).isAir())return;
        level.setBlock(at,ModBlocks.toMinecraftBlock(matter).defaultBlockState(),2);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Initialized",initialized);
        tag.putByteArray("Cells",sim.exportMatter());
        tag.putIntArray("Temps",sim.exportTemperatures());
        tag.putIntArray("Ages",sim.exportAges());
        tag.putIntArray("Pulses",sim.exportPulses());
        tag.putIntArray("Refractory",sim.exportRefractory());
        tag.putIntArray("Switches",sim.exportSwitches());
        tag.putLong("Ticks",sim.ticks());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        initialized=false;
        byte[] cells=tag.getByteArray("Cells");
        int[] temps=tag.getIntArray("Temps"),ages=tag.getIntArray("Ages");
        if(cells.length==sim.size() && temps.length==sim.size() && ages.length==sim.size()) {
            sim.restore(cells,temps,ages);
            if(tag.getIntArray("Pulses").length==sim.size() &&
               tag.getIntArray("Refractory").length==sim.size() &&
               tag.getIntArray("Switches").length==sim.size())
                sim.restoreElectricity(tag.getIntArray("Pulses"),tag.getIntArray("Refractory"),tag.getIntArray("Switches"));
            sim.setTick(tag.getLong("Ticks"));
            for(int i=0;i<projected.length;i++) {
                projected[i]=cells[i];
                projectedPowered[i]=(byte)((sim.energized(i)||sim.switchOnAt(i))?1:0);
            }
            initialized=tag.getBoolean("Initialized");
        }
    }
}
