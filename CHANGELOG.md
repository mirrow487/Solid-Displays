# Changelog

## 1.0.0 — first release

For Minecraft 26.3 (Java Edition), Fabric Loader 0.20.1+, Fabric API 0.165.0+26.3, Java 25.
Install it on the server and on every client.

### Design Table
- Craftable Axiom-style editor for display models, no commands needed.
- Searchable block & item picker; every pick becomes a part (block display or item display).
- 3D preview with orbit / pan / zoom, floor grid and anchor block.
- Move gizmo (axis arrows) and size gizmo (face handles), snap steps 1, 1/2, 1/4, 1/8, 1/16.
- Exact position / size / rotation fields, quick 90° turns, per-part collision toggle.
- Part list with copy, delete, undo / redo (Ctrl+Z / Ctrl+Y / Ctrl+D / Delete).
- Live material cost of the whole model.
- Save the design onto an Empty Blueprint, or load a filled blueprint back for editing.
- Unfinished work stays in the table.

### Blueprints
- Tooltip with model name, rotation and the materials you have (green ✔) or are missing (red ✘).
- Translucent ghost preview where you look; red when materials are missing.
- Rotate with R or Shift + mouse wheel.
- Right-click to place: materials are taken from your inventory, the blueprint is kept.

### Placed models
- Rendered with vanilla Block / Item Displays.
- Real collision through invisible collider entities: walk on them, bump into them, no jitter, works in multiplayer.
- Large parts are split into several boxes; rotated parts get a collision shape that follows them.
- Not affected by mobs, pistons, explosions or fluids; survive chunk reloads and server restarts.
- Dismantle with the Dismantle Tool (or Shift + right-click), confirmed with a second click; all materials drop.
- Only the owner (or an operator) can dismantle a model by default.

### Server
- Every design and placement is validated on the server (limits, allowed blocks, reach, open table).
- `config/solid_displays.json` with limits and the material formula; the client follows the server's values.
- `/soliddisplays reload` for operators.
- English and Turkish translations.
