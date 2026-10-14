"""Draws the 16x16 staff icons. Run from the repository root: python mod-stella-equips-enchants/tools/staff_icons.py"""
from pathlib import Path

from PIL import Image

HEAD = [
    "..........###...",
    "........#######.",
    "........#######.",
    "........########",
    ".........#####..",
    "........#####...",
    "........#####...",
]
SHAFT = [
    ".......###......",
    "......###.......",
    ".....###........",
    "....###.........",
    "...###..........",
    "..###...........",
    ".###............",
    "###.............",
    "##..............",
]
OUTLINE = (18, 16, 24, 255)

# name: (dark, mid, light, shaft, shaft light, band)
STAVES = {
    "mage_staff": ((70, 74, 86, 255), (150, 156, 170, 255), (225, 230, 240, 255), (96, 66, 38, 255), (140, 100, 60, 255), (190, 192, 200, 255)),
    "amethyst_staff": ((86, 44, 140, 255), (160, 102, 224, 255), (225, 196, 255, 255), (96, 66, 38, 255), (140, 100, 60, 255), (160, 102, 224, 255)),
    "quartz_staff": ((150, 140, 132, 255), (232, 226, 218, 255), (255, 255, 252, 255), (110, 80, 48, 255), (156, 116, 72, 255), (218, 176, 64, 255)),
    "emberheart_staff": ((140, 40, 10, 255), (240, 120, 24, 255), (255, 222, 96, 255), (74, 52, 36, 255), (112, 80, 52, 255), (240, 120, 24, 255)),
    "prismarine_staff": ((24, 90, 84, 255), (70, 190, 170, 255), (190, 255, 240, 255), (90, 70, 50, 255), (130, 100, 68, 255), (70, 190, 170, 255)),
}


def draw(palette):
    dark, mid, light, shaft, shaft_light, band = palette
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cells = {(x, y) for y, row in enumerate(HEAD) for x, c in enumerate(row) if c == "#"}
    for x, y in cells:
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = OUTLINE
        elif (x + y) % 5 == 0 or (x, y - 1) not in cells or (x - 1, y) not in cells:
            px[x, y] = light
        elif (x + y) % 3 == 0:
            px[x, y] = dark
        else:
            px[x, y] = mid
    for i, row in enumerate(SHAFT):
        y = 7 + i
        xs = [x for x, c in enumerate(row) if c == "#"]
        for k, x in enumerate(xs):
            px[x, y] = OUTLINE if k in (0, len(xs) - 1) and len(xs) == 3 and k == 0 else (shaft_light if k == 1 else shaft)
        if i in (2, 3):
            for x in xs:
                px[x, y] = band
    return img


def main():
    root = Path(__file__).resolve().parents[2]
    equips = root / "mod-stella-equips-enchants/src/main/resources/assets/mod-stella-equips-enchants/textures/item"
    rpg = root / "mod-stella-rpg/src/main/resources/assets/mod-stella-rpg/textures/item"
    for name, palette in STAVES.items():
        target = (rpg if name == "mage_staff" else equips) / (name + ".png")
        draw(palette).save(target)
        print("wrote", target)


if __name__ == "__main__":
    main()
