package com.cristian.voxelpowder.standalone;

import com.cristian.voxelpowder.core.Matter;
import com.cristian.voxelpowder.core.VoxelWorld;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Real perspective rendering with camera-facing backface rejection, hidden-face culling,
 *  clipping against the near plane, and a software Z-buffer (no external libraries).
 */
public final class VoxelRenderer {
    public static final int[][] DIR={ {-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1} };
    private static final int[][][] CORNERS={
        {{0,0,1},{0,0,0},{0,1,0},{0,1,1}},  // west
        {{1,0,0},{1,0,1},{1,1,1},{1,1,0}},  // east
        {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},  // bottom
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}},  // top
        {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},  // north
        {{0,0,1},{1,0,1},{1,1,1},{0,1,1}}   // south
    };
    private static final double NEAR=.075;
    private BufferedImage image;
    private int[] pixels;
    private float[] depth;
    private int width, height;
    public int solidCells, hiddenFaces, backfaces, drawnFaces, triangles, drawnPixels;
    private double focal;
    private VoxelWorld meshWorld;
    private MeshChunk[][][] meshChunks;
    private static final class Face {
        final int x,y,z,side;
        final Matter material;
        Face(int x,int y,int z,int side,Matter m) {this.x=x;this.y=y;this.z=z;this.side=side;material=m;}
    }
    private static final class MeshChunk {
        long revision=-1;
        final List<Face> faces=new ArrayList<Face>();
        int solids, occluded;
    }
    private void prepareChunks(VoxelWorld world) {
        if(meshWorld!=world || meshChunks==null || meshChunks.length!=world.chunksY()) {
            meshWorld=world;
            meshChunks=new MeshChunk[world.chunksY()][world.chunksZ()][world.chunksX()];
        }
        for(int cy=0;cy<world.chunksY();cy++)for(int cz=0;cz<world.chunksZ();cz++)for(int cx=0;cx<world.chunksX();cx++) {
            MeshChunk m=meshChunks[cy][cz][cx];
            if(m==null) {m=new MeshChunk();meshChunks[cy][cz][cx]=m;}
            long rev=world.chunkVersion(cx,cy,cz);
            if(m.revision==rev)continue;
            m.faces.clear();m.solids=0;m.occluded=0;
            for(int y=cy*16;y<Math.min(world.height,cy*16+16);y++)
                for(int z=cz*16;z<Math.min(world.depth,cz*16+16);z++)
                    for(int x=cx*16;x<Math.min(world.width,cx*16+16);x++) {
                        Matter material=world.get(x,y,z);
                        if(material==Matter.AIR || material==Matter.WALL)continue;
                        m.solids++;
                        for(int f=0;f<6;f++) {
                            if(exposed(world,x,y,z,f))m.faces.add(new Face(x,y,z,f,material));
                            else m.occluded++;
                        }
                    }
            m.revision=rev;
        }
    }
    public int cachedFaces() {
        if(meshChunks==null)return 0;
        int n=0;
        for(MeshChunk[][] a:meshChunks)for(MeshChunk[] b:a)for(MeshChunk c:b)if(c!=null)n+=c.faces.size();
        return n;
    }

    private void resize(int w,int h) {
        if(image==null || width!=w || height!=h) {
            width=w;height=h;
            image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            depth=new float[w*h];
        }
    }
    private static boolean seeThrough(Matter m) {
        return m==Matter.AIR || m==Matter.WATER || m==Matter.OIL || m==Matter.LAVA ||
            m==Matter.ACID || m==Matter.SMOKE || m==Matter.STEAM || m==Matter.FIRE;
    }
    public static boolean exposed(VoxelWorld world,int x,int y,int z,int face) {
        int[] d=DIR[face];
        Matter current=world.get(x,y,z), neighbor=world.get(x+d[0],y+d[1],z+d[2]);
        if(neighbor==Matter.WALL || neighbor==current)return false;
        return seeThrough(neighbor);
    }
    public static boolean frontFacing(int x,int y,int z,int face,FirstPersonCamera camera) {
        int[] d=DIR[face];
        return d[0]*(camera.x-(x+.5+d[0]*.5))+
               d[1]*(camera.y-(y+.5+d[1]*.5))+
               d[2]*(camera.z-(z+.5+d[2]*.5))>0.000001;
    }
    public static int countExposedFaces(VoxelWorld world) {
        int total=0;
        for(int y=0;y<world.height;y++)for(int z=0;z<world.depth;z++)for(int x=0;x<world.width;x++) {
            if(world.get(x,y,z)==Matter.AIR)continue;
            for(int f=0;f<6;f++) if(exposed(world,x,y,z,f))total++;
        }
        return total;
    }
    private static final class V3 {
        double x,y,z;
        V3(double xx,double yy,double zz){x=xx;y=yy;z=zz;}
    }
    private static V3 interpolate(V3 a,V3 b,double t) {
        return new V3(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,NEAR);
    }
    private static int colorFor(int base,int face,double distance) {
        double faceLight=face==3?1.04:face==2?.55:face==0?.72:face==1?.85:face==4?.8:.95;
        double fog=Math.min(.50,Math.max(0,distance-12)*.014);
        int r=(base>>16)&255, g=(base>>8)&255, b=base&255;
        r=(int)Math.min(255,r*faceLight*(1-fog)+fog*21);
        g=(int)Math.min(255,g*faceLight*(1-fog)+fog*29);
        b=(int)Math.min(255,b*faceLight*(1-fog)+fog*42);
        return 0xFF000000 | (r<<16) | (g<<8) | b;
    }
    public BufferedImage draw(VoxelWorld world,FirstPersonCamera camera,int w,int h) {
        resize(Math.max(1,w),Math.max(1,h));
        Arrays.fill(pixels,0);
        Arrays.fill(depth,0f); // larger inverse Z means closer
        solidCells=hiddenFaces=backfaces=drawnFaces=triangles=drawnPixels=0;
        focal=(height*.5)/Math.tan(Math.toRadians(73)/2);
        // Minecraft-inspired per-16^3 region mesh caching. Geometry only regenerates
        // when block types change in that region (or across its borders).
        prepareChunks(world);
        for(MeshChunk[][] row:meshChunks)for(MeshChunk[] layer:row)for(MeshChunk mesh:layer) {
            solidCells+=mesh.solids;
            hiddenFaces+=mesh.occluded;
            for(Face entry:mesh.faces) {
                int x=entry.x,y=entry.y,z=entry.z,f=entry.side;
                Matter material=entry.material;
                if(!frontFacing(x,y,z,f,camera)){backfaces++;continue;}
                int[][] corner=CORNERS[f];
                V3[] poly=new V3[7];
                for(int v=0;v<4;v++) {
                    double[] xyz=new double[3];
                    camera.worldToCamera(x+corner[v][0],y+corner[v][1],z+corner[v][2],xyz);
                    poly[v]=new V3(xyz[0],xyz[1],xyz[2]);
                }
                int count=4;
                V3[] clipped=new V3[8];int out=0;
                for(int i=0;i<count;i++) {
                    V3 a=poly[i],b=poly[(i+1)%count];
                    boolean inA=a.z>=NEAR, inB=b.z>=NEAR;
                    if(inA && inB)clipped[out++]=b;
                    else if(inA && !inB)clipped[out++]=interpolate(a,b,(NEAR-a.z)/(b.z-a.z));
                    else if(!inA && inB) {
                        clipped[out++]=interpolate(a,b,(NEAR-a.z)/(b.z-a.z));
                        clipped[out++]=b;
                    }
                }
                if(out<3)continue;
                drawnFaces++;
                double dist=Math.sqrt((x+.5-camera.x)*(x+.5-camera.x)+
                                      (y+.5-camera.y)*(y+.5-camera.y)+
                                      (z+.5-camera.z)*(z+.5-camera.z));
                int index=world.index(x,y,z);
                int baseColor=material.color;
                if(world.energized(index)) {
                    if(material==Matter.LCRY)baseColor=0xf5adff;
                    else if(material!=Matter.BTRY)baseColor=0xffdc69;
                } else if(material==Matter.SWCH && world.switchOnAt(index)) {
                    baseColor=0x4bec91;
                }
                int rgb=colorFor(baseColor,f,dist);
                for(int i=1;i<out-1;i++) rasterize(clipped[0],clipped[i],clipped[i+1],rgb);
            }
        }
        return image;
    }
    private void rasterize(V3 a,V3 b,V3 c,int rgb) {
        triangles++;
        // Perspective projection. Downscreen is negative camera-space Y.
        double x0=width*.5+focal*a.x/a.z, y0=height*.5-focal*a.y/a.z;
        double x1=width*.5+focal*b.x/b.z, y1=height*.5-focal*b.y/b.z;
        double x2=width*.5+focal*c.x/c.z, y2=height*.5-focal*c.y/c.z;
        if(!Double.isFinite(x0) || !Double.isFinite(x1) || !Double.isFinite(x2))return;
        double minX=Math.min(x0,Math.min(x1,x2)),maxX=Math.max(x0,Math.max(x1,x2));
        double minY=Math.min(y0,Math.min(y1,y2)),maxY=Math.max(y0,Math.max(y1,y2));
        if(maxX<0||maxY<0||minX>=width||minY>=height)return;
        int left=(int)Math.max(0,Math.ceil(minX-.5));
        int right=(int)Math.min(width-1,Math.floor(maxX-.5));
        int top=(int)Math.max(0,Math.ceil(minY-.5));
        int bottom=(int)Math.min(height-1,Math.floor(maxY-.5));
        double area=(x1-x0)*(y2-y0)-(y1-y0)*(x2-x0);
        if(Math.abs(area)<1e-9)return;
        double invArea=1.0/area;
        float d0=(float)(1/a.z),d1=(float)(1/b.z),d2=(float)(1/c.z);
        for(int py=top;py<=bottom;py++) {
            double fy=py+.5;
            int base=py*width;
            for(int px=left;px<=right;px++) {
                double fx=px+.5;
                double q0=((x1-fx)*(y2-fy)-(y1-fy)*(x2-fx))*invArea;
                double q1=((x2-fx)*(y0-fy)-(y2-fy)*(x0-fx))*invArea;
                double q2=1-q0-q1;
                if(q0 < -1e-6 || q1 < -1e-6 || q2 < -1e-6)continue;
                float d=(float)(q0*d0+q1*d1+q2*d2);
                int idx=base+px;
                if(d>depth[idx]) {
                    depth[idx]=d;pixels[idx]=rgb;drawnPixels++;
                }
            }
        }
    }
}
