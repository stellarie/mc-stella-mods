#!/usr/bin/env python3
"""Build every Gradle mod, run five GameTest suites, and bundle release jars."""

import argparse
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


def copy_log(mod: Path, number: int, output: Path) -> None:
    log = mod / "build" / "run" / "gameTest" / "logs" / "latest.log"
    if log.is_file():
        target = output / "test-logs" / mod.name / f"run-{number}.log"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(log, target)


def build(root: Path, output: Path) -> Path:
    mods = discover(root)
    print("Detected mods: " + ", ".join(mod.name for mod in mods), flush=True)
    bundle = output / "mc-stella-mods.zip"
    bundle.unlink(missing_ok=True)
    jars: list[tuple[Path, Path]] = []
    for mod in mods:
        try:
            run(mod, "clean", "build")
        finally:
            copy_log(mod, 0, output)
        found = release_jars(mod)
        if not found:
            raise RuntimeError(f"No release jars found for {mod.name}")
        jars.extend((mod, jar) for jar in found)

    for mod in mods:
        for number in range(1, 6):
            log = mod / "build" / "run" / "gameTest" / "logs" / "latest.log"
            log.unlink(missing_ok=True)
            try:
                run(mod, "runGameTest", "--rerun")
            finally:
                copy_log(mod, number, output)

    output.mkdir(parents=True, exist_ok=True)
    names: set[str] = set()
    for _, jar in jars:
        if jar.name in names:
            raise RuntimeError(f"Duplicate jar name: {jar.name}")
        names.add(jar.name)
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for _, jar in jars:
            archive.write(jar, jar.name)
    print(f"Release bundle: {bundle} ({len(jars)} jars)", flush=True)
    return bundle


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=ROOT / "release-output")
    args = parser.parse_args()
    try:
        build(args.root.resolve(), args.output.resolve())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
