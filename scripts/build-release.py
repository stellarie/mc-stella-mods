#!/usr/bin/env python3
"""Assemble Gradle mods without tests, bundle release jars, and pick release versions."""

import argparse
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_SUFFIXES = ("-sources.jar", "-dev.jar", "-javadoc.jar", "-gametest.jar")


def discover(root: Path) -> list[Path]:
    mods = sorted(
        path for path in root.iterdir()
        if path.is_dir()
        and (path / "build.gradle").is_file()
        and (path / "settings.gradle").is_file()
        and (path / "gradlew").is_file()
        and (path / "gradlew.bat").is_file()
    )
    if not mods:
        raise RuntimeError("No Gradle mods found")
    return mods


def release_jars(mod: Path) -> list[Path]:
    return sorted(
        jar for jar in (mod / "build" / "libs").glob("*.jar")
        if not jar.name.endswith(EXCLUDED_SUFFIXES)
    )


def run(mod: Path, *args: str) -> None:
    command = (
        [str(mod / "gradlew.bat"), *args]
        if sys.platform == "win32"
        else ["sh", str(mod / "gradlew"), *args]
    )
    print(f"Running {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=mod, check=True)


def build_one(root: Path, output: Path, name: str) -> None:
    mods = {mod.name: mod for mod in discover(root)}
    if name not in mods:
        raise RuntimeError(f"Unknown Gradle mod: {name}")
    mod = mods[name]
    stage = output / "jars" / name
    stage.mkdir(parents=True, exist_ok=True)
    for old_jar in stage.glob("*.jar"):
        old_jar.unlink()
    # Nether's included builds otherwise write the mobs jar concurrently.
    gradle_limits = ("--max-workers=1",) if name == "mod-stella-nether" else ()
    run(mod, "clean", "assemble", *gradle_limits)
    jars = release_jars(mod)
    if not jars:
        raise RuntimeError(f"No release jars found for {name}")
    for jar in jars:
        shutil.copy2(jar, stage / jar.name)
    print(f"Staged {len(jars)} release jars for {name}", flush=True)


def bundle(jars_dir: Path, expected_mods: list[str], output: Path) -> Path:
    target = output / "mc-stella-mods.zip"
    target.unlink(missing_ok=True)
    if not expected_mods or len(set(expected_mods)) != len(expected_mods):
        raise RuntimeError("Expected mod list is empty or contains duplicates")
    if not jars_dir.is_dir():
        raise RuntimeError(f"Jar directory missing: {jars_dir}")
    actual_mods = {path.name for path in jars_dir.iterdir() if path.is_dir()}
    if actual_mods != set(expected_mods):
        raise RuntimeError(f"Jar artifact mismatch: expected {expected_mods}, found {sorted(actual_mods)}")
    jars = []
    for name in expected_mods:
        found = sorted((jars_dir / name).glob("*.jar"))
        if not found:
            raise RuntimeError(f"No release jars staged for {name}")
        jars.extend(found)
    names: set[str] = set()
    for jar in jars:
        if jar.name in names:
            raise RuntimeError(f"Duplicate jar name: {jar.name}")
        names.add(jar.name)
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "mc-stella-mods.zip.tmp"
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for jar in jars:
                archive.write(jar, jar.name)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Release bundle: {target} ({len(jars)} jars)", flush=True)
    return target


def build(root: Path, output: Path) -> Path:
    mods = discover(root)
    print("Detected mods: " + ", ".join(mod.name for mod in mods), flush=True)
    (output / "mc-stella-mods.zip").unlink(missing_ok=True)
    for mod in mods:
        build_one(root, output, mod.name)
    return bundle(output / "jars", [mod.name for mod in mods], output)


TAG = re.compile(r"v(\d+)\.(\d+)\.(\d+)")


def next_version(tags: list[str], bump: str) -> str:
    if bump not in ("patch", "minor", "major"):
        raise RuntimeError(f"Unknown bump: {bump}")
    versions = [tuple(map(int, m.groups())) for t in tags if (m := TAG.fullmatch(t))]
    if not versions:
        return "v1.0.0"
    major, minor, patch = max(versions)
    if bump == "major":
        return f"v{major + 1}.0.0"
    if bump == "minor":
        return f"v{major}.{minor + 1}.0"
    return f"v{major}.{minor}.{patch + 1}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=ROOT / "release-output")
    parser.add_argument("--input", type=Path)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--mod")
    parser.add_argument("--bundle", action="store_true")
    parser.add_argument("--mods-json")
    parser.add_argument("--next-version", action="store_true")
    parser.add_argument("--bump", default="patch")
    args = parser.parse_args()
    try:
        if args.list:
            print(json.dumps([mod.name for mod in discover(args.root.resolve())]))
        elif args.next_version:
            tags = subprocess.run(
                ["git", "tag", "--list", "v*"], cwd=args.root, check=True,
                capture_output=True, text=True,
            ).stdout.split()
            print(next_version(tags, args.bump))
        elif args.mod:
            build_one(args.root.resolve(), args.output.resolve(), args.mod)
        elif args.bundle:
            if not args.mods_json:
                raise RuntimeError("--bundle requires --mods-json")
            names = json.loads(args.mods_json)
            if not isinstance(names, list) or not all(isinstance(name, str) for name in names):
                raise RuntimeError("--mods-json must be a JSON list of mod names")
            jars_dir = args.input or args.output / "jars"
            bundle(jars_dir.resolve(), names, args.output.resolve())
        else:
            build(args.root.resolve(), args.output.resolve())
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
