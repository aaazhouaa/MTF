#!/usr/bin/env python3
"""从 IconPark 官方 npm 包生成 Android VectorDrawable。

图标源：@icon-park/svg 1.4.2（ByteDance/IconPark，与 iconpark.oceanengine.com 官网同源，Apache-2.0）。
用法：
    python3 tools/fetch_iconpark.py            # 生成全部
    python3 tools/fetch_iconpark.py ic_back    # 只生成指定名字
"""

import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

PKG_VERSION = "1.4.2"
BASE_URL = f"https://cdn.jsdelivr.net/npm/@icon-park/svg@{PKG_VERSION}/es/icons"
DRAWABLE_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/res/drawable",
)
PREVIEW_PATH = "/tmp/iconpark_preview.html"

# drawable 名 -> IconPark 图标名
ICONS = {
    # 覆盖现有自绘图标
    "ic_back": "left",
    "ic_favorite_outline": "star",
    "ic_search_new": "search",
    "ic_download": "download",
    "ic_browser": "browser",
    "ic_lock_outline": "lock",
    # 新增
    "ic_arrow_up": "up",
    "ic_arrow_down": "down",
    "ic_arrow_right": "right",
    "ic_send": "send",
    "ic_check": "check",
    "ic_post_edit": "edit",
    "ic_home": "home",
    "ic_list": "list",
    "ic_find": "find",
    "ic_me": "me",
    "ic_people": "people",
    "ic_friends": "peoples",
    "ic_posts": "file-text",
    "ic_points": "currency",
    "ic_forbid": "forbid",
}

SVG_NS = "http://www.w3.org/2000/svg"
# IconPark 的颜色槽位：0 外描边 / 1 外填充 / 2 内描边 / 3 内填充。
# Android 侧统一由 tint 着色，所以 stroke 一律白、fill 一律透明；仅 fill 无 stroke 的
# 元素（如 browser 的三个圆点）保留为白色填充，否则图形会缺失。
COLOR_TOKEN = re.compile(r"__ICONPARK_C(\d)__")
PROP_TOKEN = re.compile(r"props\.(size|strokeWidth|strokeLinecap|strokeLinejoin|colors\[(\d)\])")
STRING_TOKEN = re.compile(r"'((?:[^'\\]|\\.)*)'")


def pascal(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("-"))


def fetch(icon_name: str) -> str:
    url = f"{BASE_URL}/{pascal(icon_name)}.js"
    with urllib.request.urlopen(url, timeout=30) as resp:
        if resp.status != 200:
            raise RuntimeError(f"{url} -> HTTP {resp.status}")
        return resp.read().decode("utf-8")


def unfold(js: str) -> str:
    """把 ES 模块里 `'字面量' + props.x + '字面量'` 折叠成一段 SVG 源码。"""
    body = js.split("return", 1)[1]
    out = []
    token = re.compile(r"'((?:[^'\\]|\\.)*)'|props\.[A-Za-z0-9_\[\]]+|\+")
    for m in token.finditer(body):
        text = m.group(1)
        if text is not None:
            out.append(text.replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\"))
            continue
        prop = m.group(0)
        if prop == "+":
            continue
        if prop == "props.size":
            out.append("__ICONPARK_SIZE__")
        elif prop == "props.strokeWidth":
            out.append("__ICONPARK_SW__")
        elif prop == "props.strokeLinecap":
            out.append("round")
        elif prop == "props.strokeLinejoin":
            out.append("round")
        else:
            idx = re.search(r"colors\[(\d)\]", prop).group(1)
            out.append(f"__ICONPARK_C{idx}__")
    return "".join(out)


def clean_svg(svg: str) -> str:
    # size 占位让 width/height 变成空属性，删掉
    svg = re.sub(r'\s(width|height|fill)="__ICONPARK_SIZE__"', "", svg)
    svg = svg.replace('stroke-width="__ICONPARK_SW__"', 'stroke-width="4"')
    svg = svg.replace('__ICONPARK_SIZE__', "")
    return svg


def rect_to_path(el) -> str:
    x = float(el.get("x", 0))
    y = float(el.get("y", 0))
    w = float(el.get("width"))
    h = float(el.get("height"))
    rx = min(float(el.get("rx", 0) or 0), w / 2)
    ry = min(float(el.get("ry", 0) or rx), h / 2)
    right, bottom = x + w, y + h
    if rx <= 0 or ry <= 0:
        return f"M{num(x)},{num(y)}H{num(right)}V{num(bottom)}H{num(x)}Z"
    rxs, rys = num(rx), num(ry)
    return (
        f"M{num(x + rx)},{num(y)}H{num(right - rx)}"
        f"A{rxs},{rys} 0 0,1 {num(right)},{num(y + ry)}"
        f"V{num(bottom - ry)}A{rxs},{rys} 0 0,1 {num(right - rx)},{num(bottom)}"
        f"H{num(x + rx)}A{rxs},{rys} 0 0,1 {num(x)},{num(bottom - ry)}"
        f"V{num(y + ry)}A{rxs},{rys} 0 0,1 {num(x + rx)},{num(y)}Z"
    )


def circle_to_path(el) -> str:
    cx = float(el.get("cx"))
    cy = float(el.get("cy"))
    r = float(el.get("r"))
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0Z"


def num(value: str) -> str:
    f = float(value)
    return str(int(f)) if f.is_integer() else str(f)


def path_node(el) -> dict:
    """把 SVG 图形元素转成 Android path 属性。"""
    stroke = el.get("stroke", "")
    fill = el.get("fill", "")
    has_stroke = bool(stroke) and bool(COLOR_TOKEN.search(stroke))
    has_fill = bool(fill) and bool(COLOR_TOKEN.search(fill))
    node = {
        "pathData": "",
        "fillColor": "#00000000",
    }
    if has_stroke:
        # 描边元素不填充，保证 outline 空心观感
        node["strokeColor"] = "@android:color/white"
        node["strokeWidth"] = el.get("stroke-width", "4")
        node["strokeLineCap"] = el.get("stroke-linecap", "round")
        node["strokeLineJoin"] = el.get("stroke-linejoin", "round")
    elif has_fill:
        # 纯填充元素（如 browser 的三个圆点）没有描边，保留为白色填充
        node["fillColor"] = "@android:color/white"
    else:
        node["strokeColor"] = "#00000000"
    if el.get("fill-rule") == "evenodd" or el.get("clip-rule") == "evenodd":
        node["fillType"] = "evenOdd"
    tag = el.tag.split("}")[-1]
    if tag == "path":
        node["pathData"] = el.get("d", "")
    elif tag == "rect":
        node["pathData"] = rect_to_path(el)
    elif tag == "circle":
        node["pathData"] = circle_to_path(el)
    elif tag == "ellipse":
        raise RuntimeError("ellipse 未支持")
    else:
        raise RuntimeError(f"未支持的 SVG 元素: {tag}")
    return node


def xml_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def build_vector(svg: str) -> tuple[str, list[dict]]:
    root = ET.fromstring(svg)
    view_box = root.get("viewBox", "0 0 48 48").split()
    vw, vh = view_box[2], view_box[3]
    nodes = []
    for el in root.iter():
        if el.tag.split("}")[-1] in ("path", "rect", "circle", "ellipse"):
            nodes.append(path_node(el))
    if not nodes:
        raise RuntimeError("没有解析到图形元素")
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        f'    android:viewportWidth="{vw}"',
        f'    android:viewportHeight="{vh}">',
    ]
    for node in nodes:
        attrs = [f'        android:{k}="{xml_escape(str(v))}"' for k, v in node.items()]
        lines.append("    <path")
        lines.extend(attrs)
        lines.append("        />")
    lines.append("</vector>")
    return "\n".join(lines) + "\n", nodes


def preview_page(entries: dict[str, list[dict]]) -> str:
    cards = []
    for name, nodes in entries.items():
        paths = []
        for node in nodes:
            stroke = node.get("strokeColor", "#00000000")
            fill = node.get("fillColor", "#00000000")
            if stroke == "@android:color/white":
                stroke = "#111"
            elif stroke == "#00000000":
                stroke = "none"
            if fill == "@android:color/white":
                fill = "#111"
            elif fill == "#00000000":
                fill = "none"
            paths.append(
                f'<path d="{xml_escape(node["pathData"])}" fill="{fill}" stroke="{stroke}" '
                f'stroke-width="{num(node.get("strokeWidth", "4"))}" stroke-linecap="round" '
                f'stroke-linejoin="round"/>'
            )
        cards.append(
            f'<div class="card"><svg viewBox="0 0 48 48" width="56" height="56">{"".join(paths)}</svg>'
            f"<span>{name}</span></div>"
        )
    return (
        "<html><meta charset='utf-8'><style>"
        "body{font-family:sans-serif;background:#fff;display:flex;flex-wrap:wrap;gap:12px;padding:16px}"
        ".card{width:96px;text-align:center;font-size:11px;color:#333}"
        "</style><h3>IconPark 1.4.2 · outline</h3>"
        + "".join(cards)
        + "</html>"
    )


def main() -> int:
    wanted = sys.argv[1:] or list(ICONS)
    unknown = [n for n in wanted if n not in ICONS]
    if unknown:
        print(f"未知图标: {unknown}")
        return 1
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    all_nodes: dict[str, list[dict]] = {}
    for name in wanted:
        icon = ICONS[name]
        try:
            svg = clean_svg(unfold(fetch(icon)))
            vector, nodes = build_vector(svg)
        except Exception as exc:  # noqa: BLE001 - 单个图标失败不中断整批
            print(f"[失败] {name} <- {icon}: {exc}")
            return 1
        with open(os.path.join(DRAWABLE_DIR, f"{name}.xml"), "w", encoding="utf-8") as f:
            f.write(vector)
        all_nodes[name] = nodes
        print(f"[OK] {name} <- {icon} ({len(nodes)} path)")
    with open(PREVIEW_PATH, "w", encoding="utf-8") as f:
        f.write(preview_page(all_nodes))
    print(f"预览: {PREVIEW_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
