#!/usr/bin/env python3
"""从图标源 APK 提取图标，转成当前项目的 Android VectorDrawable。

- 资源名被打散成 res/-XX，靠 aapt2 dump resources 反查「原名 -> 实际文件」
- vector 型资源：aapt2 dump xmltree 取出原始 pathData，viewport / strokeWidth /
  fillType 全部按原样保留，只把颜色统一成 @android:color/white 交给代码 tint
- 只发了位图的资源（部分 vector 在打包时被转成 png）：超采样后做方块并集边界
  追踪 + RDP 简化，轮廓填充成 vector

用法：python3 tools/fetch_icons_from_apk.py [drawable名 ...]
"""

import os
import re
import subprocess
import sys
import zipfile

APK = "/tmp/ks.apk"
AAPT2 = "/opt/android-sdk/build-tools/36.0.0/aapt2"
RES_TXT = "/tmp/icon_src_res.txt"
DRAWABLE_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/res/drawable",
)
PREVIEW_PATH = "/tmp/icon_src_preview.html"
# 源 vector 里常见「全屏矩形」占位 path（clip-path 或 mask 底），不是图形本体
FULLSCREEN_RECT = re.compile(r"^M0,0[hv]?([\d.]+)[vh]?([\d.]+)[hv]?-?\1[zZ]$")

# 项目 drawable 名 -> 图标源里的资源名；None 表示没有合适图标，保留现有版本
ICONS = {
    # 详情页 / 通用
    "ic_back": "ic_arrow_back_black_24",
    "ic_favorite_outline": "ic_shoucang_white_18dp",
    "ic_lock_outline": "ic_lock_24dp",
    # 首页 / 图片预览
    "ic_search_new": "ic_search_black_24",
    "ic_download": "ic_download",
    "ic_browser": None,
    # 导航 tab
    "ic_home": "ic_home_outline",
    "ic_list": "ic_category_outline_white_24dp",
    "ic_find": "ic_discovery_white_24dp",
    "ic_me": "ic_account_circle_outline_white_24dp",
    # 我的页
    "ic_people": "ic_account_circle_outline_white_24dp",
    "ic_friends": None,
    "ic_posts": None,
    "ic_points": None,
    "ic_forbid": None,
    # 通用控件
    "ic_arrow_up": "ic_arrow_up_24",
    "ic_arrow_down": "ic_arrow_down_24dp",
    "ic_arrow_right": "ic_keyboard_arrow_right_white_24dp",
    "ic_send": "ic_send",
    "ic_check": "ic_check",
    "ic_post_edit": "ic_edit_outline",
}

CAP = {0: "butt", 1: "round", 2: "square"}
JOIN = {0: "miter", 1: "round", 2: "bevel"}
FILL = {0: "nonZero", 1: "evenOdd"}


def resource_index():
    """drawable 原名 -> [(混淆文件, 类型), ...]"""
    lines = open(RES_TXT, encoding="utf-8", errors="replace").read().splitlines()
    idx = {}
    for i, line in enumerate(lines):
        m = re.search(r"resource 0x[0-9a-f]+ drawable/([a-zA-Z0-9_$]+)", line)
        if not m:
            continue
        nxt = lines[i + 1] if i + 1 < len(lines) else ""
        for f in re.finditer(r"res/(\S+) type=(XML|PNG)", nxt):
            idx.setdefault(m.group(1), []).append((f.group(1), f.group(2)))
    return idx


def dump_xmltree(path):
    return subprocess.run(
        [AAPT2, "dump", "xmltree", APK, "--file", "res/" + path],
        capture_output=True, text=True,
    ).stdout


TOKEN = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|(-?\d+\.?\d*(?:e-?\d+)?)")


def path_bbox(path_data):
    """解析 SVG pathData 的控制点包络盒（曲线取控制点，近似足够）。"""
    tokens = []
    for m in TOKEN.finditer(path_data):
        if m.group(1):
            tokens.append(m.group(1))
        else:
            tokens.append(float(m.group(2)))
    if not tokens:
        return None
    cmd = "M"
    i = 0
    cx = cy = 0.0
    xs, ys = [], []

    def take(n):
        nonlocal i
        vals = []
        for _ in range(n):
            if i >= len(tokens) or isinstance(tokens[i], str):
                return None
            vals.append(tokens[i])
            i += 1
        return vals

    while i < len(tokens):
        if isinstance(tokens[i], str):
            cmd = tokens[i]
            i += 1
            if cmd in "Zz":
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c in ("M", "L", "T"):
            v = take(2)
            if v is None:
                break
            cx, cy = (cx + v[0], cy + v[1]) if rel else (v[0], v[1])
            xs.append(cx)
            ys.append(cy)
        elif c == "H":
            v = take(1)
            if v is None:
                break
            cx = cx + v[0] if rel else v[0]
            xs.append(cx)
        elif c == "V":
            v = take(1)
            if v is None:
                break
            cy = cy + v[0] if rel else v[0]
            ys.append(cy)
        elif c in ("C", "S", "Q"):
            n = 4 if c == "C" else 2
            v = take(n)
            if v is None:
                break
            for k in range(0, n, 2):
                px, py = (cx + v[k], cy + v[k + 1]) if rel else (v[k], v[k + 1])
                xs.append(px)
                ys.append(py)
            cx, cy = (cx + v[n - 2], cy + v[n - 1]) if rel else (v[n - 2], v[n - 1])
        elif c == "A":
            v = take(7)
            if v is None:
                break
            cx, cy = (cx + v[5], cy + v[6]) if rel else (v[5], v[6])
        else:
            i += 1
        if c in ("M", "T"):
            cmd = "l" if rel else "L"
    if not xs or not ys:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def is_fullscreen(path_data, vw, vh):
    """整块画布的占位矩形（clip-path 或 mask 底），不是图形本体。"""
    box = path_bbox(path_data)
    if not box:
        return False
    x0, y0, x1, y1 = box
    return (x1 - x0) >= 0.95 * vw and (y1 - y0) >= 0.95 * vh


ATTR = re.compile(r'A: \S+?:(\w+)\(0x[0-9a-f]+\)=("[^"]*"|[\w#.\-]+)')
ELEM = re.compile(r"E: ([\w-]+)")


def parse_vector(path):
    """把 aapt2 的 xmltree 输出还原成 vector 节点（保留原始参数，颜色交由 tint）。

    源文件的常见结构：<vector>[<group>[<clip-path>..</clip-path><path>..</path>]</group>]</vector>
    - clip-path 非全屏：它才是图形本体，同组 path 只是被它裁剪的填充底，丢弃
    - clip-path 全屏：无效果，忽略，同组 path 是图形本体
    - path 全屏：mask 底，丢弃
    """
    txt = dump_xmltree(path)
    if "E: vector" not in txt:
        return None
    vw = float(re.search(r"viewportWidth\(0x01010402\)=([\d.]+)", txt).group(1))
    vh = float(re.search(r"viewportHeight\(0x01010403\)=([\d.]+)", txt).group(1))
    width = re.search(r"width\(0x01010159\)=([\d.]+)dp", txt)
    height = re.search(r"height\(0x01010155\)=([\d.]+)dp", txt)

    # 按缩进建树，属性归到最近的 path / clip-path 节点
    shapes = collect_shapes(txt, vw, vh)
    if not shapes:
        return None
    return {
        "vw": vw, "vh": vh,
        "width": width.group(1) if width else "24",
        "height": height.group(1) if height else "24",
        "paths": shapes,
    }


def collect_shapes(txt, vw, vh):
    """按 group 聚合：clip-path 非全屏则它是形状、同组 path 丢弃；否则相反。"""
    root = {"clip": [], "path": []}
    groups = [root]
    active = root
    stack = []
    current = None
    for line in txt.splitlines():
        em = ELEM.search(line)
        if em:
            indent = len(line) - len(line.lstrip())
            tag = em.group(1)
            while stack and stack[-1][0] >= indent:
                stack.pop()
            stack.append((indent, tag))
            if tag == "group":
                active = {"clip": [], "path": []}
                groups.append(active)
            elif tag == "vector":
                active = root
            current = {"tag": tag} if tag in ("path", "clip-path") else None
            if current:
                active["clip" if tag == "clip-path" else "path"].append(current)
            continue
        am = ATTR.search(line)
        if am and current is not None:
            name, val = am.group(1), am.group(2).strip('"')
            if name not in ("width", "height", "viewportWidth", "viewportHeight"):
                current[name] = val

    shapes = []
    for group in groups:
        clips = [
            c for c in group["clip"]
            if c.get("pathData") and not is_fullscreen(c["pathData"], vw, vh)
        ]
        if clips:
            for c in clips:
                shapes.append({
                    "pathData": c["pathData"], "source": "vector:clip",
                    "fill": "#FFFFFFFF", "stroke": None, "strokeWidth": None,
                    "cap": None, "join": None, "fillType": None,
                })
            continue
        for p in group["path"]:
            pd = p.get("pathData")
            if not pd or is_fullscreen(pd, vw, vh):
                continue
            shapes.append({
                "pathData": pd, "source": "vector",
                "fill": p.get("fillColor"), "stroke": p.get("strokeColor"),
                "strokeWidth": p.get("strokeWidth"), "cap": p.get("strokeLineCap"),
                "join": p.get("strokeLineJoin"), "fillType": p.get("fillType"),
            })
    return shapes


def png_to_vector(name, path):
    """PNG(LA) -> 轮廓追踪 vector。"""
    from PIL import Image

    with zipfile.ZipFile(APK) as z:
        data = z.read("res/" + path)
    local = f"/tmp/ks_preview/{name}.png"
    open(local, "wb").write(data)
    im = Image.open(local).convert("LA")
    w, h = im.size

    scale = 6
    big = im.resize((w * scale, h * scale), Image.LANCZOS)
    a = big.split()[1].load()
    bw, bh = big.size
    mask = [[1 if a[x, y] >= 128 else 0 for x in range(bw)] for y in range(bh)]
    loops = trace_loops(mask, bw, bh)
    subs = []
    for loop in loops:
        pts = rdp(loop, 0.5 * scale)
        if len(pts) < 3:
            continue
        subs.append("M" + "L".join(f"{x / scale:.1f},{y / scale:.1f}" for x, y in pts) + "Z")
    if not subs:
        return None
    return {
        "vw": w, "vh": h, "width": "24", "height": "24",
        "paths": [{"pathData": "".join(subs), "source": "png", "fill": "#FFFFFFFF",
                   "stroke": None, "strokeWidth": None, "cap": None, "join": None,
                   "fillType": "1"}],
    }


def trace_loops(mask, w, h):
    edges = {}

    def add(p, q):
        edges.setdefault(p, []).append(q)

    for y in range(h):
        row = mask[y]
        for x in range(w):
            if not row[x]:
                continue
            if y == 0 or not mask[y - 1][x]:
                add((x, y), (x + 1, y))
            if x + 1 >= w or not row[x + 1]:
                add((x + 1, y), (x + 1, y + 1))
            if y + 1 >= h or not mask[y + 1][x]:
                add((x + 1, y + 1), (x, y + 1))
            if x == 0 or not mask[y][x - 1]:
                add((x, y + 1), (x, y))

    loops, used = [], set()
    for start in list(edges):
        for q in edges[start]:
            if (start, q) in used:
                continue
            loop = [start]
            cur, nxt = start, q
            while True:
                used.add((cur, nxt))
                loop.append(nxt)
                if nxt == start:
                    break
                cands = [p for p in edges.get(nxt, []) if (nxt, p) not in used]
                if not cands:
                    break
                ix, iy = cur
                jx, jy = nxt
                vx, vy = jx - ix, jy - iy
                best, best_score = None, None
                for p in cands:
                    ux, uy = p[0] - jx, p[1] - jy
                    cross = vx * uy - vy * ux
                    dot = vx * ux + vy * uy
                    score = (0 if cross > 0 else 1 if cross < 0 else 2, -dot)
                    if best_score is None or score < best_score:
                        best, best_score = p, score
                cur, nxt = nxt, best
            if len(loop) >= 4:
                loops.append(loop)
    # 轮廓首尾重复，去掉尾点
    return [l[:-1] if l[0] == l[-1] else l for l in loops]


def rdp(points, eps):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5
        worst, wi = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            if d > worst:
                worst, wi = d, i
        if worst > eps:
            keep[wi] = True
            stack.append((a, wi))
            stack.append((wi, b))
    return [p for p, k in zip(points, keep) if k]


def build_xml(spec):
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{spec["width"]}dp"',
        f'    android:height="{spec["height"]}dp"',
        f'    android:viewportWidth="{fmt_num(spec["vw"])}"',
        f'    android:viewportHeight="{fmt_num(spec["vh"])}">',
    ]
    for p in spec["paths"]:
        lines.append("    <path")
        lines.append(f'        android:pathData="{xml_escape(p["pathData"])}"')
        fill = p.get("fill")
        stroke = p.get("stroke")
        has_stroke = stroke and stroke not in ("#00000000",)
        has_fill = fill and fill not in ("#00000000",)
        lines.append(f'        android:fillColor="{white_or_transparent(fill, has_fill)}"')
        if has_stroke:
            lines.append('        android:strokeColor="@android:color/white"')
            lines.append(f'        android:strokeWidth="{p.get("strokeWidth") or 2}"')
            cap = p.get("cap")
            join = p.get("join")
            if cap:
                lines.append(f'        android:strokeLineCap="{CAP.get(int(cap), "round")}"')
            if join:
                lines.append(f'        android:strokeLineJoin="{JOIN.get(int(join), "round")}"')
        ft = p.get("fillType")
        if ft and int(ft) == 1:
            lines.append('        android:fillType="evenOdd"')
        lines.append("    />")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def white_or_transparent(color, has):
    if color and color.startswith("#") and len(color) == 9 and color[1:3] != "00":
        return "@android:color/white"
    return "#00000000"


def fmt_num(v):
    f = float(v)
    return str(int(f)) if f.is_integer() else str(f)


def xml_escape(v):
    return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def preview(specs):
    cards = []
    for name, spec in specs.items():
        paths = []
        for p in spec["paths"]:
            # @android:color/white 是 Android 侧引用，浏览器不识别，预览时映射成 #fff
            fill = "#fff" if white_or_transparent(p.get("fill"), True) != "#00000000" else "none"
            stroke = "#fff" if p.get("stroke") and p["stroke"] != "#00000000" else "none"
            sw = p.get("strokeWidth") or 0
            rule = ' fill-rule="evenodd"' if p.get("fillType") == "1" else ""
            paths.append(
                f'<path d="{xml_escape(p["pathData"])}" fill="{fill}" stroke="{stroke}"'
                f' stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"{rule}/>'
            )
        cards.append(
            f'<div class="card"><div class="ic" style="background:#222">'
            f'<svg viewBox="0 0 {fmt_num(spec["vw"])} {fmt_num(spec["vh"])}" width="64" height="64">'
            f'{"".join(paths)}</svg></div><span>{name}</span></div>'
        )
    return (
        "<html><meta charset='utf-8'><style>"
        "body{font-family:sans-serif;background:#fff;display:flex;flex-wrap:wrap;gap:10px;padding:12px}"
        ".card{width:104px;text-align:center;font-size:10px;color:#333}"
        ".ic{display:flex;align-items:center;justify-content:center;height:80px;border-radius:8px}"
        "</style><h3>图标源 -> 项目 vector（深底显示）</h3>" + "".join(cards) + "</html>"
    )


def main():
    wanted = sys.argv[1:] or [k for k, v in ICONS.items() if v]
    idx = resource_index()
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    os.makedirs("/tmp/ks_preview", exist_ok=True)
    specs, failed = {}, []
    for name in wanted:
        ks = ICONS.get(name, name)
        if ks is None:
            print(f"[跳过] {name}（无合适图标，保留现有版本）")
            continue
        entries = idx.get(ks)
        if not entries:
            failed.append(name)
            print(f"[失败] {name} <- {ks}: 资源表未找到")
            continue
        xml_entry = next((e for e in entries if e[1] == "XML"), None)
        try:
            if xml_entry:
                spec = parse_vector(xml_entry[0])
                kind = "vector"
            else:
                png_entry = next((e for e in entries if e[1] == "PNG"), None)
                if not png_entry:
                    raise RuntimeError("既不是 XML 也不是 PNG")
                spec = png_to_vector(name, png_entry[0])
                kind = "png"
        except Exception as exc:  # noqa: BLE001
            failed.append(name)
            print(f"[失败] {name} <- {ks}: {exc}")
            continue
        if not spec:
            failed.append(name)
            print(f"[失败] {name} <- {ks}: 没有可用图形")
            continue
        with open(os.path.join(DRAWABLE_DIR, f"{name}.xml"), "w", encoding="utf-8") as f:
            f.write(build_xml(spec))
        specs[name] = spec
        print(f"[OK] {name} <- {ks} ({kind}, viewport {fmt_num(spec['vw'])}x{fmt_num(spec['vh'])}, {len(spec['paths'])} path)")
    if specs:
        open(PREVIEW_PATH, "w").write(preview(specs))
        print(f"预览: {PREVIEW_PATH}")
    if failed:
        print(f"未生成: {', '.join(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
