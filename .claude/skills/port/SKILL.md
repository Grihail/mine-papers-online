---
name: port
description: Порт мода Mine Papers Online на новую версию Minecraft и/или загрузчик (Forge, NeoForge, Fabric, любой другой) - с нуля, без блужданий. Использовать, когда пользователь просит портировать мод, добавить версию игры или загрузчик, "сделать под 26.x / 1.20.4 / Quilt", обновить зависимости узла под новую версию игры.
---

# Порт на версию игры или загрузчик

Порт = новый узел (подпроект `<версия>-<загрузчик>`), тонкий код загрузчика и, где нужно, условия в общем коде. Логика мода уже есть: `common/java` (без Minecraft) и `modern/src` (ванильный Minecraft 1.20+). Новый код загрузчика - только проводка событий в готовые методы. Сначала прочитать `docs/development.md` (разделы "Version subprojects", "Version folders", "Adding a new game version") - там устройство, здесь порядок работы.

## Правила (нарушались - читать до первого действия)

- **Несколько версий сразу.** Перед началом проверить соседние версии цели (то же API, есть сборки загрузчика) и сказать пользователю, какие войдут в порт вместе; сделать их все. Пример: просили Fabric 26.2 и 26.3 - сделаны 26.1.2, 26.2, 26.3.
- **Версия мода не меняется** (`mod_version` в `gradle.properties`), если пользователь не назвал другую.
- **Gradle - на JDK 25**: в Bash `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew ...`, в PowerShell `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'`. Системный JAVA_HOME сломан.
- **Gradle 9.3.1 не обновлять** ради одного плагина: от него зависят RetroFuturaGradle, MDG, ForgeGradle 7, Stonecutter. Брать последнюю версию плагина, которая работает на 9.3.1 (проверка - ниже).
- **Клиент игры не запускать** (`runClient` и т.п.), в инстансы Prism не лезть и jar туда не копировать. Проверка - компиляция, игровые тесты на сервере, javap. Вид в игре проверяет пользователь.
- **Не переписывать файлы PowerShell'ом** (`Get-Content | Set-Content` портит русские комментарии и пишет BOM). Правки - Edit/Write или Python с `encoding='utf-8', newline=''`.
- **Текст для игрока** - только ASCII-пунктуация (`-`, `x`, `...`, обычные кавычки). Комментарии в коде - по-русски.
- **Не трогать** `build/generated/stonecutter/...` (это вывод Stonecutter) и строку активной версии в `modern/stonecutter.gradle` (в git всегда `vcsVersion` из `settings.gradle`).
- Коммит - только по просьбе, через скилл `commit`.

## 1. Определить эпоху цели

| Эпоха | Признак | Где узел | Сборка | Образец |
| --- | --- | --- | --- | --- |
| Старые (до 1.13) | имена MCP, метаданные предметов | `<версия>/<загрузчик>/` со своим `build.gradle` | RetroFuturaGradle | `1.12.2/forge` |
| Современные с обфускацией (1.20 - 1.21.x) | имена Mojang через маппинги | `modern/versions/<версия>-<загрузчик>/src` (код загрузчика в узле) | `modern/<загрузчик>.gradle` | `1.21.1-fabric`, `1.21.1-neoforge`, `1.20.1-forge` |
| Без обфускации (26.x и новее) | в jar игры уже имена Mojang | код загрузчика один на эпоху: `modern/<загрузчик>26/src`, в узле только `gradle.properties` | `modern/<загрузчик>26.gradle` + `modern/mc26.gradle` | `neoforge26`, `fabric26`, `forge26` |

Нашёл образец - дальше работа по аналогии: тот же загрузчик на ближайшей версии плюс та же версия на другом загрузчике. Готовые порты как образец изменений: `git show --stat 580c998` (NeoForge 26.x), `git show --stat 97dd72f` (Forge 26.x, с правками общего кода).

Новая эпоха (например 27.x сломает что-то крупно): условия `//? if >=27.1` в `modern/src`, свой `mc27.gradle` по образцу `mc26.gradle`, папки `<загрузчик>27/src`.

## 2. Версии зависимостей

```bash
# Fabric: версии игры, загрузчика, Fabric API под версию игры
curl -s https://meta.fabricmc.net/v2/versions/game | python -c "import json,sys;print([v['version'] for v in json.load(sys.stdin) if v['stable']][:10])"
curl -s https://meta.fabricmc.net/v2/versions/loader | python -c "import json,sys;print([v['version'] for v in json.load(sys.stdin)][:5])"
curl -s https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -oE "<version>[^<]*\+26\.3</version>" | tail -3
# NeoForge
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>26\.3[^<]*</version>" | tail -3
# Forge
curl -s https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json | python -c "import json,sys;d=json.load(sys.stdin)['promos'];print({k:v for k,v in d.items() if k.startswith('26.')})"
```

Минимальная версия загрузчика для `fabric.mod.json` / `mods.toml` - та, что требует сам Fabric API / NeoForge этой версии (`"fabricloader"` в их `fabric.mod.json`).

**Подходит ли плагин к Gradle 9.3.1** - атрибут `org.gradle.plugin.api-version` в `.module` плагина должен быть не выше 9.3:

```bash
curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.15.5/fabric-loom-1.15.5.module | grep -E "plugin.api-version|jvm.version" | sort -u
```

Один плагин в сборке - одной версии на все узлы (плагины объявляются в корневом `build.gradle` с `apply false`). Известно: Fabric Loom 1.15.5 - последний на 9.3.1; для 26.x у него же есть id `net.fabricmc.fabric-loom` (без перемаппинга).

## 3. Читать API, а не угадывать

**API загрузчика** - исходники в scratchpad (скрипт рядом с этим файлом):

```bash
S=<scratchpad>; python -I .claude/skills/port/scripts/loader_sources.py fabric "$S/api" 0.161.0+26.2 0.166.2+26.3
python -I .claude/skills/port/scripts/loader_sources.py maven "$S/api" https://maven.neoforged.net/releases net.neoforged:neoforge:26.3.0.101
```

Потом `grep -rn` по нужным событиям; различия между версиями - `diff` списков классов. Fabric в 26.x переименовал модули в имена Mojang (`creative-tab-api`, `menu-api`, `resource-loader-v1`, `rendering.v1.level.LevelRenderEvents`, `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`).

**Классы игры** - javap по jar из кэша (после первой компиляции узла):

```bash
J="/c/Program Files/Java/jdk-25/bin/javap"
C=~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar      # Fabric; у NeoForge/Forge - jar из build/moddev или кэша mavenizer
"$J" -p -cp "$C" net.minecraft.client.Minecraft | grep -E "startAttack|pick"
"$J" -c -p -cp "$C" net.minecraft.client.renderer.LevelRenderer | awk '/ renderBlockOutline\(/,/^$/'
```

## 4. Проводка

1. `settings.gradle` - узел в блоке `stonecutter` (`version("<mc>-<загрузчик>", mc).buildscript('<скрипт>.gradle')`); старые версии - в список сверху.
2. Корневой `build.gradle` - новый плагин с `apply false`, если его ещё нет.
3. Узел: `gradle.properties` (`java_version`, версии загрузчика и API; для 26.x ещё `mc_upper` - верхняя граница версии игры, у Forge/NeoForge `forge_major`, `pack_max`, `pack_min`). Корневой `build.gradle` подставляет `${version}`, `${mcversion}` и эти ключи в `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`, `pack.mcmeta`.
4. Скрипт загрузчика: `stonecutter.constants.match('<загрузчик>', 'fabric', 'neoforge', 'forge')` (новый загрузчик - добавить его имя в этот список во всех скриптах), toolchain Java, наборы исходников `main` и `gametest`, задача игровых тестов в `rootProject.tasks.named('gameTest')`.
5. Код загрузчика по чек-листу ниже.

### Чек-лист: что обязан подключить загрузчик

Всё вызывает готовые методы общего кода. Сверять с образцом той же эпохи (`MinePapersNeoForge`, `MinePapersFabric` и их `...Client`).

Общая часть (класс реализует `Platform`):
- [ ] `MinePapers.init(<папка config>, <версия мода>, this)` первым делом.
- [ ] Регистрация пяти объектов через `MinePapers.make*()` и id `MinePapers.*_ID`: компонент `FRAME_SETTINGS` (1.20.5+), предмет `FRAME`, сущность `PICTURE`, сериализатор рецепта `FRAME_COPY`, меню `FRAME_MENU` (с данными открытия `ContainerPictureFrame.OpenData` write/read, клиент создаёт `ContainerPictureFrame.fromData`). Присвоить поставщиков в `MinePapers.FRAME` и т.д.
- [ ] Предмет во вкладку творчества `FUNCTIONAL_BLOCKS`.
- [ ] Пакеты (`ModPacket`, кодек из `write` и `read`), обработчики в главном потоке. На сервер: `PacketSaveFrame`, `PacketOfferFiles`, `PacketFileChunk.Upload`, `PacketHashes.Request`, `PacketBreakPicture`, `PacketOpenFrame`, `PacketPasteMaterials`, `PacketReloadRequest` -> `apply(player, msg)`. Клиенту: `PacketHashes.Need` -> `proxy.onFilesNeeded`, `PacketFileChunk` -> `proxy.onFileChunk`, `PacketReload` -> `proxy.onReload`.
- [ ] Сервер: тик -> `ServerImages.onServerTick()`, остановка -> `ServerImages.onServerStopped()`, выход игрока -> `ServerImages.onLogout(uuid)`, команды -> `CommandMinePapers.register(dispatcher)`, `Platform.server()` - текущий сервер.
- [ ] Крафт: игрок забрал результат -> `RecipePictureFrame.onCrafted(player, craftSlots)` (нет события - миксин на `ResultSlot.checkTakeAchievements`).
- [ ] `Platform`: `sendToServer`, `sendToPlayer`, `openMenu` (окно с `OpenData`), `server()`.

Клиент:
- [ ] `ModClient.init()`; отправка пакетов на сервер.
- [ ] Рендер сущности `RenderPicture::new`, экран меню `GuiPictureFrame::new`.
- [ ] Иконка настроенной рамки: до 26.1 - встроенный рендерер предмета (`RenderPictureItem.renderByItem`) и свойство модели `minepapers:configured` (`ModClient.configured`); с 26.1 - тип особой модели `RenderPictureItem.ID` -> `RenderPictureItem.Unbaked.MAP_CODEC` (событие загрузчика или ванильный `SpecialModelRenderers.ID_MAPPER`).
- [ ] Пересборка атласов / перезагрузка ресурсов -> `FrameSkin.clearCache()`.
- [ ] Конец клиентского тика -> `ModClient.onClientTick()`, отключение -> `ModClient.onDisconnect()`.
- [ ] Раз за кадр до сбора кадра -> `ModClient.CONTROLS.onRenderLevel(partialTick)`.
- [ ] Клики до ванильной обработки (ЛКМ, зажатая ЛКМ, ПКМ, колёсико) -> `ModClient.CONTROLS.onInteraction(attack)`; true - отменить клик и взмах.
- [ ] Обводка блока -> `ModClient.CONTROLS.onHighlightBlock(...)`: не null / true - ванильную не рисовать, нарисовать контур (с 26.2 - через `Recorder` в `SubmitNodeCollector`).
- [ ] До 26.1: `ModClient.itemColors` (цвета предметов; на Fabric через access widener).

Чего нет в API загрузчика - миксин (только `@Inject` в начало метода, имя метода сверить javap'ом во всех версиях узла) или access widener / access transformer.

Где разные версии одной эпохи отличаются API загрузчика - не в код загрузчика (он без условий), а в общий `modern/src/.../client/<Загрузчик>ClientEvents.java` под `//? if <загрузчик> && >=26.1` (образцы: `ForgeClientEvents`, `NeoForgeClientEvents`, `FabricClientEvents`).

## 5. Общий код

- Различия версий игры - условия Stonecutter в `modern/src`: `//? if >=26.2 {` ... `//?} else {` ... `//?}`; выключенная ветка закомментирована (`/*` ... `*/`, вложенные комментарии - `/^` ... `^/`). В `modern/src` без комментариев лежит код активной версии `1.21.1-neoforge`.
- Чистые переименования (`ResourceLocation` -> `Identifier` и т.п.) - строковыми заменами в `mc26.gradle`, а не условиями. Код загрузчика 26.x (`<загрузчик>26/src`) Stonecutter не обрабатывает: там сразу новые имена.
- Ошибка компиляции смотрится в `modern/versions/<узел>/build/generated/stonecutter/...` (номер строки оттуда), правится - в `modern/src`.
- Метод закрыт в ванили, а NeoForge/Forge его открывают (пример: `GuiGraphicsExtractor.componentHoverEffect`) - на Fabric открыть в access widener узла. В 26.x заголовок `accessWidener v2 official`.

## 6. Сборка и проверка

```bash
cd "<репозиторий>"; export JAVA_HOME="/c/Program Files/Java/jdk-25"
./gradlew :modern:26.3-fabric:compileJava --console=plain        # сначала один узел; первая сборка качает игру
./gradlew :modern:26.1.2-fabric:build :modern:26.2-fabric:build :modern:26.3-fabric:build --continue --console=plain
./gradlew :modern:26.3-fabric:runGametest --console=plain > "$TMP/gt.log" 2>&1; grep -E "GAME TESTS COMPLETE|tests passed|failed" "$TMP/gt.log"
```

- Игровые тесты: общие `modern/src/gametest/.../PictureGameTests`. До 26.1 загрузчик отдаёт `PictureGameTests.all()` (`@GameTestGenerator` / регистрация функций), с 26.1 - регистрирует `PictureGameTests.functions()` в реестр `TEST_FUNCTION` под пространством `ext.test_functions_namespace`, а описания `data/minepapers/test_instance` пишет `mc26.gradle`. Задача запуска - в `gameTest` корня. Все тесты должны пройти (сейчас 16).
- Сервер не грузит клиентские миксины: их цели проверить javap'ом во **всех** версиях узла (`defaultRequire: 1` уронит клиент при промахе).
- Содержимое jar: `unzip -p <jar> fabric.mod.json` (подставлены версия мода и границы версии игры), есть миксины, widener, `assets/minepapers/items/...` для 26.x.
- В конце - полный `build.bat` из корня (все jar и тесты `common`), при изменении общего кода - `build.bat test`.

## 7. Документы и учёт

- `CHANGELOG.md` (английский): строка `- Minecraft <версии> on <загрузчик> (...)` в `[Unreleased]`, или в раздел версии, которую назвал пользователь.
- `README.md` и `README.ru.md` - только строка таблицы версий.
- `docs/development.md` и `docs/development.ru.md`: список jar, схема папок, абзац о различиях узла.
- CI и публикация (`.github/scripts/curseforge_publish.py`) находят цели сами по `modern/versions/*`; новый загрузчик - в `LOADERS` скрипта, новая мажорная Java - в `setup-java` workflow.
- Память проекта (`porting-plan`): что сделано, чего нет.
- Ответ пользователю (по-русски): какие версии вошли и почему, пути jar (`modern/versions/<узел>/build/libs/...`), что проверено (тесты), что смотреть в игре (клики по картине, обводка, иконка настроенной рамки, окно настроек).
