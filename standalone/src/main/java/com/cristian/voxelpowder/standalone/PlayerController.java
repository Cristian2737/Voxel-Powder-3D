package com.cristian.voxelpowder.standalone;

import com.cristian.voxelpowder.core.Matter;
import com.cristian.voxelpowder.core.VoxelWorld;

/** Single player with per-axis AABB collision, gravity, sprint, jump and optional flight. */
public final class PlayerController {
    public static final double RADIUS=0.30, HEIGHT=1.80, EYE=1.62;
    public static final double CROUCH_HEIGHT=1.50, CROUCH_EYE=1.27;
    public double x=16.5, feetY=1.01, z=25.0, verticalSpeed=0;
    public boolean grounded=false, flying=false, sneaking=false;
    /** Horizontal movement was stopped by a solid voxel this frame. */
    public boolean movementBlocked=false;

    public void respawn(VoxelWorld world) {
        x=world.width/2.0+0.5; z=Math.min(world.depth-3.5,world.depth/2.0+9.0);
        feetY=1.01;verticalSpeed=0;grounded=false;flying=false;sneaking=false;
    }
    private static boolean collidable(Matter m) {
        return m!=Matter.AIR && m!=Matter.WATER && m!=Matter.OIL && m!=Matter.LAVA &&
            m!=Matter.FIRE && m!=Matter.SMOKE && m!=Matter.STEAM && m!=Matter.ACID;
    }
    public boolean overlapsSolid(VoxelWorld w,double px,double py,double pz) {
        return overlapsSolidAtHeight(w,px,py,pz,sneaking?CROUCH_HEIGHT:HEIGHT);
    }
    private boolean overlapsSolidAtHeight(VoxelWorld w,double px,double py,double pz,double height) {
        final double eps=1e-5;
        for(int xx=(int)Math.floor(px-RADIUS);xx<=(int)Math.floor(px+RADIUS-eps);xx++)
            for(int yy=(int)Math.floor(py);yy<=(int)Math.floor(py+height-eps);yy++)
                for(int zz=(int)Math.floor(pz-RADIUS);zz<=(int)Math.floor(pz+RADIUS-eps);zz++)
                    if(collidable(w.get(xx,yy,zz)))return true;
        return false;
    }
    /** Placement must not intersect the player's full standing/sneaking collision box. */
    public boolean intersectsCell(int bx,int by,int bz) {
        double h=sneaking?CROUCH_HEIGHT:HEIGHT;
        return x+RADIUS>bx && x-RADIUS<bx+1 &&
            feetY+h>by && feetY<by+1 &&
            z+RADIUS>bz && z-RADIUS<bz+1;
    }
    private boolean supported(VoxelWorld world,double cx,double cz) {
        // Center support allows Minecraft-like partial overhang while sneaking.
        return collidable(world.get((int)Math.floor(cx),(int)Math.floor(feetY-.08),(int)Math.floor(cz)));
    }
    private boolean canMove(VoxelWorld world,double nx,double nz) {
        return !overlapsSolid(world,nx,feetY,nz) &&
            (!sneaking || flying || !grounded || supported(world,nx,nz));
    }
    public void step(VoxelWorld world, FirstPersonCamera camera, boolean forwards,boolean backwards,
                     boolean left,boolean right,boolean sneak,boolean jump,boolean sprint,double dt) {
        dt=Math.min(.065,Math.max(0,dt));
        movementBlocked=false;
        // Preserve standing height if there is no room to stand back up.
        if(flying) sneaking=false;
        else if(sneak) sneaking=true;
        else if(!overlapsSolidAtHeight(world,x,feetY,z,HEIGHT)) sneaking=false;
        double moveF=(forwards?1:0)-(backwards?1:0);
        double moveR=(right?1:0)-(left?1:0);
        double length=Math.hypot(moveF,moveR);
        if(length>0) {moveF/=length;moveR/=length;}
        // Minecraft Java's approximate speeds in blocks per second (not frame-dependent).
        double speed=flying?(sprint?13.0:9.0):sneaking?1.30:(sprint && forwards?5.612:4.317);
        double dx=(moveR*camera.rightX()+moveF*camera.forwardX())*speed*dt;
        double dz=(moveR*camera.rightZ()+moveF*camera.forwardZ())*speed*dt;
        int slices=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dz))/.055));
        double stepX=dx/slices,stepZ=dz/slices;
        for(int i=0;i<slices;i++) {
            if(canMove(world,x+stepX,z+stepZ)) {x+=stepX;z+=stepZ;continue;}
            // Minecraft-style sliding: blocked walls do not rotate WASD input;
            // each remaining axis is clipped by the player's actual AABB.
            boolean moved=false;
            if(canMove(world,x+stepX,z)) {x+=stepX;moved=true;}
            if(canMove(world,x,z+stepZ)) {z+=stepZ;moved=true;}
            movementBlocked=true;
            if(!moved)break;
        }
        if(flying) {
            double dy=((jump?1:0)-(sneak?1:0))*speed*dt;
            if(!overlapsSolid(world,x,feetY+dy,z))feetY+=dy;
            verticalSpeed=0;
            grounded=false;
        } else {
            if(jump && grounded && !sneaking) {verticalSpeed=8.1;grounded=false;}
            verticalSpeed=Math.max(-18,verticalSpeed-24*dt);
            double dy=verticalSpeed*dt;
            int slicesY=Math.max(1,(int)Math.ceil(Math.abs(dy)/.06));
            grounded=false;
            for(int i=0;i<slicesY;i++) {
                if(overlapsSolid(world,x,feetY+dy/slicesY,z)) {
                    if(verticalSpeed<=0)grounded=true;
                    verticalSpeed=0;
                    break;
                }
                feetY+=dy/slicesY;
            }
            if(!grounded && Math.abs(verticalSpeed)<.0001 && supported(world,x,z))grounded=true;
        }
        camera.x=x;
        camera.y=feetY+(sneaking?CROUCH_EYE:EYE);
        camera.z=z;
    }
}
