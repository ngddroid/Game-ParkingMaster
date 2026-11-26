#!/usr/bin/env python3
"""Generates launcher icons, Play Store icon and feature graphic for Inch Perfect (PIL only)."""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
FONT = os.path.join(ROOT, "app/src/main/res/font/bungee.ttf")
NAVY_TOP, NAVY_BOT = (22, 30, 70), (7, 10, 26)
CYAN, ORANGE, LIME = (25, 227, 255), (255, 122, 26), (155, 255, 60)

def grad(w, h, top, bot):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (255,))
    return im

def background(S):
    im = grad(S, S, NAVY_TOP, NAVY_BOT)
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([S * 0.15, S * 0.1, S * 0.95, S * 0.9], fill=CYAN + (70,))
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.12))
    im.alpha_composite(glow)
    gl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(gl)
    step = S / 9
    for i in range(10):
        d.line([(i * step, 0), (i * step, S)], fill=CYAN + (22,), width=max(1, S // 260))
        d.line([(0, i * step), (S, i * step)], fill=CYAN + (22,), width=max(1, S // 260))
    im.alpha_composite(gl)
    return im

def rot(pts, cx, cy, a):
    c, s = math.cos(a), math.sin(a)
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]

def rrect_pts(w, h, r, n=10):
    pts = []
    for cx, cy, a0 in [(w/2 - r, -h/2 + r, -90), (w/2 - r, h/2 - r, 0), (-w/2 + r, h/2 - r, 90), (-w/2 + r, -h/2 + r, 180)]:
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

def car(d, cx, cy, L, W, ang, color=ORANGE):
    # nose points along -y, rotated by ang
    a = ang
    dark = tuple(int(c * 0.62) for c in color)
    light = tuple(min(255, int(c + (255 - c) * 0.35)) for c in color)
    d.polygon(rot(rrect_pts(W * 1.02, L * 1.0, W * 0.34), cx, cy, a), fill=dark + (255,))
    d.polygon(rot(rrect_pts(W * 0.9, L * 0.97, W * 0.3), cx - 0, cy, a), fill=color + (255,))
    d.polygon(rot([(x - W * 0.08, y) for x, y in rrect_pts(W * 0.45, L * 0.86, W * 0.18)], cx, cy, a), fill=tuple(int(color[i] + (light[i] - color[i]) * 0.5) for i in range(3)) + (255,))
    # glass: windshield trapezoid, roof, rear window
    d.polygon(rot([(-W * 0.40, -L * 0.17), (W * 0.40, -L * 0.17), (W * 0.32, -L * 0.03), (-W * 0.32, -L * 0.03)], cx, cy, a), fill=(18, 26, 44, 255))
    d.polygon(rot([(-W * 0.22, -L * 0.165), (-W * 0.08, -L * 0.165), (W * 0.12, -L * 0.035), (-W * 0.02, -L * 0.035)], cx, cy, a), fill=(110, 125, 150, 255))
    d.polygon(rot([(x, y + L * 0.11) for x, y in rrect_pts(W * 0.64, L * 0.26, W * 0.12)], cx, cy, a), fill=light + (255,))
    d.polygon(rot([(-W * 0.32, L * 0.25), (W * 0.32, L * 0.25), (W * 0.36, L * 0.33), (-W * 0.36, L * 0.33)], cx, cy, a), fill=(18, 26, 44, 255))
    for sx in (-1, 1):
        d.polygon(rot([(sx * W * 0.47, -L * 0.15), (sx * W * 0.6, -L * 0.13), (sx * W * 0.6, -L * 0.09), (sx * W * 0.47, -L * 0.09)], cx, cy, a), fill=dark + (255,))
        d.polygon(rot([(sx * W * 0.40, -L * 0.48), (sx * W * 0.16, -L * 0.5), (sx * W * 0.18, -L * 0.445), (sx * W * 0.40, -L * 0.44)], cx, cy, a), fill=(240, 248, 255, 255))
        d.polygon(rot([(sx * W * 0.40, L * 0.48), (sx * W * 0.16, L * 0.5), (sx * W * 0.18, L * 0.455), (sx * W * 0.40, L * 0.45)], cx, cy, a), fill=(220, 20, 40, 255))
    d.line(rot([(0, -L * 0.45), (0, -L * 0.2)], cx, cy, a), fill=tuple(int(color[i] * 0.8) for i in range(3)) + (255,), width=max(1, int(W * 0.03)))

def foreground(S, with_bg=False):
    SS = S * 4
    im = background(SS) if with_bg else Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    lay = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    cx, cy = SS * 0.56, SS * 0.47
    bw, bh = SS * 0.30, SS * 0.42
    # bay glow
    glow = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.rounded_rectangle([cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2], radius=SS * 0.03, fill=CYAN + (60,), outline=CYAN + (255,), width=int(SS * 0.03))
    glow = glow.filter(ImageFilter.GaussianBlur(SS * 0.02))
    lay.alpha_composite(glow)
    d.rounded_rectangle([cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2], radius=SS * 0.03, fill=CYAN + (38,))
    # dashed outline
    per = 2 * (bw + bh); dash = SS * 0.055; gap = SS * 0.03
    def pt(t):
        t %= per
        if t < bw: return (cx - bw/2 + t, cy - bh/2)
        t -= bw
        if t < bh: return (cx + bw/2, cy - bh/2 + t)
        t -= bh
        if t < bw: return (cx + bw/2 - t, cy + bh/2)
        t -= bw
        return (cx - bw/2, cy + bh/2 - t)
    t = 0.0
    while t < per:
        steps = 8
        seg = [pt(t + dash * k / steps) for k in range(steps + 1)]
        d.line(seg, fill=CYAN + (255,), width=int(SS * 0.018))
        t += dash + gap
    # ruler ticks on the left of the bay
    for k in range(9):
        y = cy - bh/2 + bh * k / 8
        ln = SS * (0.05 if k % 4 == 0 else 0.028)
        d.line([(cx - bw/2 - SS * 0.035 - ln, y), (cx - bw/2 - SS * 0.035, y)], fill=ORANGE + (255,), width=int(SS * 0.012))
    # motion streaks
    for k, (ox, oy, ln) in enumerate([(0.18, 0.80, 0.16), (0.12, 0.72, 0.12), (0.22, 0.88, 0.1)]):
        x0, y0 = SS * ox, SS * oy
        d.line([(x0, y0), (x0 + SS * ln * 0.6, y0 - SS * ln)], fill=(255, 255, 255, 120 - k * 25), width=int(SS * 0.012))
    # car sliding in
    carl = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    cd = ImageDraw.Draw(carl)
    sh = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    car(ImageDraw.Draw(sh), cx - SS * 0.05 + SS * 0.02, cy + SS * 0.12 + SS * 0.03, SS * 0.36, SS * 0.17, math.radians(-18), color=(0, 0, 0))
    sh = sh.filter(ImageFilter.GaussianBlur(SS * 0.02))
    sh.putalpha(sh.getchannel("A").point(lambda v: int(v * 0.5)))
    lay.alpha_composite(sh)
    car(cd, cx - SS * 0.05, cy + SS * 0.12, SS * 0.36, SS * 0.17, math.radians(-18))
    lay.alpha_composite(carl)
    im.alpha_composite(lay)
    return im.resize((S, S), Image.LANCZOS)

def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)

dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, k in dens.items():
    fg = int(108 * k); lg = int(48 * k)
    save(foreground(fg), f"{RES}/mipmap-{name}/ic_launcher_foreground.png")
    save(background(fg), f"{RES}/mipmap-{name}/ic_launcher_background.png")
    full = foreground(fg * 1, with_bg=True)
    # legacy square icon (rounded) and round icon
    inner = full.crop((int(fg * 18 / 108), int(fg * 18 / 108), int(fg * 90 / 108), int(fg * 90 / 108))).resize((lg, lg), Image.LANCZOS)
    m = Image.new("L", (lg * 4, lg * 4), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, lg * 4, lg * 4], radius=lg * 4 * 0.22, fill=255)
    sq = inner.copy(); sq.putalpha(m.resize((lg, lg), Image.LANCZOS)); save(sq, f"{RES}/mipmap-{name}/ic_launcher.png")
    m2 = Image.new("L", (lg * 4, lg * 4), 0); ImageDraw.Draw(m2).ellipse([0, 0, lg * 4, lg * 4], fill=255)
    rd = inner.copy(); rd.putalpha(m2.resize((lg, lg), Image.LANCZOS)); save(rd, f"{RES}/mipmap-{name}/ic_launcher_round.png")
# Play icon 512 (full bleed square, Play applies the mask)
play = foreground(512 * 108 // 72, with_bg=True)
o = (play.width - 512) // 2
save(play.crop((o, o, o + 512, o + 512)), os.path.join(ROOT, "docs/store/icon-512.png"))
# Feature graphic 1024x500
fgimg = background(1024).crop((0, 262, 1024, 762))
art = foreground(560)
fgimg.alpha_composite(art, (520, -30))
d = ImageDraw.Draw(fgimg)
f1 = ImageFont.truetype(FONT, 78); f2 = ImageFont.truetype(FONT, 30)
d.text((54, 150), "INCH", font=f1, fill=(255, 255, 255, 255))
d.text((54, 236), "PERFECT", font=f1, fill=CYAN + (255,))
d.text((58, 340), "CAR PARKING · 10,000 LEVELS", font=f2, fill=ORANGE + (255,))
save(fgimg.convert("RGB"), os.path.join(ROOT, "docs/store/feature-graphic.png"))
print("icons done")
