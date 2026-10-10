#!/usr/bin/env python3
"""The 16x16 art of the thirty skill books: fighter, archer, priest.

One entry per book: its path, its label, its school, its accent colour, and the
pixel ops that draw it over the school's plate. skillbook-icons.py reads this
table, so a generator pass cannot redraw a book with a shape this file does not
declare. Each shape is outlined after it is drawn, which is what makes one skill
tell from another at sixteen pixels.

  python scripts/skillbook-icons.py            write the thirty shipped icons
  python scripts/skill_icons.py --out DIR      write a review sheet instead
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

MOD_ID = "mod-stella-spells"
SHIPPED = Path(f"mod-stella-spells/src/main/resources/assets/{MOD_ID}/textures/item")
SIZE = 16
SCALE = 6
# The art box: x and y from 3 to 12, so a shape centred on 8 is centred on the plate.
LO, HI = 3, 12
OUTLINE = (15, 18, 28, 255)

SCHOOLS = {
    "fighter": ((0xB3, 0x3A, 0x3A), (0x3A, 0x1E, 0x1E), 2),
    "archer": ((0x4F, 0xA6, 0x5C), (0x1E, 0x35, 0x22), 3),
    "priest": ((0xE3, 0xC2, 0x5A), (0x3A, 0x34, 0x20), 2),
}
# The square tile each school sits on, as the mage spellbooks do: frame, bevel, upper fill, lower fill.
TILES = {
    "fighter": ((0x24, 0x08, 0x08), (0x6E, 0x22, 0x22), (0x46, 0x16, 0x16), (0x56, 0x1E, 0x1C)),
    "archer": ((0x0A, 0x1C, 0x12), (0x2A, 0x5A, 0x38), (0x17, 0x3A, 0x24), (0x1F, 0x4A, 0x2E)),
    "priest": ((0x26, 0x1E, 0x08), (0x8A, 0x73, 0x2E), (0x4A, 0x3C, 0x16), (0x5C, 0x4A, 0x1C)),
}
# Colours a "pixels" op may name, one letter each.
PIXELS = {
    "w": (0xFA, 0xFA, 0xFF), "m": (0xC8, 0xD2, 0xDC), "M": (0x7C, 0x88, 0x94), "b": (0xA8, 0x72, 0x3E),
    "B": (0x6A, 0x44, 0x22), "g": (0x9B, 0xE8, 0x7A), "G": (0x4F, 0xA6, 0x5C), "y": (0xFF, 0xD1, 0x66),
    "Y": (0xE0, 0x9A, 0x2A), "r": (0xF0, 0x60, 0x5A), "R": (0xA8, 0x2E, 0x2A), "o": (0xFF, 0x9A, 0x3C),
    "c": (0x8F, 0xE4, 0xF0), "C": (0x3E, 0x9C, 0xC0), "p": (0xC7, 0x9B, 0xFF), "P": (0x7A, 0x4E, 0xC0),
    "k": (0x05, 0x12, 0x0A), "n": (0x8A, 0x6A, 0x4A), "N": (0x4E, 0x3A, 0x28), "f": (0xE8, 0xD8, 0xB8),
}


def mix(colour: tuple[int, int, int], other: tuple[int, int, int], t: float) -> tuple[int, int, int, int]:
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(colour, other)) + (255,)  # type: ignore[return-value]


WHITE = (255, 255, 255)
BLACK = (0, 0, 0)

# A skill is its path, its label, its school, its accent main colour, and its art.
# Art ops draw in the box, one colour key each: a accent, s shade, h highlight.
SKILLS: tuple[tuple[str, str, str, int, tuple[tuple, ...]], ...] = (
    # ---- fighter: iron and fire on a clipped banner ----
    ("cleave", "Cleave", "fighter", 0xDCE1E8, (
        ("line", 4, 12, 12, 4, 2, "a"),
        ("line", 6, 12, 12, 6, 1, "s"),
        ("dot", 11, 4, 1, "h"),
    )),
    ("leap_slam", "Leap Slam", "fighter", 0xF2B23C, (
        ("chev", 8, 6, 3, "up", "a"),
        ("chev", 8, 9, 3, "up", "a"),
        ("line", 3, 12, 12, 12, 1, "s"),
        ("dot", 4, 12, 1, "h"),
        ("dot", 11, 12, 1, "h"),
    )),
    ("whirlwind", "Whirlwind", "fighter", 0xBFE9F5, (
        ("ring", 8, 8, 4, 1, "a"),
        ("spokes", 8, 8, 1, 4, 4, 1, "a"),
        ("ring", 8, 8, 2, 1, "s"),
    )),
    ("shield_bash", "Shield Bash", "fighter", 0xE0B24A, (
        ("poly", "5,3 11,3 11,8 8,12 5,8", "a"),
        ("spokes", 8, 7, 1, 3, 6, 1, "h"),
    )),
    ("charge", "Charge", "fighter", 0xF08A3C, (
        ("chev", 5, 8, 4, "right", "a"),
        ("chev", 9, 8, 4, "right", "a"),
        ("line", 3, 4, 6, 4, 1, "s"),
        ("line", 3, 12, 6, 12, 1, "s"),
    )),
    ("execute", "Execute", "fighter", 0xE8433C, (
        ("poly", "6,3 10,3 9,7 7,7", "a"),
        ("line", 8, 7, 8, 9, 1, "h"),
        ("spokes", 8, 10, 1, 3, 6, 1, "h"),
    )),
    ("war_cry", "War Cry", "fighter", 0xFFC94A, (
        ("poly", "3,6 8,8 3,10", "a"),
        ("arc", 8, 8, 3, -55, 55, "h"),
        ("arc", 8, 8, 4, -45, 45, "s"),
    )),
    ("counter_stance", "Counter Stance", "fighter", 0xF5E27A, (
        ("poly", "6,4 11,4 11,9 8,12 6,9", "a"),
        ("line", 3, 3, 7, 7, 2, "h"),
    )),
    ("iron_skin", "Iron Skin", "fighter", 0xAFC0D6, (
        ("box", 4, 4, 12, 12, "a"),
        ("frame", 4, 4, 12, 12, "s"),
        ("line", 5, 11, 11, 5, 1, "h"),
        ("dot", 5, 5, 0, "h"),
        ("dot", 11, 11, 0, "h"),
    )),
    ("earthsplitter", "Earthsplitter", "fighter", 0xC08A4E, (
        ("line", 3, 7, 12, 7, 1, "s"),
        ("poly", "8,7 5,10 9,10 6,12", "a"),
        ("dot", 3, 10, 1, "s"),
        ("dot", 12, 9, 1, "s"),
    )),
    # ---- archer: Ragnarok archer line, hand-placed pixels on a forest tile ----
    ("arrow_crafting", "Arrow Crafting", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..............",
            ".........m.y..",
            "........mM.y..",
            ".......mM.....",
            "......mM..w...",
            ".....bM.......",
            "....bn..m.....",
            "...bn..mM.....",
            "..gb...M......",
            "..gg.nn.......",
            ".....nN.......",
            "..............",
            "..............",
        ), "a"),
    )),
    ("vultures_eye", "Vulture's Eye", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..............",
            "....YYYYYY....",
            "..YYwwwwwwYY..",
            ".YwwwyyyywwwY.",
            ".wwwyykkyywww.",
            ".YwwwyyyywwwY.",
            "..YYwwwwwwYY..",
            "....YYYYYY....",
            "..............",
            "..bbbbbbbbbm..",
            "..............",
            "..............",
            "..............",
        ), "a"),
    )),
    ("summon_wolf", "Summon Wolf", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..M........M..",
            "..MM......MM..",
            "..MmM....MmM..",
            "..MmmmmmmmM...",
            "..mwkmmmmkwm..",
            "..mmmmmmmmmm..",
            "...mmffffmm...",
            "....mfkkfm....",
            "....mmrrmm....",
            ".....mmmm.....",
            "..............",
            "..............",
            "..............",
        ), "a"),
    )),
    ("multishot", "Grappling Hook", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "......m.......",
            ".....mMm......",
            "......m.......",
            "..m...m...m...",
            "..m...m...m...",
            "..mm..m..mm...",
            "...mmmmmmm....",
            "......M.......",
            "......n.......",
            ".......n......",
            "........n.....",
            ".........nn...",
            "...........nn.",
            ".............n",
        ), "a"),
    )),
    ("piercing_shot", "Sharp Shooting", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "...........w..",
            "..............",
            ".........w...w",
            "..............",
            "...........w..",
            ".ccrr...rr..m.",
            ".bbbbbbbbbbbMm",
            ".ccwwr.rwwr.m.",
            "...rr...rr....",
            "..............",
            "..............",
            "..............",
            "..............",
            "..............",
        ), "a"),
    )),
    ("arrow_rain", "Arrow Shower", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..cbc.....cbc.",
            "...b..cbc..b..",
            "...b...b...b..",
            "...b...b...b..",
            "...b...b...b..",
            "...b...b...b..",
            "..mMm..b..mMm.",
            "...m..mMm..m..",
            ".......m......",
            "M............M",
            "..............",
            "MnnnnnnnnnnnnM",
            "..............",
        ), "a"),
    )),
    ("rapid_fire", "Double Strafe", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            ".......mm.....",
            ".......Mm.....",
            "......b.......",
            ".....b........",
            "....b......mm.",
            "...b.......Mm.",
            ".yb.......b...",
            ".yy......b....",
            "........b.....",
            ".......b......",
            ".....yb.......",
            ".....yy.......",
            "..............",
        ), "a"),
    )),
    ("hunters_mark", "Blitz Beat", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "N...........N.",
            "nN.........Nn.",
            ".nN.......Nn..",
            ".nnN.....Nnn..",
            "..nnN...Nnn...",
            "...nnNfNnn....",
            "....nNfNn.....",
            "......f.......",
            ".....yfy......",
            "...w..y...w...",
            "..............",
            "...w......w...",
            "..............",
        ), "a"),
    )),
    ("ankle_snare", "Ankle Snare", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..............",
            "..m.m.m.m.m...",
            "..m.m.m.m.m...",
            ".MmmmmmmmmmM..",
            ".M.........M..",
            ".M.........M..",
            ".MmmmmmmmmmM..",
            "..m.m.m.m.m...",
            "..m.m.m.m.m...",
            "......M.......",
            "......M.......",
            "....nnMnn.....",
            "..............",
        ), "a"),
    )),
    ("arrow_repel", "Arrow Repel", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..............",
            ".............y",
            "............y.",
            "...........oy.",
            "..........o.y.",
            ".oo.....m.o.y.",
            ".bbbbbbbMmo.y.",
            ".oo.....m.o.y.",
            "..........o.y.",
            "...........oy.",
            ".............y",
            "..............",
            "..............",
        ), "a"),
    )),
    ("explosive_arrow", "Explosive Arrow", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "........y.o.y.",
            ".........oyo..",
            ".......yoywyo.",
            ".........oyo..",
            "........y.o.y.",
            "......mm......",
            "......Mm......",
            ".....b........",
            "....b.........",
            "...b..........",
            ".gb...........",
            ".gg...........",
            "..............",
        ), "a"),
    )),
    ("disengage", "Wind Walk", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            "..............",
            "ccc...........",
            "..............",
            ".cccc....NNN..",
            ".........NnN..",
            "cc.......NnN..",
            ".........NnN..",
            "........NnnN..",
            "....NNNNnnnN..",
            "...NnnnnnnnNN.",
            "...NNNNNNNNNN.",
            "..............",
            "..............",
        ), "a"),
    )),
    ("snare_trap", "Concentrate", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..............",
            ".yy........yy.",
            ".y..........y.",
            "......B.......",
            ".....B........",
            "....B...m.....",
            "....B..mm.....",
            "....BwbbbbbbM.",
            "....B..mm.....",
            "....B...m.....",
            ".....B........",
            ".y....B.....y.",
            ".yy........yy.",
            "..............",
        ), "a"),
    )),
    ("eagle_eye", "True Sight", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "......y.......",
            "..y...y...y...",
            "...y.....y....",
            "..............",
            "....wwwww.....",
            "..wwwcccwww...",
            ".wwwcCkCcwww..",
            "..wwwcccwww...",
            "....wwwww.....",
            "..............",
            "...y.....y....",
            "..y...y...y...",
            "......y.......",
            "..............",
        ), "a"),
    )),
    ("elemental_quiver", "Spectral Quiver", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "..c...c...c...",
            ".ccc.ccc.ccc..",
            "..w...w...w...",
            "..w...w...w...",
            ".PPPPPPPPPPP..",
            ".PpppppppppP..",
            ".PpcpppppcpP..",
            ".PppppppppPP..",
            "..PpppppppP...",
            "..PpcppppcP...",
            "..PppppppPP...",
            "...PpppppP....",
            "...PPPPPPP....",
            "..............",
        ), "a"),
    )),
    ("aimed_bolt", "Aimed Bolt", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "......r....ww.",
            "......r....Mw.",
            "...rrrrrrrb...",
            "..r...r..br...",
            ".r....r.b..r..",
            ".r....pb....r.",
            "rrrr..pp.rrrr.",
            ".r..........r.",
            ".r....r....r..",
            "..r...r...r...",
            "...rrrrrrr....",
            "......r.......",
            "......r.......",
            "..............",
        ), "a"),
    )),
    ("triangle_shot", "Triangle Shot", "archer", 0x4FA65C, (
        ("pixels", 1, 1, (
            "......m.......",
            ".....mMm......",
            "......b.......",
            ".mm...b...mm..",
            ".mM...b...Mm..",
            "...b..b..b....",
            "....b.b.b.....",
            ".....bbb......",
            "......b.......",
            "....gb.bg.....",
            "....gg.gg.....",
            "..............",
            "....y.y.y.....",
            "..............",
        ), "a"),
    )),
    # ---- priest: gold and light, an octagon reliquary ----
    ("holy_light", "Holy Light", "priest", 0xFFF3C4, (
        ("disc", 8, 8, 3, "a"),
        ("spokes", 8, 8, 4, 5, 8, 1, "a"),
        ("line", 8, 5, 8, 11, 1, "h"),
        ("line", 5, 8, 11, 8, 1, "h"),
    )),
    ("prayer_of_mending", "Prayer of Mending", "priest", 0xFFC7D8, (
        ("arc", 8, 11, 5, 205, 335, "a"),
        ("arc", 8, 11, 3, 205, 335, "s"),
        ("dot", 8, 4, 1, "h"),
        ("spokes", 8, 4, 2, 3, 4, 1, "h"),
    )),
    ("renew", "Renew", "priest", 0xB8F0A8, (
        ("arc", 8, 8, 4, 55, 305, "a"),
        ("poly", "10,3 12,6 9,7", "a"),
        ("ellipse", 8, 8, 2, 3, "h"),
    )),
    ("divine_shield", "Divine Shield", "priest", 0xCFE6FF, (
        ("poly", "5,4 11,4 11,8 8,12 5,8", "a"),
        ("ellipse", 8, 3, 4, 1, "h"),
    )),
    ("holy_arms", "Holy Arms", "priest", 0xFFF8DC, (
        ("line", 8, 4, 8, 12, 2, "a"),
        ("line", 6, 5, 10, 5, 1, "h"),
        ("line", 6, 7, 3, 5, 1, "h"),
        ("line", 6, 9, 3, 10, 1, "h"),
        ("line", 10, 7, 12, 6, 1, "h"),
        ("line", 10, 9, 12, 10, 1, "h"),
    )),
    ("blessing_of_fortitude", "Blessing of Fortitude", "priest", 0xF5C9A0, (
        ("poly", "5,4 8,3 11,4 11,8 8,12 5,8", "a"),
        ("line", 8, 5, 8, 9, 1, "h"),
        ("line", 6, 6, 10, 6, 1, "h"),
    )),
    ("smite", "Smite", "priest", 0xFFFFFF, (
        ("box", 5, 3, 11, 6, "a"),
        ("line", 8, 6, 8, 12, 2, "a"),
        ("spokes", 8, 9, 2, 4, 8, 1, "h"),
    )),
    ("resurrection_ward", "Resurrection Ward", "priest", 0xE0C7FF, (
        ("ring", 8, 8, 4, 1, "a"),
        ("arrow", 8, 11, 8, 4, 1, "h"),
        ("dot", 4, 8, 0, "s"),
        ("dot", 12, 8, 0, "s"),
    )),
    ("consecrate", "Consecrate", "priest", 0xFFD98A, (
        ("ellipse", 8, 10, 5, 2, "a"),
        ("poly", "8,3 10,6 9,7 8,5 7,7 6,6", "h"),
        ("dot", 3, 10, 0, "s"),
        ("dot", 12, 10, 0, "s"),
    )),
    ("guardian_angel", "Guardian Angel", "priest", 0xEAF4FF, (
        ("line", 8, 4, 8, 11, 1, "h"),
        ("dot", 8, 3, 1, "h"),
        ("line", 7, 6, 3, 4, 1, "a"),
        ("line", 7, 8, 3, 8, 1, "a"),
        ("line", 7, 10, 3, 11, 1, "a"),
        ("line", 9, 6, 12, 4, 1, "a"),
        ("line", 9, 8, 12, 8, 1, "a"),
        ("line", 9, 10, 12, 11, 1, "a"),
    )),
    ("unbroken_vow", "Unbroken Vow", "priest", 0xC8D2DE, (
        ("box", 5, 5, 11, 6, "a"),
        ("line", 8, 6, 8, 8, 1, "s"),
        ("line", 6, 8, 10, 8, 2, "a"),
        ("box", 5, 11, 11, 12, "s"),
        ("dot", 8, 3, 1, "h"),
    )),
    ("steadfast_prayer", "Steadfast Prayer", "priest", 0xF0E6B8, (
        ("line", 8, 5, 8, 12, 2, "a"),
        ("ring", 8, 4, 2, 1, "h"),
        ("box", 6, 12, 10, 12, "s"),
        ("dot", 4, 8, 0, "a"),
        ("dot", 12, 8, 0, "a"),
    )),
)


class Art:
    """The art box of one icon: one mask per colour key, merged in draw order."""

    def __init__(self) -> None:
        self.layers: dict[str, tuple[Image.Image, ImageDraw.ImageDraw]] = {}
        self.pixels: dict[tuple[int, int], tuple[int, int, int]] = {}

    def layer(self, key: str) -> ImageDraw.ImageDraw:
        if key not in self.layers:
            image = Image.new("L", (SIZE, SIZE), 0)
            self.layers[key] = (image, ImageDraw.Draw(image))
        return self.layers[key][1]

    def mask(self, key: str) -> Image.Image | None:
        return self.layers[key][0] if key in self.layers else None


def clip(value: float) -> float:
    return max(LO, min(HI, value))


def draw_ops(art: Art, ops: tuple[tuple, ...]) -> None:
    for op in ops:
        kind, args, key = op[0], op[1:-1], op[-1]
        if kind == "pixels":
            x0, y0, rows = args
            for y, row in enumerate(rows):
                for x, letter in enumerate(row):
                    if letter != ".":
                        art.pixels[(x0 + x, y0 + y)] = PIXELS[letter]
            continue
        draw = art.layer(key)
        if kind == "dot":
            x, y, r = args
            draw.ellipse([x - r, y - r, x + r + 1, y + r + 1], fill=255)
        elif kind == "box":
            draw.rectangle(list(args), fill=255)
        elif kind == "frame":
            draw.rectangle(list(args), outline=255)
        elif kind == "disc":
            cx, cy, r = args
            draw.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=255)
        elif kind == "ring":
            cx, cy, r, _ = args
            draw.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], outline=255)
        elif kind == "ellipse":
            cx, cy, rx, ry = args
            draw.ellipse([cx - rx, cy - ry, cx + rx - 1, cy + ry - 1], outline=255)
        elif kind == "line":
            x0, y0, x1, y1, w = args
            draw.line([clip(x0), clip(y0), clip(x1), clip(y1)], fill=255, width=w)
        elif kind == "arrow":
            x0, y0, x1, y1, w = args
            draw.line([x0, y0, x1, y1], fill=255, width=w)
            angle = math.degrees(math.atan2(y1 - y0, x1 - x0))
            for spread in (-150.0, 150.0):
                hx = x1 - 3.0 * math.cos(math.radians(angle + spread))
                hy = y1 - 3.0 * math.sin(math.radians(angle + spread))
                draw.line([x1, y1, hx, hy], fill=255, width=w + 1)
        elif kind == "chev":
            cx, cy, size, direction, = args
            if direction == "up":
                draw.line([cx - size, cy + size, cx, cy - size], fill=255, width=2)
                draw.line([cx, cy - size, cx + size, cy + size], fill=255, width=2)
            elif direction == "left":
                draw.line([cx + size, cy - size, cx - size, cy], fill=255, width=2)
                draw.line([cx - size, cy, cx + size, cy + size], fill=255, width=2)
            else:
                draw.line([cx - size, cy - size, cx + size, cy], fill=255, width=2)
                draw.line([cx + size, cy, cx - size, cy + size], fill=255, width=2)
        elif kind == "spokes":
            cx, cy, inner, outer, count, w = args
            for index in range(count):
                angle = math.radians(index * 360.0 / count - 90.0)
                draw.line([cx + inner * math.cos(angle), cy + inner * math.sin(angle),
                           cx + outer * math.cos(angle), cy + outer * math.sin(angle)], fill=255, width=w)
        elif kind == "arc":
            cx, cy, r, start, end = args
            draw.arc([cx - r, cy - r, cx + r, cy + r], start, end, fill=255, width=2)
        elif kind == "poly":
            points, = args
            draw.polygon([tuple(float(value) for value in pair.split(",")) for pair in points.split()], fill=255)
        else:
            raise ValueError(f"unknown op {kind}")


def plate(school: str) -> tuple[Image.Image, Image.Image]:
    """The school's square tile, and the mask of the pixels the art may outline: all but the frame."""
    frame, bevel, upper, lower = TILES[school]
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    solid = Image.new("L", (SIZE, SIZE), 0)
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                colour = frame
            elif x == 1 or y == 1:
                colour = bevel
            else:
                colour = upper if y < SIZE // 2 + 1 else lower
            image.putpixel((x, y), colour + (255,))
            if 0 < x < SIZE - 1 and 0 < y < SIZE - 1:
                solid.putpixel((x, y), 255)
    return image, solid


def render(path: str, school: str, accent: int, ops: tuple[tuple, ...]) -> Image.Image:
    main = ((accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF)
    colours = {
        "a": mix(main, BLACK, 0.0),
        "s": mix(main, BLACK, 0.55),
        "h": mix(main, WHITE, 0.6),
        "k": OUTLINE,
    }
    image, solid = plate(school)
    art = Art()
    draw_ops(art, ops)
    art_mask = Image.new("L", (SIZE, SIZE), 0)
    for key, (mask, _) in art.layers.items():
        if key == "k":
            continue
        art_mask.paste(255, mask=mask)
    for spot in art.pixels:
        art_mask.putpixel(spot, 255)
    # Outline every art pixel that touches the plate, so the shape holds on any background.
    edge = Image.new("L", (SIZE, SIZE), 0)
    for y in range(SIZE):
        for x in range(SIZE):
            if art_mask.getpixel((x, y)) != 0:
                continue
            if solid.getpixel((x, y)) == 0:
                continue
            near = any(
                0 <= x + dx < SIZE and 0 <= y + dy < SIZE and art_mask.getpixel((x + dx, y + dy)) != 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            if near:
                edge.putpixel((x, y), 255)
    for key in ("a", "s", "h"):
        mask = art.mask(key)
        if mask is not None:
            image.paste(colours[key], mask=mask)
    for spot, colour in art.pixels.items():
        image.putpixel(spot, colour + (255,))
    image.paste(colours["k"], mask=edge)
    return image


def build(root: Path, out: Path) -> list[tuple[str, str, str, Path, Path]]:
    out.mkdir(parents=True, exist_ok=True)
    written = []
    for path, label, school, accent, ops in SKILLS:
        icon = render(path, school, accent, ops)
        target = out / f"{path}.png"
        icon.save(target, format="PNG", optimize=False, compress_level=9)
        written.append((path, label, school, root / SHIPPED / f"{path}.png", target))
    return written


def labelled(draw: ImageDraw.ImageDraw, x: int, y: int, text: str, fill: tuple[int, int, int]) -> None:
    draw.text((x + 4, y), text, fill=fill)


def sheet(rows: list[tuple[str, str, str, Path, Path]], destination: Path) -> None:
    cell = SIZE * SCALE
    pair = 2 * cell + 18 + 150
    band = 34
    header = 46
    height = header + 3 * (band + 5 * (cell + 10) + 14) + 16
    width = pair * 2 + 40
    canvas = Image.new("RGB", (width, height), (20, 23, 34))
    draw = ImageDraw.Draw(canvas)
    draw.text((20, 16), "Stella skill books - current (left) vs proposed (right), 6x", fill=(230, 234, 244))
    y = header
    for school in ("fighter", "archer", "priest"):
        members = [row for row in rows if row[2] == school]
        frame = SCHOOLS[school][0]
        draw.text((20, y + 8), f"{school.upper()}   ({len(members)} books)", fill=frame)
        draw.line([(20, y + 26), (width - 20, y + 26)], fill=frame, width=1)
        y += band
        for index in range(0, len(members), 2):
            for column, member in enumerate(members[index:index + 2]):
                _, label, _, before, after = member
                x = 20 + column * pair
                with Image.open(before) as source:
                    old = source.convert("RGBA").resize((cell, cell), Image.Resampling.NEAREST)
                with Image.open(after) as source:
                    new = source.convert("RGBA").resize((cell, cell), Image.Resampling.NEAREST)
                canvas.paste(old, (x, y), old)
                canvas.paste(new, (x + cell + 18, y), new)
                draw.rectangle([x + cell + 16, y - 2, x + cell + 16 + cell + 2, y + cell + 2], outline=frame)
                labelled(draw, x + 2 * cell + 20, y + cell // 2 - 5, label, frame)
            y += cell + 10
        y += 14
    destination.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(destination, format="PNG", optimize=False, compress_level=9)
    print(f"wrote {len(rows)} icons and the sheet {destination}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, default=Path("scripts/icon-proposal"))
    args = parser.parse_args()
    root = args.root.resolve()
    out = args.out if args.out.is_absolute() else root / args.out
    rows = build(root, out)
    sheet(rows, out / "skill-icons-proposed.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


# ---- read by skillbook-icons.py, so the thirty icons have one home ----
BY_PATH = {path: (label, school, accent) for path, label, school, accent, _ in SKILLS}


def label_of(path: str) -> str:
    return BY_PATH[path][0]


def school_of(path: str) -> str:
    return BY_PATH[path][1]


def accent_of(path: str) -> int:
    return BY_PATH[path][2]


def shapes(path: str) -> tuple[tuple, ...]:
    for candidate, _, _, _, ops in SKILLS:
        if candidate == path:
            return ops
    raise KeyError(path)


def draw(path: str) -> Image.Image:
    _, school, accent, ops = (*BY_PATH[path], shapes(path))
    return render(path, school, accent, ops)
