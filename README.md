# Voxel Powder 3D — v0.4 (Java 8 preview / Forge 1.20.1 source)

This is an **original 3D particle / block simulation inspired by The Powder Toy**. It does **not** yet contain all Powder Toy elements, nor does it reproduce Minecraft's entire game engine.

## Stable update filenames (starting with v0.3)

**These names will stay the same for future updates.** Version numbers live inside README, not in the downloadable archive names:

- `Voxel_Powder_3D_Update.zip` — drop-in update; same `RUN_STANDALONE.bat` and `VoxelPowder3D_Standalone.jar` filenames each time.
- `Voxel_Powder_3D_Full.zip` — full source, Forge project, tests and ready-to-run standalone preview. Archive contents extract **directly into the current folder**, without creating an additional versioned folder.
- `RUN_STANDALONE.bat` — permanent launch filename.
- `VoxelPowder3D_Standalone.jar` — permanent Java 8 preview filename.

To update, extract `Voxel_Powder_3D_Update.zip` **into the folder that already contains `RUN_STANDALONE.bat`**, and choose **Replace**. No folder renaming needed. You may instead replace your entire project with the full archive, after preserving any custom changes you've made.

## GitHub one-click updates

The stable updater `UPDATE_VOXEL_POWDER_3D.bat` targets
`D:\.Temp\Voxel_Powder_3D_Java_Forge_v0.1` and downloads the public
`Cristian2737/Voxel-Powder-3D` repository's `main` branch.

When the GitHub repository is populated, double-click the updater to:

1. Download the latest project ZIP from GitHub.
2. Back up the current installation under `D:\.Temp\Voxel_Powder_3D_Backups`.
3. Copy the new files into the fixed local directory without deleting extras.
4. Start `RUN_STANDALONE.bat`.

The updater never calls Git or GitHub authentication. It requires Windows
PowerShell and a public repository. Do not run until the repository is populated;
an empty repository has nothing to download. The updater's own BAT file is
preserved during updates.

## New in v0.4 — The Powder Toy-style electronics

The Java engine and standalone viewer now support traveling sparks, battery power, directional silicon, switches, instant conductors, insulated wire and liquid-crystal indicator blocks.

**Open the standalone preview and press `E` to switch to the nine-slot electronics hotbar.** Select `SPRK` in slot 1 and **right-click a wire under the crosshair** to inject a pulse. `SPRK` does not replace or destroy the wire.

| Electronics hotbar slot | Material | Behavior |
|---|---|---|
| 1 | SPRK | Tool: manually sparks a conductor under crosshair |
| 2 | BTRY | Continuous power source |
| 3 | METAL | Ordinary conducting wire |
| 4 | PSCN | Positive silicon, can enable SWCH and drive INST |
| 5 | NSCN | Negative silicon, disables SWCH |
| 6 | SWCH | On/off conducting switch, starts OFF |
| 7 | INST | Fast connected-component conductor |
| 8 | INWR | Wire insulated from ordinary metal |
| 9 | LCRY | Crystal lights up when powered |

The demo contains two circuits near the north wall. `P` pauses and resumes simulation for circuit building. Electrical pulse state is stored separately from the block, so a glowing metal voxel remains metal.

Electrical behavior uses 3D six-face adjacency, with four-tick pulses, refractory periods and simple signal flow. It is an original approximation, **not yet a complete port of TPT electronics**. See `ELECTRONICS_REFERENCE.md` for exact rules and known differences.

**Forge source changes:** added electronics block registrations and blockstates, a creative `SPRK` injector item, visible powered variants, crystal light emission, and NBT persistence of electrical state. Forge is still unbuilt/unplaytested; only the standalone JAR is ready to run.

## Changes for v0.3: Minecraft-style interactions

Grounded in Minecraft Java's defaults:

| Input | Behavior |
| --- | --- |
| `W A S D` | Camera-relative walk, normalized diagonal movement |
| Mouse | First-person look; click viewport once to capture |
| `Space` | Jump; fly upward in flight mode |
| `Ctrl` | Sprint |
| `Shift` | Sneak/crouch and slow movement; prevent walking off supported edges; fly down in flight mode |
| Left click / hold | Break the block under the crosshair, repeating while held (creative-style instant break) |
| Right click / hold | Place selected block **against the hit face**, repeating while held |
| Middle click | Pick targeted block into the active hotbar slot |
| Mouse wheel / `1`–`9` | Nine-slot hotbar |
| `[` / `]` | Cycle the 13-element palette **into the selected slot** (sandbox extra) |
| `+` / `-` | Increase/decrease brush radius (sandbox extra, default zero = one block) |
| `F` | Toggle creative flight for testing (sandbox extra; not vanilla double-space flight) |
| `Esc` / `Tab` | Release/capture mouse |
| `P` | Pause physics; `R` reset world; `G` reset scene without moving player |
| Arrow keys, middle-drag (mouse uncaptured) | Alternative look input |

### Collision and targeting

- Player AABB approximates Minecraft Java (`0.6` wide × `1.8` tall, `1.5` while crouched), with eye position near `1.62` blocks.
- Walk speed is ~`4.317` blocks/s, sprint ~`5.612`, sneak ~`1.3`. It clips movement along walls and supports basic sneaking off ledges.
- Exact 3D DDA raycast finds the nearest occupied voxel, reports the face normal, and restricts reach to **5 blocks**, as in creative mode.
- The selected voxel displays a wireframe cube outline. The active material is shown in a nine-slot hotbar.
- Placement cannot overwrite an existing voxel, overlap the player collider, or leave the 32 × 18 × 32 test world. The permanent floor and boundary wall are protected from breaking.
- Controls now follow Minecraft's **left-break/right-place** convention (the reverse of v0.2.1).

### Rendering improvements

- Perspective software rasterizer, near-plane clipping, Z-buffering, backface culling, and neighbor-face culling retained.
- Added **cached 16×16×16 exposed-face meshes**; only regions with changed materials (or changed neighbor boundaries) regenerate geometry.
- Opaque block faces next to liquid/gas placeholders are now exposed. This improves surface visibility.
- Materials are still **flat shaded colors**: textured Minecraft block models, transparency sorting, dynamic lights and AO are not implemented in the standalone viewer.

## Run on Windows

1. Extract the update ZIP over your existing installation. All filenames inside are stable.
2. Double-click `RUN_STANDALONE.bat` (Java 8 or newer). A JDK isn't needed for the standalone JAR.
3. Click the viewport, move with WASD, left click to break and right click to place.

## Forge integration

- Targets **Minecraft Java Edition 1.20.1, Forge 47.4.28, JDK 17** for compiling the Forge mod.
- The shared simulation (`core/`) is used by both preview and Forge source.
- The existing Forge mod has a bounded **16×12×16** simulation chamber. It uses **Minecraft's native camera, movement, block targeting, block destruction and renderer**, rather than porting the preview's software renderer into Minecraft.
- In this update, the Forge `MatterBlock` definitions have been adjusted: solid/powder materials use normal full-block collision and occlusion, while liquid/gas placeholders remain non-colliding/non-occluding. They are still custom simulated blocks, **not vanilla water, lava, smoke or particle rendering**.
- Forge source has **not yet been built or playtested inside Minecraft**. To try it, use `BUILD_FORGE_MOD.bat` with JDK 17 and an internet connection. Please send the build output if it fails. No claim of a working Forge mod JAR is made.

## Physics and limitations

- The current independent Java core has 20 non-air materials plus one SPRK tool: SAND, WATER, STONE, WOOD, FIRE, SMOKE, STEAM, OIL, LAVA, METAL, ICE, ACID and GUNPOWDER.
- Sand falls, liquids spread, gas rises, and some elementary heat/chemical interactions occur.
- Air pressure, ambient heat, photons, advanced Powder Toy electrical networks (e.g. WIFI, DLAY, thermistors), Lua scripts, full Minecraft networking, drop items, mining times, step-up terrain, damage, inventory, survival mechanics and chunk persistence are **not implemented** in the standalone viewer.
- The test world remains a bounded **32×18×32** arena; flight is a testing shortcut, not full creative mode implementation.

## Validation performed

- All standalone Java classes compile targeting **Java 8 class-file major version 52**.
- 6 original core tests, 13 renderer/collision tests, 1,231 movement assertions, 18 interaction/mesh checks, and 44 electronics assertions pass.
- Swing viewer rendered an actual screenshot in a graphical test session (`preview.png`).
- Forge mod compilation/playtest **not verified**.

## Source layout

- `core/` — Java particle engine, voxel world and scenes
- `standalone/` — Swing renderer, camera, first-person controls and DDA raycasting
- `forge/` — Forge 1.20.1 chamber mod source
- `tests/` — core, movement, rendering and interaction tests (`RUN_CORE_TESTS.bat`)

## Licensing

The code in this prototype is original and MIT-licensed; The Powder Toy is a separate GPLv3 project. Incorporating original TPT code would require appropriate GPL licensing and attribution.

Minecraft input documentation: https://minecraft.wiki/w/Controls
Minecraft interaction range: https://minecraft.wiki/w/Interaction_range
Minecraft sneak mechanics: https://minecraft.wiki/w/Sneaking
Forge block registration/states: https://docs.minecraftforge.net/en/1.20.1/blocks/states/