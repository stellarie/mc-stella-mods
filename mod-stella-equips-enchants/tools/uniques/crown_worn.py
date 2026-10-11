#!/usr/bin/env python3
"""Draws the worn model texture of the Skeleton Crown.

Needs Python 3 with Pillow 12. Run it from anywhere. It writes the humanoid armor layer: a 64x32 sheet whose
head faces hold a netherite band with bone spikes, and nothing else.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(MOD, "src", "main", "resources", "assets", "mod-stella-equips-enchants",
                   "textures", "entity", "equipment", "humanoid", "skeleton_crown.png")

BONE = (228, 221, 192, 255)
BONE_SHADE = (184, 176, 143, 255)
DARK = (46, 42, 52, 255)
MID = (74, 69, 83, 255)
EDGE = (20, 17, 24, 255)
EMBER = (232, 116, 42, 255)

# Four rows keep the brow clear of the eyes. Rows run from the top of a head face downward. Spikes sit four columns apart, so they repeat around the head.
SIDE = [
    "..b...b.",
    ".sbs.sbs",
    "dddddddd",
    "mmmmmmmm",
]
FRONT = [
    "..b...b.",
    ".sbs.sbs",
    "dddbbddd",
    "mmmoommm",
]
PALETTE = {"b": BONE, "s": BONE_SHADE, "d": DARK, "m": MID, "e": EDGE, "o": EMBER}

# Head box of a 64x32 humanoid sheet: right 0, front 8, left 16, back 24 across; faces start at row 8.
FACES = [(0, SIDE), (8, FRONT), (16, SIDE), (24, SIDE)]


def main():
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for left, rows in FACES:
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                if char != ".":
                    image.putpixel((left + x, 8 + y), PALETTE[char])
    # The top face keeps only its rim, so the crown shows from above and the head still shows through.
    for i in range(8):
        for x, y in ((8 + i, 0), (8 + i, 7), (8, i), (15, i)):
            image.putpixel((x, y), DARK)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
