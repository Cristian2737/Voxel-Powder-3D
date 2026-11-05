# Minecraft-like behavior implementation notes

Reference target: Minecraft Java 1.20.1 / Forge 47.4.28. These links document **Minecraft** behavior; the v0.3 preview is an approximation, not the actual Minecraft engine.

- Default inputs: W/A/S/D for walk, Control for sprint, Shift for sneak, Space for jump, left-click attack/break, right-click use/place, middle-click pick block, mouse wheel and 1-9 to select the hotbar. Source: https://minecraft.wiki/w/Controls
- Creative block reach is approximately five blocks, and the targeted block is outlined. Source: https://minecraft.wiki/w/Interaction_range
- Placement goes on the face adjacent to the selected solid block. Implemented in `VoxelRaycast.java` via grid DDA, rather than imprecise fixed-distance stepping. Minecraft interactions vary by mode and block; this preview deliberately uses simple creative-like behavior.
- Java sneak slows movement and crouches the player, reducing collision height and helping prevent falls. Source: https://minecraft.wiki/w/Sneaking
- Minecraft's walking/sprinting speeds approximately 4.317/5.612 blocks per second. Source: https://minecraft.wiki/w/Player
- Typical Minecraft solids use collidable full-block states and opaque models. Forge state documentation: https://docs.minecraftforge.net/en/1.20.1/blocks/states/
- Standalone render: 16x16x16 dirty-region geometry caching, shared opaque faces omitted, normal backface culling, perspective projection, a software depth buffer and a wireframe selected-block outline.
- Forge render: Minecraft's own block model and renderer. The prototype Material blocks use ordinary Minecraft state/rendering; this code does **not** override Minecraft's camera or renderer.

Not yet implemented: exact Minecraft jump curves, full momentum physics, stair stepping, swimming, variable block collision shapes, polished transparent fluids, chunk persistence, lightmaps, damage, inventory, GUI interactions, survival mining speed and drops.
