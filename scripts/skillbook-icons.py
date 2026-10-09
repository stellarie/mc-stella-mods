#!/usr/bin/env python3
"""Generate the 16x16 item art of the skill books: fighter, archer and priest.

The art itself is the table in ``skill_icons.py``: one entry per book, drawn over
the school's own plate and outlined. The plain texture is all this script writes;
the awakened frame and sparkle come from ``spellbook-icon-check.py
--write-awakened``.

  python scripts/skillbook-icons.py                     write every skill icon
  python scripts/skillbook-icons.py --sheet out.png     also write an 8x contact sheet
  python scripts/skillbook-icons.py --check             fail on a missing or wrong-sized icon
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw

import skill_icons

MOD_ID = "mod-stella-spells"
TEXTURE_ROOT = Path("mod-stella-spells/src/main/resources/assets/mod-stella-spells/textures/item")
SIZE = 16
SCALE = 8
# Drawn outside the plate, so the frame reads as a border rather than as art.
OUTLINE = (15, 18, 28, 255)


@dataclass(frozen=True)
class School:
    name: str
    frame: tuple[int, int, int, int]
    background: tuple[int, int, int, int]
    highlight: tuple[int, int, int, int]
    shade: tuple[int, int, int, int]


SCHOOLS = {
    "fighter": School("fighter", (0xB3, 0x3A, 0x3A, 255), (0x3A, 0x1E, 0x1E, 255),
                      (0xE8, 0xE8, 0xE8, 255), (0x6E, 0x22, 0x22, 255)),
    "archer": School("archer", (0x4F, 0xA6, 0x5C, 255), (0x1E, 0x35, 0x22, 255),
                     (0xE8, 0xE8, 0xE8, 255), (0x2C, 0x5E, 0x36, 255)),
    "priest": School("priest", (0xE3, 0xC2, 0x5A, 255), (0x3A, 0x34, 0x20, 255),
                     (0xFF, 0xFF, 0xFF, 255), (0x8A, 0x73, 0x2E, 255)),
}


@dataclass(frozen=True)
class Skill:
    path: str
    label: str
    school: str
    ops: tuple[tuple, ...]


def art(path: str) -> tuple[tuple, ...]:
    """The skill's own pixel ops, drawn over its school's plate by skill_icons."""
    return skill_icons.shapes(path)


SKILLS = (
    Skill("cleave", "Cleave", "fighter", art("cleave")),
    Skill("leap_slam", "Leap Slam", "fighter", art("leap_slam")),
    Skill("whirlwind", "Whirlwind", "fighter", art("whirlwind")),
    Skill("shield_bash", "Shield Bash", "fighter", art("shield_bash")),
    Skill("charge", "Charge", "fighter", art("charge")),
    Skill("execute", "Execute", "fighter", art("execute")),
    Skill("war_cry", "War Cry", "fighter", art("war_cry")),
    Skill("counter_stance", "Counter Stance", "fighter", art("counter_stance")),
    Skill("iron_skin", "Iron Skin", "fighter", art("iron_skin")),
    Skill("earthsplitter", "Earthsplitter", "fighter", art("earthsplitter")),
    Skill("arrow_crafting", "Arrow Crafting", "archer", art("arrow_crafting")),
    Skill("multishot", "Grappling Hook", "archer", art("multishot")),
    Skill("piercing_shot", "Sharp Shooting", "archer", art("piercing_shot")),
    Skill("arrow_rain", "Arrow Shower", "archer", art("arrow_rain")),
    Skill("rapid_fire", "Double Strafe", "archer", art("rapid_fire")),
    Skill("hunters_mark", "Blitz Beat", "archer", art("hunters_mark")),
    Skill("ankle_snare", "Ankle Snare", "archer", art("ankle_snare")),
    Skill("arrow_repel", "Arrow Repel", "archer", art("arrow_repel")),
    Skill("explosive_arrow", "Explosive Arrow", "archer", art("explosive_arrow")),
    Skill("disengage", "Wind Walk", "archer", art("disengage")),
    Skill("snare_trap", "Concentrate", "archer", art("snare_trap")),
    Skill("aimed_bolt", "Aimed Bolt", "archer", art("aimed_bolt")),
    Skill("triangle_shot", "Triangle Shot", "archer", art("triangle_shot")),
    Skill("eagle_eye", "True Sight", "archer", art("eagle_eye")),
    Skill("elemental_quiver", "Spectral Quiver", "archer", art("elemental_quiver")),
    Skill("holy_light", "Holy Light", "priest", art("holy_light")),
    Skill("prayer_of_mending", "Prayer of Mending", "priest", art("prayer_of_mending")),
    Skill("renew", "Renew", "priest", art("renew")),
    Skill("divine_shield", "Divine Shield", "priest", art("divine_shield")),
    Skill("holy_arms", "Holy Arms", "priest", art("holy_arms")),
    Skill("blessing_of_fortitude", "Blessing of Fortitude", "priest", art("blessing_of_fortitude")),
    Skill("smite", "Smite", "priest", art("smite")),
    Skill("resurrection_ward", "Resurrection Ward", "priest", art("resurrection_ward")),
    Skill("consecrate", "Consecrate", "priest", art("consecrate")),
    Skill("guardian_angel", "Guardian Angel", "priest", art("guardian_angel")),
    Skill("unbroken_vow", "Unbroken Vow", "priest", art("unbroken_vow")),
    Skill("steadfast_prayer", "Steadfast Prayer", "priest", art("steadfast_prayer")),
)


def render(skill: Skill) -> Image.Image:
    return skill_icons.draw(skill.path)


def write_icons(root: Path) -> None:
    target = root / TEXTURE_ROOT
    target.mkdir(parents=True, exist_ok=True)
    for skill in SKILLS:
        render(skill).save(target / f"{skill.path}.png", format="PNG", optimize=False, compress_level=9)
    print(f"wrote {len(SKILLS)} skill icons to {target}")


def check(root: Path) -> int:
    problems: list[str] = []
    # The row and the art table both name the skill, so the two cannot drift apart.
    for skill in SKILLS:
        if skill.school != skill_icons.school_of(skill.path) or skill.label != skill_icons.label_of(skill.path):
            problems.append(f"{skill.path}: the row says {skill.label}/{skill.school}, the art table says "
                            f"{skill_icons.label_of(skill.path)}/{skill_icons.school_of(skill.path)}")
    for skill in SKILLS:
        icon = root / TEXTURE_ROOT / f"{skill.path}.png"
        if not icon.exists():
            problems.append(f"missing icon: {icon}")
            continue
        with Image.open(icon) as image:
            if image.size != (SIZE, SIZE):
                problems.append(f"{skill.path}: icon is {image.size}, want {(SIZE, SIZE)}")
            elif image.mode != "RGBA":
                problems.append(f"{skill.path}: icon mode is {image.mode}")
    for problem in problems:
        print(f"FAIL {problem}", file=sys.stderr)
    if not problems:
        print(f"OK {len(SKILLS)} skill icons")
    return 1 if problems else 0


def contact_sheet(root: Path, destination: Path) -> None:
    columns = 5
    cell = SIZE * SCALE + 12
    rows = (len(SKILLS) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell + 12, rows * (cell + 14) + 12), (20, 23, 34))
    draw = ImageDraw.Draw(sheet)
    for index, skill in enumerate(SKILLS):
        x = 12 + (index % columns) * cell
        y = 12 + (index // columns) * (cell + 14)
        with Image.open(root / TEXTURE_ROOT / f"{skill.path}.png") as source:
            icon = source.convert("RGBA").resize((SIZE * SCALE, SIZE * SCALE), Image.Resampling.NEAREST)
        sheet.paste(icon, (x, y), icon)
        school = SCHOOLS[skill.school]
        draw.text((x, y + SIZE * SCALE + 2), skill.label, fill=school.frame[:3])
    destination.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(destination, format="PNG", optimize=False, compress_level=9)
    print(f"wrote contact sheet {destination}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--check", action="store_true", help="audit the committed icons instead of writing them")
    parser.add_argument("--sheet", type=Path, metavar="PATH", help="write an 8x contact sheet of every icon")
    args = parser.parse_args()
    root = args.root.resolve()
    if args.check:
        return check(root)
    write_icons(root)
    if args.sheet is not None:
        destination = args.sheet if args.sheet.is_absolute() else root / args.sheet
        contact_sheet(root, destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
