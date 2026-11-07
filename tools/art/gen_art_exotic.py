"""
Factonic endgame art: the three Xyra elements (Solanite, Terravite, Vitae), their products,
the high-tier machines that process them, and the seamless terrain textures.

Imported and run by gen_art.py (main), but can also be run on its own:

    python3 tools/art/gen_art_exotic.py
"""
import math, os, random
import numpy as np
from PIL import Image

from gen_art import (S, OUTLINE, PNG_DIR, rect, circ, path, poly, shade, save, ground_shadow, rock, crystal,
                     glow, base_plate, bolt_ring, bld_drill, DARK, TEAL)

AMBER = "#ffb238"
AMBER_D = "#a8600a"
TERRA = "#7f9c88"
VITAE = "#ff5fa8"
VITAE_D = "#9a1a5a"
ICE = "#9fe8ff"


# ============================================================================ item icons
def ingot(s, x, y, w, h, c, glowc=None):
    top = [(x + 6, y), (x + w - 6, y), (x + w - 1, y + h * .45), (x + 1, y + h * .45)]
    front = [(x + 1, y + h * .45), (x + w - 1, y + h * .45), (x + w, y + h), (x, y + h)]
    s.add(poly(front, s.lin([(0, shade(c, -.05)), (1, shade(c, -.5))]), sw=1.6))
    s.add(poly(top, s.lin([(0, shade(c, .55)), (1, shade(c, .1))]), sw=1.6))
    s.add(path(f"M{x + 9} {y + 3} L{x + w * .55} {y + 3}", "none", stroke="#fff", sw=1.6, extra='opacity="0.7"'))
    if glowc:
        s.add(rect(x + 4, y + h * .6, w - 8, 2.4, glowc, rx=1, stroke="none", extra='opacity="0.8"'))


def ingot_icon(name, c, glowc=None, mark=None):
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    if glowc:
        glow(s, 32, 38, 28, glowc, .35)
    ingot(s, 6, 34, 30, 18, c, glowc)
    ingot(s, 28, 36, 30, 18, shade(c, -.06), glowc)
    ingot(s, 16, 20, 32, 18, shade(c, .06), glowc)
    if mark:
        s.add(circ(32, 26, 2.2, mark, stroke="none"))
    save(name, s, 128)


def solanite_ore_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 7)
    glow(s, 32, 34, 30, AMBER, .45)
    rock(s, 20, 40, 12, "#5a4038", 31)
    rock(s, 44, 42, 11, "#5a4038", 32)
    crystal(s, 26, 14, 6, 30, "#fff1b8", "#c2700f", -14)
    crystal(s, 40, 18, 5, 26, "#ffe08a", "#a85a0a", 18)
    crystal(s, 33, 8, 7, 38, "#fff6d0", "#d8841a")
    save("solanite_ore", s, 128)


def terravite_ore_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 7)
    for (x, y, r, seed) in ((21, 39, 14, 41), (43, 41, 13, 42), (32, 25, 14, 43)):
        rock(s, x, y, r, TERRA, seed)
        rnd = random.Random(seed)
        for _ in range(2):
            a = rnd.uniform(0, math.tau)
            s.add(path(f"M{x - r * .6:.1f} {y + rnd.uniform(-4, 4):.1f} Q{x:.1f} {y + rnd.uniform(-8, 8):.1f} {x + r * .6:.1f} {y + rnd.uniform(-4, 4):.1f}",
                       "none", stroke="#f4fff6", sw=1.4, extra='opacity="0.85"'))
    save("terravite_ore", s, 128)


def goo_blob(s, cx, cy, r, c, seed, n=9):
    rnd = random.Random(seed)
    p = []
    for i in range(n):
        a = i / n * math.tau
        rr = r * rnd.uniform(.82, 1.08)
        p.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr * .78))
    d = f"M{p[0][0]:.1f} {p[0][1]:.1f} " + " ".join(
        f"Q{p[i][0] + (p[(i + 1) % n][0] - p[i][0]) * .5 + (p[i][1] - cy) * .25:.1f} {p[i][1] + (p[(i + 1) % n][1] - p[i][1]) * .5 - (p[i][0] - cx) * .25:.1f} {p[(i + 1) % n][0]:.1f} {p[(i + 1) % n][1]:.1f}"
        for i in range(n)) + " Z"
    s.add(path(d, s.rad([(0, "#ffd6ea"), (.45, c), (1, shade(c, -.55))], fx=.35, fy=.3), sw=1.8))


def vitae_ooze_icon():
    s = S()
    ground_shadow(s, 32, 54, 24, 6)
    glow(s, 32, 38, 28, VITAE, .4)
    goo_blob(s, 32, 40, 20, VITAE, 5)
    goo_blob(s, 22, 28, 8, "#ff7fc0", 6, 7)
    for (x, y, r) in ((26, 38, 3), (38, 44, 2.2), (34, 34, 1.6), (20, 27, 1.6)):
        s.add(circ(x, y, r, "#fff", stroke="none", extra='opacity="0.65"'))
    s.add(f'<ellipse cx="25" cy="33" rx="6" ry="3" fill="#fff" opacity="0.45"/>')
    save("vitae_ooze", s, 128)


def tube_icon(name, liquid, cap="#7b84a0", frost=False, glowc=None):
    s = S()
    ground_shadow(s, 32, 57, 18, 5)
    if glowc:
        glow(s, 32, 36, 28, glowc, .4)
    s.add(rect(18, 10, 28, 46, "#d9e6ff", rx=9, extra='fill-opacity="0.35"'))
    s.add(rect(19, 22, 26, 33, s.lin([(0, shade(liquid, .45)), (1, shade(liquid, -.45))]), rx=8, stroke="none"))
    s.add(path("M20 26 Q32 30 44 26", "none", stroke=shade(liquid, .6), sw=1.6, extra='opacity="0.8"'))
    s.add(rect(18, 10, 28, 46, "none", rx=9))
    s.add(rect(15, 5, 34, 9, cap, rx=3))
    s.add(rect(15, 8, 34, 2, shade(cap, .4), stroke="none"))
    s.add(path("M24 18 Q22 36 25 50", "none", stroke="#fff", sw=2.4, extra='opacity="0.6"'))
    if frost:
        rnd = random.Random(9)
        for _ in range(7):
            x, y = rnd.uniform(20, 44), rnd.uniform(14, 54)
            s.add(path(f"M{x - 2.5:.1f} {y:.1f} L{x + 2.5:.1f} {y:.1f} M{x:.1f} {y - 2.5:.1f} L{x:.1f} {y + 2.5:.1f}", "none", stroke="#ffffff", sw=1, extra='opacity="0.8"'))
    for (bx, by) in ((29, 46), (35, 40), (31, 34)):
        s.add(circ(bx, by, 1.4, "#fff", stroke="none", extra='opacity="0.7"'))
    save(name, s, 128)


def powder_icon():
    s = S()
    ground_shadow(s, 32, 54, 28, 6)
    s.add(path("M4 52 Q14 30 32 22 Q50 30 60 52 Z", s.lin([(0, "#e6efe8"), (1, "#7f9688")]), sw=1.8))
    rnd = random.Random(3)
    for _ in range(26):
        x, y = rnd.uniform(12, 52), rnd.uniform(30, 50)
        s.add(circ(x, y, rnd.uniform(.6, 1.4), rnd.choice(["#ffffff", "#5f7868", "#c8d8cc"]), stroke="none"))
    for (x, y) in ((50, 18), (56, 26), (12, 24)):
        s.add(circ(x, y, 1.3, "#e6efe8", stroke="none", extra='opacity="0.7"'))
    s.add(path("M20 34 Q30 26 40 28", "none", stroke="#fff", sw=1.6, extra='opacity="0.6"'))
    save("terravite_powder", s, 128)


def film_icon():
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    for k, off in enumerate((14, 4)):
        y = 22 + off
        s.add(path(f"M6 {y} Q18 {y - 8} 32 {y} T58 {y} L58 {y + 14} Q46 {y + 22} 32 {y + 14} T6 {y + 14} Z",
                   s.lin([(0, "#ffe7a0", .85), (1, "#d88a1a", .75)], 0, 0, 1, 1), sw=1.6))
        s.add(path(f"M10 {y + 3} Q20 {y - 3} 30 {y + 3}", "none", stroke="#fff", sw=1.6, extra='opacity="0.7"'))
    save("epoxy_film", s, 128)


def epoxy_brick_icon():
    s = S()
    ground_shadow(s, 32, 54, 26, 6)
    glow(s, 32, 38, 26, AMBER, .25)

    def blk(x, y):
        s.add(poly([(x, y + 6), (x + 22, y + 6), (x + 22, y + 18), (x, y + 18)], s.lin([(0, "#e8a030"), (1, "#8a4a08")]), sw=1.6))
        s.add(poly([(x, y + 6), (x + 6, y), (x + 28, y), (x + 22, y + 6)], "#ffd27a", sw=1.6))
        s.add(poly([(x + 22, y + 6), (x + 28, y), (x + 28, y + 12), (x + 22, y + 18)], "#b86a10", sw=1.6))
        s.add(path(f"M{x + 3} {y + 9} L{x + 12} {y + 9}", "none", stroke="#fff3c8", sw=1.4, extra='opacity="0.8"'))
    blk(5, 36); blk(29, 38); blk(16, 22)
    save("epoxy_brick", s, 128)


def epoxy_glass_icon():
    s = S()
    ground_shadow(s, 32, 56, 24, 5)
    s.add(poly([(14, 10), (52, 6), (54, 52), (12, 56)], s.lin([(0, "#fff8e0", .55), (1, "#ffd27a", .25)], 0, 0, 1, 1), stroke=AMBER_D, sw=2.4))
    s.add(poly([(14, 10), (52, 6), (54, 52), (12, 56)], "none", stroke="#ffd27a", sw=1))
    s.add(path("M20 18 L30 16 M20 24 L42 20", "none", stroke="#fff", sw=2.2, extra='opacity="0.85"'))
    s.add(path("M38 48 L48 46", "none", stroke="#fff", sw=1.6, extra='opacity="0.6"'))
    save("epoxy_glass", s, 128)


def residue_icon():
    s = S()
    ground_shadow(s, 32, 52, 26, 7)
    for (x, y, r, seed) in ((21, 40, 11, 61), (42, 41, 12, 62), (31, 28, 10, 63)):
        goo_blob(s, x, y, r, "#8a3a66", seed, 7)
    for (x, y) in ((20, 37), (40, 38), (30, 25)):
        s.add(circ(x, y, 1.6, "#ffb8dc", stroke="none", extra='opacity="0.6"'))
    save("bio_residue", s, 128)


def telomere_icon():
    s = S()
    glow(s, 32, 32, 30, VITAE, .3)
    N = 9
    for i in range(N):
        t = i / (N - 1)
        y = 6 + t * 52
        x1 = 32 + math.sin(t * math.tau * 1.2) * 14
        x2 = 32 - math.sin(t * math.tau * 1.2) * 14
        s.add(f'<line x1="{x1:.1f}" y1="{y:.1f}" x2="{x2:.1f}" y2="{y:.1f}" stroke="{OUTLINE}" stroke-width="3.6"/>')
        s.add(f'<line x1="{x1:.1f}" y1="{y:.1f}" x2="{x2:.1f}" y2="{y:.1f}" stroke="{"#7dffe0" if i % 2 else "#ffd27a"}" stroke-width="2"/>')
    for sign, c in ((1, VITAE), (-1, "#b45aff")):
        d = "M" + " L".join(f"{32 + sign * math.sin(t / 40 * math.tau * 1.2) * 14:.1f} {6 + t / 40 * 52:.1f}" for t in range(41))
        s.add(path(d, "none", stroke=OUTLINE, sw=6))
        s.add(path(d, "none", stroke=c, sw=3.6))
    save("telomere_strand", s, 128)


def lattice_icon():
    s = S()
    ground_shadow(s, 32, 56, 24, 5)
    glow(s, 32, 32, 28, VITAE, .25)
    pts_ = []
    for row in range(4):
        for col in range(4):
            x = 12 + col * 13 + (6.5 if row % 2 else 0)
            y = 12 + row * 12
            if x < 56:
                pts_.append((x, y))
    for i, a in enumerate(pts_):
        for b in pts_[i + 1:]:
            if math.dist(a, b) < 15:
                s.add(f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="{OUTLINE}" stroke-width="3.6"/>')
                s.add(f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="#c8d8cc" stroke-width="2"/>')
    for (x, y) in pts_:
        s.add(circ(x, y, 4, s.rad([(0, "#ffe0f0"), (.5, VITAE), (1, VITAE_D)], fx=.35, fy=.35), sw=1.4))
    save("cell_lattice", s, 128)


def elixir_icon():
    s = S()
    ground_shadow(s, 32, 58, 16, 4)
    glow(s, 32, 38, 30, VITAE, .55)
    s.add(path("M28 4 L36 4 L36 16 Q48 22 48 38 Q48 56 32 56 Q16 56 16 38 Q16 22 28 16 Z", "#f4e8ff", extra='fill-opacity="0.35"'))
    s.add(path("M17 36 Q32 30 47 36 Q48 55 32 55 Q16 55 17 36 Z", s.rad([(0, "#ffffff"), (.35, "#ff9ad0"), (1, VITAE_D)], fx=.4, fy=.3), stroke="none"))
    s.add(path("M28 4 L36 4 L36 16 Q48 22 48 38 Q48 56 32 56 Q16 56 16 38 Q16 22 28 16 Z", "none", sw=2))
    s.add(rect(25, 1, 14, 6, "#ffd27a", rx=2, sw=1.4))
    s.add(path("M23 26 Q20 36 23 46", "none", stroke="#fff", sw=2.4, extra='opacity="0.7"'))
    # tiny infinity mark
    s.add(path("M26 44 C26 40 31 40 32 44 C33 48 38 48 38 44 C38 40 33 40 32 44 C31 48 26 48 26 44 Z", "none", stroke="#fff", sw=1.4, extra='opacity="0.9"'))
    save("vitae_elixir", s, 128)


def canister_icon():
    s = S()
    ground_shadow(s, 32, 58, 22, 5)
    glow(s, 32, 34, 28, AMBER, .3)
    s.add(rect(14, 10, 36, 46, s.lin([(0, "#6a7088"), (.5, "#c9cfe0"), (1, "#4a4f66")], 0, 0, 1, 0), rx=7, sw=2))
    s.add(rect(20, 16, 24, 32, "#2a1608", rx=4, sw=1.4))
    s.add(rect(21, 24, 22, 23, s.lin([(0, "#fff0b0"), (.4, AMBER), (1, "#a8500a")]), rx=3, stroke="none"))
    s.add(path("M23 20 L23 44", "none", stroke="#fff", sw=1.8, extra='opacity="0.6"'))
    s.add(rect(14, 50, 36, 5, "#ffcc4d", rx=2, sw=1.2))
    s.add(rect(22, 4, 20, 8, "#8a92a8", rx=3, sw=1.6))
    s.add(path("M26 4 Q32 -2 38 4", "none", stroke=OUTLINE, sw=2.2))
    save("epoxy_canister", s, 128)


def sack_icon():
    s = S()
    ground_shadow(s, 32, 58, 26, 5)
    body = "M14 26 Q10 44 16 54 Q32 60 48 54 Q54 44 50 26 Q42 20 32 20 Q22 20 14 26 Z"
    s.add(path(body, s.lin([(0, "#e8efe8"), (1, "#8a9a90")]), sw=2))
    s.add(path(body, s.lin([(0, "#ffe7a0", .55), (1, "#d88a1a", .35)], 0, 0, 1, 1), stroke="none"))
    s.add(path("M24 20 Q22 12 26 8 L38 8 Q42 12 40 20", s.lin([(0, "#ffe7a0"), (1, "#d88a1a")]), sw=1.8))
    s.add(rect(23, 15, 18, 5, "#a8600a", rx=2, sw=1.2))
    s.add(path("M18 32 Q20 44 22 50", "none", stroke="#fff", sw=2.2, extra='opacity="0.7"'))
    s.add(rect(26, 34, 14, 10, "#14101f", rx=2, stroke="none", extra='opacity="0.6"'))
    s.add(path("M28 39 L38 39 M33 36 L33 42", "none", stroke="#9fe0b8", sw=1.6))
    save("xenocrete_sack", s, 128)


def cryocase_icon():
    s = S()
    ground_shadow(s, 32, 58, 28, 5)
    glow(s, 32, 36, 30, VITAE, .3)
    s.add(rect(4, 16, 56, 38, s.lin([(0, "#eef1f7"), (1, "#8f97ad")], 0, 0, 1, 1), rx=7, sw=2))
    s.add(rect(24, 9, 16, 9, "none", rx=3, stroke=OUTLINE, sw=3))
    s.add(rect(10, 22, 44, 24, "#13172a", rx=4, sw=1.6))
    for i in range(4):
        x = 15 + i * 10
        s.add(rect(x, 25, 6, 18, "#f4e8ff", rx=3, sw=1, extra='fill-opacity="0.4"'))
        s.add(rect(x + .8, 31, 4.4, 11, s.lin([(0, "#ffb8dc"), (1, VITAE_D)]), rx=2, stroke="none"))
    s.add(rect(10, 22, 44, 24, s.lin([(0, ICE, .25), (1, ICE, 0)]), rx=4, stroke="none"))
    s.add(rect(4, 48, 56, 4, ICE, rx=2, stroke="none", extra='opacity="0.6"'))
    s.add(circ(52, 20, 2.2, "#7dff9a", sw=.8))
    save("vitae_cryocase", s, 128)


def tech_cargo_icon():
    s = S()
    s.add(circ(32, 32, 30, s.rad([(0, AMBER, .4), (1, AMBER, 0)]), stroke="none"))
    s.add(path("M24 2 Q34 12 34 32 L34 44 L14 44 L14 32 Q14 12 24 2 Z", s.lin([(0, "#9aa3b7"), (.45, "#ffffff"), (1, "#6a7388")], 0, 0, 1, 0), sw=1.8))
    s.add(path("M24 2 Q29 7 31 13 L17 13 Q19 7 24 2 Z", "#e84a5f", sw=1.4))
    s.add(poly([(14, 34), (6, 50), (14, 46)], "#e84a5f", sw=1.4))
    s.add(poly([(34, 34), (42, 50), (34, 46)], "#e84a5f", sw=1.4))
    s.add(rect(34, 34, 24, 22, s.lin([(0, "#ffd27a"), (1, "#b86a10")]), rx=3, sw=1.8))
    s.add(path("M34 45 L58 45 M46 34 L46 56", "none", stroke="#7a4008", sw=1.6))
    save("tech_cargo", s, 128)


# ============================================================================ machines
def hull3(s, color):
    """Common 3x3 plate (192 units)."""
    s.add(rect(4, 8, 186, 182, "#000", rx=12, stroke="none", extra='opacity="0.35"'))
    s.add(rect(2, 2, 188, 188, s.lin([(0, shade(color, .25)), (1, shade(color, -.4))], 0, 0, 1, 1), rx=12, sw=2.5))
    s.add(rect(10, 10, 172, 172, "none", rx=8, stroke=shade(color, -.5), sw=1.2))
    for (x, y) in ((10, 10), (172, 10), (10, 172), (172, 172)):
        s.add(rect(x, y, 10, 10, "#ffcc4d", rx=2, sw=1))


def trim3(s, c):
    for i in range(6):
        s.add(rect(30 + i * 24, 184, 14, 4, c, rx=2, stroke="none", extra='opacity="0.85"'))


def bld_ingot_forge():
    s = S(192)
    hull3(s, "#5a4048")
    s.add(circ(96, 100, 70, s.rad([(0, "#c8b0a0"), (.8, "#7a5a50"), (1, "#4a3430")]), sw=3))
    for i in range(20):
        a = i * math.tau / 20
        s.add(f'<line x1="{96 + math.cos(a) * 56:.1f}" y1="{100 + math.sin(a) * 56:.1f}" x2="{96 + math.cos(a) * 69:.1f}" y2="{100 + math.sin(a) * 69:.1f}" stroke="#3a2420" stroke-width="2"/>')
    s.add(circ(96, 100, 52, s.lin([(0, "#3a3f55"), (1, "#1b1f33")]), sw=2.5))
    s.add(circ(96, 100, 38, "#1a0c08", sw=2))
    s.add(circ(96, 100, 33, s.rad([(0, "#ffffff"), (.3, "#fff0b0"), (.6, AMBER), (1, "#8a2a08")])))
    for k in range(3):
        a = k * math.tau / 3 - math.pi / 2
        s.add(rect(96 + math.cos(a) * 45 - 7, 100 + math.sin(a) * 45 - 7, 14, 14, "#c9cfe0", rx=3, sw=1.6))
    for x in (28, 164):
        s.add(circ(x, 32, 15, s.lin([(0, "#7a6f80"), (1, "#3d3646")]), sw=2.2))
        s.add(circ(x, 32, 8, "#120c10", sw=1.4))
    trim3(s, AMBER)
    save("ingot_forge", s, 384)


def bld_liquefier():
    s = S(192)
    hull3(s, "#4a4860")
    # pipes
    s.add(path("M20 150 L60 150 L60 120 M172 60 L140 60 L140 90", "none", stroke=OUTLINE, sw=14))
    s.add(path("M20 150 L60 150 L60 120 M172 60 L140 60 L140 90", "none", stroke="#8a92a8", sw=9))
    s.add(circ(96, 96, 64, s.lin([(0, "#e8edf5"), (1, "#7d879e")], 0, 0, 1, 1), sw=3))
    s.add(circ(96, 96, 50, "#2a1608", sw=2.5))
    s.add(circ(96, 96, 48, s.rad([(0, "#fff6d0"), (.35, "#ffc24a"), (1, "#a85a0a")], fx=.35, fy=.35), stroke="none"))
    s.add(path("M52 100 Q74 86 96 100 T140 100", "none", stroke="#fff6d0", sw=3, extra='opacity="0.7"'))
    s.add(f'<ellipse cx="76" cy="70" rx="16" ry="8" fill="#fff" opacity="0.55"/>')
    bolt_ring(s, 96, 96, 57, 14)
    s.add(circ(160, 160, 14, s.lin([(0, "#b8c0d2"), (1, "#5a6378")]), sw=2))
    s.add(circ(160, 160, 6, "#ffb238", sw=1))
    trim3(s, AMBER)
    save("liquefier", s, 384)


def bld_pulverizer():
    s = S(192)
    hull3(s, "#4f5a56")
    s.add(rect(18, 40, 156, 112, s.lin([(0, "#cfd8d2"), (1, "#7d8a84")], 0, 0, 1, 1), rx=14, sw=3))
    for cx in (61, 131):
        s.add(circ(cx, 96, 36, "#1b2420", sw=2.5))
        s.add(circ(cx, 96, 30, s.rad([(0, "#5f7868"), (1, "#24302a")]), stroke="none"))
        bolt_ring(s, cx, 96, 41, 10)
    s.add(rect(88, 50, 16, 92, "#2a3430", rx=3, sw=1.6))
    rnd = random.Random(4)
    for _ in range(30):
        s.add(circ(rnd.uniform(90, 102), rnd.uniform(56, 136), rnd.uniform(.8, 1.8), "#e6efe8", stroke="none", extra='opacity="0.8"'))
    s.add(rect(30, 16, 132, 18, "#9fe0b8", rx=6, sw=2))
    s.add(rect(30, 158, 132, 18, "#9fe0b8", rx=6, sw=2))
    save("pulverizer", s, 384)


def bld_curing_kiln():
    s = S(192)
    hull3(s, "#4a3f66")
    s.add(rect(20, 20, 152, 152, s.lin([(0, "#e2dcf0"), (1, "#7d7598")], 0, 0, 1, 1), rx=16, sw=3))
    s.add(rect(34, 34, 124, 124, "#160f2a", rx=10, sw=2.5))
    for r in range(3):
        for c in range(3):
            x, y = 42 + c * 38, 42 + r * 38
            s.add(rect(x, y, 32, 32, s.lin([(0, "#e2c8ff", .9), (1, "#7a3ad8", .8)], 0, 0, 1, 1), rx=5, sw=1.6))
            s.add(path(f"M{x + 5} {y + 8} L{x + 16} {y + 5}", "none", stroke="#fff", sw=1.8, extra='opacity="0.75"'))
    s.add(rect(80, 80, 32, 32, s.lin([(0, "#fff0b0"), (1, AMBER)], 0, 0, 1, 1), rx=5, sw=1.6))
    for x in (14, 178):
        for y in (60, 96, 132):
            s.add(circ(x, y, 4, "#b57bff", sw=1))
    save("curing_kiln", s, 384)


def bld_bio_centrifuge():
    s = S(192)
    hull3(s, "#5a4060")
    s.add(circ(96, 96, 76, s.lin([(0, "#f0e8f4"), (1, "#8a7a98")], 0, 0, 1, 1), sw=3))
    for i in range(6):
        a = i * math.tau / 6 + math.pi / 6
        x, y = 96 + math.cos(a) * 58, 96 + math.sin(a) * 58
        s.add(circ(x, y, 13, "#f4e8ff", sw=2, extra='fill-opacity="0.45"'))
        s.add(circ(x, y, 9, s.rad([(0, "#ffe0f0"), (.5, VITAE), (1, VITAE_D)], fx=.35, fy=.35), stroke="none"))
    s.add(circ(96, 96, 42, "#1c1424", sw=2.5))
    s.add(circ(96, 96, 38, s.rad([(0, VITAE, .6), (1, "#1c1424", 0)]), stroke="none"))
    bolt_ring(s, 96, 96, 46, 12)
    save("bio_centrifuge", s, 384)


def bld_gene_sequencer():
    s = S(192)
    hull3(s, "#3f4766")
    s.add(rect(14, 26, 164, 140, s.lin([(0, "#eef1f7"), (1, "#8f97ad")], 0, 0, 1, 1), rx=16, sw=3))
    s.add(rect(64, 34, 64, 124, "#0e1222", rx=10, sw=2.5))
    s.add(rect(66, 36, 60, 120, s.rad([(0, VITAE, .35), (1, "#0e1222", 0)]), rx=9, stroke="none"))
    # side screens
    for x in (22, 136):
        s.add(rect(x, 44, 34, 40, "#0e2a30", rx=4, sw=1.8))
        for k in range(4):
            s.add(rect(x + 5, 50 + k * 8, 10 + (k * 7) % 18, 3, TEAL if k % 2 else "#ff7fc8", rx=1, stroke="none"))
        s.add(rect(x, 100, 34, 52, "#2a2f48", rx=4, sw=1.8))
        for k in range(3):
            s.add(circ(x + 17, 112 + k * 14, 4, ["#7dff9a", "#ffcc4d", "#ff7fc8"][k], sw=1))
    for x in (20, 172):
        s.add(rect(x - 4, 10, 8, 14, "#8a92a8", rx=2, sw=1.4))
    save("gene_sequencer", s, 384)


def bld_vitae_synth():
    s = S(192)
    hull3(s, "#4a3a5e")
    oct_ = [(96 + math.cos(a) * 86, 96 + math.sin(a) * 86) for a in [i * math.tau / 8 + math.pi / 8 for i in range(8)]]
    s.add(poly(oct_, s.lin([(0, "#ffffff"), (.6, "#c8c0dc"), (1, "#7a7098")], 0, 0, 1, 1), sw=3))
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        x, y = 96 + math.cos(a) * 62, 96 + math.sin(a) * 62
        s.add(circ(x, y, 14, s.lin([(0, "#e8edf5"), (1, "#6a7388")]), sw=2))
        s.add(circ(x, y, 7, s.rad([(0, "#fff"), (1, VITAE)]), sw=1.2))
        s.add(f'<line x1="{x:.1f}" y1="{y:.1f}" x2="96" y2="96" stroke="#ffb8dc" stroke-width="2.4" opacity="0.8"/>')
    s.add(circ(96, 96, 44, "#180c1e", sw=3))
    s.add(circ(96, 96, 40, s.rad([(0, "#ffffff"), (.25, "#ffd0e8"), (.6, VITAE), (1, "#3a0a24")]), stroke="none"))
    s.add(f'<ellipse cx="82" cy="80" rx="12" ry="7" fill="#fff" opacity="0.6"/>')
    bolt_ring(s, 96, 96, 50, 16)
    for i in range(8):
        s.add(rect(26 + i * 18, 182, 10, 4, "#ffd27a", rx=2, stroke="none"))
    save("vitae_synth", s, 384)


def bld_cargo_packer():
    s = S(192)
    hull3(s, "#4b5068")
    s.add(rect(12, 70, 168, 52, "#232536", rx=6, sw=2.5))
    for i in range(10):
        s.add(f'<line x1="{20 + i * 16}" y1="74" x2="{20 + i * 16}" y2="118" stroke="#ffb84a" stroke-width="1.6" opacity="0.5"/>')
    # crates
    for (x, y, c) in ((20, 18, "#ffd27a"), (58, 18, "#e8efe8"), (20, 132, "#f4e8ff"), (134, 132, "#ffd27a"), (134, 18, "#e8efe8")):
        s.add(rect(x, y, 38, 40, s.lin([(0, c), (1, shade(c, -.45))]), rx=4, sw=2))
        s.add(path(f"M{x} {y + 20} L{x + 38} {y + 20} M{x + 19} {y} L{x + 19} {y + 40}", "none", stroke=shade(c, -.6), sw=1.6))
    s.add(circ(96, 96, 30, s.lin([(0, "#c7ceda"), (1, "#5d6680")]), sw=2.5))
    s.add(circ(96, 96, 18, DARK, sw=2))
    s.add(rect(66, 146, 60, 22, AMBER, rx=6, sw=2))
    s.add(path("M80 157 L112 157", "none", stroke="#7a4008", sw=3))
    save("cargo_packer", s, 384)


def bld_vitae_pump():
    s = S(128)
    base_plate(s, 0, 0, 128, 128, "#5a4058")
    s.add(rect(48, 2, 32, 18, s.lin([(0, "#6a6f88"), (1, "#3c4057")]), rx=3, sw=2))
    s.add(poly([(56, 14), (64, 6), (72, 14)], VITAE, sw=1.2))
    s.add(circ(64, 66, 46, s.lin([(0, "#e8edf5"), (1, "#7d879e")], 0, 0, 1, 1), sw=2.5))
    s.add(circ(64, 66, 34, "#f4e8ff", sw=2, extra='fill-opacity="0.35"'))
    goo_blob(s, 64, 72, 24, VITAE, 12)
    s.add(f'<ellipse cx="54" cy="54" rx="10" ry="5" fill="#fff" opacity="0.55"/>')
    for (x, y) in ((22, 106), (106, 106)):
        s.add(circ(x, y, 7, s.lin([(0, "#c7ceda"), (1, "#5d6680")]), sw=1.6))
    save("vitae_pump", s, 256)


def resonance_head():
    s = S(128)
    s.add(circ(64, 64, 30, s.rad([(0, "#6a6080"), (1, "#2a2440")]), sw=2))
    for r in (24, 16):
        s.add(circ(64, 64, r, "none", stroke=AMBER, sw=2, extra='opacity="0.8"'))
    for i in range(4):
        a = i * math.tau / 4
        x, y = 64 + math.cos(a) * 22, 64 + math.sin(a) * 22
        s.add(f'<g transform="rotate({math.degrees(a) + 90:.1f} {x:.1f} {y:.1f})">' +
              poly([(x - 4, y + 6), (x, y - 9), (x + 4, y + 6)], s.lin([(0, "#fff1b8"), (1, "#c2700f")]), sw=1.3) + "</g>")
    s.add(circ(64, 64, 8, s.rad([(0, "#ffffff"), (1, AMBER)]), sw=1.5))
    save("resonance_head", s, 128)


# ============================================================================ world decoration
def rare_overlays():
    for v in range(3):
        s = S()
        rnd = random.Random(900 + v)
        s.add(circ(32, 34, 31, s.rad([(0, AMBER, .45), (1, AMBER, 0)]), stroke="none"))
        rock(s, rnd.uniform(18, 26), 46, 9, "#4a3430", v * 3 + 1)
        rock(s, rnd.uniform(38, 46), 48, 8, "#4a3430", v * 3 + 2)
        for i in range(3):
            x = rnd.uniform(16, 48); h = rnd.uniform(14, 26)
            crystal(s, x, 50 - h, rnd.uniform(3.5, 5.5), h, rnd.choice(["#fff1b8", "#ffe08a", "#fff6d0"]), "#b8600a", rnd.uniform(-25, 25))
        save(f"ore_solanite_ore_{v}", s, 96)
    for v in range(3):
        s = S()
        rnd = random.Random(950 + v)
        spots = sorted([(rnd.uniform(14, 50), rnd.uniform(16, 50), rnd.uniform(8, 12)) for _ in range(4)], key=lambda p: p[1])
        for i, (x, y, r) in enumerate(spots):
            rock(s, x, y, r, shade(TERRA, rnd.uniform(-.12, .08)), v * 10 + i + 500)
            s.add(path(f"M{x - r * .6:.1f} {y:.1f} Q{x:.1f} {y - r * .5:.1f} {x + r * .55:.1f} {y + 1:.1f}", "none", stroke="#f4fff6", sw=1.3, extra='opacity="0.9"'))
        save(f"ore_terravite_ore_{v}", s, 96)
    for v in range(2):
        s = S()
        rnd = random.Random(980 + v)
        s.add(circ(32, 32, 31, s.rad([(0, VITAE, .5), (1, VITAE, 0)]), stroke="none"))
        goo_blob(s, 32, 33, 25, VITAE, 990 + v, 11)
        for _ in range(5):
            s.add(circ(rnd.uniform(18, 46), rnd.uniform(20, 44), rnd.uniform(1.5, 3.2), "#ffe0f0", stroke="#9a1a5a", sw=.8, extra='opacity="0.85"'))
        s.add(f'<ellipse cx="24" cy="25" rx="7" ry="3.5" fill="#fff" opacity="0.45"/>')
        save(f"vitae_pool_{v}", s, 96)


# ============================================================================ seamless terrain
N = 256


def pnoise(rng, scale):
    """Periodic gaussian-filtered noise in [0,1] (tiles perfectly)."""
    w = rng.standard_normal((N, N))
    f = np.fft.fftfreq(N)
    fx, fy = np.meshgrid(f, f)
    filt = np.exp(-(fx ** 2 + fy ** 2) * (scale ** 2) * 9)
    n = np.real(np.fft.ifft2(np.fft.fft2(w) * filt))
    n -= n.min()
    return n / (n.max() + 1e-9)


def wrap_dist(cx, cy):
    y, x = np.mgrid[0:N, 0:N]
    dx = np.abs(x - cx); dx = np.minimum(dx, N - dx)
    dy = np.abs(y - cy); dy = np.minimum(dy, N - dy)
    return dx, dy, (x - cx + N / 2) % N - N / 2, (y - cy + N / 2) % N - N / 2


def colorize(t, c1, c2):
    a = np.array(c1, float); b = np.array(c2, float)
    return a[None, None, :] * (1 - t[..., None]) + b[None, None, :] * t[..., None]


def pebbles(rgb, rng, count, rmin, rmax, base, dark=.55):
    for _ in range(count):
        cx, cy = rng.uniform(0, N), rng.uniform(0, N)
        rx, ry = rng.uniform(rmin, rmax), rng.uniform(rmin, rmax) * .75
        _, _, sx, sy = wrap_dist(cx, cy)
        d = (sx / rx) ** 2 + (sy / ry) ** 2
        # drop shadow
        _, _, sx2, sy2 = wrap_dist(cx + 1.2, cy + 1.6)
        sh = ((sx2 / rx) ** 2 + (sy2 / ry) ** 2) < 1
        rgb[sh & (d >= 1)] *= dark
        m = d < 1
        light = np.clip(1.15 - (sx + sy * 1.2) / (rx + ry) * .55, .6, 1.4)
        col = np.array(base, float) * rng.uniform(.85, 1.15)
        rgb[m] = col[None, :] * light[m][:, None]


def terrain_textures():
    out = {}
    # ---- regolith: dusty violet ground with pebbles and hairline cracks
    rng = np.random.default_rng(11)
    t = pnoise(rng, 40) * .55 + pnoise(rng, 12) * .3 + pnoise(rng, 3) * .15
    rgb = colorize(t, (78, 60, 102), (124, 98, 144))
    ridge = np.abs(pnoise(rng, 20) - .5)
    rgb[ridge < .008] *= .82
    pebbles(rgb, rng, 70, 2, 5.5, (150, 128, 170))
    out["regolith"] = rgb
    # ---- moss: teal carpet with clumps and glowing specks
    rng = np.random.default_rng(22)
    t = pnoise(rng, 30) * .5 + pnoise(rng, 6) * .35 + pnoise(rng, 2) * .15
    rgb = colorize(t, (24, 62, 68), (54, 120, 106))
    clump = pnoise(rng, 9)
    rgb[clump > .62] = rgb[clump > .62] * 1.18 + np.array([4, 10, 6])
    rgb[clump < .3] *= .82
    sp = rng.random((N, N))
    rgb[sp > .9975] = (150, 255, 230)
    out["moss"] = rgb
    # ---- basalt: dark slate with polygonal cracks (periodic voronoi)
    rng = np.random.default_rng(33)
    pts_ = []
    while len(pts_) < 34:
        p = rng.uniform(0, N, 2)
        if all(min(abs(p[0] - q[0]), N - abs(p[0] - q[0])) ** 2 + min(abs(p[1] - q[1]), N - abs(p[1] - q[1])) ** 2 > 26 ** 2 for q in pts_):
            pts_.append(p)
    d1 = np.full((N, N), 1e9); d2 = np.full((N, N), 1e9); cell = np.zeros((N, N))
    for k, (px, py) in enumerate(pts_):
        dx, dy, _, _ = wrap_dist(px, py)
        d = np.sqrt(dx ** 2 + dy ** 2)
        closer = d < d1
        d2 = np.where(closer, d1, np.minimum(d2, d))
        cell = np.where(closer, k, cell)
        d1 = np.where(closer, d, d1)
    shade_k = rng.uniform(.85, 1.15, len(pts_))[cell.astype(int)]
    t = pnoise(rng, 16) * .6 + pnoise(rng, 3) * .4
    rgb = colorize(t, (34, 34, 54), (64, 62, 94)) * shade_k[..., None]
    edge = d2 - d1
    rgb[edge < 1.6] *= .45
    rgb[(edge >= 1.6) & (edge < 3)] *= 1.12
    sp = rng.random((N, N))
    rgb[sp > .997] = (160, 170, 230)
    out["basalt"] = rgb
    # ---- dunes: pink-violet sand with wind ripples
    rng = np.random.default_rng(44)
    y, x = np.mgrid[0:N, 0:N]
    warp = pnoise(rng, 30) * 2.4 + pnoise(rng, 10) * .6
    rip = np.sin((y / N * 18 + x / N * 2 + warp) * math.tau) * .5 + .5
    t = pnoise(rng, 40) * .55 + rip * .35 + pnoise(rng, 2) * .1
    rgb = colorize(t, (118, 86, 116), (168, 130, 154))
    sp = rng.random((N, N))
    rgb[sp > .993] *= 1.25
    out["dunes"] = rgb
    # ---- lake: acid teal with caustic highlights
    rng = np.random.default_rng(55)
    t = pnoise(rng, 28) * .7 + pnoise(rng, 8) * .3
    rgb = colorize(t, (10, 64, 72), (20, 120, 118))
    c1 = 1 - np.abs(pnoise(rng, 14) * 2 - 1)
    c2 = 1 - np.abs(pnoise(rng, 9) * 2 - 1)
    caus = np.clip((c1 * c2) ** 8 * 1.4, 0, 1)
    rgb = rgb + caus[..., None] * np.array([30, 90, 80])
    out["lake"] = rgb
    for name, rgb in out.items():
        Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).save(os.path.join(PNG_DIR, f"terrain_{name}.png"))
    # the old per-tile ground textures are no longer used
    for b in ("regolith", "moss", "basalt", "dunes"):
        for v in range(4):
            p = os.path.join(PNG_DIR, f"ground_{b}_{v}.png")
            if os.path.exists(p):
                os.remove(p)


def main():
    solanite_ore_icon(); terravite_ore_icon(); vitae_ooze_icon()
    ingot_icon("solanite_ingot", "#e8a030", glowc=AMBER)
    ingot_icon("terravite_ingot", "#8aa494", mark="#f4fff6")
    tube_icon("liquid_solanite", AMBER, cap="#8a92a8", glowc=AMBER)
    tube_icon("cryo_coolant", ICE, cap="#5a6a9a", frost=True)
    tube_icon("vitae_plasma", VITAE, cap="#b8a0c8", glowc=VITAE)
    powder_icon(); film_icon(); epoxy_brick_icon(); epoxy_glass_icon(); residue_icon()
    telomere_icon(); lattice_icon(); elixir_icon(); canister_icon(); sack_icon(); cryocase_icon(); tech_cargo_icon()
    bld_drill("resonance_drill", accent=AMBER, hull="#8a80a8")
    resonance_head(); bld_vitae_pump()
    bld_ingot_forge(); bld_liquefier(); bld_pulverizer(); bld_curing_kiln()
    bld_bio_centrifuge(); bld_gene_sequencer(); bld_vitae_synth(); bld_cargo_packer()
    rare_overlays()
    terrain_textures()


if __name__ == "__main__":
    main()
