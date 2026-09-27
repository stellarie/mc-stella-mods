#!/usr/bin/env python3
"""Generate and audit the 16x16 spellbook item art."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


MOD_ID = "mod-stella-spells"
CATALOG_SOURCE = Path("mod-stella-spells/src/main/java/com/stella/spells/SpellbookCatalog.java")
VISUAL_SOURCE = Path("mod-stella-spells/src/main/java/com/stella/spells/SpellVisuals.java")
MODEL_ROOT = Path("mod-stella-spells/src/main/resources/assets/mod-stella-spells/models/item")
ITEM_ROOT = Path("mod-stella-spells/src/main/resources/assets/mod-stella-spells/items")
TEXTURE_ROOT = Path("mod-stella-spells/src/main/resources/assets/mod-stella-spells/textures/item")


@dataclass(frozen=True)
class Book:
    path: str
    label: str
    element: str
    color: str
    glyph: tuple[str, ...]
    variant: int


def glyph(*rows: str) -> tuple[str, ...]:
    if len(rows) != 7 or any(len(row) != 7 for row in rows):
        raise ValueError("every glyph must be seven rows of seven pixels")
    return tuple(rows)


BOOKS = (
    Book("spark_shot", "Spark Shot", "lightning", "#f5d35e", glyph("..#....", ".##....", "..#....", ".###...", "...#...", "..##...", "...#..."), 1),
    Book("frostbind", "Frostbind", "frost", "#74d7ff", glyph("..#....", ".###...", "..#....", "#######", "..#....", ".###...", "..#...."), 2),
    Book("gale_step", "Gale Step", "air", "#b8e8ff", glyph("....#..", "...##..", "..###..", ".####..", "..###..", "...##..", "....#.."), 3),
    Book("mending_word", "Mending Word", "life", "#ff9fd5", glyph("...#...", "...#...", ".#####.", "...#...", "...#...", "..###..", "...#..."), 4),
    Book("stoneguard", "Stoneguard", "earth", "#9a704a", glyph("..###..", ".#####.", "#######", "#######", ".#####.", "..###..", "...#..."), 5),
    Book("static_lash", "Static Lash", "lightning", "#f5d35e", glyph("#....#.", ".#..#..", "..##...", "...#...", "..##...", ".#..#..", "#....#."), 6),
    Book("briar_snare", "Briar Snare", "nature", "#62c96b", glyph("#.....#", ".#...#.", "..###..", "...#...", "..###..", ".#...#.", "#.....#"), 7),
    Book("tidal_push", "Tidal Push", "water", "#4bace2", glyph(".......", ".##....", "#..##..", "...#..#", ".##....", "#..##..", "...#..."), 8),
    Book("owls_gaze", "Owl's Gaze", "arcane", "#b88cff", glyph(".#####.", "#.....#", "#..#..#", "#.....#", ".#####.", "..#.#..", "...#..."), 9),
    Book("slimebound", "Slimebound", "slime", "#88d64b", glyph("...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."), 10),
    Book("chainstorm", "Chainstorm", "storm", "#f5dc65", glyph("..###..", ".#####.", "#######", "..###..", "...#...", "..##...", "...#..."), 11),
    Book("riftwalk", "Riftwalk", "void", "#7a4ba5", glyph(".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", ".#####."), 12),
    Book("phoenix_mantle", "Phoenix Mantle", "fire", "#ff6a3d", glyph("...#...", "..###..", ".#####.", "..###..", ".#####.", "..###..", "...#..."), 13),
    Book("life_bloom", "Life Bloom", "life", "#ff9fd5", glyph("...#...", "..###..", ".#####.", "###.###", ".#####.", "..###..", "...#..."), 14),
    Book("void_well", "Void Well", "void", "#7a4ba5", glyph(".#####.", "#.....#", "#.###..", "#..#...", "..#....", "...#..#", ".#####."), 15),
    Book("wardens_roar", "Warden's Roar", "sonic", "#46e4c0", glyph("#.....#", ".#...#.", "..###..", ".#####.", "..###..", ".#...#.", "#.....#"), 16),
    Book("aegis_of_the_deep", "Aegis of the Deep", "ward", "#6abccb", glyph(".#####.", "#######", "##.#.##", "##.#.##", "##.#.##", ".#####.", "...#..."), 17),
    Book("wither_seal", "Wither Seal", "wither", "#a16b8f", glyph(".#####.", "#######", "##.###.", "#######", "#.#.#.#", ".##.##.", "...#..."), 18),
    Book("time_lock", "Time Lock", "time", "#d16de6", glyph(".#####.", "#..#..#", "#.###.#", "#..#..#", "#.###.#", "#.....#", ".#####."), 19),
    Book("dragonfall", "Dragonfall", "fire", "#ff6a3d", glyph("#....#.", "##..##.", "#######", ".#####.", "..###..", ".#.#.#.", "#.....#"), 20),
    Book("ice_lance", "Ice Lance", "frost", "#74d7ff", glyph("...#...", "..##...", ".#####.", "..##...", "..##...", "..##...", "...#..."), 21),
    Book("soul_siphon", "Soul Siphon", "soul", "#7b8cdb", glyph(".#####.", "#.....#", "#.###..", "#..#...", "..#....", "...#..#", ".#####."), 22),
    Book("venom_dart", "Venom Dart", "poison", "#a0d84b", glyph("#.....#", "##...##", ".##.##.", "..###..", "...#...", "...#...", "...#..."), 23),
    Book("sapping_hex", "Sapping Hex", "poison", "#a0d84b", glyph("..###..", ".#...#.", "#..#..#", "#..#..#", "#..#..#", ".#...#.", "..###.."), 24),
    Book("blinding_flash", "Blinding Flash", "light", "#f2f2b2", glyph("...#...", "#..#..#", ".#####.", "..###..", ".#####.", "#..#..#", "...#..."), 26),
    Book("ember_brand", "Ember Brand", "fire", "#ff6a3d", glyph("#######", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", "#######"), 28),
    Book("quicksilver", "Quicksilver", "metal", "#c2c9d8", glyph("#.....#", "##...##", ".##.##.", "..###..", ".##.##.", "##...##", "#.....#"), 29),
    Book("tide_lungs", "Tide Lungs", "water", "#4bace2", glyph("...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#..."), 30),
    Book("berserk", "Berserk", "fire", "#ff6a3d", glyph("...#...", "..###..", ".#####.", "...#...", "...#...", "..###..", ".#...#."), 31),
    Book("haste_rune", "Haste Rune", "arcane", "#b88cff", glyph("...#...", "..##...", ".#####.", "...#...", "...#...", "...#...", "...#..."), 32),
    Book("shadow_veil", "Shadow Veil", "shadow", "#8062d6", glyph("..####.", ".#####.", "##..##.", "##.....", "##..##.", ".#####.", "..####."), 33),
    Book("sanctuary", "Sanctuary", "ward", "#6abccb", glyph(".#####.", "#..#..#", "#.###.#", "#..#..#", "#.###.#", "#..#..#", ".#####."), 34),
    Book("purify", "Purify", "light", "#f2f2b2", glyph("...#...", "..#.#..", ".#####.", "..#.#..", "...#...", "..#.#..", "...#..."), 35),
    Book("frost_nova", "Frost Nova", "frost", "#74d7ff", glyph("#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"), 36),
    Book("earthspike", "Earthspike", "earth", "#9a704a", glyph("...#...", "..###..", ".#####.", "#######", "#.....#", "#.....#", "#######"), 37),
    Book("plague_cloud", "Plague Cloud", "poison", "#a0d84b", glyph("..###..", ".#####.", "#######", ".#.#.#.", "#.....#", ".#.#.#.", "......."), 38),
    Book("flame_ring", "Flame Ring", "fire", "#ff6a3d", glyph(".#####.", "##...##", "#.###.#", "#.....#", "#.###.#", "##...##", ".#####."), 40),
    Book("gravity", "Gravity", "void", "#7a4ba5", glyph("#.....#", ".#...#.", "..#.#..", "...#...", "..###..", ".#####.", "#######"), 25),
    Book("bulwark_hymn", "Bulwark Hymn", "ward", "#6abccb", glyph(".#####.", "##...##", "#.#.#.#", "#.#.#.#", "#.#.#.#", "##...##", ".#####."), 27),
    Book("tailwind", "Tailwind", "air", "#b8e8ff", glyph("#......", ".##....", "...##..", "#####..", "...##..", ".##....", "#......"), 39),
    Book("battle_hymn", "Battle Hymn", "storm", "#f5dc65", glyph("...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."), 41),
    Book("summon_skeleton", "Summon Skeleton", "summoning", "#a9bed8", glyph(".#####.", "##...##", "#.#.#.#", "#.....#", "#.###.#", ".#.#.#.", "..###.."), 42),
)


def parse_catalog(root: Path) -> list[str]:
    source = (root / CATALOG_SOURCE).read_text(encoding="utf-8")
    return re.findall(r'definition\("([a-z0-9_]+)"', source)


def shade(color: str, factor: float) -> tuple[int, int, int]:
    channels = tuple(int(color[index:index + 2], 16) for index in (1, 3, 5))
    return tuple(max(0, min(255, round(value * factor))) for value in channels)


def draw_book(book: Book) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    base = shade(book.color, 0.72)
    deep = shade(book.color, 0.42)
    bright = shade(book.color, 1.28)
    outline = (15, 18, 28)
    page = (229, 211, 165)
    page_shadow = (156, 128, 91)

    draw.polygon(((2, 1), (12, 1), (15, 4), (14, 14), (4, 15), (1, 12), (1, 4)), fill=outline)
    draw.polygon(((3, 2), (12, 2), (14, 4), (13, 13), (4, 14), (2, 11), (2, 4)), fill=deep)
    draw.polygon(((4, 3), (11, 3), (13, 5), (12, 12), (5, 13), (3, 10), (3, 5)), fill=base)
    draw.polygon(((11, 3), (13, 5), (12, 12), (10, 12), (11, 5)), fill=page)
    draw.point((12, 6), fill=page_shadow)
    draw.point((11, 9), fill=page_shadow)
    draw.line(((3, 5), (3, 10)), fill=bright)
    draw.line(((4, 3), (10, 3)), fill=bright)
    draw.point((4, 12), fill=deep)
    draw.point((5 + (book.variant % 3), 4), fill=bright)
    draw.point((9 - (book.variant % 2), 12), fill=deep)

    for y, row in enumerate(book.glyph, start=5):
        for x, pixel in enumerate(row, start=4):
            if pixel == "#":
                draw.point((x, y), fill=bright)
    # A one-pixel element mark anchors the glyph to the cover's lower corner.
    draw.point((10, 13), fill=bright if book.variant % 2 else deep)
    return image


def write_models(root: Path) -> None:
    model_root = root / MODEL_ROOT
    model_root.mkdir(parents=True, exist_ok=True)
    for book in BOOKS:
        model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{MOD_ID}:item/{book.path}"},
        }
        (model_root / f"{book.path}.json").write_text(
            json.dumps(model, indent="\t") + "\n", encoding="utf-8"
        )


def write_items(root: Path) -> None:
    item_root = root / ITEM_ROOT
    for book in BOOKS:
        path = item_root / f"{book.path}.json"
        definition = json.loads(path.read_text(encoding="utf-8"))
        model = definition.get("model")
        if not isinstance(model, dict):
            raise ValueError(f"book item definition has no model: {path}")
        model.pop("tints", None)
        path.write_text(json.dumps(definition, indent="\t") + "\n", encoding="utf-8")


def write_textures(root: Path) -> None:
    texture_root = root / TEXTURE_ROOT
    texture_root.mkdir(parents=True, exist_ok=True)
    for book in BOOKS:
        draw_book(book).save(
            texture_root / f"{book.path}.png",
            format="PNG",
            optimize=False,
            compress_level=9,
        )


def generate(root: Path) -> None:
    write_models(root)
    write_items(root)
    write_textures(root)


def expected_jar_entry(book: Book, kind: str) -> str:
    return f"assets/{MOD_ID}/{kind}/{book.path}.{'png' if kind == 'textures/item' else 'json'}"


def audit(root: Path, jar_path: Path | None, strict_tints: bool) -> list[str]:
    errors: list[str] = []
    catalog = parse_catalog(root)
    expected = [book.path for book in BOOKS]
    if catalog != expected:
        errors.append(f"catalog mismatch: expected {len(expected)} known paths, found {len(catalog)}")

    visual_source = (root / VISUAL_SOURCE).read_text(encoding="utf-8")
    area_colors = dict(re.findall(r'areaVisual\("([a-z0-9_]+)"[^\n]*,\s*(0x[0-9A-Fa-f]{6})\)', visual_source))
    for path, color in area_colors.items():
        book = next((entry for entry in BOOKS if entry.path == path), None)
        if book is None:
            errors.append(f"area visual has no book icon: {path}")
        elif int(color, 16) != int(book.color[1:], 16):
            errors.append(f"area circle color differs from book icon: {path} ({color} != {book.color})")

    hashes: dict[str, str] = {}
    for book in BOOKS:
        model_path = root / MODEL_ROOT / f"{book.path}.json"
        texture_path = root / TEXTURE_ROOT / f"{book.path}.png"
        item_path = root / ITEM_ROOT / f"{book.path}.json"
        if not model_path.exists():
            errors.append(f"missing model: {model_path}")
        else:
            model = json.loads(model_path.read_text(encoding="utf-8"))
            if model.get("parent") != "minecraft:item/generated":
                errors.append(f"unexpected model parent: {model_path}")
            layer = model.get("textures", {}).get("layer0")
            if layer != f"{MOD_ID}:item/{book.path}":
                errors.append(f"model layer mismatch: {model_path} -> {layer}")
        if not texture_path.exists():
            errors.append(f"missing texture: {texture_path}")
        else:
            with Image.open(texture_path) as image:
                if image.size != (16, 16):
                    errors.append(f"texture dimensions are {image.size}: {texture_path}")
                if image.mode != "RGBA":
                    errors.append(f"texture mode is {image.mode}: {texture_path}")
            digest = hashlib.sha256(texture_path.read_bytes()).hexdigest()
            if digest in hashes:
                errors.append(f"duplicate texture bytes: {book.path} and {hashes[digest]}")
            hashes[digest] = book.path
        if not item_path.exists():
            errors.append(f"missing item definition: {item_path}")
        else:
            item = json.loads(item_path.read_text(encoding="utf-8"))
            item_model = item.get("model", {})
            if "tints" in item_model:
                message = f"obsolete item tint remains: {item_path}"
                if strict_tints:
                    errors.append(message)
                else:
                    print(f"warning: {message}")

    if jar_path is not None:
        if not jar_path.exists():
            errors.append(f"jar does not exist: {jar_path}")
        else:
            with zipfile.ZipFile(jar_path) as archive:
                entries = set(archive.namelist())
                for book in BOOKS:
                    for kind in ("models/item", "textures/item"):
                        entry = expected_jar_entry(book, kind)
                        if entry not in entries:
                            errors.append(f"missing jar entry: {entry}")

    print(f"catalog={len(catalog)} books={len(BOOKS)} textures={len(hashes)} errors={len(errors)}")
    return errors


def contact_sheet(root: Path, destination: Path) -> None:
    columns = 5
    scale = 6
    cell_width = 124
    cell_height = 126
    rows = (len(BOOKS) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_width, rows * cell_height), (20, 23, 34))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, book in enumerate(BOOKS):
        column = index % columns
        row = index // columns
        x = column * cell_width + 12
        y = row * cell_height + 7
        with Image.open(root / TEXTURE_ROOT / f"{book.path}.png") as source:
            icon = source.convert("RGBA").resize((16 * scale, 16 * scale), Image.Resampling.NEAREST)
        sheet.paste(icon, (x, y), icon)
        draw.text((x, y + 16 * scale + 4), book.label, fill=(238, 238, 242), font=font)
        draw.text((x, y + 16 * scale + 16), book.element, fill=tuple(int(book.color[index:index + 2], 16) for index in (1, 3, 5)), font=font)
    destination.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(destination, format="PNG", optimize=False, compress_level=9)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--generate", action="store_true", help="write models, book definitions, and textures")
    parser.add_argument("--audit", action="store_true", help="audit catalog resources")
    parser.add_argument("--strict-tints", action="store_true", help="fail if a book item retains a tint")
    parser.add_argument("--jar", type=Path, help="inspect resources in a built jar")
    parser.add_argument("--contact-sheet", type=Path, metavar="PATH", help="write an enlarged contact sheet")
    args = parser.parse_args()

    root = args.root.resolve()
    try:
        if args.generate:
            generate(root)
            print(f"generated {len(BOOKS)} book models, item definitions, and textures")
        if args.contact_sheet is not None:
            destination = args.contact_sheet if args.contact_sheet.is_absolute() else root / args.contact_sheet
            contact_sheet(root, destination)
            print(f"contact sheet: {destination}")
        if args.audit or args.jar is not None:
            jar_path = None if args.jar is None else (args.jar if args.jar.is_absolute() else root / args.jar)
            errors = audit(root, jar_path, args.strict_tints)
            for error in errors:
                print(f"error: {error}", file=sys.stderr)
            return 1 if errors else 0
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
