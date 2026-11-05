package com.cristian.voxelpowder.core;

import java.util.Arrays;

/** Fixed-size 3D grid; Forge intentionally maps only bounded chambers into Minecraft blocks. */
public final class VoxelWorld {
    public final int width, height, depth;
    private final Matter[] cells;
    private final short[] temperature;
    private final short[] age;
    // Separate electrical state: a spark is a temporary pulse on its ORIGINAL material.
    private final byte[] pulse, refractory, switchOn;
    private long ticks;
    private final int chunksX,chunksY,chunksZ;
    private final long[] chunkVersion;

    public VoxelWorld(int width, int height, int depth) {
        if (width < 2 || height < 2 || depth < 2 || (long)width*height*depth > 1_000_000L)
            throw new IllegalArgumentException("Invalid simulation dimensions");
        this.width=width; this.height=height; this.depth=depth;
        chunksX=(width+15)/16;chunksY=(height+15)/16;chunksZ=(depth+15)/16;
        chunkVersion=new long[chunksX*chunksY*chunksZ];
        cells = new Matter[width*height*depth];
        temperature=new short[cells.length]; age=new short[cells.length];
        pulse=new byte[cells.length]; refractory=new byte[cells.length]; switchOn=new byte[cells.length];
        Arrays.fill(cells, Matter.AIR);
        Arrays.fill(temperature,(short)20);
    }
    public int size() { return cells.length; }
    public int chunksX() {return chunksX;}
    public int chunksY() {return chunksY;}
    public int chunksZ() {return chunksZ;}
    public long chunkVersion(int cx,int cy,int cz) {
        return chunkVersion[(cy*chunksZ+cz)*chunksX+cx];
    }
    private void touch(int x,int y,int z) {
        if(inside(x,y,z))chunkVersion[((y>>4)*chunksZ+(z>>4))*chunksX+(x>>4)]++;
    }
    private void dirty(int x,int y,int z) {
        touch(x,y,z);
        if((x&15)==0)touch(x-1,y,z);
        if((x&15)==15)touch(x+1,y,z);
        if((y&15)==0)touch(x,y-1,z);
        if((y&15)==15)touch(x,y+1,z);
        if((z&15)==0)touch(x,y,z-1);
        if((z&15)==15)touch(x,y,z+1);
    }
    private void dirtyIndex(int idx) {
        int x=idx%width, z=(idx/width)%depth, y=idx/(width*depth);
        dirty(x,y,z);
    }
    public int index(int x, int y, int z) { return (y*depth+z)*width+x; }
    public boolean inside(int x,int y,int z) {
        return x>=0 && x<width && y>=0 && y<height && z>=0 && z<depth;
    }
    public Matter get(int x,int y,int z) { return inside(x,y,z) ? cells[index(x,y,z)] : Matter.WALL; }
    public Matter at(int index) { return cells[index]; }
    public short tempAt(int index) { return temperature[index]; }
    public short ageAt(int index) { return age[index]; }
    public int pulseAt(int index) { return pulse[index]&0xff; }
    public int refractoryAt(int index) { return refractory[index]&0xff; }
    public boolean switchOnAt(int index) { return switchOn[index]!=0; }
    public boolean energized(int index) { return pulseAt(index)>0; }
    public void setPulse(int index,int ticks) {
        byte next=(byte)Math.max(0,Math.min(127,ticks));
        if((pulse[index]==0)!=(next==0))dirtyIndex(index);
        pulse[index]=next;
    }
    public void setRefractory(int index,int ticks) { refractory[index]=(byte)Math.max(0,Math.min(127,ticks)); }
    public void setSwitchOn(int index,boolean on) {
        if((switchOn[index]!=0)!=on)dirtyIndex(index);
        switchOn[index]=(byte)(on?1:0);
    }
    public long ticks() { return ticks; }
    public void incrementTick() { ticks++; }
    public void put(int x,int y,int z,Matter matter) {
        if (inside(x,y,z)) set(index(x,y,z),matter);
    }
    public void set(int idx, Matter matter) {
        if(cells[idx]!=matter)dirtyIndex(idx);
        cells[idx]=matter;
        age[idx]=0;
        pulse[idx]=0;refractory[idx]=0;switchOn[idx]=0;
        switch (matter) {
            case LAVA: temperature[idx] = 1000; break;
            case FIRE: temperature[idx] = 600; break;
            case STEAM: temperature[idx] = 110; break;
            case ICE: temperature[idx] = -10; break;
            default: temperature[idx] = 20; break;
        }
    }
    public void swap(int a, int b) {
        if(cells[a]!=cells[b]) {dirtyIndex(a);dirtyIndex(b);}
        Matter m=cells[a]; cells[a]=cells[b]; cells[b]=m;
        short t=temperature[a];temperature[a]=temperature[b];temperature[b]=t;
        short ageA=age[a];age[a]=age[b];age[b]=ageA;
        byte tPulse=pulse[a];pulse[a]=pulse[b];pulse[b]=tPulse;
        byte tRefractory=refractory[a];refractory[a]=refractory[b];refractory[b]=tRefractory;
        byte tSwitch=switchOn[a];switchOn[a]=switchOn[b];switchOn[b]=tSwitch;
    }
    public void age(int idx) { if (age[idx]<Short.MAX_VALUE) age[idx]++; }
    public void setTemp(int idx, int temp) { temperature[idx] = (short)Math.max(-273,Math.min(1500,temp)); }
    public void fill(Matter material) {
        for(int i=0;i<cells.length;i++) set(i, material);
    }
    public byte[] exportMatter() {
        byte[] data=new byte[cells.length];
        for (int i=0;i<data.length;i++) data[i]=(byte) cells[i].id;
        return data;
    }
    public int[] exportTemperatures() {
        int[] data=new int[cells.length];
        for (int i=0;i<data.length;i++) data[i]=temperature[i];
        return data;
    }
    public int[] exportPulses() { return byteValues(pulse); }
    public int[] exportRefractory() { return byteValues(refractory); }
    public int[] exportSwitches() { return byteValues(switchOn); }
    private static int[] byteValues(byte[] a) {
        int[] out=new int[a.length];for(int i=0;i<a.length;i++)out[i]=a[i]&0xff;return out;
    }
    public void restoreElectricity(int[] pulses,int[] cool,int[] switches) {
        if(pulses.length!=size()||cool.length!=size()||switches.length!=size())
            throw new IllegalArgumentException("Electrical save data size mismatch");
        for(int i=0;i<size();i++) {
            setPulse(i,pulses[i]);setRefractory(i,cool[i]);setSwitchOn(i,switches[i]!=0);
        }
    }
    public int[] exportAges() {
        int[] data=new int[cells.length];
        for (int i=0;i<data.length;i++) data[i]=age[i];
        return data;
    }
    public void restore(byte[] matter, int[] temp, int[] ages) {
        if (matter.length != cells.length || temp.length != cells.length || ages.length != cells.length)
            throw new IllegalArgumentException("Save data size does not match chamber");
        for (int i=0;i<cells.length;i++) {
            Matter newMatter=Matter.fromId(Byte.toUnsignedInt(matter[i]));
            if(cells[i]!=newMatter)dirtyIndex(i);
            cells[i]=newMatter;
            temperature[i]=(short)Math.max(-273,Math.min(1500,temp[i]));
            age[i]=(short)Math.max(0,Math.min(Short.MAX_VALUE,ages[i]));
            pulse[i]=0;refractory[i]=0;switchOn[i]=0;
        }
    }
    public void setTick(long tick) { ticks=Math.max(0,tick); }
    public void paintBall(int x,int y,int z,int radius,Matter m) {
        int r=Math.max(0,Math.min(8,radius));
        for(int dy=-r;dy<=r;dy++)for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++)
            if(dx*dx+dy*dy+dz*dz<=r*r)put(x+dx,y+dy,z+dz,m);
    }
}
