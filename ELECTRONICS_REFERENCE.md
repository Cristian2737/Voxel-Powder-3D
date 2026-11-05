# Electronics implementation notes (v0.4)

The Powder Toy (TPT) source and community documentation were used as **behavior references** only; implementation is original Java, adapted for a full 3D voxel grid with only **six face-neighbor connections**. This is not byte-for-byte TPT code and it does not claim complete TPT electronics behavior.

## Materials and tools in v0.4

| Element | In this prototype | Difference from original TPT |
|---|---|---|
| `METAL` | Carries a traveling `SPRK` pulse | TPT's `METL` uses 2D particle rules |
| `SPRK` | Right-click tool to charge the targeted wire | TPT renders SPRK as a temporary particle replacing its host material; here the material is preserved and the pulse stored separately |
| `BTRY` | Infinite source, injecting periodic sparks into adjacent conductors | Simplified 3D rate/timing |
| `PSCN` | Conducts, can turn `SWCH` on, can charge `INST` | 3D connectivity and simplified conductivity |
| `NSCN` | Conducts, turns `SWCH` off, receives from `INST` | Simplified directional rules |
| `SWCH` | Gate starts OFF; PSCN turns ON and NSCN turns OFF; conducts when ON | No full TPT thermal behavior |
| `INST` | Immediate connected-component propagation in 3D, PSCN input / NSCN output | Simplified rules, no 2D wire crossing |
| `INWR` | Insulated conductor. Only connects to INWR/PSCN/NSCN/SWCH | TPT also supports WIFI, not yet implemented |
| `LCRY` | Visible light/color output when energized | Simplified TPT crystal behavior |

Sparks spend 4 simulation ticks charged, then 3 ticks recovering. Directly adjacent wires activate one step apart; connected INST can activate in the same step. Two voxels touching only at a corner do **not** connect.

## Demo

Open standalone preview. Press **E** to select the electronics hotbar. The original main world is unchanged apart from demo circuits toward the north (low Z):

- Battery → PSCN → switch → metal → LCRY (add and spark an NSCN neighbor to disable the switch).
- Battery → PSCN → INST line → NSCN → LCRY.

Select `SPRK` (electronics hotbar slot 1) and **right-click a conductor** under the crosshair to manually spark it. No air block is created and the conductor remains intact. Build custom circuits using the other electronic elements in the palette. `P` pauses physics when editing; resume to watch pulses travel.

## Minecraft Forge integration (source only)

- `MatterBlock.POWERED` blockstate makes powered cells visible to the vanilla renderer and enables light emission for LCRY.
- New `tpt_` blocks and creative-tab entries for BTRY, PSCN, NSCN, SWCH, INST, INWR, LCRY.
- `tpt_spark_tool` is a Forge `Item`, *not* a placed spark block. Use it on existing chamber wires.
- Chamber saves persist `Pulses`, `Refractory`, and `Switches` NBT arrays. Older chambers lacking these keys load with uncharged electronics.
- Chamber still simulates only a bounded 16×12×16 space, server-side every four Minecraft ticks. The blockstates are projected into the world.
- Forge mod is **not verified built or playtested**. The separate standalone Java 8 viewer is compiled and tested.

## Missing and future work

Air pressure, electrical heating, PTCT/NTCT, WIFI channels, DLAY, sensors, lasers, pistons, and a full TPT electrical scheduler remain to be implemented. Minecraft redstone interoperability is not yet available.

## References

- Official Powder Toy sources: https://github.com/The-Powder-Toy/The-Powder-Toy
- Original INST source: https://github.com/The-Powder-Toy/The-Powder-Toy/blob/master/src/simulation/elements/INST.cpp
- Original INWR source: https://github.com/The-Powder-Toy/The-Powder-Toy/blob/master/src/simulation/elements/INWR.cpp
- Community electronics tutorial: https://powdertoy.co.uk/Discussions/Thread/View.html?Thread=24148
- Forge block states: https://docs.minecraftforge.net/en/1.20.1/blocks/states/

## License

The new Java electronics code is MIT-licensed with the rest of this independent prototype. Reusing actual TPT C++ implementations would require following TPT's GPL license.
