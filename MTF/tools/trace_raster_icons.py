#!/usr/bin/env python3
"""把单色 PNG（白底透明）描成 Android VectorDrawable。

图标是纯填充形状（不是描边），alpha 通道即形状轮廓，因此按 alpha=0.5 做
marching squares 取等值线（边缘线性插值，亚像素精度），再把等值线缝成闭合环：
偶数层为外轮廓（逆时针），奇数层为镂空（顺时针），合并进同一个 path 用
非零环绕规则填充，与原图逐像素比对确认一致。

用法：
    python3 tools/trace_raster_icons.py ic_theme_moon moon.png
    python3 tools/trace_raster_icons.py a.png sun.png b.png tip.png
"""

import os
import sys
from collections import defaultdict

import numpy as np
from PIL import Image

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE_DIR = os.path.join(PROJECT_ROOT, "app/src/main/res/drawable")

LEVEL = 0.5
RDP_EPS = 0.03  # Douglas-Peucker 简化容差（像素）
MIN_AREA = 1.0  # 小于该面积的环视为噪点


def contours(alpha: np.ndarray, level: float = LEVEL):
    """返回闭合环列表，每个环是 [(x, y), ...]（x=列, y=行）。"""
    h, w = alpha.shape
    inside = alpha >= level

    segments = []  # [( (x1,y1), (x2,y2) )]
    for j in range(h - 1):
        row = inside[j]
        nxt = inside[j + 1]
        for i in range(w - 1):
            a = bool(row[i])
            b = bool(row[i + 1])
            c = bool(nxt[i + 1])
            d = bool(nxt[i])
            # 位序 a(TL) 为最低位，与下面 pairs 表的编码一致
            code = (d << 3) | (c << 2) | (b << 1) | a
            if code in (0, 15):
                continue

            def top():
                a0, b0 = alpha[j, i], alpha[j, i + 1]
                t = (level - a0) / (b0 - a0) if b0 != a0 else 0.5
                return (i + t, float(j))

            def right():
                b0, c0 = alpha[j, i + 1], alpha[j + 1, i + 1]
                t = (level - b0) / (c0 - b0) if c0 != b0 else 0.5
                return (float(i + 1), j + t)

            def bottom():
                c0, d0 = alpha[j + 1, i + 1], alpha[j + 1, i]
                t = (level - d0) / (c0 - d0) if c0 != d0 else 0.5
                return (i + t, float(j + 1))

            def left():
                a0, d0 = alpha[j, i], alpha[j + 1, i]
                t = (level - a0) / (d0 - a0) if d0 != a0 else 0.5
                return (float(i), j + t)

            pairs = {
                1: [(left, top)],
                2: [(top, right)],
                3: [(left, right)],
                4: [(right, bottom)],
                6: [(top, bottom)],
                7: [(left, bottom)],
                8: [(bottom, left)],
                9: [(top, bottom)],
                11: [(right, bottom)],
                12: [(right, left)],
                13: [(top, right)],
                14: [(top, left)],
            }
            if code == 5 or code == 10:
                # 鞍点：用四角均值决定连通方式
                center = (alpha[j, i] + alpha[j, i + 1] + alpha[j + 1, i + 1] + alpha[j + 1, i]) / 4
                if code == 5:
                    if center >= level:
                        pairs[code] = [(top, right), (left, bottom)]
                    else:
                        pairs[code] = [(top, left), (right, bottom)]
                else:
                    if center >= level:
                        pairs[code] = [(top, left), (right, bottom)]
                    else:
                        pairs[code] = [(top, right), (bottom, left)]

            for f0, f1 in pairs[code]:
                segments.append((f0(), f1()))

    return _stitch(segments)


def _key(point):
    return (round(point[0], 3), round(point[1], 3))


def _stitch(segments):
    # 每个等值点至多被两条线段共享，因此端点图是若干路径/环的并
    touch = defaultdict(list)
    for idx, (p0, p1) in enumerate(segments):
        touch[_key(p0)].append(idx)
        touch[_key(p1)].append(idx)

    used = [False] * len(segments)
    loops = []
    for start in range(len(segments)):
        if used[start]:
            continue
        used[start] = True
        first, last = segments[start]
        ring = [first, last]
        for _ in range(len(segments)):
            candidates = [i for i in touch[_key(ring[-1])] if not used[i]]
            if not candidates:
                break
            idx = candidates[0]
            used[idx] = True
            p0, p1 = segments[idx]
            nxt = p1 if _key(p0) == _key(ring[-1]) else p0
            if _key(nxt) == _key(ring[0]):
                break
            ring.append(nxt)
        if len(ring) > 3:
            loops.append(ring)
    return loops


def signed_area(ring):
    total = 0.0
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        total += x1 * y2 - x2 * y1
    return total / 2.0


def point_in_ring(point, ring):
    x, y = point
    inside = False
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        if (y1 > y) != (y2 > y):
            t = (y - y1) / (y2 - y1)
            if x < x1 + t * (x2 - x1):
                inside = not inside
    return inside


def ring_center(ring):
    n = len(ring)
    return (sum(p[0] for p in ring) / n, sum(p[1] for p in ring) / n)


def simplify(ring, eps=RDP_EPS):
    def rdp(points):
        if len(points) < 3:
            return points
        x1, y1 = points[0]
        x2, y2 = points[-1]
        dx, dy = x2 - x1, y2 - y1
        norm = (dx * dx + dy * dy) ** 0.5
        worst, worst_idx = -1.0, -1
        for i in range(1, len(points) - 1):
            px, py = points[i]
            if norm == 0:
                d = ((px - x1) ** 2 + (py - y1) ** 2) ** 0.5
            else:
                d = abs(dy * (px - x1) - dx * (py - y1)) / norm
            if d > worst:
                worst, worst_idx = d, i
        if worst <= eps:
            return [points[0], points[-1]]
        left = rdp(points[: worst_idx + 1])
        right = rdp(points[worst_idx:])
        return left[:-1] + right

    return rdp(list(ring))


def fmt(value: float) -> str:
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def build_path_data(rings) -> str:
    # 按面积从大到到小判定嵌套层：偶数层为实心，奇数层为镂空
    ordered = sorted(rings, key=lambda r: -abs(signed_area(r)))
    out = []
    for idx, ring in enumerate(ordered):
        area = signed_area(ring)
        depth = 0
        for j in range(idx):
            if point_in_ring(ring_center(ring), ordered[j]):
                depth += 1
        hole = depth % 2 == 1
        want_ccw = area > 0
        if hole:
            want_ccw = not want_ccw
        pts = simplify(ring)
        if want_ccw:
            pts = pts[::-1]
        if len(pts) < 3:
            continue
        parts = [f"M{fmt(pts[0][0])},{fmt(pts[0][1])}"]
        for p in pts[1:]:
            parts.append(f"L{fmt(p[0])},{fmt(p[1])}")
        parts.append("Z")
        out.append("".join(parts))
    return "".join(out)


def trace(png_path: str, name: str):
    image = Image.open(png_path).convert("RGBA")
    alpha = np.asarray(image, dtype=np.float64)[..., 3] / 255.0
    height, width = alpha.shape

    rings = []
    for ring in contours(alpha):
        area = abs(signed_area(ring))
        if area < MIN_AREA:
            continue
        rings.append(ring)

    path_data = build_path_data(rings)
    xml = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        f'    android:viewportWidth="{width}"\n'
        f'    android:viewportHeight="{height}">\n'
        '    <path\n'
        f'        android:pathData="{path_data}"\n'
        '        android:fillColor="@android:color/white"\n'
        "    />\n"
        "</vector>\n"
    )
    out_path = os.path.join(DRAWABLE_DIR, f"{name}.xml")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(xml)
    print(f"[OK] {name} <- {os.path.basename(png_path)} "
          f"({len(rings)} ring, {len(path_data)} chars) -> {out_path}")


def main() -> int:
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    args = sys.argv[1:]
    if not args or len(args) % 2 != 0:
        print(__doc__)
        return 1
    for i in range(0, len(args), 2):
        name, png = args[i], args[i + 1]
        if name.endswith(".png") or png.endswith(".xml"):
            name, png = png, name
        if not os.path.basename(name) == name:
            print(f"资源名需为纯文件名: {name}")
            return 1
        trace(png, name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
