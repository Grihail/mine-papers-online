# Mine Papers Online

**English** | [Русский](README.ru.md)

A Minecraft mod that adds a frame for your own pictures. The frame can show a picture from your computer, a slideshow from a folder, a picture from a link, or one from a public Yandex Disk folder. Pictures from a player's computer are uploaded to the server, so every player sees them.

The mod is built for several game versions from a single repository:

| Minecraft version | Loader | Java |
| --- | --- | --- |
| 1.7.10 | Forge 10.13.4.1614 | 8 |
| 1.12.2 | Forge 14.23.5.2847 and newer | 8 |
| 1.20.1 | Forge 47.4.28 | 17 |
| 1.20.1 | Fabric Loader 0.16+, Fabric API | 17 |
| 1.21.1 | NeoForge 21.1.259 | 21 |
| 1.21.1 | Fabric Loader 0.16+, Fabric API | 21 |

Author: Grihail. License: [LGPLv3](COPYING.LESSER). Changelog: [CHANGELOG.md](CHANGELOG.md).

## Contents

- [Features](#features)
- [Building](#building)
- [Repository layout](#repository-layout)
- [Common code and adapters](#common-code-and-adapters)
- [Translations](#translations)
- [Resources](#resources)
- [Data and formats](#data-and-formats)
- [Network packets](#network-packets)
- [Adding a new game version](#adding-a-new-game-version)
- [Project rules](#project-rules)
- [License](#license)

## Features

### Picture frame

The frame is crafted (shapeless) from a vanilla item frame and a painting. It can be placed on any face of a block: on a wall, on the floor and on the ceiling. On the floor and ceiling the top of the picture points where the player was looking when placing it. The picture sits flush against the actual block surface, so it can hang on a slab, a carpet or the side of a fence.

In the world the picture is an entity, like vanilla paintings and item frames. It does not occupy block cells, so blocks and other pictures can stand next to it and in the same cell. It needs no support. The only restriction is that the canvas cannot pass through a block's collision or another picture. Two pictures on perpendicular walls can meet in an inner corner.

While holding a frame, the outline of the future picture is shown where it will go. If it cannot be placed there, the outline is red.

The settings window opens by right-clicking a hanging picture, or by right-clicking with a frame in hand while not looking at a block. In the second case the settings are stored in the item itself, and the picture is hung already configured. While the window is open the item cannot be moved. The window configures the whole stack in hand at once.

### Picture sources

| Source | What you enter |
| --- | --- |
| config/minepapers | A file or subfolder inside the `config/minepapers` folder. The `<` and `>` buttons cycle through what is there. |
| Yandex Disk | A link to a public folder or file. Works without authorization, through the public API. |
| Folder on this PC | The full path to a folder or file. |
| Picture link | A direct `http` or `https` link to the image file itself. |

Supported formats are png, jpg (including jfif, jpe, jif, pjpeg, pjp), gif, bmp and webp. Animated GIFs are shown animated; animated WebP shows only its first frame.

If the source has several pictures, the frame shows them as a slideshow with the given interval in seconds. The order is random, but it is derived from the game time and the frame's position. So all players see the same picture, while two frames with the same folder show different ones. Each picture is shown once per cycle.

### Size and position

- Canvas width and height are set in blocks or in pixels (1 pixel is 1/16 of a block). From 1 pixel up to 64 blocks. The canvas size is also the picture's hitbox.
- If width or height is left at 0, it is calculated from the current picture's proportions on save.
- Canvas offset to the right and up from the clicked cell. The canvas always covers that cell by at least one pixel.
- Padding of the image from the canvas edges, on all sides (0 or more).
- Zoom: makes the image bigger (positive) or smaller (negative) by the given amount on each side. What goes past the padding is cropped. Padding and zoom have their own "px" checkboxes.
- Fitting the image into the canvas works like CSS `object-fit`, always from the center: fill, contain, cover, none, scale-down.

### Look and style

The frame and the canvas are chosen separately on the "Frame" tab, with the buttons to the right of their slots. The first button in each row is an eye: it turns the frame or canvas off. In the frame row, the field after the buttons is the molding width in pixels.

| Frame | Looks like |
| --- | --- |
| No frame | No frame. The canvas edges are made of the canvas material. |
| Edges | The frame covers only the canvas edges (like the "Panel" look). Without a canvas, choosing it turns on the block canvas. |
| Molding (width 1 or more) | A real frame of the given width in pixels: it covers the edge of the picture and sticks out above it. 0 means no molding. |

| Canvas | Looks like |
| --- | --- |
| No canvas | The picture is right on the wall. Without a frame it is graffiti: a PNG with a transparent background looks like writing or a drawing on the wall. Edges are not available without a canvas, molding is. |
| Block canvas | The picture on a 1-pixel thick canvas. |
| Picture itself | The picture is 1 pixel thick, the edges stretch its outer pixels. |

The "Style" tab sets colors and resolution:

- **Sharp pixels** (on by default): no semi-transparency, a pixel is either fully visible or fully transparent, like on maps. The edges of a picture with a transparent background stay crisp instead of blending into the wall.
- **Map palette**: only the colors that maps of this game version can show, like real map art. The "Colors" slider is inactive with it, dithering still applies.
- **Colors**, 2-25 (default 25): posterization, how many shades each color channel keeps. 25 is closest to the original.
- **Dithering kind**, two buttons: diffusion (Floyd-Steinberg, default: smooth, the dots look random) or grid (ordered, Bayer 4x4: the dots form a regular square grid).
- **Dither**, 0-100% (default 100%): mixes the colors with dots for smooth gradients. 0% gives flat areas of color.
- **Pixels per block**, 1-256 (default 128). Dragging changes it by 1, the mouse wheel jumps to the neighbouring power of two (1, 2, 4 ... 256). 128 looks like a map in an item frame, 16 like a vanilla painting.

The mouse wheel over a slider moves it. The picture uses smooth lighting, like vanilla blocks.

### Materials and glow

The "Frame" tab has three slots:

- **Frame.** The block whose texture the frame is made of.
- **Canvas.** The block whose texture the canvas is made of.
- **Glow.** A light-emitting block. The picture lights the world with its light level: invisible light blocks are placed in front of the canvas. Glowstone dust makes the picture bright but does not light anything around it.

The slot takes a real item. When the picture is broken, the items are part of its drop.

The glow slot shows the light level. A frame or canvas slot that the chosen look does not need turns pale, and its blocks are returned to the player when the window closes.

For a stack of pictures in hand, the slot holds one block per picture: a stack of 16 pictures with an oak frame has 16 planks in the slot. Putting a stack of blocks into an empty slot gives the material to as many pictures as there are blocks. Taking some blocks out of the slot removes the material from the same number of pictures. If not all pictures in the stack changed, the changed ones are split off into the inventory, or dropped on the ground if there is no room. Right-click puts one block or takes half, Shift+click moves all blocks to the inventory. The server can forbid this (see [Mod settings](#mod-settings)): then materials change only for a single picture in hand.

### Title, description and lock

- A picture has a title and a description. The title shows under the picture when you look at it while holding Shift (above it for a picture on the floor). Both are shown in the item tooltip.
- `http` and `https` links in the description are underlined. In the window of someone else's picture they can be clicked.
- The lock makes the picture private. Only the owner can change it. Others see only the title and description, but anyone can break the picture. A private picture in the inventory has the enchantment glint.
- The quill button above the lock signs the picture: only a single unlocked picture, only once, after a vanilla "Yes / No" confirmation. The signature stays forever: it is not in the config for copying, and a signed picture cannot be copied by crafting or reset to a blank frame. The button then shows the signer's head; the nickname is in the tooltips and, in italics, next to the title when looking at the picture with Shift.
- The "18+" button above the quill marks the picture as NSFW. Anyone who can change the picture can set or clear the mark. Players with `hideNsfw` see an empty canvas with a red "18+" mark instead (see [Mod settings](#mod-settings)).

### Copying settings

The buttons to the right of the window copy all picture settings to the clipboard as JSON and paste them back. The JSON can be edited in any text editor and pasted into another picture, even in another game version. On paste, materials are taken from the player's inventory, or created in creative mode. If something is missing, the mod says so in chat.

### Behavior in the world

- In survival, breaking a picture takes as long as a wooden block. An axe is faster, in water and while jumping it is slower. In creative mode the picture breaks instantly.
- Explosions break the picture and drop it.
- A picture can be opened or broken from the same distance at which the player reaches blocks.
- Middle-click in creative mode gives a copy of the picture with all its settings.
- A broken picture drops as an item that keeps its settings.

### Recipes

| Recipe | Result |
| --- | --- |
| Item frame + painting | A new picture frame. |
| Configured frame + blank frames | The same number of copies of the configured frame, without materials. A signed one cannot be copied. |
| A single configured frame with materials | The materials are returned to the player, the picture keeps its settings as graffiti. |
| A single configured frame without materials | A blank frame. A signed one cannot be reset. |

Private pictures do not take part in copy recipes.

### Pictures on the server

Pictures from local sources (config/minepapers and a folder on the computer) exist only on the computer of the player who has them. So when settings are saved, the mod uploads them to the server:

1. The client prepares the files. Small png, jpg and gif files are sent as is. Others are scaled down to 2048 pixels on the longer side and re-encoded as PNG (if they have transparency) or JPEG.
2. The client sends the server a list of SHA-1 hashes of these files.
3. The server asks only for the files it does not have yet, and accepts only those.
4. Once all files are in place, the server writes the list into the picture, and all players see it.

Other players download the pictures from the server one by one and keep them in a disk cache. Identical files from different frames and different players are stored and downloaded only once.

Pictures from the internet (Yandex Disk and links) are not uploaded to the server, each player downloads them on their own.

In singleplayer and in a world opened to LAN, the host uploads the pictures of their frames automatically.

### Mod settings

The `config/minepapers.json` file is shared by all game versions. The mod creates it on first launch and reads it on every launch. The file may contain `//` comments. `stackMaterials`, `disassembleSigned` and `disassembleLocked` work on the server (a client connected to a server uses the server's value), `hideNsfw` and `titleNeedsSneak` only on the client where they are set.

| Key | Default | What it does |
| --- | --- | --- |
| `stackMaterials` | `true` | `true`: a stack of pictures in hand changes materials as a whole, one block per picture. `false`: materials change only for a single picture in hand, a stack's material slots are locked, and the "Frame" tab says why. This lets a server admin prevent players from mass-producing pictures with materials. |
| `hideNsfw` | `false` | Client only. `true`: pictures marked as NSFW (18+) show an empty canvas with a red "18+" mark in the center (1x1 block) instead of the image, the item icon shows the placeholder with the same mark, and their images are not downloaded. The source link stays visible in the window and the item tooltip. Takes effect after restarting the game. |
| `titleNeedsSneak` | `true` | Client only. `true`: the title and signer over a picture show only while looking at it with Shift held. `false`: as soon as you look at the picture. |
| `disassembleSigned` | `false` | `true`: a signed picture can be taken apart in the crafting grid (materials out, then reset to an empty picture, which removes the signature). Signed pictures can never be copied. |
| `disassembleLocked` | `false` | `true`: a private (locked) picture can be taken apart in the crafting grid (the reset removes the lock). Private pictures can never be copied. |

## Building

Gradle runs on JDK 25: the RetroFuturaGradle plugin for 1.7.10 and 1.12.2 is built for Java 25. Gradle finds Java 8 and Java 21 for the mod versions themselves through toolchains.

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-25
gradlew build
```

In PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\gradlew.bat build
```

Or with one command from the repository root: `build.bat`. It sets JAVA_HOME itself (another JDK 25 path can be given in the `MINEPAPERS_JDK` variable) and builds all versions. `build.bat test` also runs the game tests of all versions; any other arguments go to `gradlew` as they are.

Output files:

```
1.7.10/forge/build/libs/MinePapersOnline-1.7.10-forge-<version>.jar
1.12.2/forge/build/libs/MinePapersOnline-1.12.2-forge-<version>.jar
modern/versions/1.20.1-forge/build/libs/MinePapersOnline-1.20.1-forge-<version>.jar
modern/versions/1.20.1-fabric/build/libs/MinePapersOnline-1.20.1-fabric-<version>.jar
modern/versions/1.21.1-neoforge/build/libs/MinePapersOnline-1.21.1-neoforge-<version>.jar
modern/versions/1.21.1-fabric/build/libs/MinePapersOnline-1.21.1-fabric-<version>.jar
```

On Fabric, Fabric API must be in the mods folder next to the mod.

The mod version is set in `gradle.properties` (`mod_version`).

Build a single version:

```bat
gradlew :modern:1.21.1-neoforge:build
```

Only check that everything compiles:

```bat
gradlew compileJava
```

Run the game from the development environment:

```bat
gradlew :modern:1.21.1-neoforge:runClient
gradlew :modern:1.21.1-fabric:runClient
```

### Tests

- `gradlew :common:test` (part of `build`) - tests of the common code without the game (JUnit, `common/test`): settings and their limits, the config, geometry, the picture mesh, the settings window, picture exchange.
- `gradlew gameTest` (or `build.bat test`) - game tests: the server of every version and loader (except 1.7.10) starts by itself, runs the tests and exits with an error if any of them failed. The tests of modern versions are shared (`modern/src/gametest`); a loader only registers them (the node's `src/gametest`). 1.12.2 has no game test framework of its own: the same checks live in a separate test mod `1.12.2/forge/src/gametest` (not in the jar). The task `:1.12.2-forge:runGameTestServer` starts a dedicated server with a flat world (`run/gametest`); once the server has started, the mod runs the checks and shuts it down, with exit code 1 on failure.
- `gradlew :1.12.2-forge:runClientScreenshots` - the 1.12.2 client creates a flat world by itself, opens the window of the picture in hand, saves a screenshot of every tab into `1.12.2/forge/run/screenshots-client/screenshots/` and exits. Not part of `gameTest`: it needs a screen.

## Repository layout

```
Mine Papers Online/
    build.gradle            shared settings for all versions
    settings.gradle         list of version subprojects
    gradle.properties       mod group, name and version
    build.bat               builds all versions with one command
    common/                 everything shared by all versions
        java/               shared code (Java 8, no Minecraft API)
        test/               its tests (JUnit)
        lang/               translations in JSON format
        resources/          textures and item table
        proguard/           ProGuard rules for the WebP libraries
    1.7.10/forge/           Minecraft 1.7.10 + Forge version
    1.12.2/forge/           Minecraft 1.12.2 + Forge version; src/gametest - its game tests
    modern/                 modern versions (1.20+) - one Stonecutter tree
        src/                shared code and resources without a loader, with //? conditions for versions and loaders; game tests
        versions/<version>-<loader>/   node: loader code, its mod description, gradle.properties with versions
        forge.gradle, neoforge.gradle, fabric.gradle   build of each loader's nodes
        stonecutter.gradle  the active version
```

### Version subprojects

Each "Minecraft version + loader" pair is a subproject `<version>-<loader>` (the list is in `settings.gradle`). The Minecraft version is taken from the first part of the subproject name, so the name matters.

Old versions live one by one in the folder `<version>/<loader>` (1.7.10, 1.12.2).

Modern ones (1.20+, Mojang class names) are one [Stonecutter](https://stonecutter.kikugie.dev/) tree in `modern/`: subprojects `:modern:<version>-<loader>` in `modern/versions/`. All loader-free code (vanilla Minecraft only) lives once in `modern/src`; where versions or loaders differ, the code is marked with conditions in comments: `//? if >=1.20.5 {` ... `//?} else {` ... `//?}`, `//? if fabric {`. For each node Stonecutter turns the right branch on and the others off (comments them out). `modern/src` holds the active version's code uncommented (`stonecutter.gradle`; in git it is always `vcsVersion` from `settings.gradle`); the Stonecutter task "Set active project" makes another version active in the IDE. The loader code (entry point, registration, events, mixins) and its mod description live in the node's `src`, dependency versions in the node's `gradle.properties`, and the build is shared per loader: `modern/<loader>.gradle`. The condition constants are the loader names `fabric`, `neoforge`, `forge`.

For each subproject, the root `build.gradle`:

- adds `common/java` as an extra source folder;
- copies `common/resources` into the jar. For versions before 1.13 the `textures/block` and `textures/item` folders are renamed to `textures/blocks` and `textures/items`;
- puts the translations from `common/lang` into `assets/minepapers/lang`. For versions before 1.13 they are converted to the `.lang` format (see [Translations](#translations));
- fills in the mod version and Minecraft version in `mcmod.info`, `version.properties`, `META-INF/neoforge.mods.toml` and `fabric.mod.json`;
- shrinks the WebP libraries with the `shrinkWebp` task and puts their classes into the jar (see [Common code and adapters](#common-code-and-adapters));
- compiles the mod classes without local variable names (`-g:source,lines`): crash reports keep line numbers, and the jar is smaller.

A version's own `build.gradle` only keeps its plugin, Java version and loader version.

### common/java

Package `com.grihail.minepapers`. Only things that do not depend on the game version go here. The code must build on Java 8 and cannot use Minecraft or loader classes. Libraries present in all game versions can be used: Gson and log4j.

| File | What it does |
| --- | --- |
| `picture/PictureSettings.java` | Picture position and settings: fields, constants (`SOURCE_*`, `FIT_*`, `MODE_*`, `FRAME_*`, `CANVAS_*`, old `STYLE_*`), source key, size in blocks, NBT read and write through the `Tag` adapter. The version's `PictureFrame` extends it. |
| `picture/CanvasGeometry.java` | Picture geometry: "wall coordinate" axes, the frame cell corner, the canvas box in the world, light cells, pictures meeting in a corner. The version's `FrameGeometry` extends it. |
| `ModConfig.java` | Mod settings from `config/minepapers.json`. |
| `picture/MaterialStack.java` | Material slot for a stack of pictures: how many pictures a click changes and whether the stack may be changed. |
| `picture/MaterialSlots.java` | Items of the configured picture on the server: clicks on material slots, Shift+click, pasting materials from a config, saving settings from the client (blocks the look does not need go back to the player). The item stack is a version class: the version provides the `HolderMaterials` adapter (whether a stack is empty, its count, a copy, whether it is the same item, the material slots, the player inventory). |
| `picture/PictureTarget.java` | What the window configures, as a single number for packets: the picture entity id or the inventory slot of the picture item. |
| `picture/PictureConfig.java` | Picture settings as JSON for copy and paste. Translation of item names between versions before and after 1.13. |
| `picture/Links.java` | Finding links in the description. |
| `picture/Numbers.java` | Numbers in window fields: parsing, writing without trailing zeros, what can be typed and the step limits. |
| `client/PictureManager.java` | Loading pictures from all sources, decoding, memory cache, slideshow, unloading textures that have not been drawn for a while. |
| `client/ImageStylizer.java` | Preparing the texture for the canvas: fitting, pixelation, posterization, dithering. |
| `client/GifFrames.java` | Assembling animated GIF frames into full images. |
| `client/ImageSync.java` | Client side of picture exchange with the server: preparing and uploading, downloading into the cache. |
| `client/ClientHost.java` | Adapter: what the client code needs from the game version. |
| `server/ImageStore.java` | Server side of picture exchange: receiving, storing in the world folder, serving to players. |
| `server/ServerHost.java` | Adapter: what the server code needs from the game version. |
| `network/SharedImage.java` | Server picture format, SHA-1, packet chunk sizes. |
| `client/PictureMesh.java` | The whole picture in the world: canvas, frame, molding, image, plate, smooth light, glow, NSFW mark, slide choice. The version only outputs vertices, gives the light of a cell and switches textures. |
| `client/IconLayout.java` | Layout of the configured item icon: canvas, frame ring, visible part of the thumbnail. |
| `client/FrameEditor.java` | The settings window without widgets: chosen values, toggles, config copy and paste, saving into the picture, status text. The version draws the widgets. |
| `picture/PictureLights.java` | Light blocks of a glowing picture: which cells are taken and their update. The version places the blocks. |
| `picture/BreakTimer.java` | Server check that the player broke the picture long enough. |
| `picture/MapTag.java` | An in-memory settings tag, to compare settings without version classes. |

### Version folders

All versions are organized the same way. Packages inside `com.grihail.minepapers` (for modern versions they are in `modern/src`, shared by all their versions and loaders):

| Package or file | Contents |
| --- | --- |
| `MinePapers.java` | Main mod class: registers the item, entity, recipes, window and packets. Also holds the path to `config/minepapers`. In 1.21.1 it has no loader code: it says what to register and how to create it, and the loader registers it. |
| `CommonProxy.java`, `client/ClientProxy.java` | Code that works differently on client and server. |
| `picture/PictureFrame.java` | Picture settings plus materials (items in the slots). |
| `picture/NbtTag.java` | This version's NBT adapter for `PictureSettings`. |
| `picture/FrameGeometry.java` | World checks: whether the canvas intersects blocks and other pictures. |
| `picture/EntityPicture.java` | Picture entity: hitbox, light, breaking, saving, syncing to clients. |
| `picture/FrameHolder.java` | The picture the window configures: an entity or an item in the inventory. Reading and writing the item's settings. |
| `picture/ContainerPictureFrame.java` | Window container: player inventory and material slots. The slot with the item being configured is locked. Clicks on material slots are handled by the server (`MaterialSlots`): a stack of pictures changes as a whole, or the changed pictures are split off from it. |
| `picture/HolderMaterials.java` | The `MaterialSlots` adapter on this version's item stacks. |
| `client/GuiPictureFrame.java` | Picture settings window. |
| `client/GuiWidgets.java` | Buttons, tabs, checkboxes and the lock of the window. Drawn with vanilla textures. |
| `client/RenderPicture.java` | Rendering the picture in the world. |
| `client/RenderPictureItem.java` | Item icon with a picture thumbnail. |
| `client/PictureControls.java` | Picture targeting, breaking, outline and the outline of the future picture. |
| `client/FrameSkin.java` | Frame or canvas texture from the item in the slot. |
| `client/MinePapersClientHost.java` | `ClientHost` implementation for this version. |
| `server/ServerImages.java` | Player permissions for a picture and the `ServerHost` implementation. |
| `network/` | Network packets (see [Network packets](#network-packets)). |

Version differences:

- **1.7.10.** The picture item is the `ItemBlock` of the `BlockPictureFrame` block; the recipe and item are in the `block` package. The `block` package also has classes for old saves where pictures were blocks: `TileLegacyPicture` turns such a picture into an entity, `BlockPictureFiller` removes leftover parts. Light is placed with a custom invisible block `BlockPictureLight`. A custom multiline text field `GuiTextArea`, because 1.7.10 has none.
- **1.12.2.** The code is close to 1.7.10 (MCP names, item metadata, old OpenGL), but: the picture item is a plain `Item` in the `item` package (there is no picture block and no classes for old saves), registration goes through `RegistryEvent`, an empty stack is `ItemStack.EMPTY`, vertices go through `BufferBuilder`, OpenGL state through `GlStateManager`. Light is a custom invisible block `BlockPictureLight` with its strength in the `level` state. Item settings are in NBT under the `MinePapersFrame` key, as in 1.7.10. The configured picture icon is drawn by a `TileEntityItemStackRenderer` (`RenderPictureItem`): `ClientProxy` gives configured pictures their own model `RenderPictureItem.Model` with a built-in renderer, which also tells where the item is drawn (inventory or world). The default frame and canvas textures are added to the block atlas (`FrameSkin.Events`). The `GuiTextArea` field is the same as in 1.7.10. The resources have a `pack.mcmeta` with format 3: without it Forge 1.12.2 treats the mod as an old-format resource pack and looks for translations named `en_US.lang` instead of `en_us.lang`. RetroFuturaGradle uses Forge 14.23.5.2847 for the development environment; the mod runs on it and on all later 1.12.2 builds.
- **1.21.1.** The item and recipe are in the `item` package. Settings are stored in the `minepapers:frame` item component. Light is placed with the vanilla `minecraft:light` block. Window tooltips use vanilla `Tooltip`. Custom render types are in `client/PictureRenderTypes.java`. The picture position and settings reach the client as vanilla entity data (`SynchedEntityData`), without loader spawn data. Everything that depends on the loader comes to the shared code through `Platform` (packets, a window with data for the client, the current server) and `client/ModClient` (client events). The loaders are thin wrappers: NeoForge - `neoforge/MinePapersNeoForge` and `MinePapersNeoForgeClient` (registration, packets, events); Fabric - `fabric/MinePapersFabric`, `MinePapersFabricClient` and two mixins for things Fabric API has no events for: mouse clicks before vanilla handling (`MinecraftMixin`) and returning materials on crafting (`ResultSlotMixin`).
- **1.20.1** (the same `modern/` tree, differences marked with `//?` conditions). Item settings are in its NBT under the `MinePapersFrame` key, as in 1.7.10 (there are no item components before 1.20.5). Packets are the same `ModPacket`s carried by the loader's channel: Forge `SimpleChannel`, Fabric packets by `ResourceLocation` with the handler moved to the main thread. The window is drawn with the old GUI textures (`widgets.png`, `tabs.png`, `beacon.png`): there are no GUI sprites before 1.20.2. Data folders are plural (`recipes`, `structures`, `tags/entity_types`); the build renames them, resources stay in the 1.21 layout. The Forge node has a `pack.mcmeta` in its resources: without it Forge 1.20.1 does not load the mod's data.

## Common code and adapters

The common code does not know Minecraft classes. Everything version-specific comes through small adapters. Each version implements them and installs them at startup.

**ClientHost.** Installed when the client starts (`ClientHost.install`). Gives the common client code:

- the game folder and the `config/minepapers` folder;
- the User-Agent for internet requests;
- this version's base map colors (for the map palette; their number differs between versions);
- the "connected to a server" and "server running in this client" flags;
- uploading a frame to video memory and freeing it. Each version has its own texture handle: in 1.7.10 and 1.12.2 it is an OpenGL texture id, in 1.21.1 a `ResourceLocation`. The common code keeps it as an `Object` and passes it back to the version's renderer;
- sending the three client packets for picture exchange.

`ClientHost.install` also registers the TwelveMonkeys WebP reader in ImageIO, because ImageIO cannot read WebP by itself. The full TwelveMonkeys libraries weigh ~500 KB, so the `shrinkWebp` task in the root `build.gradle` runs them through ProGuard with the rules from `common/proguard/webp.pro`. ProGuard keeps only the WebP reader and what it needs (~90 KB), gives the classes short names and moves them into the `com.grihail.minepapers.lib` package, so they cannot clash with a full TwelveMonkeys copy from another mod. The resulting classes go into the jar together with the resources, the same way in both versions. The mod itself is not compiled against TwelveMonkeys: `ClientHost` creates the reader by the name `com.grihail.minepapers.lib.WebPImageReaderSpi`, set in `common/proguard/webp.map`. The table of old and new names (to read a stack trace from a crash report) is the subproject's `build/webp/mapping.txt`.

**ServerHost.** Installed when the mod starts (`ServerImages.install`). Gives `ImageStore`:

- the current world folder;
- picture settings by dimension and the number from `PictureTarget`;
- writing the list of uploaded pictures into the picture;
- sending the two server packets.

The player and dimension are passed to `ImageStore` as `Object`. The common code only stores them and hands them back to the version. For a picture item, the UUID of the player whose inventory holds it is passed instead of the dimension.

**PictureSettings.Tag.** A wrapper over the version's NBT tag (`NbtTag`). Numbers are read and written as int: both versions can read any numeric tag as int. UUID is stored as a string in 1.7.10 and 1.12.2 and as an int array in 1.21.1.

**Inheritance.** `PictureFrame extends PictureSettings` and `FrameGeometry extends CanvasGeometry`. So version code writes `PictureFrame.FIT_COVER` or `FrameGeometry.normal(frame)`, even though the constants and methods live in the common code.

Player permissions are checked by the version. `ImageStore` is called after the check.

## Translations

Translations live in `common/lang`, one JSON per language: `en_us.json`, `ru_ru.json`, `ja_jp.json`, `zh_cn.json`. This is the only set of translations for all versions. Keys are the same in all versions.

At build time the translations are converted to each version's format:

| Versions | File in the jar |
| --- | --- |
| 1.13 and newer | `en_us.json` unchanged |
| 1.11 - 1.12.2 | `en_us.lang` |
| before 1.11 | `en_US.lang` |

The `.lang` format is produced by the `convertLang` task in the root `build.gradle`: `key=value` lines, a line break inside a value is written as `\n`.

A line break in JSON is a plain `\n`. Version 1.21.1 wraps such lines by itself. Versions 1.7.10 and 1.12.2 get a literal `\n` in `.lang` and split the line on it in the window code.

Item and entity names in 1.7.10 and 1.12.2 use the same keys as in 1.21.1 (`item.minepapers.picture_frame`, `entity.minepapers.picture`). For this, they override the methods that return names.

A new key is added to all four JSON files.

## Resources

`common/resources/assets/minepapers`:

| File | What it is |
| --- | --- |
| `textures/block/frame_border.png` | Default frame. |
| `textures/block/frame_canvas.png` | Default canvas. |
| `textures/item/picture_frame.png` | Item icon. |
| `textures/gui/picture_frame.png` | Window icons: tabs, sources, fit, frame and canvas looks, checkboxes, copy, paste, lock, quill, 18+. The layout is described at the top of `GuiWidgets.java`. |
| `textures/misc/alpha_grid.png` | Checkerboard under a transparent picture on the item icon. |
| `textures/misc/nsfw.png` | Red "18+" mark on a hidden NSFW picture. |
| `flattening.txt` | Table "old item name + metadata -> name in newer versions". Needed to paste a picture config from 1.7.10 into 1.21.1 and back. Built from the vanilla DataFixer. |

Resources needed by only one version live in its folder. In 1.21.1 these are the item models, recipes and the tag (in `modern/src/main/resources`), plus the mod description for each loader: `neoforge.mods.toml`, and `fabric.mod.json` with the mixins and the access widener. The tag `data/sable/tags/entity_type/wall_entities.json` is for Sable (Create Aeronautics sublevels): with it, the picture moves along with the moving contraption it hangs on. In 1.7.10 these are `mcmod.info` and `version.properties` (the mod version for FML), in 1.12.2 the same plus the item models (`models/item`).

## Data and formats

### Picture settings in NBT

The same for all versions (`PictureSettings`):

| Key | Value |
| --- | --- |
| `FX`, `FY`, `FZ` | Frame cell. |
| `Facing` | The side the picture faces: 0 ceiling, 1 floor, 2-5 walls. |
| `Rot` | Rotation on the floor and ceiling, 0-3. |
| `Surface` | Offset from the cell boundary to the block surface, from -1 to 0. |
| `Src` | Source: 0 config/minepapers, 1 Yandex Disk, 2 folder, 3 link. |
| `Local`, `Url`, `Folder`, `ImageUrl` | Path or link for each source. |
| `Interval` | Slideshow interval in seconds. |
| `W`, `H`, `SizePx` | Canvas size and the "in pixels" flag. |
| `OX`, `OY`, `PosPx` | Canvas offset and the "in pixels" flag. |
| `Pad`, `PadPx` | Image padding and the "in pixels" flag. |
| `Zoom`, `ZoomPx` | Image zoom (positive - bigger, negative - smaller) and the "in pixels" flag. |
| `Title`, `Desc` | Title and description. |
| `Owner`, `OwnerName` | Owner of a private picture and their nickname. |
| `Signer`, `SignerName` | Who signed the picture and their nickname. Set only by the server, cannot be removed. |
| `Nsfw` | NSFW (18+) mark, 1 - marked. |
| `Molding` | Molding width in pixels. |
| `Sharp`, `MapPalette`, `Colors`, `Dither`, `DitherMode` | Sharp pixels (1 - no semi-transparency), map palette (1 - instead of the number of colors), number of colors (2-25), dither strength in percent and its kind (0 diffusion, 1 grid). Pictures from older versions have `Style` instead (0 original colors, 1-3 map palette without, with and with soft dithering); it is converted on load into the same look. |
| `Fit`, `PPB`, `Mode` | Fit, pixels per block, look. The look is a combination of frame and canvas: 0-3 are the old looks (panel, frame, graffiti, plate), 4-7 are new combinations (see `PictureSettings.LOOK_FRAME`). |
| `Files` | Pictures on the server: consecutive 20-byte SHA-1 hashes. |

Materials are written by the version: `Border`, `Canvas`, `Glow` (item id for the texture) and `BorderItem`, `CanvasItem`, `GlowItem` (the item itself). 1.7.10 and 1.12.2 also have `BorderMeta`, `CanvasMeta`, `GlowMeta`.

Where settings are stored:

- **Entity.** The picture position is right in its NBT, settings in `Settings`, the cells of placed light in `Lights`.
- **Item in 1.7.10, 1.12.2 and 1.20.1.** Item NBT, key `MinePapersFrame`.
- **Item in 1.21.1.** Component `minepapers:frame`.

### Config for copying

Example JSON that the copy button puts on the clipboard:

```json
{
  "mod": "minepapers",
  "format": 1,
  "minecraft": "1.21.1",
  "title": "Water Dragon",
  "description": "",
  "nsfw": false,
  "source": { "type": "yandex", "path": "https://disk.yandex.ru/d/...", "interval": 10 },
  "size": { "width": 2, "height": 2, "pixels": false },
  "offset": { "right": 0, "up": 0, "pixels": false },
  "padding": { "size": 0, "pixels": true },
  "zoom": { "size": 0, "pixels": true },
  "fit": "cover",
  "look": "panel",
  "frame": "edges",
  "canvas": "block",
  "style": "map-soft-dither",
  "sharpPixels": true,
  "mapPalette": true,
  "colors": 6,
  "dither": 45,
  "ditherMode": "diffusion",
  "pixelsPerBlock": 128,
  "materials": {
    "frame": { "item": "minecraft:oak_planks" },
    "canvas": null,
    "glow": null
  }
}
```

- Values are written as words: `config`, `yandex`, `folder`, `url`; `fill`, `contain`, `cover`, `none`, `scale-down`; `panel`, `frame`, `graffiti`, `plate`; `none`, `edges`, `molding` (frame); `none`, `block`, `picture` (canvas); `original`, `map`, `map-dither`, `map-soft-dither`. A number can be used instead of a word.
- With `"frame": "molding"`, `moldingWidth` is the molding width in pixels (2 if it is missing).
- `sharpPixels` turns semi-transparency off; `mapPalette` uses the map palette instead of `colors`; `colors` (2-25), `dither` (0-100) and `ditherMode` (`diffusion`, `ordered`) are the number of colors, the dither strength and kind. `style` is the color style from older mod versions: the closest one is written for them, and on paste `colors` and `dither` take precedence over it.
- `look` is the look from older mod versions. Next to `frame` and `canvas`, the closest `look` is written so that older versions understand the config. On paste, `frame` and `canvas` take precedence over `look`.
- If a field is missing, it is left unchanged on paste.
- For a material, `null` means "default", i.e. take the item out of the slot. A material can also be written shorter: `"frame": "minecraft:gold_block"`.
- The `minecraft` field tells the mod which naming the items are written in.

### Files on disk

| Where | What |
| --- | --- |
| `config/minepapers/` | The player's local pictures. The folder is created at startup. |
| `config/minepapers.json` | Mod settings (see [Mod settings](#mod-settings)). |
| `<game folder>/minepapers-cache/` | Cache of pictures downloaded from the server and prepared for upload. The file name is the SHA-1 in hex. |
| `<world folder>/minepapers/` | Pictures uploaded to the server. The file name is the SHA-1 in hex. |

A picture file on the server starts with a 12-byte header: `MPI1`, then the original width and height of the picture. Then comes the image file itself. The original size is needed because large pictures are scaled down before sending, while fitting is calculated from the original size. A picture is at most 16 MB.

## Network packets

| Packet | Direction | What it does |
| --- | --- | --- |
| `PacketOpenFrame` | client -> server | Open the picture settings window. |
| `PacketSaveFrame` | client -> server | New picture settings. The client cannot change the server file list or the owner, it can only ask to close or open the lock. |
| `PacketPasteMaterials` | client -> server | Which items to put into the material slots when pasting a config. |
| `PacketBreakPicture` | client -> server | The player started or finished breaking the picture. The server checks that enough time has passed. |
| `PacketOfferFiles` | client -> server | List of SHA-1 hashes of the pictures for a frame. |
| `PacketHashes.Need` | server -> client | Send me these pictures. |
| `PacketFileChunk.Upload` | client -> server | A chunk of an uploaded picture, up to 30000 bytes. |
| `PacketHashes.Request` | client -> server | Send me these pictures. |
| `PacketFileChunk` | server -> client | A chunk of a picture, up to 32 KB. `total = -1` means the server has no such picture. |

The rate is limited so as not to flood the connection. The client sends 2 chunks per tick. The server sends up to 3 chunks per tick to one player and up to 24 chunks per tick in total.

In 1.7.10 and 1.12.2 packet handlers run on the network thread, so server packets queue a task with `ServerImages.schedule`, and it runs on the server thread. In 1.21.1 handlers already run on the right thread.

## Adding a new game version

1. A modern version (1.20+): add it with its loaders to the `stonecutter` block in `settings.gradle`, create the node `modern/versions/<version>-<loader>` with `gradle.properties` (Java and dependency versions) and the loader code (following the neighboring node), and mark the version differences in `modern/src` with `//?` conditions. Steps 2-6 below are for old versions.
   An old version: create a `<Minecraft version>/<loader>` folder, for example `1.12.2/forge`, with its own `build.gradle`: plugin, Java version, loader version.
2. Add it to the version list in `settings.gradle`.
3. If a new build plugin is needed, declare it in the root `build.gradle` with `apply false`.
4. Port the version classes following the neighboring folders. Implement the adapters: `NbtTag`, `MinePapersClientHost`, `ServerImages` (as `ServerHost`). Make `PictureFrame` extend `PictureSettings` and `FrameGeometry` extend `CanvasGeometry`.
5. Install the adapters at startup: `ClientHost.install(...)` on the client and `ServerImages.install()` when the mod starts.
6. Translations, textures and the `.lang` format are picked up automatically from the version in the subproject name.
7. Hook up the game tests: the version's shared tests and the task that runs the test server, added to the root project's `gameTest` (see `modern/*.gradle`). If the version has no game test framework, use a test mod that runs the checks when a dedicated server starts (see `1.12.2/forge`).

If code is the same for two or more versions and does not use the game API, it moves to `common/java`. The copies in the version folders are then removed.

## Project rules

- All player-visible text (translations, window labels, tooltips, chat) uses plain punctuation: `-` instead of an em dash, `x` instead of the multiplication sign, plain quotes, `...` instead of a single ellipsis character. Japanese and Chinese translations may use the native punctuation of those languages.
- Code comments are written in Russian.
- Vanilla mechanics are used wherever possible: light blocks, wood sounds, drop rules, widget tooltips, vanilla window textures.
- The code does not guard in advance against every possible error. Limits stay where they define game rules (canvas size, position, resolution) or protect the server from foreign clients (the server accepts only the files it asked for, and no more than 16 MB). Other errors are fixed when they show up.
- Notable changes go into [CHANGELOG.md](CHANGELOG.md) under `[Unreleased]`. On release the section gets the version number.

## License

[LGPLv3](COPYING.LESSER). The text of GPLv3, which LGPLv3 refers to, is in [COPYING](COPYING).
