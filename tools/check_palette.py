"""Palette checker for Bridge Ride PDX.

Reads docs/design/colors-decided.json (the Wayfinding colors) and checks:
  - WCAG 2.2 contrast for each listed pair (4.5:1 text, 3:1 icons, lines, and UI parts)
  - color-blind separation for pairs that must stay apart (Machado 2009 simulation of
    protanopia, deuteranopia, tritanopia). Pass when the worst OKLab distance is 0.10 or more,
    or the lightness gap is 0.12 or more.
It reports only. It never changes a decided color; a FAIL is a question for Whitney.

Writes docs/design/palette-report.md. Usage: python tools/check_palette.py (no dependencies)
"""
import json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DESIGN = os.path.join(HERE, "..", "docs", "design")
DE_MIN, DL_MIN = 0.10, 0.12

# ---------- color math ----------
def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def rgb_to_hex(rgb):
    return "#" + "".join(f"{round(max(0, min(1, c)) * 255):02X}" for c in rgb)


def srgb_to_lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lin_to_srgb(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def lin_rgb_to_oklab(r, g, b):
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (math.copysign(abs(x) ** (1 / 3), x) for x in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def oklab_to_lin_rgb(L, a, b):
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)


def hex_to_oklch(h):
    L, a, b = lin_rgb_to_oklab(*(srgb_to_lin(c) for c in hex_to_rgb(h)))
    return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360


def oklch_to_hex(L, C, H):
    """Clip chroma until the color fits in sRGB."""
    for _ in range(60):
        a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
        lin = oklab_to_lin_rgb(L, a, b)
        if all(-1e-4 <= x <= 1.0001 for x in lin):
            return rgb_to_hex(tuple(lin_to_srgb(max(0, min(1, x))) for x in lin))
        C *= 0.95
    return rgb_to_hex(tuple(lin_to_srgb(max(0, min(1, x))) for x in oklab_to_lin_rgb(L, 0, 0)))


def luminance(h):
    r, g, b = (srgb_to_lin(c) for c in hex_to_rgb(h))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


# Machado, Oliveira, Fernandes (2009), severity 1.0, applied in linear RGB
CVD = {
    "protanopia": [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    "deuteranopia": [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
    "tritanopia": [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
}


def simulate(h, kind):
    lin = [srgb_to_lin(c) for c in hex_to_rgb(h)]
    M = CVD[kind]
    out = [sum(M[i][j] * lin[j] for j in range(3)) for i in range(3)]
    return rgb_to_hex(tuple(lin_to_srgb(max(0, min(1, x))) for x in out))


def delta_e(a, b):
    """OKLab distance. About 0.02 is barely noticeable; 0.10 and up is clearly different."""
    A = lin_rgb_to_oklab(*(srgb_to_lin(c) for c in hex_to_rgb(a)))
    B = lin_rgb_to_oklab(*(srgb_to_lin(c) for c in hex_to_rgb(b)))
    return math.dist(A, B)



cfg = json.load(open(os.path.join(DESIGN, "colors-decided.json")))
C = {k: v["hex"] for k, v in cfg["colors"].items()}
rows, fails = [], []
for fg, bg, need, why in cfg["contrast"]:
    r = contrast(C[fg], C[bg])
    ok = r >= need
    rows.append(f"| `{fg}` on `{bg}` | {C[fg]} / {C[bg]} | {r:.2f}:1 | {need}:1 | {'PASS' if ok else 'FAIL'} | {why} |")
    if not ok:
        fails.append(f"{fg} on {bg}: {r:.2f}:1, needs {need}:1 ({why})")
cvd = []
for a, b, why in cfg["must_differ"]:
    worst, kind = delta_e(C[a], C[b]), "normal vision"
    for k in CVD:
        d = delta_e(simulate(C[a], k), simulate(C[b], k))
        if d < worst:
            worst, kind = d, k
    dl = abs(hex_to_oklch(C[a])[0] - hex_to_oklch(C[b])[0])
    ok = worst >= DE_MIN or dl >= DL_MIN
    cvd.append(f"| `{a}` vs `{b}` | {worst:.3f} ({kind}) | {dl:.3f} | {'PASS' if ok else 'FAIL'} | {why} |")
    if not ok:
        fails.append(f"{a} vs {b}: hard to tell apart for {kind} (distance {worst:.3f}, lightness gap {dl:.3f}) ({why})")

rep = ["# Palette report", "", "Generated by `tools/check_palette.py` from `docs/design/colors-decided.json`. Reports only; decided colors are not changed.", "",
       "## Colors", "", "| Name | Hex | Status | Role |", "|---|---|---|---|"]
rep += [f"| {k} | {v['hex']} | {v['status']} | {v['role']} |" for k, v in cfg["colors"].items()]
rep += ["", "## Contrast", "", "| Pair | Colors | Ratio | Needs | Result | Why |", "|---|---|---|---|---|---|"] + rows
rep += ["", "## Color-blind separation", "", f"Pass when the worst OKLab distance is {DE_MIN} or more, or the lightness gap is {DL_MIN} or more.", "",
        "| Pair | Worst distance | Lightness gap | Result | Why |", "|---|---|---|---|---|"] + cvd
rep += ["", "## Questions raised", ""] + ([f"- {f}" for f in fails] or ["- None."])
rep += ["", "Dark mode is not checked: its values are in palette.json, which is not in the repo yet."]
open(os.path.join(DESIGN, "palette-report.md"), "w").write("\n".join(rep) + "\n")
print(f"{len(rows)} contrast checks, {len(cvd)} color-blind checks, {len(fails)} failures")
for f in fails:
    print("FAIL", f)
