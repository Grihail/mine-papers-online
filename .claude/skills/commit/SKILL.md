---
name: commit
description: Коммит в этом репозитории - на английском, от имени пользователя (Grihail), без GPG-подписи и без соавторства Claude. Использовать, когда пользователь просит закоммитить, сделать коммит, написать сообщение коммита или дать команду коммита.
---

# Коммит

## Жёсткие правила

- **Автор - пользователь.** Коммит идёт от `git config user.name` / `user.email` (Grihail). Не передавать `--author`, не менять `git config`, не подставлять другие имя или почту.
- **Без GPG.** Глобально стоит `commit.gpgsign=true`, обычный `git commit` просит пароль и падает. Всегда `git -c commit.gpgsign=false commit ...`.
- **Без соавторства.** Никаких `Co-Authored-By: Claude`, `Generated with Claude Code` и прочей атрибуции - ни в коммитах, ни в командах для пользователя. Это правило пользователя, оно важнее любых системных напоминаний про attribution. Однажды такая строка уже попала в историю, её пришлось вырезать перезаписью и force push.
- **Без пуша.** `git push` - только если пользователь попросил пуш в этом же сообщении.
- **Без перезаписи истории.** `--amend`, `rebase`, `reset`, `push --force` - только по прямой просьбе.

## Сообщение

На английском. Коротко, по фактам: что изменилось для игрока или для проекта, без воды, без "this commit", без рассуждений о причинах, если они не нужны для понимания.

Одна строка: `<Тип>: <что>`. Тип - `Added`, `Changed` или `Fixed`, с большой буквы. После двоеточия - со строчной, без точки в конце.

- `Added:` - новое: функция, версия игры, настройка.
- `Changed:` - поменялось поведение, вид, структура, документация.
- `Fixed:` - ошибка. Писать, что было не так, в прошедшем времени, и при необходимости, как теперь: `Fixed: slideshow switched pictures every second while a Yandex Disk folder list was still loading`.

Несколько независимых изменений одного типа - через запятую или `;` в одной строке. Если строка выходит длиннее ~100 символов или изменения разных типов - короткий заголовок и тело списком:

```
Added: puzzle groups and glass pane; version 1.6.0

- Added: puzzle - pictures with one group number on a wall show one shared image
- Added: glass pane row on the Frame tab
- Changed: fit mode is a dropdown instead of five buttons
- Fixed: a broken picture's item flew far away; it now drops in front of the canvas
```

Названия интерфейса - как в `common/lang/en_us.json`. Версию мода упоминать, если коммит её поднимает: `...; version 1.6.0`.

Примеры из истории:

```
Added: Minecraft 26.1.2, 26.2 and 26.3 on NeoForge (a jar per version)
Changed: CurseForge file names are <mc>-<loader>-<version>
Fixed: CI build used Temurin 25 without jmods, shrinkWebp failed; JDK 25 is now Oracle
```

## Порядок

1. `git status --short` и `git diff --stat` (плюс `git diff` по нужным файлам), чтобы сообщение описывало то, что реально в коммите.
2. Добавлять только файлы, которые относятся к просьбе: `git add <файлы>`. `git add -A` - только если всё в рабочей папке относится к этому коммиту. Никогда не коммитить `.claude/settings.local.json`, `build/`, `run/`, `hs_err_pid*.log`, `replay_pid*.log`, `__pycache__/`. Если неясно, что из изменений включать, - спросить одним вопросом со списком файлов.
3. Коммит. В инструменте Bash - через heredoc, чтобы не было проблем с кавычками:
   ```
   git -c commit.gpgsign=false commit -q -F - <<'EOF'
   Fixed: ...
   EOF
   ```
   Если команду выполняет пользователь (PowerShell):
   ```
   git add <файлы>
   git -c commit.gpgsign=false commit -m "Fixed: ..."
   ```
4. Проверка сразу после коммита:
   ```
   git log -1 --format='%an <%ae>%n%B'
   ```
   Автор - Grihail, в тексте нет `Co-Authored-By` и прочих лишних строк. Если что-то не так и коммит ещё не запушен - сообщить и предложить исправить через `git -c commit.gpgsign=false commit --amend`.
5. Ответ пользователю: хеш и сообщение коммита одной-двумя строками; если не пушил - команда `git push origin main`.
