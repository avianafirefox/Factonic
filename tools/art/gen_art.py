#!/usr/bin/env python3
"""
Factonic asset generator.

Every icon, machine, ore, plant and terrain tile in the game is authored here as
code-generated SVG (vector) and rasterised to PNG for Android. Re-run after edits:

    pip install cairosvg pillow numpy
    python3 tools/art/gen_art.py

Outputs:
    art/svg/*.svg                         vector sources
    app/src/main/res/drawable-nodpi/*.png game sprites + UI icons
    app/src/main/res/mipmap-*/            launcher icons
"""
import math, os, random
import cairosvg
import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SVG_DIR = os.path.join(ROOT, "art", "svg")
PNG_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
RES = os.path.join(ROOT, "app", "src", "main", "res")
os.makedirs(SVG_DIR, exist_ok=True)
os.makedirs(PNG_DIR, exist_ok=True)

OUTLINE = "#14101f"


# ----------------------------------------------------------------------------- helpers
class S:
    """Tiny SVG builder. Coordinates live in a 64*N unit box (N = tiles)."""

    def __init__(self, size=64):
        self.size = size
        self.defs = []
        self.body = []
        self.n = 0

    def gid(self):
        self.n += 1
        return f"g{self.n}"

    def lin(self, stops, x1=0, y1=0, x2=0, y2=1):
        i = self.gid()
        st = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in _stops(stops))
        self.defs.append(f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')
        return f"url(#{i})"

    def rad(self, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
        i = self.gid()
        st = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in _stops(stops))
        f = f' fx="{fx}" fy="{fy}"' if fx is not None else ""
        self.defs.append(f'<radialGradient id="{i}" cx="{cx}" cy="{cy}" r="{r}"{f}>{st}</radialGradient>')
        return f"url(#{i})"

    def add(self, s):
        self.body.append(s)
        return self

    def svg(self):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.size}" height="{self.size}" '
                f'viewBox="0 0 {self.size} {self.size}"><defs>{"".join(self.defs)}</defs>{"".join(self.body)}</svg>')


def _stops(stops):
    out = []
    for s in stops:
        if len(s) == 2:
            out.append((s[0], s[1], 1))
        else:
            out.append(s)
    return out


def pts(p):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in p)


def poly(p, fill, stroke=OUTLINE, sw=1.6, extra=""):
    return f'<polygon points="{pts(p)}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round" {extra}/>'


def rect(x, y, w, h, fill, rx=0, stroke=OUTLINE, sw=1.6, extra=""):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'


def circ(cx, cy, r, fill, stroke=OUTLINE, sw=1.6, extra=""):
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'


def path(d, fill, stroke=OUTLINE, sw=1.6, extra=""):
    return f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round" stroke-linecap="round" {extra}/>'


def shade(hexc, k):
    """k>0 lighten, k<0 darken."""
    hexc = hexc.lstrip("#")
    r, g, b = (int(hexc[i:i + 2], 16) for i in (0, 2, 4))
    if k >= 0:
        r, g, b = (int(c + (255 - c) * k) for c in (r, g, b))
    else:
        r, g, b = (int(c * (1 + k)) for c in (r, g, b))
    return f"#{r:02x}{g:02x}{b:02x}"


def save(name, s, px):
    svg = s.svg()
    with open(os.path.join(SVG_DIR, name + ".svg"), "w") as f:
        f.write(svg)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=os.path.join(PNG_DIR, name + ".png"),
                     output_width=px, output_height=px)


def ground_shadow(s, cx, cy, rx, ry, a=0.35):
    s.add(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{s.rad([(0, "#000", a), (1, "#000", 0)])}"/>')


# ----------------------------------------------------------------------------- item icons
def rock(s, cx, cy, r, base, seed, facets=7, accent=None):
    rnd = random.Random(seed)
    p = []
    for i in range(facets):
        a = i / facets * math.tau + rnd.uniform(-0.25, 0.25)
        rr = r * rnd.uniform(0.75, 1.05)
        p.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr * 0.82))
    s.add(poly(p, s.lin([(0, shade(base, 0.35)), (1, shade(base, -0.45))], 0, 0, 0.3, 1)))
    # top facet
    top = [(cx + (x - cx) * 0.55 - r * 0.12, cy + (y - cy) * 0.5 - r * 0.2) for x, y in p]
    s.add(poly(top, shade(base, 0.25), stroke="none", extra='opacity="0.75"'))
    s.add(poly([top[0], top[1], (cx - r * .1, cy - r * .15)], shade(base, 0.55), stroke="none", extra='opacity="0.6"'))
    if accent:
        for _ in range(3):
            ax, ay = cx + rnd.uniform(-r * .5, r * .5), cy + rnd.uniform(-r * .4, r * .4)
            s.add(circ(ax, ay, rnd.uniform(1.0, 2.0), accent, stroke="none"))


def ore_icon(name, base, accent, glow=None):
    s = S()
    ground_shadow(s, 32, 50, 26, 8)
    if glow:
        s.add(circ(32, 34, 28, s.rad([(0, glow, .55), (1, glow, 0)]), stroke="none"))
    rock(s, 22, 38, 14, base, 1, accent=accent)
    rock(s, 42, 40, 13, base, 2, accent=accent)
    rock(s, 32, 25, 14, shade(base, 0.08), 3, accent=accent)
    save(name, s, 128)


def crystal(s, x, y, w, h, c1, c2, tilt=0):
    t = f'transform="rotate({tilt} {x} {y + h})"'
    body = [(x - w, y + h * .25), (x, y), (x + w, y + h * .25), (x + w, y + h), (x - w, y + h)]
    s.add(f'<g {t}>' + poly(body, s.lin([(0, c1), (1, c2)], 0, 0, 1, 1)) +
          poly([(x - w, y + h * .25), (x, y), (x, y + h)], shade(c1, .35), stroke="none", extra='opacity="0.55"') +
          f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y + h}" stroke="#fff" stroke-opacity="0.5" stroke-width="1"/></g>')


def crystal_icon():
    s = S()
    ground_shadow(s, 32, 52, 24, 7)
    s.add(circ(32, 32, 28, s.rad([(0, "#b57bff", .5), (1, "#b57bff", 0)]), stroke="none"))
    crystal(s, 22, 20, 6, 30, "#c9a2ff", "#5a2aa8", -18)
    crystal(s, 42, 22, 6, 28, "#8ff0ff", "#2a6fa8", 16)
    crystal(s, 32, 8, 8, 44, "#e2c8ff", "#6a33c9")
    save("aether_crystal", s, 128)


def sap_icon():
    s = S()
    ground_shadow(s, 32, 56, 18, 5)
    s.add(rect(18, 12, 28, 44, s.lin([(0, "#3a3550"), (.5, "#615a7d"), (1, "#2a2540")], 0, 0, 1, 0), rx=6))
    s.add(rect(21, 20, 22, 32, s.lin([(0, "#ffd36b"), (1, "#ff8a1a")]), rx=4, sw=1))
    s.add(circ(32, 34, 12, s.rad([(0, "#fff3b0", .9), (1, "#ffb340", 0)]), stroke="none"))
    for i, yy in enumerate((26, 36, 44)):
        s.add(circ(27 + i * 4, yy, 1.6, "#fff6d0", stroke="none", extra='opacity="0.8"'))
    s.add(rect(22, 7, 20, 7, "#8c86a8", rx=2))
    s.add(rect(24, 21, 3, 28, "#fff", rx=1.5, stroke="none", extra='opacity="0.35"'))
    save("sap", s, 128)


def plate_icon(name, c, deco=None):
    s = S()
    ground_shadow(s, 32, 50, 26, 7)
    def slab(y):
        top = [(8, y), (30, y - 10), (56, y), (34, y + 10)]
        s.add(poly([(8, y), (34, y + 10), (34, y + 15), (8, y + 5)], shade(c, -0.35)))
        s.add(poly([(34, y + 10), (56, y), (56, y + 5), (34, y + 15)], shade(c, -0.55)))
        s.add(poly(top, s.lin([(0, shade(c, 0.45)), (1, shade(c, -0.1))], 0, 0, 1, 1)))
        s.add(poly([(16, y - 1), (30, y - 7), (38, y - 4), (24, y + 2)], "#fff", stroke="none", extra='opacity="0.25"'))
        if deco:
            deco(s, y)
    slab(40)
    slab(28)
    save(name, s, 128)


def brick_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 7)
    c = "#d9a27a"
    def brick(x, y):
        s.add(poly([(x, y), (x + 20, y - 7), (x + 20, y + 1), (x, y + 8)], shade(c, -0.45)))
        s.add(poly([(x, y), (x + 20, y - 7), (x + 34, y - 1), (x + 14, y + 6)], s.lin([(0, shade(c, .3)), (1, c)])))
        s.add(poly([(x, y), (x + 14, y + 6), (x + 14, y + 14), (x, y + 8)], shade(c, -0.25)))
    brick(6, 40); brick(26, 46); brick(16, 28)
    save("brick", s, 128)


def gear_path(cx, cy, r1, r2, teeth, rot=0):
    d = []
    n = teeth * 4
    for i in range(n):
        a = rot + i / n * math.tau
        r = r2 if (i % 4) in (1, 2) else r1
        d.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in d) + " Z"


def gear_icon():
    s = S()
    ground_shadow(s, 32, 54, 24, 6)
    s.add(path(gear_path(32, 33, 19, 26, 10, 0.15), s.lin([(0, "#c8d3e6"), (1, "#5d6a85")], 0, 0, 1, 1)))
    s.add(circ(32, 33, 13, s.lin([(0, "#8995b0"), (1, "#c7d0e3")], 0, 0, 1, 1), sw=1.2))
    s.add(circ(32, 33, 6, "#2a2f45"))
    s.add(path("M18 22 A18 18 0 0 1 40 15", "none", stroke="#fff", sw=2, extra='opacity="0.4"'))
    save("gear", s, 128)


def wire_icon():
    s = S()
    ground_shadow(s, 32, 54, 24, 6)
    s.add(rect(14, 16, 36, 34, "#5a4a3a", rx=4))
    s.add(f'<ellipse cx="32" cy="16" rx="18" ry="6" fill="#7d6650" stroke="{OUTLINE}" stroke-width="1.6"/>')
    for i in range(7):
        y = 20 + i * 4
        s.add(f'<path d="M14 {y} Q32 {y + 6} 50 {y}" fill="none" stroke="{shade("#e08a4a", .1 if i % 2 else -.1)}" stroke-width="3.6"/>')
    s.add(f'<ellipse cx="32" cy="50" rx="18" ry="6" fill="#4a3b2d" stroke="{OUTLINE}" stroke-width="1.6"/>')
    s.add(f'<path d="M50 30 Q60 34 56 46" fill="none" stroke="#f0a060" stroke-width="3"/>')
    save("wire", s, 128)


def board_icon(name, board, trace, chip, big=False):
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    s.add(poly([(6, 34), (32, 20), (58, 34), (32, 48)], s.lin([(0, shade(board, .25)), (1, shade(board, -.2))], 0, 0, 1, 1)))
    s.add(poly([(6, 34), (32, 48), (32, 52), (6, 38)], shade(board, -.5)))
    s.add(poly([(32, 48), (58, 34), (58, 38), (32, 52)], shade(board, -.65)))
    for a, b in [((12, 34), (26, 27)), ((20, 39), (40, 28)), ((30, 44), (50, 33)), ((16, 36), (30, 43))]:
        s.add(f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="{trace}" stroke-width="1.6" stroke-linecap="round"/>')
        s.add(circ(b[0], b[1], 1.6, trace, stroke="none"))
    w = 13 if big else 9
    s.add(poly([(32 - w, 33), (32, 33 - w * .55), (32 + w, 33), (32, 33 + w * .55)], chip))
    s.add(poly([(32 - w, 33), (32, 33 + w * .55), (32, 36 + w * .55), (32 - w, 36)], shade(chip, -.5)))
    s.add(poly([(32, 33 + w * .55), (32 + w, 33), (32 + w, 36), (32, 36 + w * .55)], shade(chip, -.7)))
    s.add(poly([(32 - w * .5, 33), (32, 33 - w * .28), (32 + w * .5, 33), (32, 33 + w * .28)], trace, stroke="none", extra='opacity="0.6"'))
    save(name, s, 128)


def pipe_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 6)
    g = s.lin([(0, "#b9c3d6"), (.45, "#eef2fa"), (1, "#5b6680")], 0, 0, 0, 1)
    s.add(f'<g transform="rotate(-28 32 32)">' + rect(6, 24, 52, 16, g, rx=3) +
          rect(4, 21, 8, 22, s.lin([(0, "#8e98ad"), (1, "#454f66")]), rx=2) +
          rect(52, 21, 8, 22, s.lin([(0, "#8e98ad"), (1, "#454f66")]), rx=2) + '</g>')
    save("pipe", s, 128)


def plastic_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 6)
    for i, (x, y) in enumerate([(8, 40), (22, 44), (16, 30)]):
        c = "#f2d9ff"
        s.add(poly([(x, y), (x + 14, y - 6), (x + 34, y + 2), (x + 20, y + 8)], s.lin([(0, "#ffffff"), (1, c)])))
        s.add(poly([(x, y), (x + 20, y + 8), (x + 20, y + 13), (x, y + 5)], "#c7a3e0"))
        s.add(poly([(x + 20, y + 8), (x + 34, y + 2), (x + 34, y + 7), (x + 20, y + 13)], "#9c78b8"))
    save("plastic", s, 128)


def sulfite_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 7)
    s.add(path("M6 50 Q14 26 32 20 Q50 26 58 50 Z", s.lin([(0, "#f6ff8a"), (1, "#a7b51e")])))
    rnd = random.Random(5)
    for _ in range(22):
        x, y = rnd.uniform(14, 50), rnd.uniform(28, 48)
        s.add(circ(x, y, rnd.uniform(1, 2.2), rnd.choice(["#fffcc2", "#cfd94a", "#8f9a1c"]), stroke="none"))
    s.add(path("M20 30 Q30 22 40 24", "none", stroke="#fff", sw=2, extra='opacity="0.6"'))
    save("sulfite", s, 128)


def battery_icon():
    s = S()
    ground_shadow(s, 32, 56, 18, 5)
    s.add(rect(18, 14, 28, 42, s.lin([(0, "#2c3a6b"), (.5, "#5470c2"), (1, "#202a52")], 0, 0, 1, 0), rx=5))
    s.add(rect(26, 8, 12, 7, "#c9cfdd", rx=2))
    for i in range(3):
        s.add(rect(22, 22 + i * 10, 20, 7, s.lin([(0, "#9dffb0"), (1, "#26c26a")]), rx=2, sw=1))
    s.add(rect(21, 16, 3, 36, "#fff", rx=1.5, stroke="none", extra='opacity="0.3"'))
    save("battery", s, 128)


def engine_icon():
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    s.add(rect(8, 28, 48, 24, s.lin([(0, "#8a93a8"), (1, "#3f4659")]), rx=4))
    for i in range(3):
        x = 14 + i * 13
        s.add(rect(x, 12, 10, 20, s.lin([(0, "#c7ceda"), (1, "#6b7387")], 0, 0, 1, 0), rx=3))
        s.add(rect(x - 1, 10, 12, 5, "#e8562a", rx=2))
    s.add(circ(32, 41, 7, s.rad([(0, "#ffd29a"), (1, "#c2561a")])))
    s.add(path(gear_path(32, 41, 3, 5, 6), "#3b2b22", sw=0.8))
    save("engine", s, 128)


def lens_icon():
    s = S()
    s.add(circ(32, 32, 30, s.rad([(0, "#d29bff", .7), (1, "#d29bff", 0)]), stroke="none"))
    hexp = [(32 + math.cos(a) * 22, 32 + math.sin(a) * 22) for a in [i * math.tau / 6 + math.pi / 6 for i in range(6)]]
    s.add(poly(hexp, s.lin([(0, "#b9a6d6"), (1, "#4b3a6e")], 0, 0, 1, 1), sw=2))
    s.add(circ(32, 32, 15, s.rad([(0, "#ffffff"), (.35, "#e6c8ff"), (1, "#6a2fd0")], fx=.35, fy=.35)))
    s.add(f'<ellipse cx="27" cy="26" rx="5" ry="3" fill="#fff" opacity="0.8" transform="rotate(-30 27 26)"/>')
    save("aether_lens", s, 128)


def lds_icon():
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    c = "#e2b25a"
    outer = [(32 + math.cos(a) * 25, 32 + math.sin(a) * 22) for a in [i * math.tau / 6 for i in range(6)]]
    s.add(poly(outer, "none", stroke=OUTLINE, sw=6))
    s.add(poly(outer, "none", stroke=c, sw=3.5))
    for i in range(6):
        s.add(f'<line x1="32" y1="32" x2="{outer[i][0]:.1f}" y2="{outer[i][1]:.1f}" stroke="{OUTLINE}" stroke-width="5"/>')
        s.add(f'<line x1="32" y1="32" x2="{outer[i][0]:.1f}" y2="{outer[i][1]:.1f}" stroke="{shade(c, -.2)}" stroke-width="2.6"/>')
    s.add(circ(32, 32, 7, s.rad([(0, "#fff1c8"), (1, "#b07a22")])))
    save("lds", s, 128)


def xenite_cell_icon():
    s = S()
    s.add(circ(32, 32, 30, s.rad([(0, "#7dff6a", .6), (1, "#7dff6a", 0)]), stroke="none"))
    s.add(rect(20, 8, 24, 48, s.lin([(0, "#42485e"), (.5, "#7c849c"), (1, "#32374a")], 0, 0, 1, 0), rx=8))
    s.add(rect(24, 15, 16, 34, s.lin([(0, "#e0ffd0"), (.5, "#6bff4f"), (1, "#1f9e2a")]), rx=6, sw=1))
    s.add(rect(18, 12, 28, 5, "#9aa2b8", rx=2))
    s.add(rect(18, 47, 28, 5, "#9aa2b8", rx=2))
    s.add(rect(27, 18, 3, 28, "#fff", rx=1.5, stroke="none", extra='opacity="0.5"'))
    save("xenite_cell", s, 128)


def rocket_fuel_icon():
    s = S()
    ground_shadow(s, 32, 56, 20, 5)
    s.add(rect(16, 14, 32, 42, s.lin([(0, "#ff6a3d"), (.5, "#ffa15c"), (1, "#a92a12")], 0, 0, 1, 0), rx=6))
    s.add(rect(24, 7, 16, 8, "#4a4560", rx=2))
    s.add(path("M32 24 L40 38 L32 34 L24 38 Z", "#fff1a8", sw=1.2))
    s.add(rect(16, 44, 32, 5, "#3a2a40", sw=1))
    save("rocket_fuel", s, 128)


def rocket_segment_icon():
    s = S()
    ground_shadow(s, 32, 56, 22, 5)
    s.add(rect(18, 10, 28, 44, s.lin([(0, "#9aa3b7"), (.45, "#f4f6fb"), (1, "#5a6378")], 0, 0, 1, 0), rx=3))
    s.add(rect(18, 18, 28, 4, "#e84a5f", sw=1))
    s.add(rect(18, 42, 28, 4, "#e84a5f", sw=1))
    s.add(circ(32, 31, 5, s.rad([(0, "#bff6ff"), (1, "#2a7aa8")])))
    s.add(poly([(18, 40), (8, 54), (18, 52)], "#5a6378"))
    s.add(poly([(46, 40), (56, 54), (46, 52)], "#5a6378"))
    save("rocket_segment", s, 128)


def satellite_icon():
    s = S()
    for x in (4, 42):
        s.add(rect(x, 24, 18, 16, s.lin([(0, "#3c5fd6"), (1, "#1a2766")], 0, 0, 1, 1), sw=1.4))
        for k in (1, 2):
            s.add(f'<line x1="{x + k * 6}" y1="24" x2="{x + k * 6}" y2="40" stroke="#8fb0ff" stroke-width="0.8"/>')
        s.add(f'<line x1="{x}" y1="32" x2="{x + 18}" y2="32" stroke="#8fb0ff" stroke-width="0.8"/>')
    s.add(rect(22, 30, 20, 4, "#9aa3b7", sw=1))
    s.add(rect(24, 18, 16, 28, s.lin([(0, "#f7d27a"), (1, "#b07a22")], 0, 0, 1, 0), rx=3))
    s.add(path("M32 18 L32 8 M26 10 Q32 4 38 10", "none", stroke=OUTLINE, sw=2))
    s.add(circ(32, 32, 4, "#bff6ff"))
    save("satellite", s, 128)


# --- science packs
def flask(name, liquid, shape):
    s = S()
    ground_shadow(s, 32, 57, 18, 5)
    lg = s.lin([(0, shade(liquid, .45)), (1, shade(liquid, -.35))])
    glass = "#d9e6ff"
    s.add(circ(32, 38, 26, s.rad([(0, liquid, .45), (1, liquid, 0)]), stroke="none"))
    if shape == "round":
        s.add(rect(27, 6, 10, 16, glass, rx=2, extra='fill-opacity="0.5"'))
        s.add(circ(32, 38, 17, glass, extra='fill-opacity="0.35"'))
        s.add(path("M16.5 40 A16 16 0 0 0 47.5 40 Z", lg, stroke="none"))
        s.add(circ(32, 38, 17, "none"))
    elif shape == "cone":
        s.add(path("M27 6 L37 6 L37 22 L52 54 L12 54 L27 22 Z", glass, extra='fill-opacity="0.4"'))
        s.add(path("M20 38 L44 38 L51 53 L13 53 Z", lg, stroke="none"))
        s.add(path("M27 6 L37 6 L37 22 L52 54 L12 54 L27 22 Z", "none"))
    elif shape == "vial":
        s.add(rect(22, 8, 20, 48, glass, rx=8, extra='fill-opacity="0.4"'))
        s.add(rect(23, 24, 18, 31, lg, rx=7, stroke="none"))
        s.add(rect(22, 8, 20, 48, "none", rx=8))
        s.add(rect(20, 5, 24, 6, "#7b84a0", rx=2))
    elif shape == "hex":
        hexp = [(32 + math.cos(a) * 19, 38 + math.sin(a) * 17) for a in [i * math.tau / 6 for i in range(6)]]
        s.add(rect(27, 6, 10, 18, glass, rx=2, extra='fill-opacity="0.5"'))
        s.add(poly(hexp, glass, extra='fill-opacity="0.35"'))
        s.add(poly([(13, 38), (51, 38), (41.5, 52.7), (22.5, 52.7)], lg, stroke="none"))
        s.add(poly(hexp, "none"))
    elif shape == "orb":
        s.add(rect(28, 4, 8, 14, glass, rx=2, extra='fill-opacity="0.5"'))
        s.add(circ(32, 36, 16, glass, extra='fill-opacity="0.3"'))
        s.add(circ(32, 36, 11, s.rad([(0, "#fffbe0"), (.6, liquid), (1, shade(liquid, -.4))])))
        s.add(f'<ellipse cx="32" cy="36" rx="25" ry="8" fill="none" stroke="{OUTLINE}" stroke-width="3.4" transform="rotate(-20 32 36)"/>')
        s.add(f'<ellipse cx="32" cy="36" rx="25" ry="8" fill="none" stroke="{shade(liquid, .3)}" stroke-width="1.8" transform="rotate(-20 32 36)"/>')
        s.add(circ(32, 36, 16, "none"))
    elif shape == "star":
        star = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            r = 22 if i % 2 == 0 else 10
            star.append((32 + math.cos(a) * r, 36 + math.sin(a) * r))
        s.add(poly(star, s.rad([(0, "#ffffff"), (.5, "#eaf2ff"), (1, "#9fb3e8")]), sw=2))
        s.add(circ(32, 36, 6, s.rad([(0, "#fff"), (1, "#cfe0ff")]), stroke="none"))
        s.add(path("M24 22 L28 28", "none", stroke="#fff", sw=2))
    if shape not in ("star",):
        s.add(f'<path d="M24 {28 if shape != "vial" else 14} Q22 40 26 50" fill="none" stroke="#fff" stroke-opacity="0.7" stroke-width="2.4" stroke-linecap="round"/>')
    if shape in ("round", "cone", "hex"):
        s.add(rect(25, 3, 14, 6, "#7b84a0", rx=2))
    for (bx, by) in ((30, 46), (36, 43), (33, 49)):
        s.add(circ(bx, by, 1.3, "#ffffff", stroke="none", extra='opacity="0.7"'))
    save(name, s, 128)


# ----------------------------------------------------------------------------- building sprites
HULL = "#d8dde8"
HULL_D = "#8a92a8"
TEAL = "#3ff2e0"
DARK = "#2a2d3e"


def base_plate(s, x, y, w, h, color="#4a4f66"):
    s.add(rect(x + 3, y + 5, w - 4, h - 4, "#000", rx=8, stroke="none", extra='opacity="0.35"'))
    s.add(rect(x + 2, y + 2, w - 4, h - 4, s.lin([(0, shade(color, .25)), (1, shade(color, -.35))], 0, 0, 1, 1), rx=8, sw=2))
    # hazard corners
    for cx, cy in ((x + 6, y + 6), (x + w - 12, y + 6), (x + 6, y + h - 12), (x + w - 12, y + h - 12)):
        s.add(rect(cx, cy, 6, 6, "#ffcc4d", rx=1, sw=1))


def bolt_ring(s, cx, cy, r, n=8):
    for i in range(n):
        a = i * math.tau / n
        s.add(circ(cx + math.cos(a) * r, cy + math.sin(a) * r, 1.6, "#c9cfe0", sw=0.8))


def glow(s, cx, cy, r, c, a=0.7):
    s.add(circ(cx, cy, r, s.rad([(0, c, a), (1, c, 0)]), stroke="none"))


def bld_drill(name="drill", accent=TEAL, hull=HULL):
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#57506a")
    s.add(rect(14, 14, 100, 100, s.lin([(0, shade(hull, .1)), (1, shade(hull, -.35))], 0, 0, 1, 1), rx=14, sw=2.5))
    s.add(rect(20, 20, 88, 88, "none", rx=10, stroke=shade(hull, -.45), sw=1.2))
    s.add(circ(64, 64, 34, DARK, sw=2.5))
    bolt_ring(s, 64, 64, 39, 12)
    # output chute (top side = north)
    s.add(rect(48, 2, 32, 18, s.lin([(0, "#6a6f88"), (1, "#3c4057")]), rx=3, sw=2))
    s.add(poly([(56, 14), (64, 6), (72, 14)], accent, sw=1.2))
    for (x, y) in ((22, 98), (98, 98), (22, 30), (98, 30)):
        s.add(circ(x, y, 4, accent, sw=1.2))
    s.add(rect(26, 104, 76, 6, accent, rx=3, sw=1, extra='opacity="0.85"'))
    save(name, s, 256)


def drill_head():
    s = S(128)
    s.add(circ(64, 64, 30, s.rad([(0, "#9aa3b7"), (1, "#3a3f55")]), sw=2))
    for i in range(3):
        a = i * math.tau / 3
        p = [(64, 64), (64 + math.cos(a) * 30, 64 + math.sin(a) * 30), (64 + math.cos(a + .7) * 22, 64 + math.sin(a + .7) * 22)]
        s.add(poly(p, "#e7ecf5", sw=1.5))
    s.add(circ(64, 64, 9, s.rad([(0, "#ffe9a8"), (1, "#d48a1a")]), sw=1.5))
    save("drill_head", s, 128)


def bld_smelter():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#5a4a52")
    s.add(circ(64, 66, 52, s.rad([(0, "#c9b8a8"), (.8, "#8a7464"), (1, "#5e4c40")]), sw=2.5))
    for i in range(16):
        a = i * math.tau / 16
        s.add(f'<line x1="{64 + math.cos(a) * 40:.1f}" y1="{66 + math.sin(a) * 40:.1f}" x2="{64 + math.cos(a) * 51:.1f}" y2="{66 + math.sin(a) * 51:.1f}" stroke="#4a3a30" stroke-width="1.4"/>')
    s.add(circ(64, 66, 38, s.lin([(0, "#e6dfd8"), (1, "#9a8f86")], 0, 0, 1, 1), sw=2))
    s.add(circ(64, 66, 22, "#2b1a14", sw=2))
    s.add(circ(64, 66, 18, s.rad([(0, "#fff2b0"), (.4, "#ff9a2a"), (1, "#a8300a")])))
    # chimneys
    for x in (26, 102):
        s.add(circ(x, 22, 10, s.lin([(0, "#7a6f80"), (1, "#3d3646")]), sw=2))
        s.add(circ(x, 22, 5, "#120c10", sw=1))
    save("smelter", s, 256)


def bld_arc_furnace():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#3d4a66")
    hexp = [(64 + math.cos(a) * 54, 64 + math.sin(a) * 54) for a in [i * math.tau / 6 + math.pi / 6 for i in range(6)]]
    s.add(poly(hexp, s.lin([(0, "#e2e8f4"), (1, "#7d879e")], 0, 0, 1, 1), sw=2.5))
    hexi = [(64 + math.cos(a) * 34, 64 + math.sin(a) * 34) for a in [i * math.tau / 6 + math.pi / 6 for i in range(6)]]
    s.add(poly(hexi, "#1b1f33", sw=2))
    s.add(circ(64, 64, 26, s.rad([(0, "#ffffff"), (.35, "#9fe8ff"), (1, "#2a3a9a")])))
    for p in hexi[::2]:
        s.add(circ(p[0], p[1], 7, s.lin([(0, "#c7ceda"), (1, "#5d6680")]), sw=1.6))
        s.add(f'<line x1="{p[0]:.1f}" y1="{p[1]:.1f}" x2="64" y2="64" stroke="#cff6ff" stroke-width="1.6" opacity="0.8"/>')
    save("arc_furnace", s, 256)


def bld_assembler(name, trim):
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#4b5068")
    s.add(rect(10, 10, 108, 108, s.lin([(0, "#eef1f7"), (1, "#8f97ad")], 0, 0, 1, 1), rx=16, sw=2.5))
    s.add(rect(10, 10, 108, 20, trim, rx=10, sw=2))
    s.add(rect(10, 98, 108, 20, trim, rx=10, sw=2))
    s.add(rect(26, 34, 76, 60, "#1c2033", rx=8, sw=2))
    # work bed
    s.add(rect(40, 48, 48, 32, s.lin([(0, "#3a4160"), (1, "#252a40")]), rx=4, sw=1.5))
    for i in range(4):
        s.add(f'<line x1="{44 + i * 12}" y1="50" x2="{44 + i * 12}" y2="78" stroke="{trim}" stroke-width="1" opacity="0.5"/>')
    for (x, y) in ((18, 20), (110, 20), (18, 108), (110, 108)):
        s.add(circ(x, y, 3, "#fff", sw=1))
    save(name, s, 256)


def assembler_arm():
    s = S(128)
    s.add(circ(64, 64, 9, "#c7ceda", sw=2))
    s.add(rect(60, 30, 8, 36, "#e7ecf5", rx=3, sw=1.6))
    s.add(rect(54, 22, 20, 10, "#ffcc4d", rx=3, sw=1.6))
    save("assembler_arm", s, 128)


def bld_chem_plant():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#45505e")
    s.add(rect(56, 24, 16, 80, "#6a7388", rx=4, sw=2))
    for cx, cy, c in ((36, 42, "#7dff9a"), (92, 86, "#ffd84a")):
        s.add(circ(cx, cy, 28, s.lin([(0, "#e8edf5"), (1, "#7d879e")], 0, 0, 1, 1), sw=2.5))
        s.add(circ(cx, cy, 19, s.rad([(0, "#ffffff"), (.3, c), (1, shade(c, -.55))], fx=.35, fy=.35), sw=2))
        s.add(f'<ellipse cx="{cx - 6}" cy="{cy - 8}" rx="6" ry="3" fill="#fff" opacity="0.6"/>')
    s.add(circ(92, 34, 14, s.lin([(0, "#b8c0d2"), (1, "#5a6378")]), sw=2))
    s.add(circ(92, 34, 6, "#1c2033", sw=1))
    s.add(circ(36, 94, 14, s.lin([(0, "#b8c0d2"), (1, "#5a6378")]), sw=2))
    s.add(circ(36, 94, 6, "#1c2033", sw=1))
    save("chem_plant", s, 256)


def bld_extractor():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#5a4a3a")
    s.add(circ(64, 64, 48, s.lin([(0, "#e3e7f0"), (1, "#7b8399")], 0, 0, 1, 1), sw=2.5))
    s.add(circ(64, 64, 34, "#2a2030", sw=2))
    s.add(circ(64, 64, 28, s.rad([(0, "#fff0a0"), (.5, "#ffb030"), (1, "#7a3a08")])))
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        s.add(rect(64 + math.cos(a) * 46 - 7, 64 + math.sin(a) * 46 - 7, 14, 14, "#ffb030", rx=3, sw=1.6))
    s.add(rect(48, 2, 32, 18, s.lin([(0, "#6a6f88"), (1, "#3c4057")]), rx=3, sw=2))
    s.add(poly([(56, 14), (64, 6), (72, 14)], "#ffb030", sw=1.2))
    save("extractor", s, 256)


def bld_lab():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#3f4766")
    s.add(circ(64, 64, 54, s.lin([(0, "#eef1f7"), (1, "#8f97ad")], 0, 0, 1, 1), sw=2.5))
    s.add(circ(64, 64, 44, s.rad([(0, "#e8ffff", .9), (.45, "#5ff7ee", .75), (1, "#1a5a8a", .9)], fx=.4, fy=.35), sw=2))
    s.add(circ(64, 64, 16, s.rad([(0, "#ffffff"), (1, "#3ff2e0")]), sw=1.5))
    for i in range(6):
        a = i * math.tau / 6
        s.add(f'<line x1="{64 + math.cos(a) * 18:.1f}" y1="{64 + math.sin(a) * 18:.1f}" x2="{64 + math.cos(a) * 43:.1f}" y2="{64 + math.sin(a) * 43:.1f}" stroke="#ffffff" stroke-opacity="0.45" stroke-width="1.4"/>')
    s.add(f'<path d="M34 44 A36 36 0 0 1 60 26" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" opacity="0.7"/>')
    save("lab", s, 256)


def bld_combustion():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#55485a")
    s.add(rect(10, 22, 108, 88, s.lin([(0, "#cfd4df"), (1, "#6d7488")], 0, 0, 1, 1), rx=12, sw=2.5))
    for i in range(5):
        s.add(rect(22, 34 + i * 12, 54, 6, "#3a3f55", rx=2, sw=1))
    s.add(rect(84, 34, 24, 64, "#2a2030", rx=6, sw=2))
    s.add(rect(88, 40, 16, 52, s.lin([(0, "#ffe08a"), (1, "#ff5a1a")]), rx=4, sw=1))
    for x in (26, 52):
        s.add(circ(x, 16, 9, s.lin([(0, "#7a6f80"), (1, "#3d3646")]), sw=2))
        s.add(circ(x, 16, 4, "#120c10", sw=1))
    save("combustion_gen", s, 256)


def bld_solar():
    s = S(128)
    s.add(rect(4, 6, 122, 122, "#000", rx=6, stroke="none", extra='opacity="0.35"'))
    s.add(rect(2, 2, 124, 124, "#9aa3b7", rx=6, sw=2.5))
    for i in range(4):
        for j in range(4):
            x, y = 7 + i * 29.5, 7 + j * 29.5
            s.add(rect(x, y, 27, 27, s.lin([(0, "#3f6ae0"), (.5, "#1c2f8a"), (1, "#0f1a52")], 0, 0, 1, 1), rx=2, sw=1.2))
            s.add(f'<line x1="{x + 13.5}" y1="{y}" x2="{x + 13.5}" y2="{y + 27}" stroke="#7fa3ff" stroke-width="0.7" opacity="0.7"/>')
            s.add(f'<line x1="{x}" y1="{y + 13.5}" x2="{x + 27}" y2="{y + 13.5}" stroke="#7fa3ff" stroke-width="0.7" opacity="0.7"/>')
    s.add(poly([(8, 8), (60, 8), (8, 60)], "#fff", stroke="none", extra='opacity="0.12"'))
    save("solar", s, 256)


def bld_accumulator():
    s = S(64)
    s.add(rect(3, 5, 60, 58, "#000", rx=6, stroke="none", extra='opacity="0.35"'))
    s.add(rect(2, 2, 60, 60, s.lin([(0, "#6a7390"), (1, "#3a4058")]), rx=6, sw=2))
    for x in (8, 34):
        s.add(rect(x, 8, 22, 48, s.lin([(0, "#2c3a6b"), (.5, "#6d8ae0"), (1, "#202a52")], 0, 0, 1, 0), rx=6, sw=1.6))
        s.add(rect(x + 4, 4, 14, 6, "#c9cfdd", rx=2, sw=1))
    save("accumulator", s, 128)


def bld_geothermal():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#5a3d3a")
    s.add(rect(10, 10, 108, 108, s.lin([(0, "#d9dde6"), (1, "#7b8399")], 0, 0, 1, 1), rx=18, sw=2.5))
    s.add(circ(64, 64, 40, "#1c1420", sw=2.5))
    s.add(circ(64, 64, 36, s.rad([(0, "#ffb070", .9), (1, "#5a1a08", .9)]), stroke="none"))
    for (x, y) in ((20, 20), (108, 20), (20, 108), (108, 108)):
        s.add(circ(x, y, 7, s.lin([(0, "#8a92a8"), (1, "#3d4255")]), sw=1.6))
    save("geothermal", s, 256)


def fan():
    s = S(128)
    for i in range(6):
        a = i * math.tau / 6
        s.add(f'<path d="M64 64 Q{64 + math.cos(a) * 20:.1f} {64 + math.sin(a) * 20 - 14:.1f} {64 + math.cos(a + .5) * 34:.1f} {64 + math.sin(a + .5) * 34:.1f} Q{64 + math.cos(a + .2) * 20:.1f} {64 + math.sin(a + .2) * 20:.1f} 64 64 Z" fill="#c9d0e0" stroke="{OUTLINE}" stroke-width="1.5"/>')
    s.add(circ(64, 64, 8, "#e8562a", sw=1.6))
    save("fan", s, 128)


def bld_lamp():
    s = S(64)
    s.add(circ(33, 35, 22, "#000", stroke="none", extra='opacity="0.3"'))
    s.add(circ(32, 32, 20, s.lin([(0, "#cfd4df"), (1, "#5d6578")], 0, 0, 1, 1), sw=2))
    s.add(circ(32, 32, 12, s.rad([(0, "#ffffff"), (.6, "#bdfff6"), (1, "#3ff2e0")]), sw=1.6))
    save("lamp", s, 128)


def bld_chest():
    s = S(64)
    s.add(rect(6, 8, 54, 52, "#000", rx=5, stroke="none", extra='opacity="0.35"'))
    s.add(rect(5, 5, 54, 54, s.lin([(0, "#b9a6d6"), (1, "#5c4a7a")], 0, 0, 1, 1), rx=5, sw=2))
    s.add(rect(10, 10, 44, 44, "none", rx=3, stroke="#2c2040", sw=1.4))
    s.add(f'<line x1="10" y1="32" x2="54" y2="32" stroke="#2c2040" stroke-width="1.6"/>')
    s.add(rect(27, 27, 10, 10, "#ffcc4d", rx=2, sw=1.4))
    for (x, y) in ((9, 9), (55, 9), (9, 55), (55, 55)):
        s.add(circ(x, y, 2.4, "#e0d6f0", sw=0.8))
    save("chest", s, 128)


def bld_launcher():
    s = S(192)
    s.add(rect(4, 8, 186, 182, "#000", rx=12, stroke="none", extra='opacity="0.35"'))
    s.add(rect(2, 2, 188, 188, s.lin([(0, "#6a7088"), (1, "#30344a")], 0, 0, 1, 1), rx=12, sw=2.5))
    for i in range(10):
        s.add(poly([(2 + i * 19, 2), (12 + i * 19, 2), (2 + i * 19 + 2, 12), (2 + i * 19 - 8, 12)], "#ffcc4d", stroke="none", extra='opacity="0.85"'))
    s.add(circ(96, 96, 72, s.lin([(0, "#dfe4ee"), (1, "#7b8399")], 0, 0, 1, 1), sw=3))
    s.add(circ(96, 96, 54, "#0e1020", sw=3))
    s.add(circ(96, 96, 50, s.rad([(0, "#3ff2e0", .5), (1, "#0e1020", 0)]), stroke="none"))
    for i in range(8):
        a = i * math.tau / 8
        s.add(rect(96 + math.cos(a) * 63 - 5, 96 + math.sin(a) * 63 - 5, 10, 10, TEAL, rx=2, sw=1.4))
    for (x, y) in ((22, 22), (170, 22), (22, 170), (170, 170)):
        s.add(rect(x - 10, y - 10, 20, 20, s.lin([(0, "#c7ceda"), (1, "#5d6680")]), rx=4, sw=2))
    save("launcher", s, 384)


def rocket():
    s = S(64)
    s.add(path("M32 2 Q44 14 44 40 L44 54 L20 54 L20 40 Q20 14 32 2 Z", s.lin([(0, "#9aa3b7"), (.45, "#ffffff"), (1, "#6a7388")], 0, 0, 1, 0), sw=1.8))
    s.add(path("M32 2 Q38 8 41 16 L23 16 Q26 8 32 2 Z", "#e84a5f", sw=1.4))
    s.add(circ(32, 28, 5, s.rad([(0, "#bff6ff"), (1, "#2a7aa8")]), sw=1.4))
    s.add(poly([(20, 40), (10, 58), (20, 54)], "#e84a5f", sw=1.4))
    s.add(poly([(44, 40), (54, 58), (44, 54)], "#e84a5f", sw=1.4))
    s.add(rect(24, 54, 16, 6, "#4a4560", rx=2, sw=1.2))
    save("rocket", s, 128)


def bld_power_core():
    s = S(192)
    ground_shadow(s, 100, 104, 92, 80, 0.5)
    # crashed lander hull
    hull = [(30, 150), (14, 96), (52, 30), (128, 18), (176, 64), (178, 134), (120, 176), (58, 176)]
    s.add(poly(hull, s.lin([(0, "#e6e9f1"), (.6, "#9aa1b6"), (1, "#5a6076")], 0, 0, 1, 1), sw=3))
    s.add(poly([(52, 30), (128, 18), (110, 62), (64, 66)], "#c8ccd8", sw=2))
    s.add(poly([(30, 150), (14, 96), (40, 110)], "#6a6f84", sw=2))
    # scorch & panels
    s.add(path("M140 150 L170 120 L176 134 L120 176 Z", "#3d3546", sw=2))
    s.add(path("M66 140 L100 150 L96 166 L62 158 Z", "#4c5066", sw=1.5))
    s.add(circ(100, 100, 34, "#151826", sw=3))
    s.add(circ(100, 100, 28, s.rad([(0, "#ffffff"), (.3, "#7ffff2"), (1, "#0d6a7a")])))
    for i in range(6):
        a = i * math.tau / 6
        s.add(f'<line x1="{100 + math.cos(a) * 30:.1f}" y1="{100 + math.sin(a) * 30:.1f}" x2="{100 + math.cos(a) * 40:.1f}" y2="{100 + math.sin(a) * 40:.1f}" stroke="{TEAL}" stroke-width="3"/>')
    s.add(rect(126, 74, 34, 10, "#e84a5f", rx=3, sw=1.5))
    s.add(rect(126, 90, 24, 6, "#ffcc4d", rx=2, sw=1.2))
    s.add(path("M30 70 L10 40 M150 30 L176 10", "none", stroke="#6a6f84", sw=5))
    save("power_core", s, 384)


def bld_inserter_base():
    s = S(64)
    s.add(circ(33, 35, 18, "#000", stroke="none", extra='opacity="0.35"'))
    s.add(rect(12, 12, 40, 40, s.lin([(0, "#6a7088"), (1, "#30344a")], 0, 0, 1, 1), rx=8, sw=2))
    s.add(circ(32, 32, 12, s.lin([(0, "#c7ceda"), (1, "#5d6680")]), sw=1.6))
    save("inserter_base", s, 128)


def belt_icon(name, c):
    s = S()
    ground_shadow(s, 32, 50, 28, 7)
    s.add(poly([(4, 36), (30, 22), (60, 34), (34, 48)], "#2a2d3e"))
    s.add(poly([(4, 36), (34, 48), (34, 54), (4, 42)], "#1a1c28"))
    s.add(poly([(34, 48), (60, 34), (60, 40), (34, 54)], "#121420"))
    s.add(poly([(10, 34), (30, 25), (54, 34), (34, 43)], "#3c4058", stroke="none"))
    for k in range(3):
        ox = k * 8 - 8
        oy = -k * 3.6 + 3.6
        s.add(path(f"M{26 + ox} {33 + oy} L{36 + ox} {29 + oy} L{40 + ox} {35 + oy}", "none", stroke=c, sw=3))
    s.add(poly([(4, 36), (30, 22), (30, 19), (4, 33)], c, sw=1.2))
    s.add(poly([(34, 48), (60, 34), (60, 31), (34, 45)], c, sw=1.2))
    save(name, s, 128)


def inserter_icon(name, c):
    s = S()
    ground_shadow(s, 32, 54, 20, 5)
    s.add(rect(18, 44, 28, 10, "#4a4f66", rx=3))
    s.add(path("M32 46 L22 26 L42 12", "none", stroke=OUTLINE, sw=8))
    s.add(path("M32 46 L22 26 L42 12", "none", stroke=c, sw=5))
    s.add(circ(32, 46, 5, "#c7ceda"))
    s.add(circ(22, 26, 4, "#c7ceda"))
    s.add(path("M40 8 L48 10 M42 12 L50 16", "none", stroke=OUTLINE, sw=3))
    save(name, s, 128)


# ----------------------------------------------------------------------------- tech icons
def tech_icons():
    s = S()
    s.add(circ(32, 32, 28, s.rad([(0, "#ffcc4d", .35), (1, "#ffcc4d", 0)]), stroke="none"))
    rock(s, 24, 42, 13, "#7a8aa8", 11, accent="#cfe0ff")
    s.add(path("M30 34 L50 14", "none", stroke=OUTLINE, sw=6))
    s.add(path("M30 34 L50 14", "none", stroke="#a87a4a", sw=3.5))
    s.add(path("M38 8 Q52 6 58 20", "none", stroke=OUTLINE, sw=7))
    s.add(path("M38 8 Q52 6 58 20", "none", stroke="#d8dde8", sw=4))
    s.add(path("M44 40 L52 40 M48 36 L48 44", "none", stroke="#7dff9a", sw=3.4))
    save("tech_mining", s, 128)

    s = S()
    s.add(circ(32, 32, 28, s.rad([(0, "#3ff2e0", .35), (1, "#3ff2e0", 0)]), stroke="none"))
    s.add(path("M24 8 L34 8 L34 24 L46 50 L12 50 L24 24 Z", "#d9e6ff", extra='fill-opacity="0.45"'))
    s.add(path("M18 38 L40 38 L46 50 L12 50 Z", "#3ff2e0", stroke="none"))
    s.add(path("M24 8 L34 8 L34 24 L46 50 L12 50 L24 24 Z", "none"))
    s.add(path("M44 30 L56 18 M56 18 L48 18 M56 18 L56 26", "none", stroke=OUTLINE, sw=6))
    s.add(path("M44 30 L56 18 M56 18 L48 18 M56 18 L56 26", "none", stroke="#ffcc4d", sw=3.5))
    save("tech_research", s, 128)

    s = S()
    s.add(circ(32, 32, 30, s.rad([(0, "#ffffff", .5), (1, "#9f7bff", 0)]), stroke="none"))
    for r, o in ((26, .25), (19, .45), (12, .7)):
        s.add(f'<path d="M{32 - r} {30 - r * .2} A{r} {r} 0 0 1 {32 + r} {30 - r * .2}" fill="none" stroke="#bff6ff" stroke-opacity="{o}" stroke-width="3" stroke-linecap="round"/>')
    s.add(path("M32 30 L22 58 L42 58 Z", "#c7ceda", sw=2))
    s.add(path("M26 46 L38 46", "none", stroke=OUTLINE, sw=2))
    s.add(circ(32, 28, 5, s.rad([(0, "#ffffff"), (1, "#3ff2e0")]), sw=1.6))
    save("tech_signal", s, 128)


# ----------------------------------------------------------------------------- world decoration
def ore_overlays():
    ores = {
        "ferrite_ore": ("#7d8fb3", "#d0dcf5", None),
        "cuprite_ore": ("#d07a3e", "#5ff7c8", None),
        "lumite": ("#d8c8a6", "#fff7e0", None),
        "carbon": ("#2c2a38", "#7a7790", None),
        "xenite_ore": ("#3f6a3a", "#9dff6a", "#7dff6a"),
    }
    for name, (base, accent, gl) in ores.items():
        for v in range(3):
            s = S()
            rnd = random.Random(sum(map(ord, name)) % 1000 + v * 17)
            if gl:
                s.add(circ(32, 32, 30, s.rad([(0, gl, .35), (1, gl, 0)]), stroke="none"))
            spots = [(rnd.uniform(14, 50), rnd.uniform(14, 50), rnd.uniform(8, 12.5)) for _ in range(4)]
            spots.sort(key=lambda p: p[1])
            for i, (x, y, r) in enumerate(spots):
                rock(s, x, y, r, shade(base, rnd.uniform(-.1, .1)), v * 10 + i, facets=6, accent=accent)
            save(f"ore_{name}_{v}", s, 96)
    # aether crystals
    for v in range(3):
        s = S()
        rnd = random.Random(300 + v)
        s.add(circ(32, 34, 30, s.rad([(0, "#b57bff", .35), (1, "#b57bff", 0)]), stroke="none"))
        for i in range(3):
            x = rnd.uniform(16, 48)
            h = rnd.uniform(16, 28)
            crystal(s, x, 52 - h, rnd.uniform(3.5, 5.5), h, rnd.choice(["#d9b8ff", "#9ff4ff", "#c79bff"]), "#4a2290", rnd.uniform(-25, 25))
        save(f"ore_aether_crystal_{v}", s, 96)


def flora():
    # glowing mushrooms
    s = S()
    for (x, y, r, c) in ((22, 40, 11, "#ff5ad8"), (42, 34, 14, "#c45aff"), (32, 50, 7, "#ff8ae8")):
        glow(s, x, y - 4, r * 2, c, .45)
        s.add(rect(x - 2, y - 2, 4, 12, "#e8d8f0", rx=2, sw=1))
        s.add(path(f"M{x - r} {y} Q{x} {y - r * 1.5} {x + r} {y} Z", s.lin([(0, shade(c, .4)), (1, shade(c, -.3))]), sw=1.4))
        s.add(circ(x - r * .3, y - r * .5, 1.5, "#fff", stroke="none"))
    save("flora_mushroom", s, 128)
    # crystal spire
    s = S()
    glow(s, 32, 34, 30, "#3ff2e0", .4)
    crystal(s, 24, 22, 5, 30, "#bffff8", "#1a8a8a", -15)
    crystal(s, 38, 14, 6, 38, "#9ff4ff", "#2a5aa8", 8)
    save("flora_spire", s, 128)
    # tendril plant
    s = S()
    rnd = random.Random(7)
    for i in range(7):
        a = -math.pi / 2 + rnd.uniform(-1.2, 1.2)
        L = rnd.uniform(18, 28)
        x2, y2 = 32 + math.cos(a) * L, 50 + math.sin(a) * L
        s.add(f'<path d="M32 50 Q{32 + math.cos(a) * L * .4 + rnd.uniform(-8, 8):.1f} {50 + math.sin(a) * L * .6:.1f} {x2:.1f} {y2:.1f}" fill="none" stroke="{OUTLINE}" stroke-width="5" stroke-linecap="round"/>')
        s.add(f'<path d="M32 50 Q{32 + math.cos(a) * L * .4:.1f} {50 + math.sin(a) * L * .6:.1f} {x2:.1f} {y2:.1f}" fill="none" stroke="#2fcfa8" stroke-width="3" stroke-linecap="round"/>')
        glow(s, x2, y2, 6, "#7dffe0", .7)
        s.add(circ(x2, y2, 2.2, "#e0fff6", stroke="none"))
    save("flora_tendril", s, 128)
    # bulb tree (large, 2x2)
    s = S(128)
    glow(s, 64, 50, 60, "#9f5aff", .35)
    s.add(path("M58 120 Q60 80 52 60 L76 60 Q68 80 70 120 Z", s.lin([(0, "#6a3a7a"), (1, "#3a1a48")], 0, 0, 1, 0), sw=2))
    for (x, y, r, c) in ((64, 40, 30, "#b45aff"), (36, 58, 18, "#ff5ad8"), (92, 58, 18, "#5ad8ff")):
        s.add(circ(x, y, r, s.rad([(0, "#ffffff"), (.35, c), (1, shade(c, -.5))], fx=.35, fy=.35), sw=2))
        s.add(f'<ellipse cx="{x - r * .3}" cy="{y - r * .4}" rx="{r * .25}" ry="{r * .15}" fill="#fff" opacity="0.6"/>')
    save("flora_tree", s, 256)
    # boulder
    s = S()
    ground_shadow(s, 32, 50, 26, 8)
    rock(s, 30, 36, 20, "#4a4560", 99, facets=8)
    s.add(path("M16 30 Q24 24 30 30", "none", stroke="#5ff7c8", sw=2, extra='opacity="0.7"'))
    save("flora_rock", s, 128)


def sap_vent_tiles():
    for v in range(2):
        s = S()
        rnd = random.Random(500 + v)
        pts_ = []
        for i in range(10):
            a = i / 10 * math.tau
            r = rnd.uniform(22, 30)
            pts_.append((32 + math.cos(a) * r, 32 + math.sin(a) * r))
        s.add(poly(pts_, s.rad([(0, "#ffe79a"), (.5, "#ffa21a"), (1, "#7a3a08")]), stroke="#3a2008", sw=2))
        for _ in range(4):
            s.add(circ(rnd.uniform(18, 46), rnd.uniform(18, 46), rnd.uniform(1.5, 3), "#fff6c8", stroke="none", extra='opacity="0.8"'))
        save(f"sap_pool_{v}", s, 96)
    s = S()
    s.add(circ(32, 32, 30, s.rad([(0, "#ff7a2a", .6), (1, "#ff7a2a", 0)]), stroke="none"))
    s.add(circ(32, 32, 18, "#2a1410", sw=2))
    s.add(circ(32, 32, 12, s.rad([(0, "#ffe0a0"), (.5, "#ff6a1a"), (1, "#5a1a08")]), stroke="none"))
    for i in range(8):
        a = i * math.tau / 8
        s.add(f'<line x1="{32 + math.cos(a) * 18:.1f}" y1="{32 + math.sin(a) * 18:.1f}" x2="{32 + math.cos(a) * 26:.1f}" y2="{32 + math.sin(a) * 26:.1f}" stroke="#ff9a4a" stroke-width="1.6" opacity="0.7"/>')
    save("vent", s, 96)


def terrain_tiles():
    """Seamless-ish ground textures (raster, numpy noise)."""
    biomes = {
        "regolith": ((86, 66, 110), (110, 86, 132)),
        "moss": ((32, 74, 78), (46, 102, 98)),
        "basalt": ((40, 40, 62), (58, 56, 84)),
        "dunes": ((130, 98, 124), (156, 122, 146)),
    }
    N = 64
    for name, (c1, c2) in biomes.items():
        for v in range(4):
            rng = np.random.default_rng(sum(map(ord, name)) % 10000 + v)
            noise = np.zeros((N, N))
            for octave, amp in ((4, .55), (8, .3), (16, .15)):
                g = rng.random((octave + 1, octave + 1))
                g[-1, :] = g[0, :]
                g[:, -1] = g[:, 0]
                img = Image.fromarray((g * 255).astype(np.uint8)).resize((N + N // octave, N + N // octave), Image.BICUBIC)
                noise += np.asarray(img, dtype=float)[:N, :N] / 255 * amp
            noise = (noise - noise.min()) / (noise.max() - noise.min() + 1e-9)
            a = np.array(c1, float)
            b = np.array(c2, float)
            rgb = a[None, None, :] * (1 - noise[..., None]) + b[None, None, :] * noise[..., None]
            speck = rng.random((N, N))
            rgb[speck > .985] *= 1.25
            rgb[speck < .015] *= 0.7
            rgb = np.clip(rgb, 0, 255).astype(np.uint8)
            Image.fromarray(rgb).save(os.path.join(PNG_DIR, f"ground_{name}_{v}.png"))


# ----------------------------------------------------------------------------- app icon & ui
def logo_svg(size=108, bg=True):
    s = S(108)
    if bg:
        s.add(rect(0, 0, 108, 108, s.rad([(0, "#3a1f66"), (1, "#0e0a1f")], cy=.35, r=.8), stroke="none"))
    # planet
    s.add(circ(54, 54, 26, s.rad([(0, "#9fffee"), (.4, "#3ff2e0"), (1, "#3a1f9a")], fx=.3, fy=.3), sw=2.5))
    s.add(f'<ellipse cx="54" cy="54" rx="40" ry="11" fill="none" stroke="{OUTLINE}" stroke-width="6" transform="rotate(-22 54 54)"/>')
    s.add(f'<ellipse cx="54" cy="54" rx="40" ry="11" fill="none" stroke="#ffcc4d" stroke-width="3.2" transform="rotate(-22 54 54)"/>')
    s.add(path(gear_path(54, 54, 9, 13, 8, .2), s.lin([(0, "#ffffff"), (1, "#b9c3d6")]), sw=2))
    s.add(circ(54, 54, 4, "#3a1f66", sw=1.5))
    # re-draw front part of ring over planet
    s.add(f'<path d="M17 69 A40 11 0 0 0 91 39" fill="none" stroke="{OUTLINE}" stroke-width="6" transform="rotate(0 54 54)" opacity="0"/>')
    return s


def app_icons():
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    fg = logo_svg(bg=False)
    full = logo_svg(bg=True)
    with open(os.path.join(SVG_DIR, "app_icon.svg"), "w") as f:
        f.write(full.svg())
    for d, k in dens.items():
        dd = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(dd, exist_ok=True)
        cairosvg.svg2png(bytestring=fg.svg().encode(), write_to=os.path.join(dd, "ic_launcher_foreground.png"),
                         output_width=int(108 * k), output_height=int(108 * k))
        # legacy icon (cropped centre 72/108 → scale)
        legacy = S(108)
        legacy.defs = full.defs
        legacy.body = full.body
        svg = legacy.svg().replace('viewBox="0 0 108 108"', 'viewBox="14 14 80 80"')
        cairosvg.svg2png(bytestring=svg.encode(), write_to=os.path.join(dd, "ic_launcher.png"),
                         output_width=int(48 * k), output_height=int(48 * k))
        cairosvg.svg2png(bytestring=svg.encode(), write_to=os.path.join(dd, "ic_launcher_round.png"),
                         output_width=int(48 * k), output_height=int(48 * k))
    # Play-store icon
    cairosvg.svg2png(bytestring=full.svg().replace('viewBox="0 0 108 108"', 'viewBox="14 14 80 80"').encode(),
                     write_to=os.path.join(ROOT, "art", "play_icon_512.png"), output_width=512, output_height=512)


# ----------------------------------------------------------------------------- main
def main():
    ore_icon("ferrite_ore", "#7d8fb3", "#d0dcf5")
    ore_icon("cuprite_ore", "#d07a3e", "#5ff7c8")
    ore_icon("lumite", "#d8c8a6", None)
    ore_icon("carbon", "#2c2a38", "#9a97b0")
    ore_icon("xenite_ore", "#3f6a3a", "#b6ff8a", glow="#7dff6a")
    crystal_icon()
    sap_icon()
    plate_icon("ferrite_plate", "#8fa3c9")
    plate_icon("copper_plate", "#e0884a")
    plate_icon("steel", "#9aa0b4", deco=lambda s, y: s.add(f'<line x1="18" y1="{y + 1}" x2="44" y2="{y - 6}" stroke="#ffffff" stroke-width="1" opacity="0.7"/>'))
    brick_icon()
    gear_icon()
    wire_icon()
    board_icon("circuit", "#1f8a7a", "#ffd56b", "#2a2d3e")
    board_icon("adv_circuit", "#b8325a", "#ffd56b", "#2a2d3e")
    board_icon("processing_unit", "#2f4fd0", "#9ff4ff", "#1a1c2c", big=True)
    pipe_icon()
    plastic_icon()
    sulfite_icon()
    battery_icon()
    engine_icon()
    lens_icon()
    lds_icon()
    xenite_cell_icon()
    rocket_fuel_icon()
    rocket_segment_icon()
    satellite_icon()
    flask("sci_kinetic", "#ff4a5a", "round")
    flask("sci_logistic", "#4aff7a", "cone")
    flask("sci_chemical", "#4aa8ff", "vial")
    flask("sci_fabrication", "#c44aff", "hex")
    flask("sci_quantum", "#ffd84a", "orb")
    flask("sci_astral", "#ffffff", "star")
    belt_icon("belt", "#ffb84a")
    belt_icon("fast_belt", "#3ff2e0")
    belt_icon("express_belt", "#ff5ad8")
    inserter_icon("inserter", "#ffcc4d")
    inserter_icon("fast_inserter", "#4aa8ff")
    bld_drill()
    bld_drill("deep_drill", accent="#ff5ad8", hull="#b9b0d6")
    drill_head()
    bld_smelter()
    bld_arc_furnace()
    bld_assembler("assembler", "#ffb84a")
    bld_assembler("assembler2", "#3ff2e0")
    assembler_arm()
    bld_chem_plant()
    bld_extractor()
    bld_lab()
    bld_combustion()
    bld_solar()
    bld_accumulator()
    bld_geothermal()
    fan()
    bld_lamp()
    bld_chest()
    bld_launcher()
    rocket()
    bld_power_core()
    bld_inserter_base()
    tech_icons()
    ore_overlays()
    flora()
    sap_vent_tiles()
    import gen_art_exotic
    gen_art_exotic.main()
    app_icons()
    print("assets written to", PNG_DIR)


if __name__ == "__main__":
    main()
