"""Исходники API загрузчика - в папку, чтобы читать их grep'ом, а не угадывать.

  python -I loader_sources.py fabric <папка> <версия Fabric API> [...]      модули Fabric API -> <папка>/<версия игры>/<модуль>/
  python -I loader_sources.py maven  <папка> <репозиторий> <группа:артефакт:версия> [...]   любой -sources.jar -> <папка>/<артефакт>-<версия>/

Пример: fabric "$S/api" 0.161.0+26.2 0.166.2+26.3;  maven "$S/api" https://maven.neoforged.net/releases net.neoforged:neoforge:26.3.0.101
Папку брать в scratchpad, не в репозитории.
"""
import io
import os
import re
import sys
import urllib.request
import zipfile

FABRIC = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/'


def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'loader-sources'})
    return urllib.request.urlopen(req, timeout=120).read()


def unpack(url, out):
    try:
        zipfile.ZipFile(io.BytesIO(fetch(url))).extractall(out)
        return True
    except Exception as e:
        print('fail', url, e)
        return False


def fabric(out, versions):
    for v in versions:
        pom = fetch(FABRIC + f'fabric-api/{v}/fabric-api-{v}.pom').decode()
        mods = re.findall(r'<artifactId>([^<]+)</artifactId>\s*<version>([^<]+)</version>', pom)
        target = os.path.join(out, v.split('+')[-1])
        n = sum(unpack(FABRIC + f'{m}/{mv}/{m}-{mv}-sources.jar', os.path.join(target, m)) for m, mv in mods if m not in ('fabric-api', 'fabric-api-deprecated'))  # у сборных jar нет исходников
        print(f'{v}: {n} modules -> {target}')


def maven(out, repo, coords):
    for c in coords:
        group, artifact, version = c.split(':')
        url = f"{repo.rstrip('/')}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}-sources.jar"
        target = os.path.join(out, f'{artifact}-{version}')
        if unpack(url, target):
            print(f'{c} -> {target}')


if __name__ == '__main__':
    if len(sys.argv) < 4 or sys.argv[1] not in ('fabric', 'maven'):
        sys.exit(__doc__)
    if sys.argv[1] == 'fabric':
        fabric(sys.argv[2], sys.argv[3:])
    else:
        maven(sys.argv[2], sys.argv[3], sys.argv[4:])
