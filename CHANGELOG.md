# Changelog

All notable changes to the mod are listed here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.6.0]

### Added

- Glass over the picture: on the "Frame" tab, a new row between the frame and the canvas, with a slot and the "No glass" and "Glass" buttons. Plain glass by default; any block can go into the slot. Its texture covers the whole canvas without seams (the border only runs along the canvas edge), and stained glass is translucent. In the copied settings: the `glass` field and the `glass` material.
- Puzzle: a button and a group number field to the right of the fit list. Pictures with the same number on the same wall join into one: the image is fitted into their shared rectangle, each picture shows its own piece, and the slideshow is shared. The button turns the puzzle on with the smallest free number; any number can be typed into the field, so the picture joins that group and takes its settings. Source, style and frame look are synced across the whole group when any of its pictures is saved; size and offset stay per picture. Numbers are shared by all players; for a locked picture, only among its owner's pictures. In the copied settings: the `puzzle` field.
- New server setting `maxImageMb` in config/minepapers.json (default 16, from 1 to 256): the largest image players can upload to the server. Heavier images are scaled down before sending, and an animated GIF over the limit becomes a still image. Applies to new uploads.
- Minecraft 26.1.2, 26.2 and 26.3 on Forge and NeoForge (a separate jar for each version and loader).
- Minecraft 1.20.1: Forge and Fabric (Fabric needs Fabric API next to the mod).
- Minecraft 1.12.2 (Forge 14.23.5.2847 or newer).
- Online folder: public Google Drive folders (links like drive.google.com/drive/folders/... and .../drive/u/1/folders/...), no API key needed. Pictures are downloaded as a reduced copy up to 2048 pixels, GIFs in full.
- A glowing picture's inventory icon has a mark in the top right corner: a yellow dot for glowstone dust, a dot with a halo for a light-emitting block (torch, glowstone, etc.).
- Moving the image inside its area: the "Move" row on the size tab, with right and up fields and a "px" checkbox. Size, offset and padding set the area; zoom and move shift the image inside it. In the copied settings: the `image` field.
- Buttons for what glows, to the right of the glow slot on the "Frame" tab: everything, only the frame, or only the picture with its canvas (default). Before, everything glowed. In the copied settings: the `glowPart` field.
- A path or link pasted into the field of the wrong source moves to the right source's field, and the source switches: a Yandex Disk or Google Drive folder link goes to the online folder, any other link to the picture link, a full path to the folder on this PC, a name from config/minepapers to that source.
- The last frame shown by each picture is kept on disk (minepapers-cache/covers, separately for each world and server): when the picture's chunk loads again, it shows that frame right away instead of an empty canvas while the source is read again.

### Changed

- New frame recipe: an item frame, a painting, a book and quill and a glass pane (shapeless). The recipe is in config/minepapers.json (the `recipe` key): any items, including ones from other mods, shapeless or with a shape up to 3x3. In 1.7.10 and 1.12.2 it shows in NEI and JEI; in newer versions it works but does not show in the recipe book.
- Fit is picked from a dropdown with icons (opens upwards, the full description of each mode is in its tooltip) instead of five buttons in a row.
- Picture sounds are those of the vanilla item frame instead of planks: placing and breaking use the item frame sounds, hits while breaking are the item frame's soft click (in 1.7.10, where the item frame has no sounds of its own, they ship with the mod).
- The "Yandex Disk" source became the general "Online folder" source, with a folder-and-globe icon: the service is detected from the link, and the button's short tooltip lists the supported services. A link to an unsupported service gives a clear error. Pictures and configs set up before keep working as they did.
- A new picture has a 2-pixel molding by default (before, edges).
- The size tab is more compact: each setting has its own row (label, fields, "px"), with a separator between the canvas and image settings. Number fields show direction arrows in their tooltips instead of long text; row labels have a short note on how the setting works.
- On the "Frame" tab, the frame and the canvas are turned off with a regular button with a cross, like the neighbouring look buttons, instead of a separate eye icon.
- The frame and canvas buttons on the "Frame" tab no longer depend on each other: edges can be picked without a canvas.
- In a frame without a canvas, the image no longer lies flat on the wall like graffiti: it hangs in the frame at canvas level, and the wall shows through the transparent background. Graffiti is only without both frame and canvas.
- A picture stays in memory while its chunk is loaded and unloads with it. The mod's own unload rules (half a minute unseen, 48 blocks from the player) are gone.
- The "Source", "Size and position", "Frame" and "Style" tabs have no page title (the tab name is in its button tooltip), so their content moved up.
- A signed picture has the enchantment glint (like a locked one) and takes no part in crafting: it cannot be copied, taken apart or reset.
- New server settings `disassembleSigned` and `disassembleLocked` in config/minepapers.json (default `false`): with `true`, signed or locked pictures can be taken apart in crafting. They can never be copied.
- The glint of locked and signed pictures on the icon is brighter (in modern versions it was the armor glint instead of the item glint).

### Fixed

- The item of a broken picture flew far in a random direction: it spawned inside the picture, right against the wall, and got pushed out of the block. Now it drops in front of the middle of the canvas without scattering.
- While a Yandex Disk folder list was still loading, the slideshow switched pictures about once a second instead of waiting for the timer: the slide order was rebuilt with each new page of the list. Now a picture stays until its interval ends.
- Pictures near the player unloaded while a singleplayer game was paused: after the pause, turning around showed an empty canvas. Paused time no longer counts.
- Yandex Disk pictures appear faster: file links are taken straight from the folder list (no separate request per picture), JPEG photos are downloaded as previews up to 1280 pixels instead of the original, and while the current slide is loading, any already loaded picture is shown.
- A picture nobody had looked at for half a minute unloaded from memory even a couple of steps from the player: turning back showed an empty canvas while it loaded again. Now pictures closer than 48 blocks (from the canvas edge) stay in memory even when not visible.
- A Yandex Disk folder with many pictures took minutes to load, and the picture showed nothing until it finished; only the first 1000 files were shown. Now the list is read page by page: pictures appear right after the first page, the rest load in the background, and there is no 1000-file limit.
- "Sharp pixels" did not keep pixel art sharp: when the image was moved by a fraction of a pixel or scaled down, neighbouring pixels blended. Now pixels do not blend with this option; without it the image is smoothed as before. Frames of animated GIFs that have to be scaled down are no longer smoothed in advance.

## [1.5.1]

### Added

- Fabric 1.21.1. Fabric API is needed next to the mod in the mods folder.

### Changed

- A picture is lit like the blocks around it: the front and edges are shaded by the side they face, like vanilla block faces (darker on north and south walls, darker still on west and east). Before, a picture was noticeably brighter than the wall it hung on.

## [1.5.0]

### Added

- The frame and the canvas are picked separately on the "Frame" tab, with buttons to the right of their slots. Frame: no frame, edges, molding. Canvas: no canvas, block canvas, picture itself. An eye button turns the frame or the canvas off. Besides the four old looks there are new combinations: a molding around graffiti, a canvas without a frame, a plate in edges or in a molding.
- Signing a picture: a button with a quill above the lock. Only one unlocked picture can be signed, and only once, after confirming in the vanilla "Yes / No" window. The signature stays forever: it is not in the copied settings, and a signed picture cannot be copied by crafting or reset. The button shows the signer's head. With Shift held, the signer's name shows in italics next to the title.
- Frame and canvas blocks that the chosen look does not need go back to the player when the window closes. While the window is open, their slots are faded and the look can be switched freely.
- The `frame` and `canvas` fields in the copied settings.
- NSFW (18+) mark: the "18+" button above the signature; anyone who can edit the picture can set or remove it. A marked picture shows it in the item tooltip. New client setting `hideNsfw` in config/minepapers.json: with it, marked pictures show an empty canvas with a red "18+" badge in the middle instead of the image (1x1 block; on the item icon too, over the placeholder), and their images are not downloaded. In the copied settings: the `nsfw` field.
- New client setting `titleNeedsSneak` in config/minepapers.json: with `false`, the title and the signer's name above a picture show right away on hover, without Shift.
- Molding width: a number field instead of the molding button, width in pixels (0 means no molding). In the copied settings: the `moldingWidth` field.
- A picture without a canvas has a checkerboard under the image in the inventory: the transparent background is visible and the picture does not get lost in the slot.
- WebP images (including transparent ones) from every source. An animated WebP shows only its first frame. Small WebP files go to the server as they are, without re-encoding.
- Number fields in the window: only a number can be typed or pasted, the up and down arrows change it by 1, and so does the mouse wheel over the field, even without clicking it.

### Changed

- The "Look and style" tab became the "Style" tab, without a repeated title.
- A new picture uses the original colors by default (before, a palette with soft dithering).
- Material slots no longer have block names under them (they are in the block tooltip). The glow slot still shows the light level.
- The item tooltip shows the picture's frame and canvas.
- A picture's title shows only when looking at it with Shift held.
- Instead of four color styles (original colors, map palette, dithered, soft dithered): a "Sharp pixels" checkbox (on by default: no translucency, and the edges of an image with a transparent background do not smear over the wall, as with the map palette), a "Map palette" checkbox (as before, each game version has its own map colors; the color count slider does nothing with it) and sliders below them: color count from 2 to 25 (posterization) and dither strength from 0% to 100% (works with the palette too). The mouse wheel over a slider moves it. Old pictures and configs look as before: map palette with the same dithering and sharp pixels. In the copied settings: the `sharpPixels`, `mapPalette`, `colors` and `dither` fields.
- Dithering type: two icon buttons between the color count and dither strength sliders: diffusion (Floyd-Steinberg, as before, the default) and grid (ordered, Bayer 4x4: dots in an even square grid). In the copied settings: the `ditherMode` field.
- "Pixels per block" is a slider instead of an input field, next to the other sliders: the mouse changes the value by 1, the wheel jumps to the neighbouring power of two (1, 2, 4 ... 256).
- A wide molding has no "grid": a border of the frame texture runs along the edge, with seamless wood inside; on the side bars the grain runs along the bar.
- A zoom field with its own "px" checkbox to the right of the padding: above zero the image gets bigger, below zero smaller; whatever goes past the padding is cropped. In the copied settings: the `zoom` field.
- The edges button no longer turns off without a canvas: it turns on a block canvas by itself.
- The plain canvas is the vanilla painting back texture. The default frame is stick-colored instead of brass.
- The mod file is 2.5 times smaller (about 315 KB instead of 808 KB): only what is needed to read WebP goes into it from the WebP libraries (TwelveMonkeys).

### Fixed

- A picture without a canvas, with a large padding and a negative zoom, showed a plain canvas instead of the image: no room was left for the image, and the empty result was taken for "not loaded yet". Now zoom does not shrink the image below one pixel, and a loaded image of which nothing is visible does not draw the placeholder.
- GIFs in a big modpack played slower and in jerks: the animation followed game time, which slows down and jumps when TPS drops. Now it follows the real clock (players with correct clocks see the same frame). GIF frames are uploaded to video memory bit by bit over several game frames, without freezes, and images are processed in 2-4 background threads with slightly below normal priority instead of two with the lowest.
- In 1.7.10 the mod showed the old version 1.4.1 in the mod list and in the User-Agent: the version was written in the code. Now, as in 1.21.1, it comes from the mod description, where the build fills it in.
- The heads of the owner and the signer did not show on offline servers and in launchers with an offline account (Prism): now the skin comes straight from Mojang, by UUID or by name.

## [1.4.1]

### Added

- The "Description" tab: the picture's title and description. The title shows next to the picture when looking at it. The title and description show in the item tooltip.
- Links in the description are underlined. In the window of someone else's picture they can be clicked.
- Lock: only the owner can edit the picture; others see only the title and description. A locked picture has a glint in the inventory.
- Image padding from the canvas edges.
- In creative mode, pasting settings creates the missing materials.
- The picture settings window also opens for the item: right-click with a picture in hand while not looking at a block. The window sets up the whole stack at once. While the window is open, the picture cannot be moved.
- For a stack of pictures, the material slot holds one block per picture. A stack of blocks changes the material of the whole stack of pictures; fewer blocks change only some of the pictures: the changed pictures split off the stack into the inventory.
- The settings file `config/minepapers.json`, shared by all versions. The `stackMaterials` setting (on by default) lets a server admin forbid changing materials on a stack of pictures: then they change on one picture only.

### Changed

- One set of JSON translation files for all game versions. For 1.7.10, the `.lang` files are built from it.
- Shared code for loading images, exchanging them with the server, storing them on the server, settings and picture geometry. Both versions work the same way.
- In 1.7.10 a picture computes coordinates as precisely as in 1.21.1 and does not drift far from the world center.
- In 1.7.10 large pictures detect each other correctly when checking for overlap.
- In 1.7.10 a picture can be opened or broken from the same distance as in 1.21.1.
- In 1.7.10 lit variants of the lamp, furnace and redstone ore glow in the glow slot.
- Window tooltips in 1.21.1 are vanilla ones.
- The size label: "Size: width, height:". The "Size and position" and "Description" tabs have no tab title.
- Simpler code, smaller mod.

### Fixed

- Pictures have no cracks or particles when broken.
- The item icon offset for the "Frame" look.

## [1.4.0]

### Added

- A new settings window.
- A frame material slot: the frame is made from the texture of a real block placed in the slot.
- A canvas material slot.
- A glow slot: the picture glows with the light level of the block in the slot. Glowstone dust makes the picture bright but does not light up the blocks around.
- When a picture is taken apart in crafting, the player first gets back the frame, canvas and glow materials. To reset the picture completely, take it apart again.
- Copying and pasting all picture settings as JSON. Works between different game versions too.

### Fixed

- Texture mapping on the frame edges.

## [1.3.0]

### Added

- Animated GIFs.

## [1.2.1]

### Fixed

- Pictures on moving Create Aeronautics contraptions (Sable sublevels).

## [1.2.0]

### Added

- Minecraft 1.21.1 on NeoForge.
- Japanese and Chinese translations.
- The "Plate" look.

## [1.1.0]

First version.

### Added

- A picture frame. The picture is an entity: it takes no block space and needs no support.
- A picture can hang on any block face, including the floor and the ceiling.
- Picture sources: config/minepapers, a public Yandex Disk folder, a folder on this PC, a picture link.
- Resolution from 1 pixel per block.
