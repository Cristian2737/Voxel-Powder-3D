package com.cristian.voxelpowder.core;

import java.util.Random;

/** Original, bounded 3D cellular-automaton prototype. No TPT C++ code is copied. */
public final class Simulator {
    private final Random random;
    private int[] stamps=new int[0];
    private int epoch=0;
    private int moves;
    private int limit;
    public Simulator(long seed) { random=new Random(seed); }

    /** One fixed-step update. maxChanges bounds server work; each move may touch 2 cells. */
    public int step(VoxelWorld world,int maxChanges) {
        if(stamps.length!=world.size()) { stamps=new int[world.size()];epoch=0; }
        epoch++;
        if(epoch==Integer.MAX_VALUE) { java.util.Arrays.fill(stamps,0);epoch=1; }
        moves=0; limit=Math.max(1,maxChanges);
        world.incrementTick();
        // Electronics advance on the same fixed step, with a global update budget.
        // Electrical state is orthogonal to material identity and never changes cell IDs.
        int electricOps=Electricity.step(world,limit);
        moves=electricOps;
        final int phaseX=random.nextInt(2), phaseZ=random.nextInt(2);
        for(int y=0;y<world.height && moves<limit;y++) {
            for(int zi=0;zi<world.depth && moves<limit;zi++) {
                int z=phaseZ==0?zi:world.depth-1-zi;
                for(int xi=0;xi<world.width && moves<limit;xi++) {
                    int x=phaseX==0?xi:world.width-1-xi;
                    int idx=world.index(x,y,z);
                    if(stamps[idx]==epoch)continue;
                    Matter m=world.at(idx);
                    if(m==Matter.AIR || m==Matter.WALL)continue;
                    if(react(world,x,y,z,idx,m)) continue;
                    if(moves>=limit)break;
                    switch(m.kind) {
                        case POWDER: gravity(world,x,y,z,idx,m,true); break;
                        case LIQUID: gravity(world,x,y,z,idx,m,false); break;
                        case GAS: rise(world,x,y,z,idx,m); break;
                        default: break;
                    }
                }
            }
        }
        return moves;
    }
    private boolean react(VoxelWorld w,int x,int y,int z,int i,Matter m) {
        if(m==Matter.FIRE || m==Matter.SMOKE || m==Matter.STEAM)w.age(i);
        if(m==Matter.FIRE) {
            if(w.ageAt(i)>18) { change(w,i,Matter.SMOKE); return true; }
            // Fire consumes adjacent fuels and heats/melts local materials.
            for(int k=0;k<4 && moves<limit;k++) {
                int a=random.nextInt(6);
                int nx=x+(a==0?1:a==1?-1:0), ny=y+(a==2?1:a==3?-1:0),nz=z+(a==4?1:a==5?-1:0);
                if(!w.inside(nx,ny,nz))continue;
                int j=w.index(nx,ny,nz);Matter other=w.at(j);
                if((other==Matter.WOOD || other==Matter.OIL || other==Matter.GUNPOWDER) && random.nextInt(3)==0) {
                    change(w,j,Matter.FIRE);
                }else if(other==Matter.ICE && random.nextInt(3)==0)change(w,j,Matter.WATER);
                else if(other==Matter.WATER && random.nextInt(6)==0)change(w,j,Matter.STEAM);
            }
        } else if(m==Matter.SMOKE && w.ageAt(i)>60) { change(w,i,Matter.AIR);return true; }
        else if(m==Matter.STEAM && w.ageAt(i)>85) { change(w,i,Matter.WATER);return true; }
        else if(m==Matter.WOOD || m==Matter.GUNPOWDER || m==Matter.OIL) {
            if(neighbors(w,x,y,z,Matter.FIRE,Matter.LAVA) && random.nextInt(m==Matter.GUNPOWDER?2:8)==0) {
                change(w,i,Matter.FIRE);return true;
            }
        } else if(m==Matter.ICE && neighbors(w,x,y,z,Matter.LAVA,Matter.FIRE)) {
            change(w,i,Matter.WATER);return true;
        } else if(m==Matter.WATER && neighbors(w,x,y,z,Matter.LAVA,null)) {
            change(w,i,Matter.STEAM);return true;
        } else if(m==Matter.LAVA && neighbors(w,x,y,z,Matter.WATER,null)) {
            change(w,i,Matter.STONE);return true;
        } else if(m==Matter.ACID && random.nextInt(9)==0) {
            int a=random.nextInt(6);
            int nx=x+(a==0?1:a==1?-1:0), ny=y+(a==2?1:a==3?-1:0),nz=z+(a==4?1:a==5?-1:0);
            if(w.inside(nx,ny,nz)) {
                int j=w.index(nx,ny,nz);Matter other=w.at(j);
                if(other==Matter.METAL || other==Matter.STONE || other==Matter.WOOD) {
                    change(w,j,Matter.AIR);
                    if(random.nextInt(3)==0 && moves<limit)change(w,i,Matter.WATER);
                }
            }
        }
        return stamps[i]==epoch;
    }
    private boolean neighbors(VoxelWorld w,int x,int y,int z,Matter a,Matter b) {
        return matching(w.get(x+1,y,z),a,b)||matching(w.get(x-1,y,z),a,b)||
            matching(w.get(x,y+1,z),a,b)||matching(w.get(x,y-1,z),a,b)||
            matching(w.get(x,y,z+1),a,b)||matching(w.get(x,y,z-1),a,b);
    }
    private boolean matching(Matter v,Matter a,Matter b) {return v==a || (b!=null&&v==b);}
    private void change(VoxelWorld w,int idx,Matter m) {
        if(moves>=limit||w.at(idx)==m)return;
        w.set(idx,m);stamps[idx]=epoch;moves++;
    }
    private boolean shift(VoxelWorld w,int x,int y,int z,int i,Matter m) {
        if(!w.inside(x,y,z)||moves>=limit)return false;
        int j=w.index(x,y,z),otherDensity=w.at(j).density;
        Matter other=w.at(j);
        boolean canSwap=other==Matter.AIR || (other.kind==Matter.Kind.GAS && m.kind!=Matter.Kind.GAS)
            || (m.kind==Matter.Kind.GAS && other==Matter.AIR)
            || (m.kind!=Matter.Kind.GAS && other.kind==Matter.Kind.LIQUID && m.density>otherDensity);
        if(!canSwap || stamps[j]==epoch)return false;
        w.swap(i,j);stamps[i]=epoch;stamps[j]=epoch;moves++;return true;
    }
    private void gravity(VoxelWorld w,int x,int y,int z,int i,Matter m,boolean powder) {
        if(shift(w,x,y-1,z,i,m))return;
        int first=random.nextInt(4);
        for(int n=0;n<4;n++) {
            int k=(n+first)%4;
            int dx=(k==0?1:k==1?-1:0), dz=(k==2?1:k==3?-1:0);
            if(shift(w,x+dx,y-1,z+dz,i,m))return;
        }
        if(powder)return;
        // Liquids flow sideways through all four horizontal directions.
        for(int n=0;n<4;n++) {
            int k=(n+first)%4;
            int dx=(k==0?1:k==1?-1:0), dz=(k==2?1:k==3?-1:0);
            if(shift(w,x+dx,y,z+dz,i,m))return;
        }
    }
    private void rise(VoxelWorld w,int x,int y,int z,int i,Matter m) {
        if(shift(w,x,y+1,z,i,m))return;
        int first=random.nextInt(4);
        for(int n=0;n<4;n++) {
            int k=(first+n)%4;
            int dx=k==0?1:k==1?-1:0,dz=k==2?1:k==3?-1:0;
            if(shift(w,x+dx,y+1,z+dz,i,m)||shift(w,x+dx,y,z+dz,i,m))return;
        }
    }
}
