#!/usr/bin/env python3
"""Deterministic original painted-style forest valley wallpaper for Middle-earth TV test build.
No copyrighted characters or third-party images; one compressed still image, no animations.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

random.seed(6174)
W, H = 1200, 675
image = Image.new("RGB", (W, H))
pix = image.load()
for y in range(H):
    t = y / H
    for x in range(W):
        # Morning gold near the distant valley, dark teal towards the corners.
        dx = abs(x - 690) / 900
        warmth = max(0.0, 1.0 - ((y - 220) / 300) ** 2 - dx * dx) * 0.47
        pix[x, y] = (
            int(11 + 53 * t + 115 * warmth),
            int(31 + 44 * t + 75 * warmth),
            int(31 + 17 * t + 44 * warmth),
        )
draw = ImageDraw.Draw(image, "RGBA")
# Sunlit mist behind the far peaks.
for radius in range(220, 20, -12):
    opacity = max(0, int(3.4 * (220 - radius) / 16))
    draw.ellipse((690-radius*1.9, 260-radius*.72, 690+radius*1.9, 260+radius*.72),
                 fill=(241, 198, 113, opacity))

def mountains(baseline, amplitude, color, seed, step=65):
    rng = random.Random(seed)
    ridge = [(0, baseline)]
    for x in range(0, W+step, step):
        peak = math.sin(x/104.0+seed)*amplitude*.43 + rng.uniform(-amplitude*.45, amplitude*.45)
        ridge.append((x, int(baseline+peak)))
    ridge += [(W, H), (0, H)]
    draw.polygon(ridge, fill=color)

mountains(278, 105, (47, 67, 72, 255), 1)
mountains(338, 95, (33, 59, 58, 255), 4)
# A distant river winding toward the viewer
draw.polygon([(666, 285), (690, 285), (682, 340), (710, 395), (675, 465),
              (810, 570), (880, 675), (470, 675), (560, 570),
              (636, 475), (645, 395), (659, 339)], fill=(91, 120, 114, 220))
draw.line([(677, 319), (668, 390), (682, 455), (630, 510), (692, 590)],
          fill=(212, 196, 136, 80), width=5)
# Tree-covered foothills, leaving a central glowing valley.
draw.polygon([(0, 335), (100, 360), (210, 392), (300, 423), (380, 504),
              (490, 595), (500, H), (0, H)], fill=(15, 43, 38, 255))
draw.polygon([(1200, 335), (1040, 375), (950, 405), (850, 478),
              (740, 606), (850, H), (1200, H)], fill=(15, 44, 40, 255))
# Elven-inspired original distant citadel (not a film depiction).
def tower(x, base, h, width, shade):
    draw.rectangle((x-width//2, base-h, x+width//2, base), fill=shade)
    draw.polygon([(x-width//2-4, base-h), (x, base-h-29),
                  (x+width//2+4, base-h)], fill=(81, 90, 72, 255))
    for y in range(base-h+20, base-8, 28):
        draw.rounded_rectangle((x-2, y, x+2, y+11), radius=1,
                               fill=(226, 176, 82, 180))
for x, base, ht, wd in [(545, 341, 52, 18), (569, 335, 86, 19),
                         (602, 349, 64, 22), (629, 341, 109, 24),
                         (652, 351, 74, 19), (706, 352, 73, 20)]:
    tower(x, base, ht, wd, (79, 88, 72, 255))
draw.arc((505, 330, 748, 437), 187, 350, fill=(137, 124, 86, 230), width=5)
# Forest crowns and slender pine trunks
rng = random.Random(882)
def pine(x, y, size, deep):
    trunk=(13, 29, 26, 235) if deep else (25, 54, 44, 245)
    leaf=(8, 33, 29, 250) if deep else (22, 62, 46, 235)
    draw.line((x,y,x,y-size), fill=trunk, width=max(1,size//15))
    for l in (.26, .47, .67, .85):
        yy=y-int(size*l)
        ww=int(size*(.22 if deep else .18)*(1-l*.55))
        draw.polygon(((x, yy-int(size*.33)), (x-ww, yy+int(size*.08)),
                      (x+ww, yy+int(size*.08))), fill=leaf)
for layer in (0, 1):
    for i in range(140 if layer == 0 else 96):
        side = rng.choice(("left", "right"))
        x = rng.randrange(0, 525) if side == "left" else rng.randrange(755, W)
        top = int(362+abs(x-600)*.37)
        y = min(H+30, rng.randint(top, H+20))
        size = rng.randint(23, 83) if layer == 0 else rng.randint(55, 149)
        pine(x, y, size, layer == 1)
# Warm lantern specks in the valley.
for i in range(145):
    x = rng.randint(475, 752)
    y = rng.randint(300, 590)
    if rng.random() < .5 and abs(x-610) > (y-300)*.5: continue
    r = rng.choice((1, 1, 2))
    draw.ellipse((x-r,y-r,x+r,y+r),fill=(241,190,96,rng.randint(90,185)))
# Dark vignette protects readability of white clock and icon labels.
shade = Image.new("RGBA", (W,H), (0,0,0,0))
sp = shade.load()
for y in range(H):
    for x in range(W):
        normx = ((x-W*.55)/(W*.72))**2
        normy = ((y-H*.45)/(H*.77))**2
        edge = min(1., max(0., normx+normy))
        bottom = min(.55, max(0., (y/H-.58)*1.0))
        sp[x,y]=(2,8,7,int(95*edge+145*bottom))
image = Image.alpha_composite(image.convert("RGBA"),shade).convert("RGB")
output=Path(sys.argv[1])
output.parent.mkdir(parents=True,exist_ok=True)
image.resize((1920,1080),Image.Resampling.LANCZOS).save(output,"JPEG",quality=83,optimize=True,progressive=True)
print("Created static Middle-earth wallpaper:",output,output.stat().st_size,"bytes")
