#!/usr/bin/env python3
"""Draws the item art of the ruins mod from templates, recolored by palette.

Usage:
  python ruin_textures.py --spec SPEC.json --out ASSETS_DIR
  python ruin_textures.py --self-test
  python ruin_textures.py --spec SPEC.json --out ASSETS_DIR --preview

ASSETS_DIR is the folder that holds `textures`, `models`, `items` and `equipment`, for
example `src/main/resources/assets/mod-stella-ruins`.

The spec is JSON:
  {
    "namespace": "mod-stella-ruins",
    "palettes": {"name": ["#outline", "#shadow", "#base", "#light", "#highlight"]},
    "items": [{"id": "x_ingot", "template": "ingot", "palette": "name"}],
    "sets": [{"id": "x", "asset": "x", "palette": "name"}],
    "pieces": [{"id": "x_helmet", "armor": "helmet", "asset": "x", "palette": "name"}]
  }
A set writes four armor items (`<id>_helmet`, `_chestplate`, `_leggings`, `_boots`), the equipment asset,
and both humanoid layer textures. A piece writes one armor item and the same asset and layers. The tool
uses the Python standard library only.
"""

import argparse
import json
import os
import shutil
import struct
import sys
import tempfile
import zlib

SIZE = 16
ITEM_TEMPLATES = ("ingot", "shard", "feather", "gem", "map", "sword", "axe", "bow", "pickaxe", "compass")
ARMOR_TYPES = ("helmet", "chestplate", "leggings", "boots")
HANDHELD = ("sword", "axe")
WOOD = [(52, 34, 18), (84, 56, 28), (120, 84, 44), (150, 108, 60), (176, 132, 78)]
PAPER = [(120, 96, 56), (176, 150, 100), (222, 200, 150), (238, 224, 184), (250, 240, 210)]
STRING = (214, 214, 206)


class Canvas:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.px = [[None] * width for _ in range(height)]

    def put(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = color

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return None

    def line(self, x0, y0, x1, y1, color):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            self.put(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), color)

    def outline(self, color):
        edge = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is None and any(
                    self.get(x + dx, y + dy) is not None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                ):
                    edge.append((x, y))
        for x, y in edge:
            self.px[y][x] = color

    def rgba(self):
        rows = []
        for row in self.px:
            line = bytearray()
            for color in row:
                if color is None:
                    line += bytes((0, 0, 0, 0))
                else:
                    line += bytes((color[0], color[1], color[2], 255))
            rows.append(bytes(line))
        return rows


def parse_palette(values):
    if len(values) != 5:
        raise ValueError("a palette needs 5 colors: outline, shadow, base, light, highlight")
    out = []
    for value in values:
        text = value.lstrip("#")
        if len(text) != 6:
            raise ValueError("bad color " + value)
        out.append((int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16)))
    return out


def spans(canvas, rows, pal, light_top=1):
    """Fills (y, x0, x1) spans. The left edge is light, the right edge is shadow, the top is highlight."""
    top = min(y for y, _, _ in rows)
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            color = pal[2]
            if x == x0:
                color = pal[3]
            if x == x1:
                color = pal[1]
            if y < top + light_top:
                color = pal[4] if x < x1 else pal[3]
            canvas.put(x, y, color)


def draw_ingot(c, pal):
    rows = [(5, 4, 12), (6, 3, 12), (7, 2, 11), (8, 2, 11), (9, 1, 10), (10, 1, 9)]
    spans(c, rows, pal)
    c.line(4, 6, 11, 6, pal[3])
    c.line(3, 8, 10, 8, pal[3])


def draw_shard(c, pal):
    rows = [(1, 7, 8), (2, 6, 9), (3, 6, 9), (4, 5, 10), (5, 5, 10), (6, 5, 10), (7, 5, 10), (8, 5, 10),
            (9, 6, 9), (10, 6, 9), (11, 7, 8), (12, 7, 8)]
    spans(c, rows, pal, light_top=2)
    for y in range(3, 11):
        c.put(7, y, pal[4])


def draw_gem(c, pal):
    rows = [(3, 5, 10), (4, 4, 11), (5, 3, 12), (6, 3, 12), (7, 3, 12), (8, 4, 11), (9, 5, 10), (10, 6, 9), (11, 7, 8)]
    spans(c, rows, pal, light_top=2)
    for y in range(5, 10):
        c.put(7, y, pal[4])
        c.put(8, y, pal[3])
    c.put(5, 4, pal[4])


def draw_feather(c, pal):
    for i in range(13):
        x, y = 1 + i, 14 - i
        width = min(i, 12 - i) // 2 + 1
        for k in range(-width, width + 1):
            color = pal[3] if k < 0 else pal[2]
            if k == width:
                color = pal[1]
            if k == 0:
                color = pal[4]
            c.put(x + k, y + k, color)
            c.put(x + k + 1, y + k, color)
    c.put(1, 14, pal[1])
    c.put(0, 15, pal[1])


def draw_map(c, pal):
    for y in range(2, 14):
        for x in range(2, 14):
            c.put(x, y, PAPER[2])
    for i in range(2, 14):
        c.put(i, 2, PAPER[3])
        c.put(2, i, PAPER[3])
        c.put(i, 13, PAPER[1])
        c.put(13, i, PAPER[1])
    c.line(4, 10, 7, 7, pal[2])
    c.line(7, 7, 10, 9, pal[2])
    c.line(10, 9, 11, 5, pal[2])
    c.put(11, 4, pal[1])
    c.put(10, 4, pal[1])
    c.put(11, 5, pal[1])
    c.put(4, 10, pal[3])


def draw_sword(c, pal):
    for i in range(10):
        c.put(13 - i, 1 + i, pal[4])
        c.put(14 - i, 1 + i, pal[2])
    c.put(14, 0, pal[4])
    c.put(15, 1, pal[1])
    for k in range(5):
        c.put(2 + k, 9 + k, pal[3] if k < 2 else pal[1])
    c.put(4, 11, pal[4])
    for i, color in enumerate((WOOD[2], WOOD[1], WOOD[1])):
        c.put(3 - i, 12 + i, color)
    c.put(0, 15, pal[3])


def draw_axe(c, pal):
    for i in range(10):
        c.put(2 + i, 14 - i, WOOD[2])
        c.put(3 + i, 14 - i, WOOD[1])
    for i in range(0, 9, 3):
        c.put(3 + i, 13 - i, WOOD[3])
    rows = [(1, 9, 12), (2, 8, 13), (3, 8, 14), (4, 8, 14), (5, 9, 14), (6, 10, 14), (7, 11, 13)]
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            color = pal[2]
            if x == x0:
                color = pal[1]
            if x == x1:
                color = pal[4]
            if y == 1:
                color = pal[3]
            c.put(x, y, color)
    c.put(10, 4, pal[3])
    c.put(11, 3, pal[3])


def draw_bow(c, pal, pull=-1):
    arc = [(9, 1), (10, 1), (11, 2), (12, 3), (13, 4), (13, 5), (14, 6), (14, 7), (14, 8), (13, 9), (13, 10),
           (12, 11), (11, 12), (10, 13), (9, 14), (8, 14)]
    for x, y in arc:
        c.put(x, y, WOOD[2])
    for x, y in ((11, 3), (12, 4), (13, 6), (13, 7), (12, 10)):
        c.put(x, y, WOOD[3])
    for x, y in ((14, 6), (14, 8), (13, 10), (11, 12)):
        c.put(x, y, WOOD[1])
    c.put(8, 14, pal[3])
    c.put(9, 1, pal[3])
    if pull < 0:
        c.line(8, 2, 8, 13, STRING)
    else:
        nock = 7 - pull * 2
        c.line(8, 2, nock, 7, STRING)
        c.line(nock, 7, 8, 13, STRING)
        c.line(nock, 7, 12, 7, pal[2])
        c.put(12, 7, pal[4])


def draw_pickaxe(c, pal):
    for i in range(11):
        c.put(2 + i, 14 - i, WOOD[2])
        c.put(3 + i, 14 - i, WOOD[1])
    for i in range(0, 11, 3):
        c.put(3 + i, 13 - i, WOOD[3])
    rows = [(2, 8, 13), (3, 6, 15), (4, 6, 7), (4, 14, 15), (5, 6, 6), (5, 15, 15)]
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            color = pal[2]
            if x == x0:
                color = pal[1]
            if x == x1:
                color = pal[4]
            if y == 2:
                color = pal[3]
            c.put(x, y, color)
    c.put(11, 3, pal[3])
    c.put(12, 3, pal[3])


def draw_compass(c, pal):
    rows = [(3, 6, 9), (4, 5, 10), (5, 4, 11), (6, 4, 11), (7, 4, 11), (8, 4, 11), (9, 4, 11), (10, 5, 10), (11, 6, 9)]
    spans(c, rows, pal, light_top=2)
    c.line(6, 10, 9, 5, pal[1])
    c.line(7, 10, 10, 6, pal[2])
    c.line(9, 6, 10, 9, pal[4])
    c.line(9, 5, 10, 8, pal[3])
    c.put(9, 4, pal[4])
    c.put(6, 11, pal[0])


def draw_helmet(c, pal):
    spans(c, [(2, 6, 9), (3, 5, 10), (4, 4, 11), (5, 3, 12), (6, 3, 12), (7, 3, 12), (8, 3, 12), (9, 3, 5), (10, 3, 5), (11, 3, 4)], pal)
    spans(c, [(9, 10, 12), (10, 10, 12), (11, 11, 12)], pal, light_top=0)
    c.line(5, 7, 10, 7, pal[0])
    c.line(5, 8, 10, 8, pal[1])
    for y in range(7, 11):
        c.put(7, y, pal[3])
        c.put(8, y, pal[2])
    c.put(7, 3, pal[4])
    c.put(8, 3, pal[4])


def draw_chestplate(c, pal):
    spans(c, [(4, 1, 4), (5, 1, 4), (6, 1, 3), (7, 1, 3), (8, 1, 3)], pal)
    spans(c, [(4, 11, 14), (5, 11, 14), (6, 12, 14), (7, 12, 14), (8, 12, 14)], pal)
    spans(c, [(2, 3, 6), (3, 3, 6)], pal)
    spans(c, [(2, 9, 12), (3, 9, 12)], pal)
    spans(c, [(4, 4, 11), (5, 4, 11), (6, 4, 11), (7, 4, 11), (8, 4, 11), (9, 4, 11), (10, 4, 11), (11, 4, 11), (12, 5, 10), (13, 5, 10)], pal)
    for y in range(5, 12):
        c.put(7, y, pal[3])
        c.put(8, y, pal[3])
    c.line(4, 9, 11, 9, pal[1])
    c.put(7, 6, pal[4])


def draw_leggings(c, pal):
    spans(c, [(2, 3, 12), (3, 3, 12), (4, 3, 12), (5, 3, 12)], pal)
    spans(c, [(6, 3, 7), (7, 3, 7), (8, 3, 7), (9, 3, 6), (10, 3, 6), (11, 3, 6), (12, 3, 6), (13, 3, 6)], pal, light_top=0)
    spans(c, [(6, 8, 12), (7, 8, 12), (8, 8, 12), (9, 9, 12), (10, 9, 12), (11, 9, 12), (12, 9, 12), (13, 9, 12)], pal, light_top=0)
    c.line(3, 4, 12, 4, pal[1])
    c.put(7, 3, pal[4])
    c.put(8, 3, pal[4])


def draw_boots(c, pal):
    spans(c, [(5, 2, 6), (6, 2, 6), (7, 2, 6), (8, 2, 6), (9, 2, 6), (10, 1, 6), (11, 1, 6), (12, 1, 6)], pal)
    spans(c, [(5, 9, 13), (6, 9, 13), (7, 9, 13), (8, 9, 13), (9, 9, 13), (10, 9, 14), (11, 9, 14), (12, 9, 14)], pal)
    c.line(2, 6, 6, 6, pal[4])
    c.line(9, 6, 13, 6, pal[4])
    c.line(1, 12, 6, 12, pal[1])
    c.line(9, 12, 14, 12, pal[1])


DRAWERS = {
    "ingot": draw_ingot, "shard": draw_shard, "feather": draw_feather, "gem": draw_gem, "map": draw_map,
    "sword": draw_sword, "axe": draw_axe, "bow": draw_bow, "pickaxe": draw_pickaxe, "compass": draw_compass,
    "helmet": draw_helmet, "chestplate": draw_chestplate, "leggings": draw_leggings, "boots": draw_boots,
}


def sprite(template, pal, **kwargs):
    canvas = Canvas(SIZE, SIZE)
    DRAWERS[template](canvas, pal, **kwargs)
    canvas.outline(pal[0])
    return canvas


def plate(canvas, x, y, w, h, pal):
    for yy in range(h):
        for xx in range(w):
            color = pal[2]
            if xx == 0 or yy == 0:
                color = pal[3]
            if xx == w - 1 or yy == h - 1:
                color = pal[1]
            if (xx + yy * 2) % 7 == 3 and 0 < xx < w - 1 and 0 < yy < h - 1:
                color = pal[4]
            canvas.put(x + xx, y + yy, color)
    for xx in range(w):
        canvas.put(x + xx, y + h - 1, pal[0] if w > 2 else pal[1])


def box(canvas, u, v, w, h, d, pal):
    plate(canvas, u + d, v, w, d, pal)
    plate(canvas, u + d + w, v, w, d, pal)
    plate(canvas, u, v + d, d, h, pal)
    plate(canvas, u + d, v + d, w, h, pal)
    plate(canvas, u + d + w, v + d, d, h, pal)
    plate(canvas, u + 2 * d + w, v + d, w, h, pal)


def humanoid_layer(pal, leggings):
    canvas = Canvas(64, 32)
    if leggings:
        box(canvas, 16, 16, 8, 12, 4, pal)
        box(canvas, 0, 16, 4, 12, 4, pal)
    else:
        box(canvas, 0, 0, 8, 8, 8, pal)
        box(canvas, 16, 16, 8, 12, 4, pal)
        box(canvas, 40, 16, 4, 12, 4, pal)
        box(canvas, 0, 16, 4, 12, 4, pal)
    return canvas


def png_bytes(rows, width, height):
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + row for row in rows)
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, indent="\t")
        handle.write("\n")


def write_png(path, canvas):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png_bytes(canvas.rgba(), canvas.w, canvas.h))


def write_item(out, ns, item_id, template, pal):
    textures = os.path.join(out, "textures", "item")
    if template == "bow":
        frames = {item_id: -1, item_id + "_pulling_0": 0, item_id + "_pulling_1": 1, item_id + "_pulling_2": 2}
        for name, pull in frames.items():
            write_png(os.path.join(textures, name + ".png"), sprite("bow", pal, pull=pull))
            write_json(os.path.join(out, "models", "item", name + ".json"), {
                "parent": "minecraft:item/bow", "textures": {"layer0": f"{ns}:item/{name}"}})
        write_json(os.path.join(out, "items", item_id + ".json"), {"model": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": {"type": "minecraft:model", "model": f"{ns}:item/{item_id}"},
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.1,
                        "fallback": {"type": "minecraft:model", "model": f"{ns}:item/{item_id}_pulling_0"},
                        "entries": [
                            {"threshold": 0.65, "model": {"type": "minecraft:model", "model": f"{ns}:item/{item_id}_pulling_1"}},
                            {"threshold": 0.9, "model": {"type": "minecraft:model", "model": f"{ns}:item/{item_id}_pulling_2"}}]}}})
        return
    write_png(os.path.join(textures, item_id + ".png"), sprite(template, pal))
    parent = "minecraft:item/handheld" if template in HANDHELD else "minecraft:item/generated"
    write_json(os.path.join(out, "models", "item", item_id + ".json"), {
        "parent": parent, "textures": {"layer0": f"{ns}:item/{item_id}"}})
    write_json(os.path.join(out, "items", item_id + ".json"), {
        "model": {"type": "minecraft:model", "model": f"{ns}:item/{item_id}"}})


def write_set(out, ns, set_id, asset, pal):
    for armor in ARMOR_TYPES:
        write_item(out, ns, f"{set_id}_{armor}", armor, pal)
    write_json(os.path.join(out, "equipment", asset + ".json"), {"layers": {
        "humanoid": [{"texture": f"{ns}:{asset}"}],
        "humanoid_leggings": [{"texture": f"{ns}:{asset}"}]}})
    base = os.path.join(out, "textures", "entity", "equipment")
    write_png(os.path.join(base, "humanoid", asset + ".png"), humanoid_layer(pal, False))
    write_png(os.path.join(base, "humanoid_leggings", asset + ".png"), humanoid_layer(pal, True))


def write_piece(out, ns, item_id, pal, armor="helmet", asset=None):
    """One armor item on its own, with the equipment asset its layer needs."""
    asset = asset or item_id
    write_item(out, ns, item_id, armor, pal)
    write_json(os.path.join(out, "equipment", asset + ".json"), {"layers": {
        "humanoid": [{"texture": f"{ns}:{asset}"}],
        "humanoid_leggings": [{"texture": f"{ns}:{asset}"}]}})
    base = os.path.join(out, "textures", "entity", "equipment")
    write_png(os.path.join(base, "humanoid", asset + ".png"), humanoid_layer(pal, False))
    write_png(os.path.join(base, "humanoid_leggings", asset + ".png"), humanoid_layer(pal, True))


def generate(spec, out):
    ns = spec["namespace"]
    palettes = {name: parse_palette(values) for name, values in spec.get("palettes", {}).items()}

    def palette(name):
        if name not in palettes:
            raise ValueError("unknown palette " + name)
        return palettes[name]

    written = {"items": 0, "sets": 0, "pieces": 0}
    for item in spec.get("items", []):
        if item["template"] not in DRAWERS:
            raise ValueError("unknown template " + item["template"])
        write_item(out, ns, item["id"], item["template"], palette(item["palette"]))
        written["items"] += 1
    for entry in spec.get("sets", []):
        write_set(out, ns, entry["id"], entry.get("asset", entry["id"]), palette(entry["palette"]))
        written["sets"] += 1
    for entry in spec.get("pieces", []):
        armor = entry.get("armor", "helmet")
        if armor not in ARMOR_TYPES:
            raise ValueError("unknown armor piece " + armor)
        write_piece(out, ns, entry["id"], palette(entry["palette"]), armor, entry.get("asset"))
        written["pieces"] += 1
    return written


# ----- validation -----

def read_png(path):
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(path + ": bad signature")
    pos = 8
    width = height = None
    idat = b""
    ended = False
    while pos < len(data):
        length, = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        crc, = struct.unpack(">I", data[pos + 8 + length:pos + 12 + length])
        if zlib.crc32(kind + body) & 0xFFFFFFFF != crc:
            raise ValueError(f"{path}: bad crc in {kind!r}")
        if kind == b"IHDR":
            width, height, depth, ctype, comp, filt, inter = struct.unpack(">IIBBBBB", body)
            if (depth, ctype, comp, filt, inter) != (8, 6, 0, 0, 0):
                raise ValueError(path + ": not 8-bit RGBA")
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            ended = True
        pos += 12 + length
    if not ended or width is None:
        raise ValueError(path + ": truncated")
    raw = zlib.decompress(idat)
    stride = 1 + width * 4
    if len(raw) != stride * height:
        raise ValueError(path + ": wrong pixel data size")
    alphas = []
    for row in range(height):
        if raw[row * stride] != 0:
            raise ValueError(path + ": unexpected filter")
        alphas += [raw[row * stride + 1 + col * 4 + 3] for col in range(width)]
    return width, height, alphas


def check_png(path, width, height, min_opaque):
    w, h, alphas = read_png(path)
    if (w, h) != (width, height):
        raise ValueError(f"{path}: size {w}x{h}, want {width}x{height}")
    opaque = sum(1 for a in alphas if a == 255)
    if opaque < min_opaque:
        raise ValueError(f"{path}: only {opaque} opaque pixels")
    if any(a not in (0, 255) for a in alphas):
        raise ValueError(path + ": partial alpha")
    return opaque


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def model_ids(node):
    if isinstance(node, dict):
        if node.get("type") == "minecraft:model":
            yield node["model"]
        for value in node.values():
            yield from model_ids(value)
    elif isinstance(node, list):
        for value in node:
            yield from model_ids(value)


def split(ref):
    namespace, _, path = ref.partition(":")
    return namespace, path


def validate(spec, out):
    ns = spec["namespace"]
    count = {"item_textures": 0, "models": 0, "definitions": 0, "equipment": 0, "layers": 0}
    item_ids = [item["id"] for item in spec.get("items", [])]
    for entry in spec.get("sets", []):
        item_ids += [f"{entry['id']}_{armor}" for armor in ARMOR_TYPES]
    item_ids += [entry["id"] for entry in spec.get("pieces", [])]
    for item_id in item_ids:
        definition = load_json(os.path.join(out, "items", item_id + ".json"))
        count["definitions"] += 1
        refs = list(model_ids(definition))
        if not refs:
            raise ValueError(item_id + ": item definition names no model")
        for ref in refs:
            namespace, path = split(ref)
            if namespace != ns:
                raise ValueError(f"{item_id}: foreign model {ref}")
            model = load_json(os.path.join(out, "models", path + ".json"))
            count["models"] += 1
            if not model.get("parent", "").startswith("minecraft:item/"):
                raise ValueError(ref + ": bad parent")
            for texture in model["textures"].values():
                namespace, tpath = split(texture)
                check_png(os.path.join(out, "textures", tpath + ".png"), SIZE, SIZE, 20)
                count["item_textures"] += 1
    for entry in list(spec.get("sets", [])) + list(spec.get("pieces", [])):
        asset = entry.get("asset", entry["id"])
        equipment = load_json(os.path.join(out, "equipment", asset + ".json"))
        count["equipment"] += 1
        for layer, folder in (("humanoid", "humanoid"), ("humanoid_leggings", "humanoid_leggings")):
            for part in equipment["layers"][layer]:
                namespace, path = split(part["texture"])
                if namespace != ns:
                    raise ValueError(f"{asset}: foreign layer texture")
                check_png(os.path.join(out, "textures", "entity", "equipment", folder, path + ".png"), 64, 32, 100)
                count["layers"] += 1
    return count


def tree_bytes(root):
    files = {}
    for base, _, names in os.walk(root):
        for name in names:
            path = os.path.join(base, name)
            with open(path, "rb") as handle:
                files[os.path.relpath(path, root)] = handle.read()
    return files


SELF_TEST_SPEC = {
    "namespace": "mod-stella-ruins",
    "palettes": {
        "ember": ["#2a0f0a", "#7a2616", "#c8481f", "#f08a3c", "#ffd27a"],
        "frost": ["#0b1d2e", "#2c5778", "#5a9cc8", "#9fd6f0", "#e4f8ff"],
    },
    "items": [{"id": f"selftest_{name}", "template": name, "palette": "ember" if i % 2 else "frost"}
              for i, name in enumerate(ITEM_TEMPLATES)],
    "sets": [{"id": "selftest_ember", "asset": "selftest_ember", "palette": "ember"},
             {"id": "selftest_frost", "asset": "selftest_frost", "palette": "frost"}],
}


def self_test():
    pal = parse_palette(SELF_TEST_SPEC["palettes"]["ember"])
    for name in DRAWERS:
        filled = sum(1 for row in sprite(name, pal).px for color in row if color is not None)
        if not 20 <= filled <= SIZE * SIZE - 60:
            raise ValueError(f"{name}: {filled} filled pixels, want 20 to {SIZE * SIZE - 60}")
    workdir = tempfile.mkdtemp(prefix="ruin_textures_")
    try:
        first = os.path.join(workdir, "first")
        second = os.path.join(workdir, "second")
        generate(SELF_TEST_SPEC, first)
        generate(SELF_TEST_SPEC, second)
        counts = validate(SELF_TEST_SPEC, first)
        if tree_bytes(first) != tree_bytes(second):
            raise ValueError("two runs wrote different bytes")
        files = len(tree_bytes(first))
        want_items = len(ITEM_TEMPLATES) + 4 * 2
        if counts["definitions"] != want_items:
            raise ValueError(f"definitions {counts['definitions']}, want {want_items}")
        if counts["equipment"] != 2 or counts["layers"] != 4:
            raise ValueError("equipment assets or layers missing")
        print(f"self-test ok: {files} files, {counts}")
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def preview(out, scale=12):
    """Writes a scaled copy of each item texture next to it, to look at the art."""
    base = os.path.join(out, "textures", "item")
    target = os.path.join(out, "preview")
    for name in sorted(os.listdir(base)):
        w, h, alphas = read_png(os.path.join(base, name))
        with open(os.path.join(base, name), "rb") as handle:
            data = handle.read()
        raw = zlib.decompress(b"".join(part for part in _idat(data)))
        rows = []
        stride = 1 + w * 4
        for y in range(h):
            line = raw[y * stride + 1:(y + 1) * stride]
            wide = b"".join(line[x * 4:x * 4 + 4] * scale for x in range(w))
            rows += [wide] * scale
        os.makedirs(target, exist_ok=True)
        with open(os.path.join(target, name), "wb") as handle:
            handle.write(png_bytes(rows, w * scale, h * scale))


def _idat(data):
    pos = 8
    while pos < len(data):
        length, = struct.unpack(">I", data[pos:pos + 4])
        if data[pos + 4:pos + 8] == b"IDAT":
            yield data[pos + 8:pos + 8 + length]
        pos += 12 + length


def main(argv):
    parser = argparse.ArgumentParser(description="Draw ruins item art from templates.")
    parser.add_argument("--spec")
    parser.add_argument("--out")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--preview", action="store_true", help="also write 12x scaled copies to OUT/preview")
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    if not args.spec or not args.out:
        parser.error("--spec and --out are required")
    spec = load_json(args.spec)
    written = generate(spec, args.out)
    counts = validate(spec, args.out)
    if args.preview:
        preview(args.out)
    print(f"wrote {written['items']} items and {written['sets']} sets; {counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
