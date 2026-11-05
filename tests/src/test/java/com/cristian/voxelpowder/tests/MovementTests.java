package com.cristian.voxelpowder.tests;

import com.cristian.voxelpowder.core.Matter;
import com.cristian.voxelpowder.core.VoxelWorld;
import com.cristian.voxelpowder.standalone.FirstPersonCamera;
import com.cristian.voxelpowder.standalone.PlayerController;

/** Regression tests for accidental use of camera height instead of camera heading. */
public final class MovementTests {
    private static final double EPS = 1e-9;
    private static int cases = 0;

    private static void near(double actual, double expected, String reason) {
        if (Math.abs(actual-expected) > EPS) {
            throw new AssertionError(reason + " expected " + expected + " got " + actual);
        }
        cases++;
    }

    private static void ok(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        cases++;
    }

    private static VoxelWorld flatRoom() {
        VoxelWorld room = new VoxelWorld(32, 12, 32);
        for (int x=0; x<room.width; x++)
            for (int z=0; z<room.depth; z++) room.put(x, 0, z, Matter.STONE);
        return room;
    }

    private static void verifyInput(double yaw,double pitch,int forward,int right) {
        VoxelWorld world = flatRoom();
        PlayerController p = new PlayerController();
        p.x=16.5; p.z=16.5; p.feetY=1.01;
        FirstPersonCamera camera = new FirstPersonCamera(p.x, p.feetY+PlayerController.EYE,p.z,yaw,pitch);
        double oldX = p.x, oldZ = p.z;
        double inputLength=Math.hypot(forward,right);
        p.step(world, camera,forward>0,forward<0,right<0,right>0,false,false,false,.03);
        double dx = p.x-oldX, dz=p.z-oldZ;
        double projectedRight=dx*camera.rightX()+dz*camera.rightZ();
        double projectedForward=dx*camera.forwardX()+dz*camera.forwardZ();
        double expectedRight = inputLength==0?0:(right/inputLength)*4.317*.03;
        double expectedForward = inputLength==0?0:(forward/inputLength)*4.317*.03;
        near(projectedRight,expectedRight, "Camera-relative strafe yaw="+yaw+" pitch="+pitch+" input="+right+","+forward);
        near(projectedForward,expectedForward,"Camera-relative forward yaw="+yaw+" pitch="+pitch+" input="+right+","+forward);
        ok(!p.movementBlocked,"Empty room should never block movement");
    }

    public static void main(String[] args) {
        // All 8 directions plus idle, at nine headings and five pitch angles.
        double[] yawAngles = {0,Math.PI/8,Math.PI/4,Math.PI/2,Math.PI,3*Math.PI/2,
                              -Math.PI/3,2*Math.PI + .4,-4.0};
        double[] pitches = {-1.2,-.5,0,.5,1.2};
        for (double yaw : yawAngles)
            for (double pitch : pitches)
                for (int f=-1;f<=1;f++)
                    for (int r=-1;r<=1;r++) verifyInput(yaw,pitch,f,r);

        // Regression: Changing the camera's height MUST NOT change sideways movement.
        VoxelWorld world = flatRoom();
        double firstDx=Double.NaN, firstDz=Double.NaN;
        for(double eyeHeight: new double[] {1.9,2.59,4.2,8.0}) {
            PlayerController p=new PlayerController();p.x=16.5;p.z=16.5;p.feetY=1.01;
            FirstPersonCamera c=new FirstPersonCamera(p.x,eyeHeight,p.z,Math.PI,.32);
            p.step(world,c,false,false,false,true,false,false,false,.03);
            if(Double.isNaN(firstDx)) {firstDx=p.x-16.5;firstDz=p.z-16.5;}
            near(p.x-16.5,firstDx,"Eye height cannot change strafe X");
            near(p.z-16.5,firstDz,"Eye height cannot change strafe Z");
        }

        // Minecraft-like wall sliding clips the blocked axis while keeping the other.
        VoxelWorld wallRoom=flatRoom();
        for(int y=1;y<=3;y++)for(int z=1;z<wallRoom.depth-1;z++)wallRoom.put(4,y,z,Matter.STONE);
        PlayerController strafe=new PlayerController();
        strafe.x=3.69;strafe.z=8.5;strafe.feetY=1.01;
        FirstPersonCamera c=new FirstPersonCamera(strafe.x,strafe.feetY+PlayerController.EYE,strafe.z,Math.PI/4,0);
        double beforeZ=strafe.z;
        strafe.step(wallRoom,c,false,false,false,true,false,false,false,.065);
        ok(strafe.movementBlocked,"Wall collision should register");
        ok(strafe.x+PlayerController.RADIUS < 4.01,"AABB must not enter wall");
        ok(strafe.z < beforeZ-.01,"Unblocked axis should slide along wall");

        // Java-style CTRL sprint and SHIFT sneak speed check.
        PlayerController walker=new PlayerController();walker.x=16.5;walker.z=16.5;walker.feetY=1.01;
        FirstPersonCamera walkerCam=new FirstPersonCamera(walker.x,2.63,walker.z,0,0);
        walker.step(world,walkerCam,true,false,false,false,false,false,false,.025);
        near(walker.z-16.5,4.317*.025,"Walking speed");
        PlayerController runner=new PlayerController();runner.x=16.5;runner.z=16.5;runner.feetY=1.01;
        FirstPersonCamera runnerCam=new FirstPersonCamera(runner.x,2.63,runner.z,0,0);
        runner.step(world,runnerCam,true,false,false,false,false,false,true,.025);
        near(runner.z-16.5,5.612*.025,"CTRL sprint speed");
        PlayerController sneak=new PlayerController();sneak.x=16.5;sneak.z=16.5;sneak.feetY=1.01;
        FirstPersonCamera sneakCam=new FirstPersonCamera(sneak.x,2.63,sneak.z,0,0);
        sneak.step(world,sneakCam,true,false,false,false,true,false,false,.025);
        near(sneak.z-16.5,1.30*.025,"SHIFT sneak speed");
        near(sneakCam.y,sneak.feetY+PlayerController.CROUCH_EYE,"Sneak eye lowered");
        ok(sneak.sneaking,"SHIFT engages crouch");
        System.out.println("PASS: " + cases + " movement and camera-alignment assertions");
    }
}
