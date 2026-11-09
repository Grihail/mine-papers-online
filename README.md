# Mine Papers Online

**English** | [Русский](README.ru.md)

A Minecraft picture frame for your own images: a file from your PC, a folder slideshow, an image link, or a public Yandex Disk or Google Drive folder. Images from your PC are uploaded to the server, so every player sees them.

| Minecraft | Loader |
| --- | --- |
| 1.7.10 | Forge |
| 1.12.2 | Forge |
| 1.20.1 | Forge, Fabric |
| 1.21.1 | NeoForge, Fabric |
| 26.1.2, 26.2, 26.3 | Forge |

Fabric needs Fabric API.

## How to use

Craft the frame from an item frame and a painting. Right-click a hanging picture to open its settings: source, size, frame and canvas material, glow, colors, title. Settings can be copied as JSON and pasted into another picture, even in another game version.

## Config

`config/minepapers.json`:

| Key | Default | What it does |
| --- | --- | --- |
| `stackMaterials` | `true` | A stack of pictures in hand changes materials all at once. |
| `hideNsfw` | `false` | Hide pictures marked 18+. |
| `titleNeedsSneak` | `true` | Show the title only while holding Shift. |
| `disassembleSigned` | `false` | Allow taking signed pictures apart in crafting. |
| `disassembleLocked` | `false` | Allow taking locked pictures apart in crafting. |

## Building

Needs JDK 25. Run `build.bat` (or `build.bat test` to also run game tests). Jars end up in `build/libs` of each version.

## License

[LGPLv3](COPYING.LESSER). Author: Grihail. Changes: [CHANGELOG.md](CHANGELOG.md).
