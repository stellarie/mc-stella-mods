#!/usr/bin/env python3
"""Install the single Stella jar into a Prism instance and retire the per-mod jars."""

import argparse
import hashlib
import os
import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK_NAME = "mc-stella-mods.jar"
STELLA_PREFIXES = ("mod-stella-", "mc-stella-")


def default_mods_dir() -> Path:
    appdata = os.environ.get("APPDATA")
    if not appdata:
        raise RuntimeError("APPDATA is not set; pass --mods-dir")
    return Path(appdata) / "PrismLauncher/instances/Fabulously Optimized/minecraft/mods"


def stella_jars(mods_dir: Path) -> list[Path]:
    return sorted(
        path for path in mods_dir.iterdir()
        if path.is_file() and path.suffix == ".jar" and path.name.startswith(STELLA_PREFIXES)
    )


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def deploy(jar: Path, mods_dir: Path, backup_root: Path, dry_run: bool = False) -> list[Path]:
    if jar.name != PACK_NAME or not jar.is_file():
        raise RuntimeError(f"Expected the packed jar {PACK_NAME}, got {jar}")
    if not mods_dir.is_dir():
        raise RuntimeError(f"Mods directory missing: {mods_dir}")
    old = stella_jars(mods_dir)
    if dry_run:
        return old
    backup = backup_root / f"deploy-single-jar-{datetime.now():%Y%m%d-%H%M%S}"
    backup.mkdir(parents=True)
    for path in old:
        shutil.move(str(path), backup / path.name)
    target = mods_dir / PACK_NAME
    shutil.copy2(jar, target)
    if sha256(target) != sha256(jar):
        raise RuntimeError(f"Installed jar does not match {jar}")
    print(f"Backed up {len(old)} jars to {backup}")
    print(f"Installed {target} SHA-256 {sha256(target)}")
    return old


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "release-output" / PACK_NAME)
    parser.add_argument("--mods-dir", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        mods_dir = args.mods_dir or default_mods_dir()
        old = deploy(args.jar.resolve(), mods_dir, mods_dir.parent / "mods-backup", args.dry_run)
        if args.dry_run:
            print("Would replace: " + (", ".join(path.name for path in old) or "nothing"))
    except (OSError, RuntimeError) as error:
        print(f"Deploy failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
