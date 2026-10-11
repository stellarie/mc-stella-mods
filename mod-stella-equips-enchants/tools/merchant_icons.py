"""Draws the 16x16 icons of the merchant weapons into the equips textures folder.

Run: python tools/merchant_icons.py
The bow and the crossbow take the frames of the Rudra Bow and the Siege Arbalest and swap the palette,
so the pull and charge frames keep the geometry the models expect.
"""
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "mod-stella-equips-enchants", "textures", "item")
OUT = os.environ.get("MERCHANT_ICON_OUT", TEX)

PAL = {
    ".": (0, 0, 0, 0),
    "K": (0x12, 0x10, 0x18, 255),
    "D": (0x25, 0x21, 0x2e, 255),
    "M": (0x3b, 0x36, 0x4a, 255),
    "L": (0x66, 0x61, 0x7e, 255),
    "H": (0x95, 0x90, 0xb0, 255),
    "C": (0x38, 0xd6, 0xf0, 255),
    "c": (0x1b, 0x8f, 0xb4, 255),
    "d": (0x0e, 0x56, 0x78, 255),
    "W": (0xd2, 0xfc, 0xff, 255),
    "G": (0x4a, 0x34, 0x2e, 255),
    "g": (0x2e, 0x1f, 0x22, 255),
    "V": (0xa8, 0x55, 0xf0, 255),
    "v": (0x6a, 0x2c, 0xb0, 255),
}


def canvas():
    return [["."] * 16 for _ in range(16)]


def put(grid, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16:
        grid[y][x] = ch


def save(grid, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), PAL[grid[y][x]])
    img.save(os.path.join(OUT, name + ".png"))
    return img


def sword():
    g = canvas()
    for x in range(6, 15):
        y = 15 - x
        put(g, x, y, "L")
        put(g, x - 1, y, "C")
        put(g, x, y + 1, "M")
        put(g, x + 1, y + 1, "K")
    put(g, 14, 1, "W")
    put(g, 13, 1, "C")
    put(g, 14, 2, "H")
    for x, y in [(3, 8), (4, 9), (5, 10), (6, 11), (7, 12)]:
        put(g, x, y, "D")
        put(g, x, y + 1, "K")
    put(g, 3, 8, "M")
    put(g, 7, 12, "M")
    put(g, 5, 10, "C")
    for x, y in [(4, 11), (3, 12), (2, 13)]:
        put(g, x, y, "G")
    put(g, 1, 14, "M")
    put(g, 0, 15, "K")
    put(g, 1, 15, "K")
    put(g, 2, 14, "g")
    for x, y in [(9, 4), (11, 2), (12, 0), (15, 3), (10, 1)]:
        put(g, x, y, "c")
    put(g, 12, 1, "W")
    put(g, 15, 2, "C")
    put(g, 9, 3, "C")
    return save(g, "soulreaver")


def mask_of(points):
    from PIL import ImageDraw
    m = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    return [[m.getpixel((x, y)) > 0 for x in range(16)] for y in range(16)]


def shade_head(g, mask, edge_dir):
    """Fills a mask with dark steel, an outline, and a soul-fire cutting edge on the side the edge points to."""
    dx, dy = edge_dir

    def inside(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and mask[y][x]

    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                continue
            border = not (inside(x + 1, y) and inside(x - 1, y) and inside(x, y + 1) and inside(x, y - 1))
            on_edge = not inside(x + dx, y + dy) or not inside(x + dx, y) or not inside(x, y + dy)
            if on_edge:
                put(g, x, y, "W" if (x + y) % 3 == 0 else "C")
            elif border:
                put(g, x, y, "K")
            elif not inside(x - dx, y - dy) or not inside(x - dx, y) or not inside(x, y - dy):
                put(g, x, y, "K")
            elif not inside(x + dx - 2 * dx, y + dy - 2 * dy):
                put(g, x, y, "D")
            else:
                put(g, x, y, "L" if (x - y) % 4 == 0 and x + y > 12 else "M")
    # A band of cyan just inside the edge keeps the edge reading as fire.
    for y in range(16):
        for x in range(16):
            if mask[y][x] and g[y][x] in ("L", "M") and (not inside(x + 2 * dx, y + 2 * dy)):
                put(g, x, y, "c")


def axe():
    g = canvas()
    for i in range(0, 9):
        put(g, 2 + i, 13 - i, "G")
        put(g, 3 + i, 13 - i, "g")
        put(g, 2 + i, 14 - i, "K")
    put(g, 1, 14, "g")
    put(g, 1, 13, "K")
    put(g, 2, 14, "g")
    # A broad cleaver blade on the far end of the haft, with its edge to the upper right.
    mask = mask_of([(6, 5), (8, 1), (12, 0), (15, 3), (15, 8), (12, 10), (10, 9)])
    shade_head(g, mask, (1, -1))
    # The haft enters the blade through a dark collar.
    for x, y in [(8, 7), (9, 6), (7, 8)]:
        put(g, x, y, "K")
    put(g, 10, 5, "M")
    for x, y in [(14, 1), (15, 2), (13, 11), (15, 9), (11, 11)]:
        put(g, x, y, "c")
    return save(g, "gravecleaver")


def spear():
    g = canvas()
    # A bare dark shaft with no head: it ends square, and soul fire wraps it and licks past the end.
    for i in range(0, 14):
        x, y = 1 + i, 14 - i
        put(g, x, y, "D")
        put(g, x - 1, y, "M")
        put(g, x, y + 1, "K")
    for i in (2, 5, 8, 11):
        x, y = 1 + i, 14 - i
        put(g, x - 1, y - 1, "C")
        put(g, x, y - 1, "c")
        put(g, x + 1, y, "C")
        put(g, x + 1, y + 1, "c")
        put(g, x, y, "W" if i == 8 else "C")
        put(g, x - 1, y, "c")
    # The flame at the tip: no metal, only fire.
    for x, y in [(14, 1), (15, 0), (14, 0), (13, 0), (15, 2), (12, 1), (15, 1)]:
        put(g, x, y, "C")
    put(g, 14, 0, "W")
    put(g, 15, 1, "W")
    put(g, 13, 1, "c")
    put(g, 12, 0, "c")
    put(g, 0, 15, "K")
    put(g, 1, 15, "g")
    put(g, 0, 14, "g")
    return save(g, "wraithpike")


def staff():
    g = canvas()
    for i in range(0, 11):
        x, y = 1 + i, 14 - i
        put(g, x, y, "D")
        put(g, x - 1, y, "M")
        put(g, x, y + 1, "K")
    for x, y in [(3, 12), (6, 9)]:
        put(g, x, y, "C")
        put(g, x - 1, y, "c")
    put(g, 0, 15, "K")
    put(g, 1, 15, "g")
    # The claw cradles an orb of soul fire at the top.
    claw = [
        "..........KKK...",
        ".........KcCCK..",
        "........KcCWWCK.",
        "........KCWWWCK.",
        ".........KCCcK..",
        "........MKKKK...",
    ]
    for dy, row in enumerate(claw):
        for x, ch in enumerate(row):
            if ch != ".":
                put(g, x, dy + 0, ch)
    for x, y in [(9, 5), (12, 5), (13, 4), (8, 6), (10, 6), (11, 6), (12, 6)]:
        put(g, x, y, "L" if (x + y) % 2 else "M")
    put(g, 10, 5, "K")
    put(g, 11, 5, "K")
    for x, y in [(14, 1), (15, 3), (8, 1), (12, 0)]:
        put(g, x, y, "c")
    return save(g, "archmage_staff")


def remap(name_in, name_out, mapper):
    img = Image.open(os.path.join(TEX, name_in + ".png")).convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            out.putpixel((x, y), mapper(r, g, b, a, x, y))
    out.save(os.path.join(OUT, name_out + ".png"))
    return out


def lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def bow_map(r, g, b, a, x, y):
    # Red studs become soul fire, the pale limb and string become glowing cyan, the dark body turns blue-black.
    if r > g + 45 and r > b + 45:
        return (0x38, 0xd6, 0xf0, a)
    l = lum(r, g, b)
    if l > 150:
        return (0xcf, 0xfb, 0xff, a)
    if l > 95:
        return (0x5a, 0xc8, 0xe0, a)
    if l > 55:
        return (0x35, 0x4a, 0x6e, a)
    if l > 30:
        return (0x1f, 0x24, 0x3c, a)
    return (0x0e, 0x10, 0x1c, a)


def crossbow_map(r, g, b, a, x, y):
    # Red and orange studs become hex violet, white tips become pale violet, the body turns violet-black.
    if r > g + 45 and r > b + 25:
        return (0xb8, 0x62, 0xff, a)
    l = lum(r, g, b)
    if l > 200:
        return (0xf0, 0xdc, 0xff, a)
    if l > 120:
        return (0x7c, 0x58, 0xb8, a)
    if l > 70:
        return (0x4b, 0x38, 0x73, a)
    if l > 38:
        return (0x2a, 0x20, 0x44, a)
    return (0x14, 0x10, 0x24, a)


def main():
    sheet = []
    sheet.append(sword())
    sheet.append(axe())
    sheet.append(spear())
    sheet.append(staff())
    for frame in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
        sheet.append(remap("rudra_bow" + frame, "wraithstring" + frame, bow_map))
    for frame in ("", "_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework"):
        sheet.append(remap("siege_arbalest" + frame, "hexbolt" + frame, crossbow_map))
    if len(sys.argv) > 1:
        scale = 12
        out = Image.new("RGBA", (len(sheet) * (16 * scale + 8), 16 * scale), (58, 58, 70, 255))
        for i, im in enumerate(sheet):
            big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
            out.paste(big, (i * (16 * scale + 8), 0), big)
        out.save(sys.argv[1])


if __name__ == "__main__":
    main()
