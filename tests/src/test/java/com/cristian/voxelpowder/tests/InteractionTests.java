package com.cristian.voxelpowder.tests;

import com.cristian.voxelpowder.core.*;
import com.cristian.voxelpowder.standalone.*;

/** DDA face-hit, reach, player placement, and chunk mesh invalidation checks. */
public final class InteractionTests {
    private static int checks=0;
    private static void ok(boolean c,String message) { if(!c)throw new AssertionError(message);checks++; }
    private static void near(double actual,double expected,String message) {
        ok(Math.abs(actual-expected)<1e-8,message+" expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        VoxelWorld world=new VoxelWorld(32,18,32);
        world.put(12,3,12,Matter.STONE);
        FirstPersonCamera camera=new FirstPersonCamera(12.5,3.5,15.5,Math.PI,0);
        VoxelRaycast.Hit hit=VoxelRaycast.cast(world,camera,5);
        ok(hit!=null,"Aimed voxel should be targeted");
        ok(hit.x==12 && hit.y==3 && hit.z==12,"First occupied cell must be selected");
        ok(hit.nx==0 && hit.ny==0 && hit.nz==1,"Looking north at voxel's south face");
        ok(hit.placeX()==12 && hit.placeY()==3 && hit.placeZ()==13,"Place in adjacent cell");
        near(hit.distance,2.5,"Precise boundary distance");
        ok(VoxelRaycast.cast(world,camera,2.49)==null,"Reach limit rejects distant target");
        camera.yaw=Math.PI/2;camera.x=9.0;camera.z=12.5;
        hit=VoxelRaycast.cast(world,camera,5);
        ok(hit!=null && hit.nx==-1 && hit.x==12,"Side face normal is exact");
        ok(hit.placeX()==11,"New voxel is placed against west face");
        camera=new FirstPersonCamera(12.5,6.0,12.5,0,-Math.PI/2+0.0001);
        hit=VoxelRaycast.cast(world,camera,5);
        ok(hit!=null && hit.ny==1,"Downward hit selects upper face");
        ok(hit.placeY()==4,"Place above surface");
        camera=new FirstPersonCamera(12.5,3.5,15.5,Math.PI,0);
        world.put(12,3,14,Matter.WOOD);
        hit=VoxelRaycast.cast(world,camera,5);
        ok(hit!=null && hit.z==14,"Nearest block wins over far block");
        PlayerController player=new PlayerController();
        player.x=12.5;player.z=13.5;player.feetY=3.0;
        ok(player.intersectsCell(12,3,13),"Player collision prevents inside placement");
        ok(!player.intersectsCell(12,3,11),"Placement away from player permitted");

        // Mesh caching must refresh faces when changing block at a 16x16x16 border.
        VoxelWorld mesh=new VoxelWorld(32,18,32);
        mesh.put(15,1,4,Matter.STONE);
        VoxelRenderer renderer=new VoxelRenderer();
        renderer.draw(mesh,new FirstPersonCamera(16.5,2.2,8,Math.PI,0),480,320);
        ok(renderer.cachedFaces()==6,"Initial single cube has six exposed faces");
        long first=mesh.chunkVersion(1,0,0);
        mesh.put(16,1,4,Matter.STONE);
        ok(mesh.chunkVersion(1,0,0)>first,"Neighbor chunk gets dirty on cross-border edit");
        renderer.draw(mesh,new FirstPersonCamera(16.5,2.2,8,Math.PI,0),480,320);
        ok(renderer.cachedFaces()==10,"Adjacent solid blocks hide two internal faces");
        mesh.put(16,1,4,Matter.AIR);
        renderer.draw(mesh,new FirstPersonCamera(16.5,2.2,8,Math.PI,0),480,320);
        ok(renderer.cachedFaces()==6,"Destroying cell restores exposed boundary");
        mesh.put(16,1,4,Matter.WATER);
        renderer.draw(mesh,new FirstPersonCamera(16.5,2.2,8,Math.PI,0),480,320);
        ok(renderer.cachedFaces()>=10,"Opaque block side remains exposed next to water");
        System.out.println("PASS: "+checks+" raycast, placement and chunk mesh assertions");
    }
}
