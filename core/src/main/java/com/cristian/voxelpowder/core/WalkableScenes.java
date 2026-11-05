package com.cristian.voxelpowder.core;

/** An enclosed but fully traversable 32x18x32 3D test area. Forge's original seed scene is unchanged. */
public final class WalkableScenes {
    private WalkableScenes() {}

    public static void create(VoxelWorld w) {
        w.fill(Matter.AIR);
        int maxX = w.width - 1, maxZ = w.depth - 1;
        for (int x = 0; x < w.width; x++) for (int z = 0; z < w.depth; z++) {
            w.put(x, 0, z, Matter.STONE);
            // Short perimeter makes the world boundary visible without obscuring the view.
            if (x == 0 || z == 0 || x == maxX || z == maxZ) {
                for (int y = 1; y <= 3; y++) w.put(x,y,z,Matter.STONE);
            }
        }
        int cx = w.width / 2, cz = w.depth / 2;
        // Powder pile west, a few suspended grains for falling-sand demonstrations.
        for (int x = cx - 11; x <= cx - 7; x++)
            for (int z = cz - 8; z <= cz - 4; z++)
                for (int y = 1; y <= 3; y++) w.put(x,y,z,Matter.SAND);
        for (int x = cx - 10; x <= cx - 8; x++)
            for (int z = cz - 7; z <= cz - 5; z++)
                w.put(x, 9, z, Matter.SAND);

        // Low stone pool with a liquid surface at eye level only when approaching.
        int px = cx + 5, pz = cz - 6;
        for (int x = px-3; x <= px+3; x++) for (int z = pz-3; z <= pz+3; z++) {
            if (x==px-3 || x==px+3 || z==pz-3 || z==pz+3) w.put(x,1,z,Matter.STONE);
            else w.put(x,1,z,Matter.WATER);
        }

        // Small reachable timber ruin at the opposite side.
        for (int y=1;y<=5;y++) {
            w.put(cx-9,y,cz+5,Matter.WOOD);
            w.put(cx-4,y,cz+5,Matter.WOOD);
            w.put(cx-9,y,cz+10,Matter.WOOD);
            w.put(cx-4,y,cz+10,Matter.WOOD);
        }
        for (int x=cx-9;x<=cx-4;x++) for (int z=cz+5;z<=cz+10;z++)
            w.put(x,6,z,Matter.WOOD);
        w.put(cx-8,1,cz+6,Matter.FIRE);

        // Small lava vessel, isolated from the water pool.
        for (int x=cx+5;x<=cx+10;x++) for (int z=cz+5;z<=cz+10;z++) {
            if (x==cx+5 || x==cx+10 || z==cz+5 || z==cz+10) w.put(x,1,z,Matter.STONE);
            else w.put(x,1,z,Matter.LAVA);
        }

        // Distinct metal marker to orient the player and provide a tool target.
        for (int y=1;y<=4;y++) w.put(cx, y, cz-10, Matter.METAL);

        // Electronic demonstration near the north wall, at eye-height for editing.
        // Circuit A: BTRY -> PSCN -> SWCH -> METAL -> LCRY.
        // PSCN arms the switch; add an NSCN touching it later to turn it off.
        int sz=4;
        w.put(cx+4,1,sz,Matter.BTRY);
        w.put(cx+5,1,sz,Matter.PSCN);
        w.put(cx+6,1,sz,Matter.SWCH);
        w.put(cx+7,1,sz,Matter.METAL);
        w.put(cx+8,1,sz,Matter.METAL);
        w.put(cx+9,1,sz,Matter.LCRY);

        // Circuit B: INST carries the pulse instantly between PSCN and NSCN.
        w.put(cx-10,1,8,Matter.BTRY);
        w.put(cx-9,1,8,Matter.PSCN);
        for(int x=cx-8;x<=cx-4;x++)w.put(x,1,8,Matter.INST);
        w.put(cx-3,1,8,Matter.NSCN);
        w.put(cx-2,1,8,Matter.LCRY);
    }
}
