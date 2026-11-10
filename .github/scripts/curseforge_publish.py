#!/usr/bin/env python3
"""Загружает jar всех версий мода на CurseForge через Upload API. Запускается из .github/workflows/curseforge.yml.

Окружение:
  CURSEFORGE_TOKEN       токен автора (authors.curseforge.com/#/settings/api-tokens)
  CURSEFORGE_PROJECT_ID  числовой ID проекта
  TAG                    тег релиза, должен быть равен v<mod_version>
  DRY_RUN                "true" - только проверки, без загрузки
  ONLY                   цели через запятую (26.2-forge,26.3-neoforge) - загрузить только их; пусто - все
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://minecraft.curseforge.com/api"
ROOT = Path(__file__).resolve().parents[2]
LOADERS = {"forge": "Forge", "neoforge": "NeoForge", "fabric": "Fabric"}
# Мод нужен и на клиенте, и на сервере.
ENVIRONMENTS = ["Client", "Server"]
FABRIC_API = {"slug": "fabric-api", "type": "requiredDependency"}
RETRY_CODES = {429, 500, 502, 503, 504}
RETRIES = 3


def read_properties():
    props = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def changelog_section(version):
    """Текст раздела ## [version] из CHANGELOG.md или None, если раздела нет или он пуст."""
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## \[|\Z)", text, re.M | re.S)
    return (match.group(1).strip() or None) if match else None


def target_dirs():
    """Папка сборки каждой цели: modern/versions/<цель> и <версия>/<движок> для старых версий."""
    dirs = {p.name: p for p in (ROOT / "modern" / "versions").iterdir() if (p / "gradle.properties").is_file()}
    dirs.update({f"{p.parent.name}-{p.name}": p for p in ROOT.glob("[0-9]*/*") if (p / "build.gradle").is_file()})
    return dirs


def sort_key(target):
    mc, loader = target.split("-")
    return [int(part) for part in mc.split(".")], loader


def release_type(version):
    lowered = version.lower()
    if "alpha" in lowered:
        return "alpha"
    return "beta" if "beta" in lowered or "-rc" in lowered else "release"


def game_version_names(target):
    mc, loader = target.split("-")
    return [mc, LOADERS[loader]] + ENVIRONMENTS


def call(method, path, token, body=None, content_type=None):
    headers = {"X-Api-Token": token, "User-Agent": "mine-papers-online-publish"}
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(API + path, data=body, method=method, headers=headers)
    for attempt in range(RETRIES + 1):
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")
            if error.code not in RETRY_CODES or attempt == RETRIES:
                raise RuntimeError(f"HTTP {error.code}: {detail}") from error
            delay = 5 * 2 ** attempt
            print(f"  HTTP {error.code}, retrying in {delay}s")
            time.sleep(delay)


def multipart(metadata, jar):
    boundary = uuid.uuid4().hex
    head = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
        f"Content-Type: application/json\r\n\r\n{json.dumps(metadata)}\r\n"
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
        "Content-Type: application/java-archive\r\n\r\n"
    ).encode("utf-8")
    return head + jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode("utf-8"), f"multipart/form-data; boundary={boundary}"


def metadata_for(target, version, changelog):
    mc, loader = target.split("-")
    metadata = {
        "changelog": changelog,
        "changelogType": "markdown",
        # Имя файла на сайте: 26.3-neoforge-1.6.0
        "displayName": f"{target}-{version}",
        "gameVersionNames": game_version_names(target),
        "releaseType": release_type(version),
    }
    if loader == "fabric":
        metadata["relations"] = {"projects": [FABRIC_API]}
    return metadata


def main():
    token = os.environ.get("CURSEFORGE_TOKEN", "")
    project = os.environ.get("CURSEFORGE_PROJECT_ID", "")
    tag = os.environ.get("TAG", "")
    dry_run = os.environ.get("DRY_RUN", "") == "true"
    only = {t.strip() for t in os.environ.get("ONLY", "").split(",") if t.strip()}

    props = read_properties()
    mod_name, version = props["mod_name"], props["mod_version"]
    dirs = target_dirs()

    # Несовпадение тега и пустой раздел CHANGELOG при пробном запуске только предупреждают: его обычно запускают на main до подготовки релиза.
    release_problems = []
    errors = []
    if tag != f"v{version}":
        release_problems.append(f"tag '{tag}' does not match mod_version: expected v{version}")
    changelog = changelog_section(version)
    if changelog is None:
        release_problems.append(f"CHANGELOG.md has no section [{version}]")

    errors += [f"unknown target in ONLY: {t}" for t in sorted(only - dirs.keys())]
    selected = sorted((only or dirs.keys()) & dirs.keys(), key=sort_key)
    jars = {t: dirs[t] / "build" / "libs" / f"{mod_name}-{t}-{version}.jar" for t in selected}
    errors += [f"jar not found: {jar}" for jar in jars.values() if not jar.is_file()]

    if not token or not project.isdigit():
        message = "CURSEFORGE_TOKEN and a numeric CURSEFORGE_PROJECT_ID are required"
        if dry_run:
            print(f"WARNING: {message}; game versions are not checked")
        else:
            errors.append(message)
    else:
        known = {v["name"] for v in call("GET", "/game/versions", token)}
        for target in selected:
            errors += [f"{target}: CurseForge has no game version '{name}'" for name in game_version_names(target) if name not in known]

    if dry_run:
        for problem in release_problems:
            print(f"WARNING: {problem}")
    else:
        errors = release_problems + errors
    # Все проверки идут до первой загрузки: иначе релиз окажется опубликован наполовину.
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    failed = []
    for target in selected:
        metadata = metadata_for(target, version, changelog or "")
        print(f"{target}: {jars[target].name} -> {metadata['gameVersionNames']}, {metadata['releaseType']}")
        if dry_run:
            continue
        try:
            body, content_type = multipart(metadata, jars[target])
            result = call("POST", f"/projects/{project}/upload-file", token, body, content_type)
            print(f"  uploaded, file id {result['id']}")
        except (RuntimeError, OSError) as error:
            print(f"  FAILED: {error}")
            failed.append(target)

    if failed:
        print(f"\n{len(failed)} of {len(selected)} failed. Re-run the workflow by hand with ref={tag}, dry_run off and only={','.join(failed)}")
        return 1
    print(f"\nDry run OK: {len(selected)} files" if dry_run else f"\nUploaded {len(selected)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
