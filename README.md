# Solid Displays

A Fabric mod for Minecraft **26.3** (Java Edition). In survival, without any commands, design
decorations and models out of block and item displays at a **Design Table** (Axiom-style editor),
save them to a **Blueprint**, and place them in the world with your materials — with **real collision**:
you can walk on them and cannot walk through them.

## Requirements

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |

Install the mod on the **server and on every client** (it adds blocks, items and an entity).

## Installing

1. Install Fabric Loader for 26.3 with the installer from <https://fabricmc.net/use/installer/>.
2. Put `solid_displays-<version>.jar` and the Fabric API jar into the `mods` folder:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
   - Dedicated server: the `mods` folder next to the server jar.
3. Start the game with the `fabric-loader-…-26.3` profile.

## Crafting

| Item | Recipe |
|---|---|
| Design Table | glass pane, paper, glass pane / planks, crafting table, planks / planks, —, planks |
| Empty Blueprint ×8 | 8 paper around 1 blue dye |
| Dismantle Tool | iron, —, iron / —, stick, — / —, stick, — |

## Using it

### 1. Design at the Design Table
Right-click the table.

- **Left:** search blocks & items; click one to add it as a new part. Below: the part list with
  Copy / Delete / Undo / Redo. The small square shows whether the part has collision.
- **Centre (3D view):** click a part to select it. In **Move** mode drag the coloured arrows, in **Size**
  mode drag the face handles (the opposite face stays put). Drag empty space to orbit,
  Shift+drag or middle mouse to pan, mouse wheel to zoom, **Frame** to reset the camera.
  The grid button sets the snap step: 1, 1/2, 1/4, 1/8, 1/16 block.
- **Part tab:** exact position / size / rotation, quick 90° turns, collision on/off
  (turn it off for grass, flowers…), and the materials the whole model will cost.
- **Blueprint tab:** model name, the blueprint slot and your inventory. **Save** writes the design onto
  the (empty or filled) blueprint in the slot; **Load** opens a filled blueprint's design for editing.
- Shortcuts: Ctrl+Z / Ctrl+Y, Ctrl+D (duplicate), Delete, G (move), S (size).
- Unsaved work stays in the table when you close it.

### 2. Place with the Blueprint
- The tooltip lists the materials: green ✔ when you have enough, red ✘ when not (your amount in brackets).
- While holding it, a translucent **ghost** of the model shows where you look (red-tinted if materials are missing).
- **R** (rebindable) or **Shift + mouse wheel** rotates it in 90° steps.
- **Right-click** a block: the materials are taken and the model is placed. The blueprint is not used up.
  If something is missing, the list is shown in chat. In creative mode placing is free.

### 3. Dismantle
Right-click a part with the **Dismantle Tool** (or Shift + right-click with anything), then click again within
3 seconds to confirm. All parts are removed and the materials drop where the model stood.
By default only the player who placed a model (or an operator) can dismantle it.

## How the collision works

Each part is a vanilla **Block Display** or **Item Display**. Every part with collision also gets an invisible
`solid_displays:model_collider` entity that uses vanilla's boat/shulker mechanism (`canBeCollidedWith`), so the
client predicts exactly the same collision as the server: no jitter, no rubber-banding, and it works in
multiplayer. Its X/Y/Z size is synced entity data.

- Parts bigger than 3 blocks are split into several boxes (vanilla only searches a few blocks around an
  entity, so very large boxes could be missed near their edges).
- Parts rotated by angles that are not multiples of 90° are wrapped in a "staircase" of small boxes that
  follows their real shape (grid step `rotatedCollisionStep`).
- Models are immune to mobs, pistons, explosions, fluids and damage.
- All parts share a model id; models are saved per dimension in `data/solid_displays_models.dat`.
  Parts that were in unloaded chunks when a model was dismantled are removed as soon as they load.

## Configuration

`config/solid_displays.json` on the server (the client receives the server's values when joining).
`/soliddisplays reload` (operators) re-reads it.

| Option | Default | Meaning |
|---|---|---|
| `maxElements` | 256 | Parts per model |
| `minElementSize` / `maxElementSize` | 0.0625 / 16 | Part size limits (blocks) |
| `maxModelRadius` | 16 | Parts must stay this close to the origin (blocks) |
| `maxPlaceDistance` | 8 | Max distance between player and placement spot |
| `maxModelsPerPlayer` | 0 | 0 = unlimited |
| `onlyOwnerCanDismantle` | true | Protect models from other players |
| `rotatedCollisionStep` | 0.125 | Collision accuracy for rotated parts |
| `maxCollidersPerRotatedPart` | 32 | Box limit per rotated part |
| `volumeMultiplier` | 1.0 | Items per cubic block of volume |
| `minPerBlockType` | 1 | Minimum items per block type used |
| `requireMaterials` | true | false = free placement for everyone |

Material formula: `amount = max(minPerBlockType, ceil(totalVolume × volumeMultiplier))`, where the volumes of
all parts made of the same block are added up first (eight half-block parts cost 4, not 8).

The server checks everything a client sends: the table must really be open, part count / sizes / distances
are within the limits, and technical blocks (barrier, command block, …) cannot be used. Blueprint data is
validated again on every placement.

## Building

```bash
./gradlew build
```

The mod jar is `build/libs/solid_displays-<version>.jar` (the `-sources.jar` next to it is only the source code).

## Automated in-game tests

`src/gametest` contains Fabric client game tests that run the real game with real mouse/keyboard input
(collision physics, designer, placement, multiplayer server). They are not part of the mod jar.

```bash
./gradlew runClientGameTest
```

On a headless Linux machine (software OpenGL):

```bash
LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

## License

MIT. All textures are original.
