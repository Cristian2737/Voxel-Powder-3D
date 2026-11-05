package com.cristian.voxelpowder.standalone;

import com.cristian.voxelpowder.core.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/** First-person walkable 3D Java8 test chamber, driven by the same engine used by Forge. */
public final class Viewer extends JPanel {
    private static final int W=32,H=18,D=32;
    private static final int SIDEBAR=264;
    private static final double REACH=5.0;
    private final VoxelWorld world=new VoxelWorld(W,H,D);
    private final Simulator simulator=new Simulator(0x1337);
    private final VoxelRenderer renderer=new VoxelRenderer();
    private final PlayerController player=new PlayerController();
    private final FirstPersonCamera camera=new FirstPersonCamera(16.5,2.59,25,Math.PI,-.12);
    private final Set<Integer> pressed=new HashSet<Integer>();
    private final Matter[] pick={Matter.SAND,Matter.WATER,Matter.STONE,Matter.WOOD,Matter.FIRE,
        Matter.OIL,Matter.LAVA,Matter.METAL,Matter.ICE,Matter.ACID,Matter.GUNPOWDER,Matter.SMOKE,Matter.STEAM,
        Matter.BTRY,Matter.PSCN,Matter.NSCN,Matter.SWCH,Matter.INST,Matter.INWR,Matter.LCRY,Matter.SPRK};
    private Matter selected=Matter.SAND;
    private boolean running=true,mouseCaptured=false;
    private Robot mouseRobot;
    private int brush=0, ops=0, selectedIndex=0;
    private boolean electronicsPalette=false;
    private final Matter[] hotbar={Matter.SAND,Matter.STONE,Matter.WOOD,Matter.WATER,Matter.METAL,Matter.LAVA,Matter.FIRE,Matter.ICE,Matter.ACID};
    private boolean breaking=false,placing=false;
    private long lastBreak=0,lastPlace=0;
    private long lastTick=System.nanoTime();
    private double simAccumulator=0;
    private VoxelRaycast.Hit targetHit;
    private int lookX,lookY;
    private boolean dragLook=false;
    private String status="Click the viewport to capture the mouse. ESC releases it.";
    private int viewportWidth(){return Math.max(1,getWidth()-SIDEBAR);}

    public Viewer() {
        setPreferredSize(new Dimension(1180,720));
        setBackground(new Color(13,19,29));
        setFocusable(true);
        reset();
        addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if(e.getX()>=viewportWidth())return;
                if(SwingUtilities.isMiddleMouseButton(e)) {
                    if(mouseCaptured) pickTarget();
                    else {dragLook=true;lookX=e.getX();lookY=e.getY();}
                    return;
                }
                if(!mouseCaptured) {captureMouse();return;}
                if(SwingUtilities.isLeftMouseButton(e)) {breaking=true;interact(false);}
                if(SwingUtilities.isRightMouseButton(e)) {placing=true;interact(true);}
            }
            @Override public void mouseReleased(MouseEvent e) {
                if(SwingUtilities.isMiddleMouseButton(e)) dragLook=false;
                if(SwingUtilities.isLeftMouseButton(e))breaking=false;
                if(SwingUtilities.isRightMouseButton(e))placing=false;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseMoved(MouseEvent e) {trackMouse(e);}
            @Override public void mouseDragged(MouseEvent e) {trackMouse(e);}
        });
        addMouseWheelListener(e->{
            if(e.getX()<viewportWidth()) {
                selectedIndex=Math.floorMod(selectedIndex+e.getWheelRotation(),9);
                selected=hotbar[selectedIndex];
            }
        });
        addKeyListener(new KeyAdapter(){
            @Override public void keyPressed(KeyEvent e) {
                int key=e.getKeyCode();boolean first=pressed.add(key);
                if(!first && key!=KeyEvent.VK_LEFT && key!=KeyEvent.VK_RIGHT && key!=KeyEvent.VK_UP && key!=KeyEvent.VK_DOWN)return;
                if(key>=KeyEvent.VK_1&&key<=KeyEvent.VK_9) {
                    selectedIndex=key-KeyEvent.VK_1;selected=hotbar[selectedIndex];
                }
                switch(key) {
                    case KeyEvent.VK_ESCAPE: releaseMouse();break;
                    case KeyEvent.VK_TAB: if(mouseCaptured)releaseMouse();else captureMouse();e.consume();break;
                    case KeyEvent.VK_OPEN_BRACKET: select(-1);break;
                    case KeyEvent.VK_CLOSE_BRACKET: select(1);break;
                    case KeyEvent.VK_EQUALS:
                    case KeyEvent.VK_ADD: brush=Math.min(4,brush+1);break;
                    case KeyEvent.VK_MINUS:
                    case KeyEvent.VK_SUBTRACT: brush=Math.max(0,brush-1);break;
                    case KeyEvent.VK_F: if(!e.isConsumed()) {player.flying=!player.flying;player.verticalSpeed=0;status=player.flying?"Flight mode enabled":"Walking mode enabled";e.consume();}break;
                    case KeyEvent.VK_P: running=!running;break;
                    case KeyEvent.VK_R: reset();break;
                    case KeyEvent.VK_G: world.fill(Matter.AIR);WalkableScenes.create(world);status="World reset to walkable scene";break;
                    case KeyEvent.VK_E: toggleElectronics();break;
                    case KeyEvent.VK_LEFT: camera.look(-.07,0);break;
                    case KeyEvent.VK_RIGHT: camera.look(.07,0);break;
                    case KeyEvent.VK_UP: camera.look(0,.06);break;
                    case KeyEvent.VK_DOWN: camera.look(0,-.06);break;
                    default:break;
                }
            }
            @Override public void keyReleased(KeyEvent e) {pressed.remove(e.getKeyCode());}
        });
        addFocusListener(new FocusAdapter(){
            @Override public void focusLost(FocusEvent e) {pressed.clear();releaseMouse();}
        });
        new javax.swing.Timer(33,e->tick()).start();
    }
    private void toggleElectronics() {
        electronicsPalette=!electronicsPalette;
        Matter[] palette=electronicsPalette
            ? new Matter[]{Matter.SPRK,Matter.BTRY,Matter.METAL,Matter.PSCN,Matter.NSCN,
                           Matter.SWCH,Matter.INST,Matter.INWR,Matter.LCRY}
            : new Matter[]{Matter.SAND,Matter.STONE,Matter.WOOD,Matter.WATER,Matter.METAL,
                           Matter.LAVA,Matter.FIRE,Matter.ICE,Matter.ACID};
        System.arraycopy(palette,0,hotbar,0,hotbar.length);
        selected=hotbar[selectedIndex];
        status=electronicsPalette?"Electronics hotbar: SPRK fires at the TARGETED wire":"Standard materials hotbar";
    }
    private void select(int step) {
        // Sandbox extra: cycle the full material catalog into current hotbar slot.
        int index=0;for(int i=0;i<pick.length;i++)if(pick[i]==selected){index=i;break;}
        selected=pick[Math.floorMod(index+step,pick.length)];hotbar[selectedIndex]=selected;
    }
    private boolean key(int k){return pressed.contains(k);}
    private void reset() {
        WalkableScenes.create(world);
        player.respawn(world);
        camera.yaw=Math.PI;
        camera.pitch=-.12;
        camera.x=player.x;camera.y=player.feetY+PlayerController.EYE;camera.z=player.z;
        lastTick=System.nanoTime();simAccumulator=0;
    }
    private void tick() {
        long now=System.nanoTime();
        double dt=Math.min(.065,(now-lastTick)/1e9);lastTick=now;
        player.step(world,camera,key(KeyEvent.VK_W),key(KeyEvent.VK_S),key(KeyEvent.VK_A),key(KeyEvent.VK_D),
            key(KeyEvent.VK_SHIFT),key(KeyEvent.VK_SPACE),key(KeyEvent.VK_CONTROL),dt);
        long ms=System.currentTimeMillis();
        if(mouseCaptured && breaking && ms-lastBreak>=165)interact(false);
        if(mouseCaptured && placing && ms-lastPlace>=180)interact(true);
        if(running) {
            simAccumulator+=dt;
            if(simAccumulator>=.075) {
                simAccumulator=Math.min(.075,simAccumulator-.075); // bounded sim work / frame
                ops=simulator.step(world,4000);
            }
        }
        target();
        repaint();
    }
    private void captureMouse() {
        if(!isShowing())return;
        if(mouseRobot==null) {
            try {mouseRobot=new Robot();}
            catch(AWTException | SecurityException e) {
                status="Mouse capture unavailable. Middle-drag or arrow keys to look.";
                return;
            }
        }
        mouseCaptured=true;status="Minecraft controls: LMB break / RMB place / MMB pick / CTRL sprint / SHIFT sneak.";
        setCursor(getToolkit().createCustomCursor(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),new Point(0,0),"hidden"));
        recenterMouse();
    }
    private void releaseMouse() {
        if(!mouseCaptured)return;
        mouseCaptured=false;breaking=false;placing=false;
        setCursor(Cursor.getDefaultCursor());
    }
    private void recenterMouse() {
        if(!mouseCaptured || mouseRobot==null || !isShowing())return;
        try {
            Point onScreen=getLocationOnScreen();
            mouseRobot.mouseMove(onScreen.x+viewportWidth()/2,onScreen.y+getHeight()/2);
        } catch(IllegalComponentStateException ignored) {releaseMouse();}
    }
    private void trackMouse(MouseEvent e) {
        if(mouseCaptured) {
            int dx=e.getX()-viewportWidth()/2,dy=e.getY()-getHeight()/2;
            if(Math.abs(dx)>1||Math.abs(dy)>1) {
                // Clamp large deltas if window was moved or focus switched.
                camera.look(Math.max(-100,Math.min(100,dx))*.0035,
                    -Math.max(-100,Math.min(100,dy))*.0035);
                recenterMouse();
            }
        } else if(dragLook) {
            camera.look((e.getX()-lookX)*.005,-(e.getY()-lookY)*.005);
            lookX=e.getX();lookY=e.getY();
        }
    }
    /** Minecraft-like first solid-voxel hit, with the actual hit-face normal. */
    private void target() {
        targetHit=VoxelRaycast.cast(world,camera,REACH);
    }
    private void pickTarget() {
        target();
        if(targetHit==null)return;
        selected=world.get(targetHit.x,targetHit.y,targetHit.z);
        if(selected==Matter.AIR || selected==Matter.WALL)return;
        hotbar[selectedIndex]=selected;
        status="Picked "+selected.displayName+" into hotbar slot "+(selectedIndex+1);
    }
    private boolean protectedBorder(int x,int y,int z) {
        return y==0 || x==0 || z==0 || x==W-1 || z==D-1;
    }
    private void interact(boolean place) {
        target();
        long ms=System.currentTimeMillis();
        if(place)lastPlace=ms;else lastBreak=ms;
        if(targetHit==null) {status="No block in reach (5 blocks).";return;}
        if(place && selected==Matter.SPRK) {
            boolean ok=Electricity.spark(world,targetHit.x,targetHit.y,targetHit.z);
            status=ok?"SPRK injected into "+world.get(targetHit.x,targetHit.y,targetHit.z).displayName:
                "SPRK needs an idle conductor (switches must be ON).";
            return;
        }
        int x=place?targetHit.placeX():targetHit.x;
        int y=place?targetHit.placeY():targetHit.y;
        int z=place?targetHit.placeZ():targetHit.z;
        if(!world.inside(x,y,z) || protectedBorder(x,y,z))return;
        if(place) {
            if(world.get(x,y,z)!=Matter.AIR)return;
            if(player.intersectsCell(x,y,z)) {status="Cannot place a block inside the player.";return;}
            world.paintBall(x,y,z,brush,selected);
        } else {
            world.paintBall(x,y,z,brush,Matter.AIR);
        }
    }
    private void drawBlockOutline(Graphics2D g) {
        if(targetHit==null)return;
        int x=targetHit.x,y=targetHit.y,z=targetHit.z;
        final int[][] corners={{0,0,0},{1,0,0},{0,1,0},{1,1,0},
            {0,0,1},{1,0,1},{0,1,1},{1,1,1}};
        final int[][] edges={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},
            {2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        double[][] transformed=new double[8][3];
        int[][] scr=new int[8][2];
        double focal=(getHeight()*.5)/Math.tan(Math.toRadians(73)/2);
        for(int i=0;i<8;i++) {
            camera.worldToCamera(x+corners[i][0],y+corners[i][1],z+corners[i][2],transformed[i]);
            if(transformed[i][2]>.075) {
                scr[i][0]=(int)(viewportWidth()*.5+focal*transformed[i][0]/transformed[i][2]);
                scr[i][1]=(int)(getHeight()*.5-focal*transformed[i][1]/transformed[i][2]);
            }
        }
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(8,13,18,220));
        for(int[] edge:edges)if(transformed[edge[0]][2]>.075 && transformed[edge[1]][2]>.075)
            g.drawLine(scr[edge[0]][0],scr[edge[0]][1],scr[edge[1]][0],scr[edge[1]][1]);
        g.setStroke(new BasicStroke(1.35f));
        g.setColor(new Color(247,247,238,230));
        for(int[] edge:edges)if(transformed[edge[0]][2]>.075 && transformed[edge[1]][2]>.075)
            g.drawLine(scr[edge[0]][0],scr[edge[0]][1],scr[edge[1]][0],scr[edge[1]][1]);
    }
    private void drawHotbar(Graphics2D g,int viewportWidth,int screenHeight) {
        int size=43,gap=3,total=size*9+gap*8;
        int x=(viewportWidth-total)/2,y=screenHeight-72;
        for(int i=0;i<9;i++) {
            int xx=x+i*(size+gap);
            g.setColor(new Color(10,13,19,208));g.fillRoundRect(xx,y,size,size,6,6);
            g.setStroke(new BasicStroke(i==selectedIndex?3f:1.2f));
            g.setColor(i==selectedIndex?new Color(255,252,232):new Color(110,117,128));
            g.drawRoundRect(xx,y,size,size,6,6);
            g.setColor(new Color(0xFF000000|hotbar[i].color));g.fillRect(xx+10,y+9,23,23);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));g.setColor(Color.WHITE);
            g.drawString(Integer.toString(i+1),xx+4,y+size-5);
        }
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));
        String name=selected.displayName;
        FontMetrics fm=g.getFontMetrics();
        g.setColor(new Color(7,12,20,176));
        g.fillRoundRect(viewportWidth/2-fm.stringWidth(name)/2-12,y-29,fm.stringWidth(name)+24,23,6,6);
        g.setColor(Color.WHITE);g.drawString(name,viewportWidth/2-fm.stringWidth(name)/2,y-12);
    }
    private void label(Graphics2D g,String text,int x,int y,Color color) {
        g.setColor(color);g.drawString(text,x,y);
    }
    @Override protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g=(Graphics2D)gg.create();
        int vw=viewportWidth(),height=getHeight();
        // Sky gradient remains visible in pixels where no voxel passes depth testing.
        g.setPaint(new GradientPaint(0,0,new Color(31,50,69),0,height,new Color(15,21,31)));
        g.fillRect(0,0,vw,height);
        BufferedImage frame=renderer.draw(world,camera,vw,height);
        g.drawImage(frame,0,0,null);
        drawBlockOutline(g);
        drawHotbar(g,vw,height);
        int cx=vw/2,cy=height/2;
        g.setStroke(new BasicStroke(2f));g.setColor(new Color(0,0,0,190));
        g.drawLine(cx-9,cy,cx+9,cy);g.drawLine(cx,cy-9,cx,cy+9);
        g.setColor(targetHit!=null?new Color(255,211,89):new Color(225,241,255));
        g.drawLine(cx-7,cy,cx+7,cy);g.drawLine(cx,cy-7,cx,cy+7);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));
        if(!mouseCaptured) {
            g.setColor(new Color(7,12,21,182));g.fillRoundRect(cx-190,cy+46,380,53,12,12);
            g.setColor(Color.WHITE);
            g.drawString("Click viewport to capture mouse - ESC releases",cx-148,cy+67);
            g.drawString("WASD to walk • SPACE to jump",cx-115,cy+86);
        }
        // Side panel: HUD, coordinates and an overhead orientation map.
        int sx=vw;
        g.setColor(new Color(11,16,24));g.fillRect(sx,0,SIDEBAR,height);
        g.setColor(new Color(65,80,99));g.drawLine(sx,0,sx,height);
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
        label(g,"VOXEL POWDER 3D",sx+12,29,new Color(227,238,248));
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        label(g,"FIRST-PERSON 3D TEST CHUNK",sx+12,47,new Color(121,218,199));
        g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        String[] lines={"WASD      move", "Mouse     look (click to lock)","LMB       break block",
            "RMB       place on target face", "MMB       pick targeted block", "SPACE     jump / fly up",
            "CTRL      sprint", "SHIFT     sneak / fly down", "F         toggle flight (extra)",
            "1-9       hotbar select", "Wheel     hotbar scroll", "E         toggle electronics palette", "[ / ]     change block type", 
            "+ / -     brush radius (extra)","P         pause physics", "R         reset world",
            "ESC       release mouse", "Arrows    look without mouse"};
        int y=76;
        for(String line:lines){label(g,line,sx+12,y,new Color(190,204,219));y+=14;}
        y+=12;
        label(g,"SELECTED: "+selected.displayName.toUpperCase(),sx+12,y,new Color(243,220,155));y+=20;
        label(g,"Palette: "+(electronicsPalette?"ELECTRONICS":"STANDARD"),sx+12,y,new Color(151,222,185));y+=18;
        label(g,"Brush: "+brush+"    Physics: "+(running?"ON":"PAUSED"),sx+12,y,new Color(181,220,217));y+=20;
        label(g,"Position:",sx+12,y,new Color(122,217,188));y+=18;
        label(g,String.format(java.util.Locale.ROOT,"%.1f / %.1f / %.1f",player.x,player.feetY,player.z),sx+12,y,new Color(213,221,229));y+=20;
        label(g,"Move: "+(player.movementBlocked?"BLOCKED":"FREE")+"  Yaw: "+Math.round(Math.toDegrees(camera.yaw)%360)+" deg",sx+12,y,
            player.movementBlocked?new Color(255,177,145):new Color(178,224,203));y+=20;
        label(g,"Flight: "+(player.flying?"ON":"OFF")+"  Material cells: "+renderer.solidCells,sx+12,y,new Color(203,215,226));y+=20;
        label(g,"Faces drawn: "+renderer.drawnFaces,sx+12,y,new Color(203,215,226));y+=20;
        label(g,"Culled b/h: "+renderer.backfaces+" / "+renderer.hiddenFaces,sx+12,y,new Color(203,215,226));y+=20;
        label(g,"Sim ops: "+ops+"   Tick: "+world.ticks(),sx+12,y,new Color(203,215,226));
        if(targetHit!=null) {
            int i=world.index(targetHit.x,targetHit.y,targetHit.z);
            label(g,"Target: "+world.get(targetHit.x,targetHit.y,targetHit.z).displayName,sx+12,height-182,new Color(240,213,151));
            label(g,"Pulse: "+world.pulseAt(i)+"   Switch: "+(world.switchOnAt(i)?"ON":"OFF"),sx+12,height-166,new Color(247,221,143));
        }
        drawMinimap(g,sx+42,height-158,4);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));
        label(g,"32x18x32 bounded voxel world",sx+24,height-17,new Color(116,147,167));
        g.setColor(new Color(228,237,248,215));
        g.drawString(status,12,height-15); // may clip, but not gameplay-critical
        g.dispose();
    }
    private void drawMinimap(Graphics2D g,int startX,int startY,int tile) {
        g.setColor(new Color(35,49,61));g.fillRect(startX-5,startY-5,W*tile+10,D*tile+10);
        // Plan view: highest non-air voxel in each column.
        for(int x=0;x<W;x++)for(int z=0;z<D;z++) {
            Matter m=Matter.AIR;
            for(int yy=H-1;yy>=0;yy--) {
                Matter a=world.get(x,yy,z);
                if(a!=Matter.AIR) {m=a;break;}
            }
            g.setColor(m==Matter.AIR?new Color(26,35,45):new Color(0xff000000|m.color));
            g.fillRect(startX+x*tile,startY+z*tile,tile,tile);
        }
        int px=startX+(int)(player.x*tile),py=startY+(int)(player.z*tile);
        g.setColor(Color.WHITE);g.fillOval(px-3,py-3,7,7);
        g.drawLine(px,py,px+(int)(camera.forwardX()*12),py+(int)(camera.forwardZ()*12));
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->{
            JFrame frame=new JFrame("Voxel Powder 3D v0.4 - Electronics");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            Viewer viewer=new Viewer();frame.setContentPane(viewer);
            frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);
            viewer.requestFocusInWindow();
        });
    }
}
