package com.cristian.voxelpowder.core;

import java.util.ArrayDeque;

/**
 * Original 3D pulse network inspired by TPT's electronics (no copied TPT code).
 * Face adjacency, non-instant propagating pulses, 4-tick charge and 3-tick refractory.
 * INST is special: PSCN (or the spark tool) energizes an entire face-connected region
 * in one step; output is delivered only to NSCN. No air/pressure simulation required.
 */
public final class Electricity {
    private Electricity() {}
    private static final int[][] D={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final int PULSE=4, REFRACTORY=3;

    public static boolean isWire(Matter m) {
        return m==Matter.METAL || m==Matter.PSCN || m==Matter.NSCN ||
               m==Matter.SWCH || m==Matter.INST || m==Matter.INWR;
    }
    /** Spark tool: energize an existing conductor. Nothing is placed or destroyed. */
    public static boolean spark(VoxelWorld w,int x,int y,int z) {
        if(!w.inside(x,y,z))return false;
        int i=w.index(x,y,z);Matter m=w.at(i);
        if(!isWire(m) || (m==Matter.SWCH&&!w.switchOnAt(i)))return false;
        if(w.pulseAt(i)>0 || w.refractoryAt(i)>0)return false;
        w.setPulse(i,PULSE);
        return true;
    }
    private static boolean passes(Matter from,Matter to,boolean destSwitchOn) {
        if(to==Matter.BTRY || to==Matter.SPRK)return false;
        if(to==Matter.LCRY)return from!=Matter.INST;
        if(to==Matter.SWCH && !destSwitchOn)return false;
        if(from==Matter.INST)return to==Matter.NSCN;
        if(to==Matter.INST)return from==Matter.PSCN;
        if(from==Matter.NSCN && to==Matter.PSCN)return false;
        if(from==Matter.INWR)return to==Matter.INWR || to==Matter.PSCN || to==Matter.NSCN || to==Matter.SWCH;
        if(to==Matter.INWR)return from==Matter.INWR || from==Matter.PSCN || from==Matter.NSCN || from==Matter.SWCH;
        return isWire(to);
    }
    private static void seedInstant(VoxelWorld w,boolean[] pending,int start,int[] remaining) {
        ArrayDeque<Integer> queue=new ArrayDeque<Integer>();
        queue.add(start);
        pending[start]=true;
        while(!queue.isEmpty() && remaining[0]>0) {
            int i=queue.removeFirst();
            remaining[0]--;
            int x=i%w.width,z=(i/w.width)%w.depth,y=i/(w.width*w.depth);
            for(int[] d:D) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                if(!w.inside(nx,ny,nz))continue;
                int j=w.index(nx,ny,nz);
                if(w.at(j)==Matter.INST && w.pulseAt(j)==0 && w.refractoryAt(j)==0 && !pending[j]) {
                    pending[j]=true;queue.addLast(j);
                }
            }
        }
    }
    public static int step(VoxelWorld w,int maxUpdates) {
        int n=w.size(),limit=Math.max(1,maxUpdates);
        boolean[] next=new boolean[n], nextCrystal=new boolean[n];
        byte[] toggle=new byte[n]; // 1=enable with PSCN, 2=disable with NSCN
        int[][] offsets=D;
        // Read all old pulse states before mutating them. One unit of normal-wire
        // distance is crossed per tick; activation never cascades mid-update.
        for(int y=0;y<w.height;y++)for(int z=0;z<w.depth;z++)for(int x=0;x<w.width;x++) {
            int i=w.index(x,y,z);Matter from=w.at(i);
            if(from!=Matter.BTRY && w.pulseAt(i)==0)continue;
            if(from==Matter.SWCH && !w.switchOnAt(i))continue;
            if(from==Matter.LCRY)continue;
            for(int[] d:offsets) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                if(!w.inside(nx,ny,nz))continue;
                int j=w.index(nx,ny,nz);Matter to=w.at(j);
                if(to==Matter.SWCH) {
                    if(from==Matter.PSCN && toggle[j]!=2)toggle[j]=1;
                    if(from==Matter.NSCN)toggle[j]=2; // explicit OFF always wins
                }
                if(to==Matter.LCRY) {
                    if(from!=Matter.INST)nextCrystal[j]=true;
                } else if(to==Matter.INST && from==Matter.INST) {
                    next[j]=true; // manual spark spreads instantly through an INST component
                } else if(passes(from,to,w.switchOnAt(j))) next[j]=true;
            }
        }
        // One-tick-off / cooldown prevents the battery continuously recharging
        // the same conductor, and gives a visible traveling wave.
        int updates=0;
        for(int i=0;i<n;i++) {
            int before=w.pulseAt(i),cool=w.refractoryAt(i);
            if(before>0) {
                w.setPulse(i,before-1);
                if(before==1)w.setRefractory(i,REFRACTORY);
            } else if(cool>0)w.setRefractory(i,cool-1);
            if(toggle[i]!=0 && w.at(i)==Matter.SWCH && updates<limit) {
                boolean on=toggle[i]==1;
                if(w.switchOnAt(i)!=on) {
                    w.setSwitchOn(i,on);
                    if(!on)w.setPulse(i,0);
                    updates++;
                }
            }
        }
        // Fold instant-conductor components into the pending set (bounded traversal).
        int[] budget={limit};
        for(int i=0;i<n&&budget[0]>0;i++)
            if(next[i]&&w.at(i)==Matter.INST&&w.pulseAt(i)==0&&w.refractoryAt(i)==0)
                seedInstant(w,next,i,budget);
        for(int i=0;i<n && updates<limit;i++) {
            if(w.at(i)==Matter.LCRY && nextCrystal[i]) {
                if(w.pulseAt(i)<6) {
                    w.setPulse(i,8);
                    updates++;
                }
            } else if(next[i] && w.pulseAt(i)==0 && w.refractoryAt(i)==0 && isWire(w.at(i)) &&
                      (w.at(i)!=Matter.SWCH||w.switchOnAt(i))) {
                w.setPulse(i,PULSE);
                updates++;
            }
        }
        return updates;
    }
}
