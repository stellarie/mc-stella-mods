#!/usr/bin/env python3
"""Generate and audit the 18x18 status effect icons of mod-stella-spells.

Run from anywhere. Without flags it writes one PNG per effect. The PNGs are committed;
this script is a generator, not a build step.

  --check          Read the effect ids from SpellStatus.java and fail on a missing,
                   mis-sized, or empty icon, or a missing language entry.
  --sheet PATH     Also write an 8x contact sheet of every icon to PATH.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod-stella-spells"
SOURCE = MOD / "src/main/java/com/stella/spells/SpellStatus.java"
TEXTURES = MOD / "src/main/resources/assets/mod-stella-spells/textures/mob_effect"
LANG = MOD / "src/main/resources/assets/mod-stella-spells/lang/en_us.json"
MOD_ID = "mod-stella-spells"

ICON = 18
SYMBOL = 16
OUTLINE = (0x16, 0x12, 0x1E, 255)

# Main colors, copied from the effect registry. The registry is the source of truth.
COLORS = {
    "chilled": 0xA8E4F0,
    "frozen": 0x6FC8FF,
    "soaked": 0x2E6FD8,
    "scorched": 0xD9541E,
    "brittle": 0xC9C3B0,
    "marked": 0xE0303A,
    "staggered": 0xE8C547,
    "arcane_ward": 0x9C6BFF,
    "bleeding": 0x9E1B1B,
}


def rgb(value: int) -> tuple[int, int, int, int]:
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, 255)


def mix(color: tuple[int, int, int, int], other: tuple[int, int, int], amount: float) -> tuple[int, int, int, int]:
    return tuple(round(c + (o - c) * amount) for c, o in zip(color[:3], other)) + (255,)  # type: ignore[return-value]


def light(color):
    return mix(color, (255, 255, 255), 0.5)


def shade(color):
    return mix(color, (0, 0, 0), 0.38)


def registry_ids() -> list[str]:
    text = SOURCE.read_text(encoding="utf-8")
    return re.findall(r'register\(\s*"([a-z_]+)"', text)


# Each painter draws on a 16x16 RGBA canvas. ImageDraw has no anti-aliasing, so the edges stay crisp.
def snowflake(d: ImageDraw.ImageDraw, c) -> None:
    hi = light(c)
    d.rectangle([7, 1, 8, 14], fill=c)
    d.rectangle([1, 7, 14, 8], fill=c)
    d.line([(2, 2), (13, 13)], fill=c)
    d.line([(13, 2), (2, 13)], fill=c)
    for x, y in [(6, 2), (9, 2), (6, 13), (9, 13), (2, 6), (2, 9), (13, 6), (13, 9)]:
        d.point((x, y), fill=c)
    d.rectangle([7, 7, 8, 8], fill=hi)


def ice_cube(d: ImageDraw.ImageDraw, c) -> None:
    d.rectangle([2, 2, 13, 13], fill=c)
    d.line([(2, 2), (13, 2)], fill=light(c))
    d.line([(2, 2), (2, 13)], fill=light(c))
    d.line([(13, 3), (13, 13)], fill=shade(c))
    d.line([(3, 13), (13, 13)], fill=shade(c))
    d.rectangle([4, 4, 6, 5], fill=(255, 255, 255, 255))
    d.point((4, 7), fill=(255, 255, 255, 255))
    d.line([(9, 8), (11, 10)], fill=shade(c))
    d.line([(11, 10), (10, 12)], fill=shade(c))


def water_drop(d: ImageDraw.ImageDraw, c) -> None:
    d.polygon([(8, 0), (3, 9), (13, 9)], fill=c)
    d.ellipse([2, 5, 13, 15], fill=c)
    d.line([(5, 10), (5, 12)], fill=light(c))
    d.line([(6, 13), (7, 13)], fill=light(c))
    d.line([(10, 12), (11, 10)], fill=shade(c))


def flame(d: ImageDraw.ImageDraw, c) -> None:
    d.polygon([(8, 0), (10, 3), (12, 6), (13, 9), (12, 13), (9, 15), (6, 15), (3, 13), (2, 9), (4, 6), (5, 8), (6, 4)], fill=c)
    d.polygon([(8, 7), (10, 10), (10, 13), (8, 14), (6, 13), (6, 10)], fill=mix(c, (255, 214, 64), 0.75))
    d.polygon([(8, 10), (9, 12), (8, 13), (7, 12)], fill=(255, 245, 190, 255))


def shard(d: ImageDraw.ImageDraw, c) -> None:
    d.polygon([(3, 1), (12, 0), (14, 7), (10, 15), (4, 13), (1, 6)], fill=c)
    d.polygon([(3, 1), (12, 0), (9, 5), (5, 6)], fill=light(c))
    d.polygon([(14, 7), (10, 15), (9, 10), (11, 7)], fill=shade(c))
    crack = (0x3A, 0x34, 0x40, 255)
    d.line([(9, 5), (7, 8), (9, 10), (7, 13)], fill=crack)
    d.line([(7, 8), (4, 9)], fill=crack)


def crosshair(d: ImageDraw.ImageDraw, c) -> None:
    d.ellipse([2, 2, 13, 13], outline=c, width=2)
    d.rectangle([7, 0, 8, 4], fill=c)
    d.rectangle([7, 11, 8, 15], fill=c)
    d.rectangle([0, 7, 4, 8], fill=c)
    d.rectangle([11, 7, 15, 8], fill=c)
    d.rectangle([7, 7, 8, 8], fill=light(c))


def star_ring(d: ImageDraw.ImageDraw, c) -> None:
    for x, y in [(8, 2), (13, 6), (11, 12), (5, 12), (2, 6)]:
        d.point([(x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)], fill=c)
        d.point((x, y), fill=light(c))


def shield(d: ImageDraw.ImageDraw, c) -> None:
    d.polygon([(2, 1), (13, 1), (13, 8), (11, 12), (8, 15), (5, 12), (2, 8)], fill=c)
    d.polygon([(4, 3), (11, 3), (11, 7), (10, 10), (8, 12), (6, 10), (4, 7)], fill=shade(c))
    d.line([(8, 4), (8, 11)], fill=light(c))
    d.line([(5, 6), (10, 6)], fill=light(c))
    d.line([(2, 1), (13, 1)], fill=light(c))


def drops(d: ImageDraw.ImageDraw, c) -> None:
    def drop(cx: float, top: int) -> None:
        d.polygon([(cx, top), (cx - 2, top + 3), (cx + 3, top + 3)], fill=c)
        d.ellipse([cx - 2.5, top + 2, cx + 3.5, top + 7], fill=c)
        d.point((cx - 1, top + 4), fill=light(c))

    drop(3.5, 0)
    drop(12, 1)
    drop(8, 8)


PAINTERS = {
    "chilled": snowflake,
    "frozen": ice_cube,
    "soaked": water_drop,
    "scorched": flame,
    "brittle": shard,
    "marked": crosshair,
    "staggered": star_ring,
    "arcane_ward": shield,
    "bleeding": drops,
}


def render(effect: str) -> Image.Image:
    symbol = Image.new("RGBA", (SYMBOL, SYMBOL), (0, 0, 0, 0))
    PAINTERS[effect](ImageDraw.Draw(symbol), rgb(COLORS[effect]))
    canvas = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    canvas.paste(symbol, (1, 1), symbol)
    source = canvas.copy()
    pixels = canvas.load()
    reads = source.load()
    for y in range(ICON):
        for x in range(ICON):
            if reads[x, y][3] != 0:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < ICON and 0 <= ny < ICON and reads[nx, ny][3] != 0:
                        pixels[x, y] = OUTLINE
    return canvas


def contact_sheet(path: Path) -> None:
    scale = 8
    cell = ICON * scale
    gap = 12
    ids = list(COLORS)
    width = len(ids) * (cell + gap) + gap
    sheet = Image.new("RGBA", (width, cell + 2 * gap), (0x7A, 0x7A, 0x84, 255))
    for index, effect in enumerate(ids):
        icon = Image.open(TEXTURES / f"{effect}.png").convert("RGBA").resize((cell, cell), Image.NEAREST)
        sheet.paste(icon, (gap + index * (cell + gap), gap), icon)
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)


def check() -> int:
    ids = registry_ids()
    problems: list[str] = []
    if not ids:
        problems.append(f"no effect ids found in {SOURCE}")
    if sorted(ids) != sorted(COLORS):
        problems.append(f"registry ids {sorted(ids)} differ from the generator's {sorted(COLORS)}")
    lang = json.loads(LANG.read_text(encoding="utf-8")) if LANG.exists() else {}
    for effect in ids:
        icon = TEXTURES / f"{effect}.png"
        if not icon.exists():
            problems.append(f"{effect}: missing icon {icon}")
        else:
            image = Image.open(icon)
            if image.size != (ICON, ICON):
                problems.append(f"{effect}: icon is {image.size[0]}x{image.size[1]}, want {ICON}x{ICON}")
            elif image.convert("RGBA").getchannel("A").getbbox() is None:
                problems.append(f"{effect}: icon is fully transparent")
        if f"effect.{MOD_ID}.{effect}" not in lang:
            problems.append(f"{effect}: no lang entry effect.{MOD_ID}.{effect}")
    for problem in problems:
        print(f"FAIL {problem}", file=sys.stderr)
    if not problems:
        print(f"OK {len(ids)} effects: {', '.join(ids)}")
    return 1 if problems else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="audit the committed icons instead of writing them")
    parser.add_argument("--sheet", type=Path, help="write an 8x contact sheet of the icons to this path")
    args = parser.parse_args()
    if args.check:
        return check()
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for effect in COLORS:
        render(effect).save(TEXTURES / f"{effect}.png")
    print(f"wrote {len(COLORS)} icons to {TEXTURES}")
    if args.sheet:
        contact_sheet(args.sheet)
        print(f"wrote {args.sheet}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
