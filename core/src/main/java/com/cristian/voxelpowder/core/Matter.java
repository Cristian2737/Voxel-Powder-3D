package com.cristian.voxelpowder.core;

/** Independent element IDs shared by the standalone viewer and the Forge mod. */
public enum Matter {
    AIR(0, "Air", 0x000000, 0, Kind.STATIC),
    WALL(1, "Vanilla wall", 0x52565d, 1000, Kind.STATIC),
    SAND(2, "Sand", 0xe8d59b, 65, Kind.POWDER),
    WATER(3, "Water", 0x2989e7, 30, Kind.LIQUID),
    STONE(4, "Stone", 0x8b8b8f, 90, Kind.STATIC),
    WOOD(5, "Wood", 0x8b582f, 40, Kind.STATIC),
    FIRE(6, "Fire", 0xff9c33, 1, Kind.GAS),
    SMOKE(7, "Smoke", 0x707477, 2, Kind.GAS),
    STEAM(8, "Steam", 0xd4ebf0, 3, Kind.GAS),
    OIL(9, "Oil", 0x38313a, 20, Kind.LIQUID),
    LAVA(10, "Lava", 0xe65d16, 75, Kind.LIQUID),
    METAL(11, "Metal", 0xc1c8d1, 100, Kind.STATIC),
    ICE(12, "Ice", 0x8dcdeb, 45, Kind.STATIC),
    ACID(13, "Acid", 0x8bdf35, 32, Kind.LIQUID),
    GUNPOWDER(14, "Gunpowder", 0x585257, 60, Kind.POWDER),
    BTRY(15, "BTRY - Battery", 0xb89b46, 100, Kind.STATIC),
    PSCN(16, "PSCN - Positive Silicon", 0xf6a5b4, 100, Kind.STATIC),
    NSCN(17, "NSCN - Negative Silicon", 0x659dcc, 100, Kind.STATIC),
    SWCH(18, "SWCH - Switch", 0x176746, 100, Kind.STATIC),
    INST(19, "INST - Instant Conductor", 0x45453e, 100, Kind.STATIC),
    INWR(20, "INWR - Insulated Wire", 0x745657, 100, Kind.STATIC),
    LCRY(21, "LCRY - Liquid Crystal", 0x6547a8, 100, Kind.STATIC),
    SPRK(22, "SPRK - Spark Tool", 0xffee57, 0, Kind.STATIC);

    public enum Kind { STATIC, POWDER, LIQUID, GAS }
    public final int id, color, density;
    public final String displayName;
    public final Kind kind;
    Matter(int id, String displayName, int color, int density, Kind kind) {
        this.id=id; this.displayName=displayName; this.color=color; this.density=density; this.kind=kind;
    }
    public static Matter fromId(int id) {
        for (Matter m : values()) if (m.id==id) return m;
        return AIR;
    }
}
