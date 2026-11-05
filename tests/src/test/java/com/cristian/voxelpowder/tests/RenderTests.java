package com.cristian.voxelpowder.tests;

import com.cristian.voxelpowder.core.*;
import com.cristian.voxelpowder.standalone.*;
import java.awt.image.BufferedImage;

/** Headless renderer, occlusion, camera and player-collision checks. */
public final class RenderTests {
    private static void ok(boolean passed,String message) {
        if(!passed)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int count=0;
        VoxelWorld v=new VoxelWorld(8,8,8);
        v.put(4,1,4,Matter.STONE);
        ok(VoxelRenderer.countExposedFaces(v)==6,"Single isolated voxel should have six exposed faces");count++;
        v.put(5,1,4,Matter.STONE);
        ok(VoxelRenderer.countExposedFaces(v)==10,"Adjacent cubes must have two hidden interior faces");count++;
        ok(!VoxelRenderer.exposed(v,4,1,4,1) && !VoxelRenderer.exposed(v,5,1,4,0),"Shared voxel faces are culled");count++;
        FirstPersonCamera cam=new FirstPersonCamera(4.5,2.5,7,Math.PI,0);
        ok(VoxelRenderer.frontFacing(4,1,4,5,cam),"South face points toward camera");count++;
        ok(!VoxelRenderer.frontFacing(4,1,4,4,cam),"North face is a backface");count++;
        cam=new FirstPersonCamera(4.5,2.0,7,Math.PI,0);
        ok(Math.abs(cam.dirZ()+1)<1e-6,"Yaw PI faces toward negative Z");count++;
        double[] xyz=new double[3];cam.worldToCamera(4.5,2.0,4.0,xyz);
        ok(xyz[2]>2.9 && Math.abs(xyz[0])<1e-6,"Camera transform points ahead");count++;
        VoxelRenderer renderer=new VoxelRenderer();
        BufferedImage img=renderer.draw(v,cam,480,320);
        ok(img!=null && renderer.drawnFaces>=1 && renderer.triangles>=2,"Renderer draws a visible voxel");count++;
        ok(renderer.backfaces>=1 && renderer.hiddenFaces>=2,"Renderer actually rejects backfaces and hidden geometry");count++;
        int visiblePixels=0;
        for(int yy=0;yy<img.getHeight();yy++)for(int xx=0;xx<img.getWidth();xx++)
            if(img.getRGB(xx,yy)>>>24 !=0)visiblePixels++;
        ok(visiblePixels>10,"Perspective rasterizer draws color pixels");count++;
        // Collision: player can move on an unobstructed floor but not through a wall.
        VoxelWorld room=new VoxelWorld(8,8,8);
        for(int xx=0;xx<8;xx++)for(int zz=0;zz<8;zz++)room.put(xx,0,zz,Matter.STONE);
        for(int yy=1;yy<4;yy++)room.put(4,yy,3,Matter.STONE);
        PlayerController p=new PlayerController();p.x=4.5;p.z=5.2;p.feetY=1.01;
        FirstPersonCamera pc=new FirstPersonCamera(p.x,2.59,p.z,Math.PI,0);
        for(int i=0;i<35;i++)p.step(room,pc,true,false,false,false,false,false,false,.033);
        ok(p.z>3.2 && p.z<5.2,"Player approaches wall without passing through it");count++;
        ok(p.feetY>=.99 && p.feetY<1.05,"Player stands on the solid floor");count++;
        // World is actually traversable without trapping spawn in another voxel.
        VoxelWorld walk=new VoxelWorld(32,18,32);WalkableScenes.create(walk);
        PlayerController spawn=new PlayerController();spawn.respawn(walk);
        ok(!spawn.overlapsSolid(walk,spawn.x,spawn.feetY,spawn.z),"Spawn is clear of solid cells");count++;
        System.out.println("PASS: "+count+" renderer, camera, culling, collision and world tests");
        System.out.println("Faces rendered in sample: "+renderer.drawnFaces+
            ", hidden: "+renderer.hiddenFaces+", backfaces: "+renderer.backfaces);
    }
}
