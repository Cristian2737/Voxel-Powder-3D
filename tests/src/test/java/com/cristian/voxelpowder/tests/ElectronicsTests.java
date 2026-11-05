package com.cristian.voxelpowder.tests;

import com.cristian.voxelpowder.core.*;

/** Deterministic electronics regression tests: all connections use six face neighbors. */
public final class ElectronicsTests {
    private static int passed=0;
    private static void assertThat(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        passed++;
    }
    private static VoxelWorld world(){return new VoxelWorld(16,8,16);}
    private static void step(VoxelWorld w,int count){Simulator s=new Simulator(42);for(int n=0;n<count;n++)s.step(w,500);}
    private static int idx(VoxelWorld w,int x,int y,int z){return w.index(x,y,z);}
    private static boolean powered(VoxelWorld w,int x,int y,int z){return w.energized(idx(w,x,y,z));}
    public static void main(String[] args) {
        VoxelWorld b=world(); b.put(2,2,2,Matter.BTRY);b.put(3,2,2,Matter.METAL);
        step(b,1);
        assertThat(powered(b,3,2,2),"Battery charges adjacent metal");
        assertThat(b.get(3,2,2)==Matter.METAL,"Pulsed conductor retains original material");
        assertThat(!powered(b,3,3,2),"Does not power empty space");
        int pulse=b.pulseAt(idx(b,3,2,2));
        step(b,1);
        assertThat(b.pulseAt(idx(b,3,2,2))==pulse-1,"Pulse ages one tick at a time");

        VoxelWorld line=world();
        for(int x=2;x<=6;x++)line.put(x,2,2,Matter.METAL);
        assertThat(Electricity.spark(line,2,2,2),"SPRK tool fires into metal");
        assertThat(!Electricity.spark(line,2,2,2),"Cannot retrigger active wire immediately");
        step(line,1);
        assertThat(powered(line,3,2,2),"Pulse moves one voxel in first step");
        assertThat(!powered(line,4,2,2),"Pulse does not teleport through metal");
        step(line,1);
        assertThat(powered(line,4,2,2),"Pulse continues down wire");
        step(line,2);
        assertThat(powered(line,6,2,2),"Pulse reaches fourth link");
        assertThat(!Electricity.spark(line,2,2,2),"Wire refractory after pulse expires");
        step(line,4);
        assertThat(Electricity.spark(line,2,2,2),"Spark can be retriggered after cooldown");

        VoxelWorld diagonal=world();
        diagonal.put(3,2,3,Matter.METAL);
        diagonal.put(4,3,3,Matter.METAL);
        Electricity.spark(diagonal,3,2,3);step(diagonal,1);
        assertThat(!powered(diagonal,4,3,3),"Diagonal-only contact does not connect in 3D");
        diagonal.put(4,2,3,Matter.METAL);step(diagonal,1);
        assertThat(powered(diagonal,4,2,3),"Face contact connects wire");

        VoxelWorld pToN=world();
        pToN.put(3,2,2,Matter.PSCN);pToN.put(4,2,2,Matter.NSCN);
        Electricity.spark(pToN,3,2,2);step(pToN,1);
        assertThat(powered(pToN,4,2,2),"PSCN passes pulse to NSCN");

        VoxelWorld nToP=world();
        nToP.put(3,2,2,Matter.NSCN);nToP.put(4,2,2,Matter.PSCN);
        Electricity.spark(nToP,3,2,2);step(nToP,1);
        assertThat(!powered(nToP,4,2,2),"NSCN cannot pass pulse to PSCN");

        VoxelWorld sw=world();
        sw.put(2,2,2,Matter.PSCN);sw.put(3,2,2,Matter.SWCH);
        sw.put(4,2,2,Matter.METAL);sw.put(5,2,2,Matter.LCRY);
        assertThat(!sw.switchOnAt(idx(sw,3,2,2)),"Switch defaults OFF");
        assertThat(!Electricity.spark(sw,3,2,2),"SPRK cannot energize OFF switch directly");
        Electricity.spark(sw,2,2,2);
        step(sw,1);
        assertThat(sw.switchOnAt(idx(sw,3,2,2)),"PSCN pulse enables switch");
        step(sw,2);
        assertThat(powered(sw,4,2,2),"Enabled switch forwards pulse");
        step(sw,1);
        assertThat(powered(sw,5,2,2),"LCRY lights after switch-fed pulse");
        sw.put(3,2,3,Matter.NSCN);
        Electricity.spark(sw,3,2,3);step(sw,1);
        assertThat(!sw.switchOnAt(idx(sw,3,2,2)),"NSCN pulse disables switch");

        VoxelWorld instant=world();
        instant.put(1,2,2,Matter.PSCN);
        for(int x=2;x<=9;x++)instant.put(x,2,2,Matter.INST);
        instant.put(10,2,2,Matter.NSCN);
        Electricity.spark(instant,1,2,2);step(instant,1);
        for(int x=2;x<=9;x++)assertThat(powered(instant,x,2,2),"INST flood reaches x="+x);
        assertThat(!powered(instant,10,2,2),"INST does not instantly energize NSCN in same tick");
        step(instant,1);
        assertThat(powered(instant,10,2,2),"INST outputs to NSCN");
        VoxelWorld instantNoP=world();
        instantNoP.put(1,2,2,Matter.METAL);
        instantNoP.put(2,2,2,Matter.INST);
        Electricity.spark(instantNoP,1,2,2);step(instantNoP,1);
        assertThat(!powered(instantNoP,2,2,2),"METAL cannot feed INST (requires PSCN)");

        VoxelWorld inwr=world();
        inwr.put(1,2,2,Matter.METAL);inwr.put(2,2,2,Matter.INWR);
        Electricity.spark(inwr,1,2,2);step(inwr,1);
        assertThat(!powered(inwr,2,2,2),"METAL cannot feed insulated wire");
        VoxelWorld inwrGood=world();
        inwrGood.put(1,2,2,Matter.PSCN);inwrGood.put(2,2,2,Matter.INWR);
        Electricity.spark(inwrGood,1,2,2);step(inwrGood,1);
        assertThat(powered(inwrGood,2,2,2),"PSCN feeds insulated wire");
        inwrGood.put(3,2,2,Matter.METAL);step(inwrGood,1);
        assertThat(!powered(inwrGood,3,2,2),"INWR does not directly feed METAL");

        VoxelWorld state=world();
        state.put(3,2,3,Matter.SWCH);state.setSwitchOn(idx(state,3,2,3),true);
        state.setPulse(idx(state,3,2,3),4);state.setRefractory(idx(state,3,2,3),2);
        VoxelWorld saved=world();
        saved.restore(state.exportMatter(),state.exportTemperatures(),state.exportAges());
        saved.restoreElectricity(state.exportPulses(),state.exportRefractory(),state.exportSwitches());
        assertThat(saved.switchOnAt(idx(saved,3,2,3)),"Saved switch state restored");
        assertThat(saved.pulseAt(idx(saved,3,2,3))==4,"Saved pulse life restored");
        assertThat(saved.refractoryAt(idx(saved,3,2,3))==2,"Saved refractory life restored");
        long before=state.chunkVersion(0,0,0);
        state.setPulse(idx(state,3,2,3),0);
        assertThat(state.chunkVersion(0,0,0)>before,"Electrical color invalidates cached chunk appearance");
        state.set( idx(state,3,2,3), Matter.METAL);
        assertThat(state.pulseAt(idx(state,3,2,3))==0 && !state.switchOnAt(idx(state,3,2,3)),"Replacing a wire clears its electric state");

        // The included level must be a functional demo, not just decorative blocks.
        VoxelWorld demo=new VoxelWorld(32,18,32);
        WalkableScenes.create(demo);
        Simulator engine=new Simulator(0x1337);
        boolean crystalA=false,crystalB=false;
        for(int t=0;t<48;t++) {
            engine.step(demo,4000);
            if(powered(demo,25,1,4))crystalA=true;
            if(powered(demo,14,1,8))crystalB=true;
        }
        assertThat(crystalA,"Switch circuit in demo actually lights crystal");
        assertThat(crystalB,"Instant circuit in demo actually lights crystal");
        assertThat(demo.switchOnAt(idx(demo,22,1,4)),"Demo switch stays ON without NSCN feedback");
        System.out.println("PASS: "+passed+" electronics assertions");
    }
}
