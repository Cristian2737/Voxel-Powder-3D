package com.cristian.voxelpowder.core;

public final class SeedScenes {
    private SeedScenes() {}
    public static void demo(VoxelWorld w) {
        for(int x=0;x<w.width;x++)for(int z=0;z<w.depth;z++)w.put(x,0,z,Matter.STONE);
        int cx=w.width/2,cz=w.depth/2;
        for(int x=cx-10;x<cx-3;x++)for(int z=cz-9;z<cz-3;z++)for(int y=11;y<16;y++)w.put(x,y,z,Matter.SAND);
        for(int x=cx+2;x<cx+9;x++)for(int z=cz-9;z<cz-3;z++)for(int y=5;y<12;y++)w.put(x,y,z,Matter.WATER);
        for(int x=cx-7;x<cx-2;x++)for(int z=cz+3;z<cz+8;z++)for(int y=2;y<6;y++)w.put(x,y,z,Matter.WOOD);
        for(int x=cx+3;x<cx+7;x++)for(int z=cz+3;z<cz+8;z++)for(int y=5;y<10;y++)w.put(x,y,z,Matter.LAVA);
        w.put(cx-4,6,cz+5,Matter.FIRE);
    }
}
