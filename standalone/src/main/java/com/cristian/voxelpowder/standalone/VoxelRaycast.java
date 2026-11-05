package com.cristian.voxelpowder.standalone;

import com.cristian.voxelpowder.core.Matter;
import com.cristian.voxelpowder.core.VoxelWorld;

/** Voxel boundary traversal (3D DDA): exact first surface and adjacent face. */
public final class VoxelRaycast {
    private VoxelRaycast() {}
    public static final class Hit {
        public final int x,y,z,nx,ny,nz;
        public final double distance;
        Hit(int x,int y,int z,int nx,int ny,int nz,double distance) {
            this.x=x;this.y=y;this.z=z;
            this.nx=nx;this.ny=ny;this.nz=nz;this.distance=distance;
        }
        public int placeX() { return x+nx; }
        public int placeY() { return y+ny; }
        public int placeZ() { return z+nz; }
    }
    private static double firstBoundary(double pos,int cell,double direction) {
        return direction > 0 ? (cell+1-pos)/direction :
            direction < 0 ? (pos-cell)/(-direction) : Double.POSITIVE_INFINITY;
    }
    public static Hit cast(VoxelWorld w,FirstPersonCamera c,double reach) {
        if(reach<=0)return null;
        double dx=c.dirX(),dy=c.dirY(),dz=c.dirZ();
        int x=(int)Math.floor(c.x),y=(int)Math.floor(c.y),z=(int)Math.floor(c.z);
        int sx=dx>0?1:dx<0?-1:0, sy=dy>0?1:dy<0?-1:0, sz=dz>0?1:dz<0?-1:0;
        double tx=firstBoundary(c.x,x,dx),ty=firstBoundary(c.y,y,dy),tz=firstBoundary(c.z,z,dz);
        double dxT=sx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx);
        double dyT=sy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy);
        double dzT=sz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        if(w.inside(x,y,z) && w.get(x,y,z)!=Matter.AIR) return new Hit(x,y,z,0,0,0,0);
        double t=0;
        for(int i=0;i<200 && t<=reach;i++) {
            int nx=0,ny=0,nz=0;
            if(tx<=ty && tx<=tz) {t=tx;x+=sx;nx=-sx;tx+=dxT;}
            else if(ty<=tz) {t=ty;y+=sy;ny=-sy;ty+=dyT;}
            else {t=tz;z+=sz;nz=-sz;tz+=dzT;}
            if(t>reach+1e-8 || !w.inside(x,y,z)) return null;
            if(w.get(x,y,z)!=Matter.AIR) return new Hit(x,y,z,nx,ny,nz,t);
        }
        return null;
    }
}
