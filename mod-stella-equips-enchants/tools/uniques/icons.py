#!/usr/bin/env python3
"""Icon tool of the uniques.

Needs Python 3 with Pillow 12. Run it from anywhere.

    python icons.py build [ID ...]    draw sprites/*.txt into 16x16 PNG textures
    python icons.py check [ID ...]    validate the sprite files and write nothing
    python icons.py models ID KIND    write the item definition and model files of a unique

A sprite file is a palette block, a blank line, then 16 rows of 16 characters:

    b=#EDE8D2FF
    s=#B9B294FF

    ................
    ...16 rows...

Each palette line is one character, '=', and a colour as #RRGGBBAA or #RRGGBB. The character '.' is transparent and
cannot be in the palette. A name is the file name without '.txt'. Sprites of one unique share its id as a prefix:

    bow       {id}, {id}_pulling_0, {id}_pulling_1, {id}_pulling_2
    crossbow  the bow frames, then {id}_arrow and {id}_firework
    others    {id}

KIND of the models command is one of: bow, crossbow, sword, axe, mace, spear, helmet, chestplate, leggings, boots.
"""
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(os.path.dirname(HERE))
NAMESPACE = "mod-stella-equips-enchants"
ASSETS = os.path.join(MOD, "src", "main", "resources", "assets", NAMESPACE)
TEXTURES = os.path.join(ASSETS, "textures", "item")
SPRITES = os.path.join(HERE, "sprites")
TEMPLATES = os.path.join(HERE, "templates")
SIZE = 16

# kind -> (item definition template, [(model template, file suffix, extra values)]).
MODELS = {
    "bow": ("bow.item.json", [
        ("bow.model.json", "", {}),
        ("bow_pulling.model.json", "_pulling_0", {"n": "0"}),
        ("bow_pulling.model.json", "_pulling_1", {"n": "1"}),
        ("bow_pulling.model.json", "_pulling_2", {"n": "2"}),
    ]),
    "crossbow": ("crossbow.item.json", [
        ("crossbow.model.json", "", {}),
        ("crossbow_pulling.model.json", "_pulling_0", {"n": "0"}),
        ("crossbow_pulling.model.json", "_pulling_1", {"n": "1"}),
        ("crossbow_pulling.model.json", "_pulling_2", {"n": "2"}),
        ("crossbow_arrow.model.json", "_arrow", {}),
        ("crossbow_firework.model.json", "_firework", {}),
    ]),
    "sword": ("single.item.json", [("handheld.model.json", "", {})]),
    "axe": ("single.item.json", [("handheld.model.json", "", {})]),
    "mace": ("single.item.json", [("handheld_mace.model.json", "", {})]),
    "spear": ("spear.item.json", [("generated.model.json", "", {}), ("spear_in_hand.model.json", "_in_hand", {})]),
    "helmet": ("single.item.json", [("generated.model.json", "", {})]),
    "chestplate": ("single.item.json", [("generated.model.json", "", {})]),
    "leggings": ("single.item.json", [("generated.model.json", "", {})]),
    "boots": ("single.item.json", [("generated.model.json", "", {})]),
}


class SpriteError(Exception):
    pass


def read_sprite(path):
    """Returns the 16 rows as lists of RGBA tuples or None."""
    with open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")
    palette = {}
    index = 0
    while index < len(lines) and lines[index].strip() != "":
        match = re.fullmatch(r"(\S)=#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?", lines[index].strip())
        if not match:
            raise SpriteError(f"{path}:{index + 1}: bad palette line {lines[index]!r}")
        char, rgb, alpha = match.groups()
        if char == ".":
            raise SpriteError(f"{path}:{index + 1}: '.' is transparent and cannot be in the palette")
        if char in palette:
            raise SpriteError(f"{path}:{index + 1}: palette character {char!r} is defined twice")
        palette[char] = tuple(int(rgb[i:i + 2], 16) for i in (0, 2, 4)) + (int(alpha or "FF", 16),)
        index += 1
    rows = [line.rstrip("\r") for line in lines[index + 1:] if line.strip() != ""]
    if len(rows) != SIZE:
        raise SpriteError(f"{path}: expected {SIZE} rows, found {len(rows)}")
    pixels = []
    for number, row in enumerate(rows):
        if len(row) != SIZE:
            raise SpriteError(f"{path}: row {number + 1} has {len(row)} characters, expected {SIZE}")
        converted = []
        for char in row:
            if char == ".":
                converted.append((0, 0, 0, 0))
            elif char in palette:
                converted.append(palette[char])
            else:
                raise SpriteError(f"{path}: row {number + 1} uses {char!r}, which the palette does not define")
        pixels.append(converted)
    if not any(pixel[3] for row in pixels for pixel in row):
        raise SpriteError(f"{path}: the sprite is fully transparent")
    return pixels


def sprite_names():
    if not os.path.isdir(SPRITES):
        return []
    return sorted(name[:-4] for name in os.listdir(SPRITES) if name.endswith(".txt"))


def groups(names):
    """Maps each unique id to its frame names. A frame suffix is stripped to find the id."""
    suffix = re.compile(r"^(?P<id>.+?)(_pulling_[012]|_arrow|_firework)?$")
    found = {}
    for name in names:
        found.setdefault(suffix.match(name).group("id"), []).append(name)
    return found


def required(unique, frames):
    if any(name.endswith(("_arrow", "_firework")) for name in frames):
        return [unique] + [f"{unique}_pulling_{n}" for n in range(3)] + [f"{unique}_arrow", f"{unique}_firework"]
    if any("_pulling_" in name for name in frames):
        return [unique] + [f"{unique}_pulling_{n}" for n in range(3)]
    return [unique]


def selected(wanted):
    names = sprite_names()
    found = groups(names)
    if wanted:
        for unique in wanted:
            if unique not in found:
                raise SpriteError(f"no sprite file for {unique} in {SPRITES}")
        found = {unique: found[unique] for unique in wanted}
    return found


def check_group(unique, frames):
    missing = [name for name in required(unique, frames) if name not in frames]
    if missing:
        raise SpriteError(f"{unique}: missing frames {', '.join(missing)}")
    for name in required(unique, frames):
        read_sprite(os.path.join(SPRITES, name + ".txt"))


def build(wanted, write):
    found = selected(wanted)
    count = 0
    for unique, frames in sorted(found.items()):
        check_group(unique, frames)
        for name in required(unique, frames):
            pixels = read_sprite(os.path.join(SPRITES, name + ".txt"))
            if write:
                image = Image.new("RGBA", (SIZE, SIZE))
                for y, row in enumerate(pixels):
                    for x, pixel in enumerate(row):
                        image.putpixel((x, y), pixel)
                os.makedirs(TEXTURES, exist_ok=True)
                image.save(os.path.join(TEXTURES, name + ".png"))
            count += 1
    print(f"{'built' if write else 'checked'} {count} sprites of {len(found)} uniques")


def fill(template, values):
    with open(os.path.join(TEMPLATES, template), encoding="utf-8") as handle:
        text = handle.read()
    for key, value in values.items():
        text = text.replace("{" + key + "}", value)
    leftover = re.findall(r"\{[a-z]+\}", text)
    if leftover:
        raise SpriteError(f"{template}: unfilled placeholders {leftover}")
    json.loads(text)
    return text


def models(unique, kind):
    if kind not in MODELS:
        raise SpriteError(f"unknown kind {kind!r}; use one of {', '.join(MODELS)}")
    if not re.fullmatch(r"[a-z0-9_]+", unique):
        raise SpriteError(f"bad unique id {unique!r}")
    item_template, parts = MODELS[kind]
    os.makedirs(os.path.join(ASSETS, "items"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "models", "item"), exist_ok=True)
    base = {"ns": NAMESPACE, "id": unique}
    with open(os.path.join(ASSETS, "items", unique + ".json"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write(fill(item_template, base))
    for template, suffix, extra in parts:
        with open(os.path.join(ASSETS, "models", "item", unique + suffix + ".json"), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(fill(template, {**base, **extra}))
    print(f"wrote the item definition and {len(parts)} models of {unique} ({kind})")


def main(argv):
    if len(argv) < 2 or argv[1] not in ("build", "check", "models"):
        print(__doc__)
        return 2
    try:
        if argv[1] == "models":
            if len(argv) != 4:
                print("usage: icons.py models ID KIND")
                return 2
            models(argv[2], argv[3])
        else:
            build(argv[2:], argv[1] == "build")
    except SpriteError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
