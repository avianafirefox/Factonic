#!/usr/bin/env python3
"""
Factonic v1.0 logistics icons: Tunnel Belt and Belt Splitter.

Same style and helpers as gen_art.py (isometric 64-unit box, #14101f outlines). Rasterises with cairosvg
when it is installed, otherwise with rsvg-convert, so it runs on a stock Linux box:

    python3 tools/art/gen_art_logistics.py
"""
import os
import shutil
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SVG_DIR = os.path.join(ROOT, "art", "svg")
PNG_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUTLINE = "#14101f"
TUNNEL = "#9b87ff"
AMBER = "#ffb84a"
TEAL = "#3ff2e0"


class S:
    def __init__(self, size=64):
        self.size, self.defs, self.body, self.n = size, [], [], 0

    def gid(self):
        self.n += 1
        return f"g{self.n}"

    def lin(self, stops, x1=0, y1=0, x2=0, y2=1):
        i = self.gid()
        st = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
        self.defs.append(f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')
        return f"url(#{i})"

    def rad(self, stops):
        i = self.gid()
        st = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in stops)
        self.defs.append(f'<radialGradient id="{i}" cx="0.5" cy="0.5" r="0.5">{st}</radialGradient>')
        return f"url(#{i})"

    def add(self, s):
        self.body.append(s)

    def svg(self):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.size}" height="{self.size}" viewBox="0 0 {self.size} {self.size}">'
                f'<defs>{"".join(self.defs)}</defs>{"".join(self.body)}</svg>')


def pts(p):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in p)


def poly(p, fill, stroke=OUTLINE, sw=1.6):
    return f'<polygon points="{pts(p)}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round"/>'


def path(d, fill, stroke=OUTLINE, sw=1.6):
    return f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round" stroke-linecap="round"/>'


def ellipse(cx, cy, rx, ry, fill, stroke=OUTLINE, sw=1.6):
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def up(p, h):
    return (p[0], p[1] - h)


def save(name, s, px=128):
    svg = s.svg()
    os.makedirs(SVG_DIR, exist_ok=True)
    src = os.path.join(SVG_DIR, name + ".svg")
    with open(src, "w") as f:
        f.write(svg)
    out = os.path.join(PNG_DIR, name + ".png")
    try:
        import cairosvg
        cairosvg.svg2png(bytestring=svg.encode(), write_to=out, output_width=px, output_height=px)
    except ImportError:
        if not shutil.which("rsvg-convert"):
            raise SystemExit("Install cairosvg (pip install cairosvg) or rsvg-convert (librsvg2-bin)")
        subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), "-o", out, src], check=True)
    print("wrote", out)


def shadow(s, cx, cy, rx, ry, a=0.35):
    s.add(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{s.rad([(0, "#000", a), (1, "#000", 0)])}"/>')


# belt slab corners, as in gen_art.belt_icon: A back-left, B front-left, C front-right, D back-right
A, B, C, D = (4, 36), (30, 22), (60, 34), (34, 48)


def belt_slab(s, rail):
    s.add(poly([A, B, C, D], "#2a2d3e"))
    s.add(poly([A, D, (34, 54), (4, 42)], "#1a1c28"))
    s.add(poly([D, C, (60, 40), (34, 54)], "#121420"))
    s.add(poly([(10, 34), (30, 25), (54, 34), (34, 43)], "#3c4058", stroke="none"))
    s.add(poly([A, B, (30, 19), (4, 33)], rail, sw=1.2))
    s.add(poly([D, C, (60, 31), (34, 45)], rail, sw=1.2))


def tunnel_belt():
    s = S()
    shadow(s, 32, 50, 29, 8)
    belt_slab(s, TUNNEL)
    # chevrons on the open (back) half
    for k in range(2):
        ox, oy = k * 8 - 10, -k * 3.6 + 4
        s.add(path(f"M{24 + ox} {35 + oy} L{34 + ox} {31 + oy} L{38 + ox} {37 + oy}", "none", stroke=TUNNEL, sw=3))
    # hood over the front half of the belt
    h = 15
    p1, p2, p3, p4 = lerp(A, B, .42), B, C, lerp(D, C, .42)
    q1, q2, q3, q4 = up(p1, h), up(p2, h), up(p3, h), up(p4, h)
    s.add(poly([p4, p3, q3, q4], s.lin([(0, "#3a3f5a"), (1, "#262a3c")], 0, 0, 1, 0)))  # right side
    s.add(poly([p1, p4, q4, q1], "#4a5070"))  # mouth face, toward the viewer
    s.add(poly([q1, q2, q3, q4], s.lin([(0, "#6a7398"), (1, "#454c6a")], 0, 0, 1, 1)))  # roof
    # violet trim along the roof edges
    s.add(f'<polyline points="{pts([q1, q4, q3])}" fill="none" stroke="{TUNNEL}" stroke-width="2.2" stroke-linejoin="round"/>')
    # the dark arched opening
    m1, m2 = lerp(p1, p4, .16), lerp(p1, p4, .84)
    t1, t2 = up(m1, h * .62), up(m2, h * .62)
    mid = up(lerp(m1, m2, .5), h * .86)
    s.add(path(f"M{m1[0]:.2f} {m1[1]:.2f} L{t1[0]:.2f} {t1[1]:.2f} Q{mid[0]:.2f} {mid[1]:.2f} {t2[0]:.2f} {t2[1]:.2f} L{m2[0]:.2f} {m2[1]:.2f} Z", "#0b0914", sw=1.4))
    # a chevron on the roof pointing the way items flow
    c = lerp(lerp(q1, q2, .5), lerp(q4, q3, .5), .5)
    s.add(path(f"M{c[0] - 7:.2f} {c[1] + 1:.2f} L{c[0] + 1:.2f} {c[1] - 4:.2f} L{c[0] + 4:.2f} {c[1] + 3:.2f}", "none", stroke=TUNNEL, sw=2.6))
    save("tunnel_belt", s)


def splitter():
    """Top-down, like its in-world look: one belt in, shared between the other three sides."""
    s = S()
    s.add(f'<rect x="9" y="11" width="50" height="50" rx="10" fill="#000" opacity="0.35"/>')

    def stub(rot, outward):
        g = f'<g transform="rotate({rot} 32 32)">'
        g += f'<rect x="22" y="2" width="20" height="14" fill="#232536" stroke="{OUTLINE}" stroke-width="1.4"/>'
        g += f'<rect x="22" y="2" width="3" height="14" fill="{AMBER}"/><rect x="39" y="2" width="3" height="14" fill="{AMBER}"/>'
        d = "M27 11 L32 5 L37 11" if outward else "M27 6 L32 12 L37 6"
        g += f'<path d="{d}" fill="none" stroke="{AMBER}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>'
        s.add(g + "</g>")

    stub(0, True)     # front (up)
    stub(90, True)    # right
    stub(180, True)   # back (down)
    stub(270, False)  # left: the input
    s.add(f'<rect x="12" y="12" width="40" height="40" rx="9" fill="{s.lin([(0, "#6a7398"), (1, "#3a3f5a")], 0, 0, 1, 1)}" stroke="{OUTLINE}" stroke-width="2"/>')
    s.add(f'<rect x="15" y="15" width="34" height="34" rx="7" fill="none" stroke="{AMBER}" stroke-width="1.8"/>')
    s.add(f'<circle cx="32" cy="32" r="10" fill="#1a1c28" stroke="{OUTLINE}" stroke-width="1.4"/>')
    for a in (0, 120, 240):
        s.add(f'<line x1="32" y1="32" x2="32" y2="24" stroke="{AMBER}" stroke-width="2.6" stroke-linecap="round" transform="rotate({a + 20} 32 32)"/>')
    s.add(f'<circle cx="32" cy="32" r="2.6" fill="#c7ceda" stroke="{OUTLINE}" stroke-width="0.8"/>')
    s.add(poly([(32, 13.5), (27, 19.5), (37, 19.5)], TEAL, sw=1.2))
    save("splitter", s)


if __name__ == "__main__":
    tunnel_belt()
    splitter()
