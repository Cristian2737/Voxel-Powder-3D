package com.cristian.voxelpowder.tests;

import com.cristian.voxelpowder.core.*;

/** Standalone invariants, runnable without Gradle or Minecraft. */
public final class CoreTests {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int passed=0;
        VoxelWorld sand=new VoxelWorld(7,8,7);
        sand.put(3,5,3,Matter.SAND);
        new Simulator(13).step(sand,200);
        check(sand.get(3,4,3)==Matter.SAND,"Sand must fall one voxel");passed++;

        VoxelWorld wall=new VoxelWorld(7,8,7);
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)wall.put(x,0,z,Matter.STONE);
        wall.put(3,1,3,Matter.WATER);
        Simulator fluid=new Simulator(17);
        for(int t=0;t<10;t++)fluid.step(wall,100);
        int waterCount=0;
        for(int i=0;i<wall.size();i++)if(wall.at(i)==Matter.WATER)waterCount++;
        check(waterCount==1,"Water must be conserved during flow");passed++;

        VoxelWorld chemistry=new VoxelWorld(7,8,7);
        chemistry.put(3,3,3,Matter.WATER);chemistry.put(4,3,3,Matter.LAVA);
        new Simulator(22).step(chemistry,200);
        boolean changed=chemistry.get(3,3,3)!=Matter.WATER || chemistry.get(4,3,3)!=Matter.LAVA;
        check(changed,"Contact with lava must trigger a phase reaction");passed++;

        VoxelWorld solid=new VoxelWorld(7,8,7);
        solid.put(3,3,3,Matter.WALL);
        solid.put(3,6,3,Matter.SAND);
        for(int i=0;i<100;i++)new Simulator(i).step(solid,100);
        check(solid.get(3,3,3)==Matter.WALL,"Vanilla barrier must remain intact");passed++;

        VoxelWorld save=new VoxelWorld(7,8,7);save.put(2,4,1,Matter.ACID);save.put(4,2,6,Matter.FIRE);
        byte[] bytes=save.exportMatter();int[] temp=save.exportTemperatures(),age=save.exportAges();
        VoxelWorld restored=new VoxelWorld(7,8,7);restored.restore(bytes,temp,age);
        check(restored.get(2,4,1)==Matter.ACID && restored.get(4,2,6)==Matter.FIRE,"Save round-trip");passed++;

        VoxelWorld ball=new VoxelWorld(7,8,7);ball.paintBall(3,3,3,2,Matter.OIL);
        check(ball.get(3,3,3)==Matter.OIL && ball.get(0,0,0)==Matter.AIR,"3D brush geometry");passed++;
        System.out.println("PASS: "+passed+" Java simulation smoke tests");
    }
}
