package com.cristian.voxelpowder.standalone;

/** FPS camera: coordinates are Minecraft-like (Y up), yaw 0 looks toward +Z. */
public final class FirstPersonCamera {
    public double x, y, z;
    public double yaw, pitch;
    public FirstPersonCamera(double x, double y, double z, double yaw, double pitch) {
        this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch;
    }
    public void look(double deltaYaw, double deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-Math.toRadians(86), Math.min(Math.toRadians(86), pitch+deltaPitch));
    }
    /** The same horizontal camera basis is used for movement and projection.
     *  Yaw zero looks down +Z, and positive camera-space X is screen-right.
     */
    public double forwardX() { return Math.sin(yaw); }
    public double forwardZ() { return Math.cos(yaw); }
    public double rightX() { return Math.cos(yaw); }
    public double rightZ() { return -Math.sin(yaw); }
    public double dirX() { return forwardX()*Math.cos(pitch); }
    public double dirY() { return Math.sin(pitch); }
    public double dirZ() { return forwardZ()*Math.cos(pitch); }
    /** Return camera-space X/right, Y/up, and positive Z/forward. */
    public void worldToCamera(double wx, double wy, double wz, double[] out) {
        double dx=wx-x, dy=wy-y, dz=wz-z;
        double cp=Math.cos(pitch), sp=Math.sin(pitch);
        double right=rightX()*dx+rightZ()*dz;
        double forward=forwardX()*dx+forwardZ()*dz;
        out[0]=right;
        out[1]=cp*dy-sp*forward;
        out[2]=sp*dy+cp*forward;
    }
}
