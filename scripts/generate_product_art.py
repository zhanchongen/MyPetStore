#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MyPetStore 商品配图生成器（纯标准库，可重复运行）
=====================================================

为 40 个商品 SKU 生成统一的扁平插画 SVG（800x800），覆盖
`static/images/pets/` 与 `static/images/supplies/` 下同名占位文件。
DB 中保存的是这些文件路径，因此文件名必须保持不变。

设计规则
--------
配色：
  * 背景渐变按「物种家族」成组：犬 DOG 暖琥珀 / 猫 CAT 紫罗兰 / 鸟 BIRD 薄荷 /
    鱼 FISH 天蓝 / 爬宠 REP 青柠；用品沿用其前缀家族的同套配色。
  * 家族色（main/deep/light）只用于「语境元素」：背景装饰圆、底板细环、项圈、
    铃铛、气泡、灯具光晕等。动物本体使用自然毛色/羽色/鳞色（否则犬会变成紫色），
    这样既保留分组感，又不让插画失真。
  * 深色只用于五官与投影；投影 = deep 色 + 0.10~0.12 透明度的一层椭圆，
    模拟「极淡投影」，不使用 filter（避免部分渲染器/CSP 问题）。

造型：
  * 统一骨架：地面投影 -> 尾巴 -> 躯干 -> 胸腹浅色 -> 爪 -> 耳 -> 头 -> 斑纹 ->
    五官 -> 项圈。每张图主体包围盒约 440~520px（画布的 55%~65%）。
  * 中轴固定在 x=400：所有成对部件（眼、耳、爪、鳍、翅）都用 s=+1/-1 镜像生成，
    保证左右完全对称，不靠肉眼调。
  * 每个品种都有写死在函数首行的「标志性特征」注释，4 个品种靠耳型、脸型、
    斑纹、体型区分，而不是只换颜色。用品则画出可辨认的物件剪影 + 1~2 个语境细节。

工程约束：
  * 不产生 <text>/<image>/<style>/base64/外链；所有表现为 fill/stroke 直接写在元素上。
  * 不使用 transform，所有坐标都是绝对坐标，方便脚本用包围盒做几何自检。
  * 幂等：不读时间、不用 random 模块；斑点/卵石等「随机感」由 _Rng（xorshift32）
    以 SKU 字符串为种子生成，跨平台、跨 Python 版本结果一致。

用法：
    python scripts/generate_product_art.py            # 生成/覆盖 40 个 SVG
    python scripts/generate_product_art.py --check    # 只校验现有文件是否与期望一致
    python scripts/generate_product_art.py --verbose  # 生成并打印每张图的包围盒
"""

import math
import os
import re
import sys
import xml.etree.ElementTree as ET

# --------------------------------------------------------------------------
# 路径与画布常量
# --------------------------------------------------------------------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMAGES = os.path.join(
    ROOT, "MyPetStore-SSM", "src", "main", "resources", "static", "images"
)
SVG_NS = "http://www.w3.org/2000/svg"

W = H = 800          # 画布尺寸
CX = CY = 400        # 画布中心 / 镜像中轴
PLATE_R = 300        # 白色圆形底板半径

# 通用墨色：暖灰，比纯黑柔和，保证「扁平插画」而非矢量图标感
INK = "#40382f"
NOSE = "#4b3f36"
WHITE = "#ffffff"
BLUSH = "#fb7185"

# --------------------------------------------------------------------------
# 家族配色（与需求文档逐字对应）
# --------------------------------------------------------------------------
FAMILIES = {
    "DOG": {"bg": ("#fff7ed", "#ffedd5"), "main": "#f59e0b", "deep": "#d97706", "light": "#fde68a"},
    "CAT": {"bg": ("#f5f3ff", "#ede9fe"), "main": "#a78bfa", "deep": "#7c3aed", "light": "#ede9fe"},
    "BIRD": {"bg": ("#ecfdf5", "#d1fae5"), "main": "#34d399", "deep": "#059669", "light": "#d1fae5"},
    "FISH": {"bg": ("#f0f9ff", "#e0f2fe"), "main": "#38bdf8", "deep": "#0284c7", "light": "#e0f2fe"},
    "REP": {"bg": ("#f7fee7", "#ecfccb"), "main": "#84cc16", "deep": "#4d7c0f", "light": "#ecfccb"},
}


# --------------------------------------------------------------------------
# 数值 / 图元工具
# --------------------------------------------------------------------------
def fnum(value):
    """统一数值格式：最多 1 位小数、去掉无意义的 .0，保证输出稳定、体积可控。"""
    text = "%.1f" % float(value)
    if text.endswith(".0"):
        text = text[:-2]
    if text == "-0":
        text = "0"
    return text


def _el(tag, **attrs):
    """生成一个自闭合元素；属性名中的下划线自动转连字符（stroke_width -> stroke-width）。"""
    out = ["<", tag]
    for key, value in attrs.items():
        if value is None:
            continue
        attr = key.replace("_", "-")
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            value = fnum(value)
        out.append(' %s="%s"' % (attr, value))
    out.append("/>")
    return "".join(out)


class Art:
    """收集插画图元。全部为绝对坐标，不使用 transform / style。"""

    def __init__(self):
        self.els = []

    def circle(self, cx, cy, r, fill, op=None, stroke=None, sw=4, dash=None):
        self.els.append(_el("circle", cx=cx, cy=cy, r=r, fill=fill, opacity=op,
                            stroke=stroke, stroke_width=sw if stroke else None,
                            stroke_dasharray=dash))

    def ellipse(self, cx, cy, rx, ry, fill, op=None, stroke=None, sw=4, dash=None):
        self.els.append(_el("ellipse", cx=cx, cy=cy, rx=rx, ry=ry, fill=fill,
                            opacity=op, stroke=stroke,
                            stroke_width=sw if stroke else None,
                            stroke_dasharray=dash))

    def rect(self, x, y, w, h, rx=0, fill=None, op=None, stroke=None, sw=4):
        self.els.append(_el("rect", x=x, y=y, width=w, height=h, rx=rx if rx else None,
                            fill=fill, opacity=op, stroke=stroke,
                            stroke_width=sw if stroke else None))

    def path(self, d, fill="none", stroke=None, sw=4, op=None, cap="round",
             join="round", dash=None):
        self.els.append(_el("path", d=d, fill=fill, opacity=op, stroke=stroke,
                            stroke_width=sw if stroke else None,
                            stroke_linecap=cap if stroke else None,
                            stroke_linejoin=join if stroke else None,
                            stroke_dasharray=dash))

    def line(self, x1, y1, x2, y2, stroke, sw=4, op=None):
        self.els.append(_el("line", x1=x1, y1=y1, x2=x2, y2=y2, stroke=stroke,
                            stroke_width=sw, stroke_linecap="round", opacity=op))

    def poly(self, points, fill, op=None, stroke=None, sw=4):
        pts = " ".join("%s,%s" % (fnum(x), fnum(y)) for x, y in points)
        self.els.append(_el("polygon", points=pts, fill=fill, opacity=op,
                            stroke=stroke, stroke_width=sw if stroke else None,
                            stroke_linejoin="round" if stroke else None))


class _Rng:
    """确定性伪随机（xorshift32）。种子由 SKU 字符串派生，保证脚本幂等。"""

    def __init__(self, seed_text):
        seed = 0x9E3779B9
        for ch in seed_text:
            seed = (seed * 31 + ord(ch)) & 0xFFFFFFFF
        self.state = seed or 0x9E3779B9

    def next(self):
        x = self.state
        x ^= (x << 13) & 0xFFFFFFFF
        x ^= x >> 17
        x ^= (x << 5) & 0xFFFFFFFF
        self.state = x & 0xFFFFFFFF
        return self.state

    def rand(self, lo, hi):
        return lo + (hi - lo) * (self.next() / 0xFFFFFFFF)

    def pick(self, seq):
        return seq[self.next() % len(seq)]


def smooth_d(points, closed=False):
    """折线 -> Catmull-Rom 平滑三次贝塞尔路径（用于蛇身、绳、枝条等有机曲线）。"""
    def at(index):
        if closed:
            return points[index % len(points)]
        return points[max(0, min(len(points) - 1, index))]

    d = "M %s %s" % (fnum(points[0][0]), fnum(points[0][1]))
    rounds = len(points) if closed else len(points) - 1
    for i in range(rounds):
        p0, p1 = at(i - 1), at(i)
        p2, p3 = at(i + 1), at(i + 2)
        c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
        d += " C %s %s %s %s %s %s" % (
            fnum(c1[0]), fnum(c1[1]), fnum(c2[0]), fnum(c2[1]),
            fnum(p2[0]), fnum(p2[1]),
        )
    if closed:
        d += " Z"
    return d


# --------------------------------------------------------------------------
# 哺乳类通用骨架（犬 / 猫共用，保证同家族排版一致）
# --------------------------------------------------------------------------
def _ground(a, deep, cy=650, rx=150, ry=16):
    """地面投影：深色 + 低透明度，等效极淡投影。

    底板是半径 300 的圆，投影椭圆必须落在圆内：随 cy 下移，允许的半宽
    sqrt(289^2 - dy^2) 会迅速变小，因此这里自动收窄，避免阴影漏出白底圆。
    """
    limit = math.sqrt(max(0.0, 289.0 ** 2 - (cy - 400.0) ** 2)) - 6
    a.ellipse(400, cy, max(60.0, min(rx, limit)), ry, deep, op=0.11)


def _torso(a, coat, cream, rx=152, ry=100, cy=570, paw_y=646,
           paw_dx=58, paw_rx=44, paw_ry=26, paw_fill=None, chest=True,
           chest_dy=22, chest_scale=0.54, chest_ry_scale=0.72):
    """躯干 + 胸腹浅色块 + 两只前爪。"""
    a.ellipse(400, cy, rx, ry, coat)
    if chest:
        a.ellipse(400, cy + chest_dy, rx * chest_scale, ry * chest_ry_scale, cream)
    paw_fill = paw_fill or cream
    for s in (-1, 1):
        px = 400 + s * paw_dx
        a.ellipse(px, paw_y + 4, paw_rx, paw_ry, "#a8a29e", op=0.16)
        a.ellipse(px, paw_y, paw_rx, paw_ry, paw_fill)
        for t in (-paw_rx * 0.36, paw_rx * 0.36):
            a.path("M %s %s L %s %s" % (fnum(px + t), fnum(paw_y + 8),
                                        fnum(px + t), fnum(paw_y + paw_ry - 4)),
                   stroke="#a8a29e", sw=3.5, op=0.45)


def _collar(a, deep, light, y=486, half=92, h=28):
    """项圈 + 吊牌：家族色点缀，也遮住头/身的接缝。"""
    a.rect(400 - half, y, half * 2, h, rx=h / 2, fill=deep)
    a.circle(400, y + h + 16, 16, light, stroke=deep, sw=5)
    a.circle(400, y + h + 16, 5, deep)


def _dog_face(a, cream, muzzle_w=78, muzzle_h=58, eye_dx=54, eye_r=16,
              eye_color=INK, nose_color=NOSE, blush=True):
    """犬脸：浅色口鼻 + 圆角三角鼻 + 双弧嘴 + 圆眼高光。"""
    a.ellipse(400, 414, muzzle_w, muzzle_h, cream)
    a.path("M 384 384 Q 400 376 416 384 Q 417 401 400 410 Q 383 401 384 384 Z",
           fill=nose_color)
    a.path("M 400 410 L 400 423", stroke=nose_color, sw=5)
    a.path("M 400 423 Q 386 437 372 424", stroke=nose_color, sw=5)
    a.path("M 400 423 Q 414 437 428 424", stroke=nose_color, sw=5)
    for s in (-1, 1):
        a.circle(400 + s * eye_dx, 336, eye_r, eye_color)
        a.circle(400 + s * eye_dx - 5, 330, 5.5, WHITE)
    if blush:
        for s in (-1, 1):
            a.ellipse(400 + s * 106, 406, 22, 13, BLUSH, op=0.30)


def _ear_upright(a, s, fill, inner, tip_y=176, base_y=292,
                 dx_out=152, dx_in=54, dx_tip=98):
    """立耳（犬/猫通用）：外侧宽、内侧窄、圆角耳尖；s=±1 镜像。"""
    X = lambda dx: 400 + s * dx
    a.path(
        "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
            fnum(X(dx_out)), fnum(base_y),
            fnum(X(dx_out - 10)), fnum(base_y - 44),
            fnum(X(dx_tip + 22)), fnum(tip_y + 58),
            fnum(X(dx_tip + 6)), fnum(tip_y),
            fnum(X(dx_tip - 12)), fnum(tip_y + 20),
            fnum(X(dx_tip - 28)), fnum(tip_y + 78),
            fnum(X(dx_in)), fnum(base_y - 6),
        ),
        fill=fill,
    )
    a.path(
        "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
            fnum(X(dx_out - 20)), fnum(base_y - 8),
            fnum(X(dx_out - 28)), fnum(base_y - 48),
            fnum(X(dx_tip + 8)), fnum(tip_y + 62),
            fnum(X(dx_tip + 1)), fnum(tip_y + 26),
            fnum(X(dx_tip - 12)), fnum(tip_y + 46),
            fnum(X(dx_tip - 26)), fnum(tip_y + 94),
            fnum(X(dx_in + 14)), fnum(base_y - 18),
        ),
        fill=inner, op=0.8,
    )


def _ear_floppy(a, s, fill, inner, top=272, bot=470, bulge=190):
    """垂耳（金毛/拉布拉多）：从脸侧挂下的水滴形，s=±1 镜像。"""
    X = lambda dx: 400 + s * dx
    a.path(
        "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
            fnum(X(86)), fnum(top),
            fnum(X(168)), fnum(top + 16),
            fnum(X(180)), fnum(top + 100),
            fnum(X(bulge)), fnum(bot - 18),
            fnum(X(140)), fnum(bot),
            fnum(X(104)), fnum(bot + 12),
            fnum(X(88)), fnum(bot - 18),
            fnum(X(74)), fnum(top + 100),
            fnum(X(86)), fnum(top),
        ),
        fill=fill,
    )
    a.path(
        "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
            fnum(X(100)), fnum(top + 18),
            fnum(X(158)), fnum(top + 32),
            fnum(X(166)), fnum(top + 98),
            fnum(X(174)), fnum(bot - 38),
            fnum(X(134)), fnum(bot - 18),
            fnum(X(110)), fnum(bot - 8),
            fnum(X(100)), fnum(bot - 34),
            fnum(X(90)), fnum(top + 84),
            fnum(X(100)), fnum(top + 18),
        ),
        fill=inner, op=0.65,
    )


def _cat_ears(a, coat, inner, tip_y=190, base_y=296, dx_out=150,
              dx_in=44, dx_tip=100):
    """猫耳：比犬耳更宽更尖，底边贴着圆脸。"""
    X = lambda dx: 400 + dx
    for s in (-1, 1):
        Xs = lambda dx, s=s: 400 + s * dx
        a.path(
            "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
                fnum(Xs(dx_out)), fnum(base_y),
                fnum(Xs(dx_tip + 28)), fnum(tip_y + 66),
                fnum(Xs(dx_tip + 2)), fnum(tip_y),
                fnum(Xs(dx_tip - 18)), fnum(tip_y + 44),
                fnum(Xs(dx_in)), fnum(base_y - 10),
                fnum(Xs(dx_in + 6)), fnum(base_y - 30),
                fnum(Xs(dx_out)), fnum(base_y),
            ),
            fill=coat,
        )
        a.path(
            "M %s %s Q %s %s %s %s Q %s %s %s %s Q %s %s %s %s Z" % (
                fnum(Xs(dx_out - 20)), fnum(base_y - 14),
                fnum(Xs(dx_tip + 10)), fnum(tip_y + 68),
                fnum(Xs(dx_tip - 4)), fnum(tip_y + 22),
                fnum(Xs(dx_tip - 24)), fnum(tip_y + 56),
                fnum(Xs(dx_in + 22)), fnum(base_y - 22),
                fnum(Xs(dx_in + 26)), fnum(base_y - 38),
                fnum(Xs(dx_out - 20)), fnum(base_y - 14),
            ),
            fill=inner, op=0.85,
        )


def _cat_face(a, cream, eye_color="#65a30d", nose_color="#f472b6",
              muzzle_w=74, eye_dx=56, eye_y=340, pupil=True):
    """猫脸：杏仁眼 + 竖瞳/圆瞳 + 小三角鼻 + 六根胡须。"""
    a.ellipse(400, 414, muzzle_w, 46, cream, op=0.95)
    a.path("M 386 398 Q 400 391 414 398 Q 408 412 400 417 Q 392 412 386 398 Z",
           fill=nose_color)
    a.path("M 400 417 L 400 426", stroke=INK, sw=4.5)
    a.path("M 400 426 Q 387 438 374 427", stroke=INK, sw=4.5)
    a.path("M 400 426 Q 413 438 426 427", stroke=INK, sw=4.5)
    for s in (-1, 1):
        a.ellipse(400 + s * eye_dx, eye_y, 21, 16, eye_color)
        if pupil:
            a.ellipse(400 + s * eye_dx, eye_y, 7, 15, INK)
        a.circle(400 + s * eye_dx - 7, eye_y - 7, 5, WHITE)
    for s in (-1, 1):
        X = lambda dx: 400 + s * dx
        a.path("M %s 404 Q %s 392 %s 398" % (fnum(X(112)), fnum(X(168)), fnum(X(196))),
               stroke="#a8a29e", sw=3.5, op=0.85)
        a.path("M %s 416 Q %s 416 %s 424" % (fnum(X(112)), fnum(X(172)), fnum(X(200))),
               stroke="#a8a29e", sw=3.5, op=0.85)
        a.path("M %s 428 Q %s 440 %s 450" % (fnum(X(114)), fnum(X(164)), fnum(X(192))),
               stroke="#a8a29e", sw=3.5, op=0.85)


# ==========================================================================
# 犬 DOG —— 4 个品种
# ==========================================================================
def dog_corgi(a):
    """柯基：大立耳（近乎竖直）+ 橙白双色 + 白色鼻梁与胸腹 + 短腿小尾球。"""
    coat, deep, cream, inner = "#f59e0b", "#c2740b", "#fff7ed", "#fbbf24"
    _ground(a, deep)
    a.circle(566, 594, 30, coat)                         # 尾球（柯基特征）
    _torso(a, coat, cream, 160, 96, 574, 650)
    _ear_upright(a, -1, coat, inner, tip_y=176, dx_out=152, dx_in=50, dx_tip=94)
    _ear_upright(a, 1, coat, inner, tip_y=176, dx_out=152, dx_in=50, dx_tip=94)
    a.circle(400, 362, 136, coat)
    a.path("M 400 244 Q 430 244 434 300 L 438 368 Q 454 398 450 422 "
           "Q 442 464 400 468 Q 358 464 350 422 Q 346 398 362 368 L 366 300 "
           "Q 370 244 400 244 Z", fill=cream)            # 白鼻梁 + 白口罩
    _dog_face(a, cream, muzzle_w=80, muzzle_h=56)
    a.ellipse(400, 388, 30, 20, cream, op=0.9)
    _collar(a, deep, "#fde68a", y=484)


def dog_golden(a):
    """金毛：垂耳 + 浅金毛 + 羽状尾巴 + 丰厚胸毛。"""
    coat, deep, cream, ear = "#fbbf24", "#d97706", "#fffbeb", "#f59e0b"
    _ground(a, deep)
    a.path("M 540 596 Q 620 600 646 552 Q 664 512 638 486 Q 656 458 630 442 "
           "Q 610 470 606 508 Q 598 560 538 566 Z", fill=coat)   # 羽状尾
    a.path("M 628 560 Q 648 520 630 496 Q 640 470 624 458 Q 612 486 610 522 Z",
           fill=cream, op=0.55)
    _torso(a, coat, cream, 156, 100, 570, 648)
    _ear_floppy(a, -1, ear, cream)
    _ear_floppy(a, 1, ear, cream)
    a.circle(400, 362, 136, coat)
    _dog_face(a, cream, muzzle_w=82, muzzle_h=58)
    _collar(a, deep, "#fde68a", y=484)


def dog_labrador(a):
    """拉布拉多：垂耳略短 + 深黄毛 + 方头宽吻 + 粗直尾。"""
    coat, deep, cream, ear = "#d97706", "#92400e", "#fef3c7", "#b45309"
    _ground(a, deep)
    a.path("M 546 600 Q 598 608 628 586 Q 640 574 630 560 Q 596 570 546 566 Z",
           fill=coat)                                 # 粗直尾
    _torso(a, coat, cream, 154, 102, 570, 648)
    _ear_floppy(a, -1, ear, "#fbbf24", top=276, bot=436, bulge=188)
    _ear_floppy(a, 1, ear, "#fbbf24", top=276, bot=436, bulge=188)
    a.circle(400, 362, 138, coat)
    a.ellipse(400, 344, 108, 52, coat)                # 方头感
    _dog_face(a, cream, muzzle_w=86, muzzle_h=62, eye_dx=56)
    _collar(a, deep, "#fde68a", y=484)


def dog_shiba(a):
    """柴犬：小立耳 + 尖脸（颊部外扩成角）+ 红棕毛 + 卷尾 + 眉点。"""
    coat, deep, cream, inner = "#e8843c", "#b45309", "#fff7ed", "#fde68a"
    _ground(a, deep)
    a.path("M 548 598 Q 600 608 620 562 Q 632 518 608 506 Q 590 502 584 528",
           stroke=coat, sw=34)                        # 卷尾
    a.path("M 594 528 Q 574 534 582 556", stroke=coat, sw=30)
    _torso(a, coat, cream, 148, 98, 572, 648)
    _ear_upright(a, -1, coat, inner, tip_y=190, base_y=294, dx_out=140, dx_in=58, dx_tip=98)
    _ear_upright(a, 1, coat, inner, tip_y=190, base_y=294, dx_out=140, dx_in=58, dx_tip=98)
    a.path("M 400 224 Q 478 224 514 284 Q 540 330 526 392 L 538 428 L 468 452 "
           "Q 436 492 400 492 Q 364 492 332 452 L 262 428 L 274 392 "
           "Q 260 330 286 284 Q 322 224 400 224 Z", fill=coat)  # 尖脸轮廓
    a.path("M 400 250 Q 426 250 430 296 L 432 366 Q 444 396 438 418 "
           "Q 430 462 400 466 Q 370 462 362 418 Q 356 396 368 366 L 370 296 "
           "Q 374 250 400 250 Z", fill=cream)            # 白口罩
    a.ellipse(354, 306, 14, 9, cream)                    # 眉点
    a.ellipse(446, 306, 14, 9, cream)
    _dog_face(a, cream, muzzle_w=68, muzzle_h=52, eye_dx=56, eye_r=15)
    _collar(a, deep, "#fde68a", y=486)


# ==========================================================================
# 猫 CAT —— 4 个品种
# ==========================================================================
def _cat_base(a, coat, deep, cream, rx=150, ry=100, cy=570, paw_y=646,
              paw_fill=None, head_r=138):
    _ground(a, deep)
    _torso(a, coat, cream, rx, ry, cy, paw_y, paw_dx=54, paw_rx=42,
           paw_ry=25, paw_fill=paw_fill)


def cat_american(a):
    """美短：圆脸 + 虎斑（额头 M 纹/颊纹/身纹/尾环）+ 银灰底 + 绿眼。"""
    coat, deep, cream = "#9ca3af", "#4b5563", "#f3f4f6"
    _ground(a, deep)
    a.path("M 540 612 Q 612 600 622 540 Q 626 508 600 500", stroke=coat, sw=30)
    a.path("M 558 596 L 592 584", stroke=deep, sw=9)
    a.path("M 594 578 L 628 558", stroke=deep, sw=9)
    _torso(a, coat, cream, 150, 100, 570, 646, paw_dx=54, paw_rx=42, paw_ry=25)
    _cat_ears(a, coat, "#fbcfe8", tip_y=188, base_y=296)
    a.circle(400, 352, 138, coat)
    # 额头 M 形虎斑（美短最醒目的识别点）
    a.path("M 352 300 L 380 248 L 400 286 L 420 248 L 448 300",
           fill="none", stroke=deep, sw=12)
    a.path("M 300 356 L 266 344", stroke=deep, sw=10)
    a.path("M 500 356 L 534 344", stroke=deep, sw=10)
    a.path("M 294 400 L 262 400", stroke=deep, sw=10)
    a.path("M 506 400 L 538 400", stroke=deep, sw=10)
    for i in range(2):
        y = 520 + i * 38
        a.path("M 310 %s Q 350 %s 356 %s" % (fnum(y), fnum(y - 20), fnum(y + 16)),
               stroke=deep, sw=11, op=0.85)
    _cat_face(a, cream, eye_color="#65a30d")
    _collar(a, "#7c3aed", "#ede9fe", y=486)


def cat_british(a):
    """英短：圆胖脸（宽椭圆 + 双颊肉）+ 小耳 + 蓝灰毛 + 琥珀圆眼 + 粗短尾。"""
    coat, deep, cream, inner = "#94a3b8", "#475569", "#e2e8f0", "#f9a8d4"
    _ground(a, deep)
    a.path("M 540 612 Q 606 604 600 556 Q 594 526 566 530", stroke=coat, sw=38)
    a.ellipse(400, 574, 168, 102, coat)                 # 圆胖躯干
    a.ellipse(400, 596, 92, 72, cream)
    for s in (-1, 1):
        a.ellipse(400 + s * 52, 648, 44, 27, cream)
    _cat_ears(a, coat, inner, tip_y=214, base_y=300, dx_out=136, dx_in=52, dx_tip=96)
    a.ellipse(400, 350, 156, 144, coat)                 # 圆胖脸
    for s in (-1, 1):
        a.ellipse(400 + s * 108, 392, 44, 38, coat)     # 双颊肉
    a.ellipse(400, 410, 92, 56, cream, op=0.5)
    _cat_face(a, cream, eye_color="#f59e0b", muzzle_w=80, eye_dx=58, eye_y=334)
    _collar(a, "#7c3aed", "#ede9fe", y=490)


def cat_ragdoll(a):
    """布偶：长毛（颊部/胸口绒毛）+ 浅米色身体 + 深色耳/尾尖 + 大蓝眼。"""
    coat, point, cream = "#f4efe6", "#a8a199", "#fffdf8"
    _ground(a, point, rx=176)
    a.path("M 534 618 Q 596 616 612 558 Q 620 516 598 500",
           stroke=coat, sw=44)                          # 毛茸尾
    a.path("M 598 500 Q 612 520 608 538", stroke=point, sw=42)
    _torso(a, coat, cream, 156, 104, 570, 646)
    # 胸口长毛（三簇）
    for dx in (-46, 0, 46):
        a.path("M %s 526 Q %s 588 %s 626 Q %s 588 %s 526 Z" % (
            fnum(400 + dx - 22), fnum(400 + dx - 10), fnum(400 + dx),
            fnum(400 + dx + 10), fnum(400 + dx + 22)), fill=cream)
    _cat_ears(a, point, "#fbcfe8", tip_y=192, base_y=300)
    for s in (-1, 1):                                    # 耳尖绒毛
        X = lambda dx: 400 + s * dx
        a.path("M %s 208 L %s 172 L %s 214 Z" % (fnum(X(86)), fnum(X(100)), fnum(X(112))), fill=coat)
        a.path("M %s 214 L %s 184 L %s 220 Z" % (fnum(X(112)), fnum(X(124)), fnum(X(130))), fill=coat)
    a.circle(400, 352, 138, coat)
    for s in (-1, 1):                                    # 脸颊长毛（三簇，向外）
        X = lambda dx: 400 + s * dx
        a.path("M %s 396 Q %s 420 %s 468 Q %s 448 %s 424 Z" % (
            fnum(X(114)), fnum(X(164)), fnum(X(188)),
            fnum(X(150)), fnum(X(106))), fill=coat)
        a.path("M %s 452 Q %s 478 %s 530 Q %s 500 %s 470 Z" % (
            fnum(X(100)), fnum(X(150)), fnum(X(166)),
            fnum(X(128)), fnum(X(92))), fill=coat)
        a.path("M %s 506 Q %s 530 %s 578 Q %s 552 %s 524 Z" % (
            fnum(X(88)), fnum(X(132)), fnum(X(142)),
            fnum(X(110)), fnum(X(78))), fill=cream, op=0.85)
    _cat_face(a, cream, eye_color="#3b82f6", nose_color="#e7b7c0", muzzle_w=80, eye_dx=58)
    _collar(a, "#7c3aed", "#ede9fe", y=492)


def cat_siamese(a):
    """暹罗：修长身 + 大三角耳 + 深色重点色（面罩/耳/爪/尾）+ 蓝眼。"""
    coat, point, cream = "#fef3c7", "#44403c", "#fffbeb"
    _ground(a, point, rx=146)
    a.path("M 536 604 Q 600 612 612 562 Q 618 528 596 516",
           stroke=coat, sw=24)
    a.path("M 604 546 Q 614 530 604 518", stroke=point, sw=26)
    a.ellipse(400, 584, 118, 108, coat)                 # 修长躯干
    a.ellipse(400, 596, 64, 78, cream)
    for s in (-1, 1):
        a.ellipse(400 + s * 42, 666, 34, 22, point)     # 深色爪
    _cat_ears(a, point, "#a8a29e", tip_y=186, base_y=302, dx_out=158, dx_in=48, dx_tip=104)
    a.circle(400, 356, 130, coat)
    a.ellipse(400, 372, 98, 80, point)                  # 深色面罩
    a.ellipse(400, 360, 62, 44, point)
    a.path("M 400 250 Q 424 250 426 292 L 426 358 Q 438 388 432 410 "
           "Q 424 452 400 456 Q 376 452 368 410 Q 362 388 374 358 L 374 292 "
           "Q 376 250 400 250 Z", fill=point, op=0.45)
    _cat_face(a, cream, eye_color="#38bdf8", muzzle_w=64, eye_dx=52, eye_y=336)
    _collar(a, "#7c3aed", "#ede9fe", y=492, half=80)


# ==========================================================================
# 鸟 BIRD —— 4 个品种（统一：身体竖椭圆 + 头圆 + 三角喙 + 两层翅膀 + 细腿）
# ==========================================================================
def _bird_legs(a, foot_y=660, color="#f59e0b"):
    """两条鸟腿 + 前后三趾，站在投影上。"""
    for dx in (-22, 18):
        top_x, bot_x = 400 + dx, 400 + dx - 4
        a.line(top_x, 596, bot_x, foot_y, stroke=color, sw=10)
        a.path("M %s %s L %s %s" % (fnum(bot_x), fnum(foot_y),
                                    fnum(bot_x - 30), fnum(foot_y + 4)),
               stroke=color, sw=8)
        a.path("M %s %s L %s %s" % (fnum(bot_x), fnum(foot_y),
                                    fnum(bot_x + 20), fnum(foot_y + 10)),
               stroke=color, sw=8)


def bird_budgie(a):
    """虎皮鹦鹉：体型小 + 黄头绿身 + 黑色鳞状条纹 + 蓝尾 + 蓝颊斑。"""
    dark = "#1f2937"
    _ground(a, "#059669", cy=672, rx=150)
    a.poly([(348, 560), (286, 626), (332, 646), (382, 604)], fill="#38bdf8")   # 长尾
    a.poly([(358, 572), (316, 620), (346, 632), (380, 602)], fill="#7dd3fc")
    a.ellipse(412, 470, 104, 126, "#4ade80")
    a.ellipse(432, 500, 62, 84, "#86efac")
    a.path("M 330 430 Q 296 508 338 572 Q 378 602 404 560 Q 358 520 366 446 Z",
           fill="#22c55e")
    for i in range(3):                                   # 翅膀鳞纹
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(338 + i * 10), fnum(470 + i * 30),
            fnum(360 + i * 10), fnum(510 + i * 26),
            fnum(384 + i * 6), fnum(540 + i * 18)),
            stroke=dark, sw=7, op=0.75)
    a.circle(438, 326, 82, "#fde047")                    # 黄头
    for i in range(3):                                   # 头顶黑横纹
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(382 + i * 6), fnum(266 + i * 22),
            fnum(420 + i * 4), fnum(252 + i * 22),
            fnum(462 - i * 2), fnum(272 + i * 22)),
            stroke=dark, sw=8, op=0.85)
    a.circle(468, 352, 18, "#38bdf8", op=0.9)            # 蓝颊斑
    a.circle(468, 352, 5, dark)
    a.poly([(502, 318), (566, 344), (502, 360)], fill="#94a3b8")   # 喙
    a.circle(466, 314, 12, dark)
    a.circle(463, 311, 4.5, WHITE)
    _bird_legs(a, foot_y=652)


def bird_cockatiel(a):
    """玄凤：头顶三根羽冠 + 黄脸 + 橙圆腮 + 灰色身 + 长尾。"""
    gray, face, accent = "#a8a29e", "#fde047", "#fb923c"
    _ground(a, "#059669", cy=672, rx=152)
    a.path("M 330 570 Q 292 644 288 668 Q 324 662 350 632 Q 372 606 372 588 Z",
           fill=gray)                                     # 长尾
    a.path("M 340 584 Q 320 626 310 652 Q 334 640 350 616 Z", fill="#d6d3d1")
    a.ellipse(406, 480, 122, 136, gray)
    a.ellipse(428, 508, 74, 100, "#d6d3d1")
    a.path("M 318 436 Q 282 514 326 578 Q 372 612 400 566 Q 350 524 358 452 Z",
           fill="#78716c")
    a.path("M 340 474 Q 320 520 350 554", stroke="#d6d3d1", sw=12, op=0.8)
    a.circle(424, 334, 92, gray)
    a.ellipse(470, 364, 64, 52, face)                    # 黄脸
    a.circle(474, 372, 24, accent)                       # 橙腮
    # 三根羽冠（头冠是玄凤最明显的识别点）
    a.path("M 424 254 Q 412 190 436 178 Q 448 214 448 252 Z", fill=face)
    a.path("M 452 260 Q 466 198 492 196 Q 486 232 470 264 Z", fill=face)
    a.path("M 396 262 Q 366 208 342 214 Q 366 240 378 270 Z", fill=face)
    a.poly([(504, 330), (556, 352), (504, 364)], fill="#a8a29e")
    a.circle(456, 322, 11, INK)
    a.circle(453, 319, 4, WHITE)
    _bird_legs(a, foot_y=654)


def bird_finch(a):
    """文鸟（斑胸草雀）：白身 + 红喙 + 黑泪痕 + 黑白横斑翅 + 橙颊。"""
    white, dark, red = "#f8fafc", "#44403c", "#ef4444"
    _ground(a, "#059669", cy=674, rx=148)
    a.poly([(352, 566), (306, 640), (348, 654), (386, 606)], fill="#cbd5e1")
    a.ellipse(422, 476, 112, 134, "#f1f5f9", stroke="#cbd5e1", sw=3)
    a.ellipse(436, 506, 68, 96, "#e8edf3")
    a.path("M 332 438 Q 300 516 340 578 Q 380 606 404 566 Q 358 522 366 452 Z",
           fill="#e2e8f0", stroke="#cbd5e1", sw=3)
    for i in range(4):                                   # 黑白横斑翅
        y = 490 + i * 20
        a.line(322, y, 386, y - 5, stroke=dark, sw=7, op=0.85)
    a.circle(446, 330, 84, white, stroke="#e2e8f0", sw=2)
    a.ellipse(438, 372, 30, 22, "#fb923c", op=0.85)      # 橙颊
    a.path("M 476 330 Q 488 360 480 392", stroke=dark, sw=7)   # 黑泪痕
    a.poly([(516, 322), (574, 346), (516, 358)], fill=red)     # 红喙
    a.circle(466, 318, 12, dark)
    a.circle(463, 315, 4.5, WHITE)
    _bird_legs(a, foot_y=656, color="#f97316")


def bird_lovebird(a):
    """牡丹鹦鹉：矮胖身 + 桃色脸 + 大红喙 + 绿身蓝腰 + 短尾。"""
    green, peach, red = "#4ade80", "#fdba74", "#f87171"
    _ground(a, "#059669", cy=676, rx=146)
    a.poly([(344, 578), (300, 654), (350, 666), (392, 612)], fill="#22c55e")
    a.ellipse(408, 482, 124, 134, green)
    a.ellipse(426, 512, 76, 96, "#86efac")
    a.path("M 320 444 Q 288 522 330 586 Q 376 618 402 574 Q 352 532 358 458 Z",
           fill="#16a34a")
    a.circle(422, 338, 104, green)
    a.ellipse(468, 372, 72, 62, peach)                   # 桃色脸
    a.ellipse(508, 372, 34, 34, "#fdba74")
    a.poly([(520, 326), (584, 354), (520, 370)], fill=red)
    a.path("M 522 350 Q 508 372 520 388", stroke="#b91c1c", sw=5)
    a.circle(452, 316, 14, "#f8fafc")
    a.circle(452, 316, 9, INK)
    a.circle(449, 313, 3.5, WHITE)
    a.ellipse(400, 596, 44, 26, "#60a5fa")               # 蓝腰
    _bird_legs(a, foot_y=660, color="#f59e0b")


# ==========================================================================
# 鱼 FISH —— 4 个品种（统一：侧视、头朝右、尾鳍在左、上背鳍下臀鳍）
# ==========================================================================
def fish_betta(a):
    """斗鱼：大而飘逸的尾鳍/背鳍/臀鳍 + 深蓝身 + 红粉鳍。"""
    fin, fin2, body, deep = "#f472b6", "#fb7185", "#2563eb", "#1e40af"
    _ground(a, "#0284c7", cy=668, rx=190)
    a.path("M 348 420 Q 300 330 238 296 Q 200 282 204 330 Q 168 344 180 390 "
           "Q 148 414 174 452 Q 154 492 196 508 Q 206 556 258 542 "
           "Q 306 528 350 470 Z", fill=fin)
    a.path("M 340 420 Q 300 356 258 330 Q 232 320 236 356 Q 210 372 220 406 "
           "Q 202 428 224 456 Q 214 486 248 486 Q 286 482 342 452 Z",
           fill=fin2, op=0.85)
    a.path("M 420 330 Q 380 250 396 210 Q 430 238 462 300 Q 500 320 540 344 "
           "L 500 348 Q 456 330 420 330 Z", fill=fin)
    a.path("M 430 512 Q 420 590 462 616 Q 500 600 520 540 Q 480 552 430 512 Z",
           fill=fin)
    a.ellipse(470, 420, 140, 112, body)
    a.ellipse(500, 456, 96, 60, "#3b82f6")
    a.path("M 500 440 Q 540 470 560 500 Q 520 500 492 470 Z",
           fill="#93c5fd", op=0.9)
    a.path("M 540 344 Q 520 400 540 470", stroke=deep, sw=6, op=0.55)
    a.circle(556, 386, 16, WHITE)
    a.circle(558, 386, 9, "#1f2937")
    a.circle(554, 382, 4, WHITE)
    a.path("M 604 430 Q 616 434 608 444", stroke=deep, sw=5)
    for cx, cy, r in ((600, 300, 11), (630, 262, 7), (592, 244, 5)):
        a.circle(cx, cy, r, "#bae6fd", op=0.9)


def fish_goldfish(a):
    """金鱼：圆胖身 + 双叶尾 + 小背鳍 + 鳞片弧线。"""
    orange, deep, light = "#fb923c", "#ea580c", "#fdba74"
    _ground(a, "#0284c7", cy=664, rx=188)
    a.path("M 322 400 Q 250 320 190 300 Q 168 298 176 336 Q 150 356 168 390 "
           "Q 148 420 178 444 Q 160 478 200 484 Q 210 520 252 500 "
           "Q 300 478 338 448 Z", fill=orange)
    a.path("M 314 432 Q 248 470 206 512 Q 190 534 216 536 Q 268 512 322 476 Z",
           fill=orange)
    a.path("M 264 392 Q 218 356 196 338 Q 210 380 226 414 Z", fill=light, op=0.8)
    a.ellipse(448, 424, 152, 122, orange)
    a.ellipse(470, 464, 104, 66, light)
    a.path("M 424 306 Q 466 258 512 306 Q 476 316 424 306 Z", fill=deep)
    a.path("M 430 540 Q 462 588 500 552 Q 470 540 430 540 Z", fill=deep, op=0.9)
    a.path("M 520 350 Q 500 420 520 496", stroke=deep, sw=6, op=0.45)
    a.circle(548, 392, 20, WHITE)
    a.circle(551, 392, 11, "#1f2937")
    a.circle(546, 387, 4.5, WHITE)
    a.path("M 594 430 Q 610 434 600 446", stroke=deep, sw=5)
    for i in range(3):                                   # 鳞片弧线
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(430 + i * 34), fnum(402 + i * 12),
            fnum(444 + i * 34), fnum(424 + i * 12),
            fnum(430 + i * 34), fnum(446 + i * 12)),
            stroke=deep, sw=4, op=0.35)
    for cx, cy, r in ((600, 300, 10), (628, 272, 6), (592, 252, 4)):
        a.circle(cx, cy, r, "#bae6fd", op=0.9)


def fish_guppy(a):
    """孔雀鱼：细身 + 巨大彩色扇尾（紫/蓝/橙三层）+ 小背鳍。"""
    violet, blue, orange = "#a78bfa", "#38bdf8", "#fb923c"
    _ground(a, "#0284c7", cy=662, rx=186)
    a.path("M 388 420 Q 300 300 200 270 Q 160 258 168 310 Q 140 330 152 372 "
           "Q 130 404 160 440 Q 146 480 186 502 Q 196 548 250 526 "
           "Q 306 504 380 462 Z", fill=violet)
    a.path("M 372 420 Q 292 340 226 316 Q 196 310 202 344 Q 178 362 190 394 "
           "Q 174 420 196 448 Q 188 480 224 490 Q 234 514 268 500 "
           "Q 314 478 366 450 Z", fill=blue, op=0.85)
    a.path("M 356 420 Q 300 366 258 350 Q 238 344 242 372 Q 226 388 236 410 "
           "Q 228 432 250 444 Q 248 466 278 462 Q 316 452 352 440 Z",
           fill=orange, op=0.85)
    for i in range(5):                                   # 尾鳍斑点
        a.circle(236 + i * 26, 366 + (i % 2) * 62, 7, "#f8fafc", op=0.65)
    a.ellipse(480, 422, 112, 72, "#cbd5e1")
    a.ellipse(490, 444, 76, 38, "#e2e8f0")
    a.path("M 470 352 Q 502 280 544 268 Q 522 314 542 348 Q 506 350 470 352 Z",
           fill=violet)
    a.path("M 470 490 Q 496 536 520 512 Q 500 502 490 486 Z", fill=orange)
    a.path("M 540 380 Q 520 420 540 460", stroke="#64748b", sw=5, op=0.4)
    a.circle(548, 406, 15, WHITE)
    a.circle(550, 406, 9, "#1f2937")
    a.circle(547, 402, 4, WHITE)
    a.path("M 590 430 Q 604 434 596 444", stroke="#64748b", sw=4)


def fish_neon(a):
    """霓虹灯鱼：细长银身 + 亮蓝荧光带 + 红色下带 + 叉尾。"""
    silver, cyan, red = "#e2e8f0", "#22d3ee", "#ef4444"
    _ground(a, "#0284c7", cy=650, rx=180)
    a.path("M 300 430 L 214 386 L 240 430 L 214 474 Z", fill="#cbd5e1")
    a.path("M 300 430 L 244 404 L 262 430 L 244 456 Z", fill="#94a3b8", op=0.8)
    a.ellipse(470, 430, 185, 66, silver)
    a.ellipse(470, 448, 150, 38, "#f8fafc")
    a.path("M 300 408 Q 450 398 620 410", stroke=cyan, sw=15, op=0.95)
    a.path("M 320 444 Q 460 448 600 440", stroke=red, sw=13, op=0.9)
    a.path("M 470 366 Q 502 336 514 364 Q 492 372 470 366 Z", fill="#94a3b8")
    a.path("M 470 494 Q 496 520 510 498 Q 490 496 470 494 Z", fill="#94a3b8")
    a.path("M 600 396 Q 590 430 602 462", stroke="#94a3b8", sw=5, op=0.5)
    a.circle(626, 414, 13, WHITE)
    a.circle(628, 414, 8, "#1f2937")
    a.circle(625, 411, 3.5, WHITE)
    a.path("M 650 432 Q 660 436 654 444", stroke="#64748b", sw=4)
    for cx, cy, r in ((636, 330, 9), (612, 300, 6), (664, 360, 5)):
        a.circle(cx, cy, r, "#bae6fd", op=0.9)


# ==========================================================================
# 爬宠 REP —— 4 个品种
# ==========================================================================
def rep_bearded(a):
    """鬃狮蜥：楔形抬头 + 下颌尖刺胡须 + 背脊尖刺 + 长尾 + 弯肘四肢。"""
    body, dark, cream, spike = "#d6b98c", "#a97c50", "#f3e3c3", "#b08954"
    _ground(a, "#4d7c0f", cy=662, rx=180)
    a.path("M 286 486 Q 200 486 152 546 Q 162 578 202 574 Q 262 560 316 540 Z",
           fill=body)                                    # 向左侧收细的长尾
    for i in range(3):
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(250 - i * 24), fnum(506 + i * 10),
            fnum(208 - i * 18), fnum(522 + i * 12),
            fnum(180 - i * 8), fnum(552 + i * 6)),
            stroke=dark, sw=4, op=0.35)
    a.ellipse(390, 516, 160, 92, body)                   # 躯干
    a.ellipse(390, 552, 112, 48, cream, op=0.85)
    for i in range(7):                                   # 背脊尖刺
        x = 286 + i * 26
        y = 430 - i * 2
        a.poly([(x, y + 20), (x + 12, y - 14), (x + 24, y + 18)], spike)
    a.path("M 462 486 Q 478 424 514 396 L 574 424 Q 548 498 504 540 Z", fill=body)
    a.ellipse(530, 336, 82, 64, body)                    # 头
    a.path("M 576 302 Q 618 306 636 340 Q 648 368 622 390 Q 596 406 562 398 Z",
           fill=body)                                    # 楔形吻部
    a.path("M 570 382 Q 610 396 638 366 Q 642 390 612 404 Q 576 414 558 396 Z",
           fill=cream, op=0.9)                           # 下颌浅色
    a.ellipse(556, 318, 19, 17, "#fbbf24", op=0.75)
    a.circle(558, 319, 10, INK)                          # 眼
    a.circle(555, 316, 3.5, WHITE)
    a.circle(624, 338, 4, dark)                          # 鼻孔
    a.path("M 636 360 Q 612 384 574 392", stroke=dark, sw=5)
    a.path("M 506 388 L 526 432 L 548 398 L 570 438 L 592 402 L 612 388 "
           "Q 558 404 506 388 Z", fill=dark)             # 胡须
    for i in range(4):                                   # 胡须尖刺
        x = 522 + i * 22
        a.poly([(x, 420), (x + 9, 450), (x + 18, 416)], fill=spike)
    a.path("M 432 552 Q 458 584 444 606", stroke=body, sw=26)   # 前肢
    a.ellipse(438, 612, 30, 13, body)
    for t in (-14, 0, 14):
        a.line(438 + t, 618, 438 + t, 630, stroke=body, sw=7)
    a.path("M 346 568 Q 320 596 330 618", stroke=body, sw=24)   # 后肢
    a.ellipse(332, 624, 26, 12, body)
    for t in (-12, 0, 12):
        a.line(332 + t, 630, 332 + t, 640, stroke=body, sw=6)


def rep_corn(a):
    """玉米蛇：盘曲长条 + 深色鞍状花纹 + 小头吐信。"""
    orange, dark = "#f97316", "#c2410c"
    pts = []
    for i in range(26):
        t = i / 25.0 * (2 * math.pi * 1.55)
        r = 55 + (220 - 55) * (i / 25.0)
        pts.append((400 + r * math.cos(t + 1.2) * 1.05,
                    470 + r * math.sin(t + 1.2) * 0.95))
    d = smooth_d(pts)
    _ground(a, "#4d7c0f", cy=676, rx=176)
    a.path(d, fill="none", stroke=orange, sw=48)
    a.path(d, fill="none", stroke=dark, sw=30, dash="30 66", op=0.9)
    a.ellipse(392, 262, 62, 46, orange)                  # 头
    a.path("M 392 218 Q 424 218 436 250 Q 420 236 392 240 Q 364 236 348 250 "
           "Q 360 218 392 218 Z", fill=dark, op=0.55)
    a.circle(420, 250, 10, INK)
    a.circle(417, 247, 3.5, WHITE)
    a.path("M 392 306 Q 392 330 404 336", stroke=dark, sw=5)
    a.path("M 404 336 L 436 322 M 404 336 L 440 344", stroke="#dc2626", sw=6)
    for i in range(3):                                   # 头部浅色纵纹
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(366 + i * 18), fnum(228), fnum(366 + i * 18),
            fnum(258), fnum(366 + i * 18), fnum(292)),
            stroke="#ffedd5", sw=5, op=0.5)


def rep_crested(a):
    """睫角守宫：大头 + 眼睫毛状棘刺 + 头冠棘 + 卷尾 + 吸盘趾。"""
    body, dark, cream = "#cbbd8e", "#8a7a45", "#f1e8c8"
    _ground(a, "#4d7c0f", cy=678, rx=170)
    a.path("M 486 606 Q 556 642 566 596 Q 572 560 540 556",
           stroke=body, sw=38)                          # 卷尾
    a.ellipse(400, 560, 118, 98, body)
    a.ellipse(400, 582, 78, 60, cream, op=0.8)
    for s in (-1, 1):                                    # 四肢
        a.path("M %s 520 Q %s 570 %s 590" % (
            fnum(400 + s * 88), fnum(400 + s * 140), fnum(400 + s * 150)),
            stroke=body, sw=24)
        a.circle(400 + s * 150, 596, 16, cream)
    a.ellipse(400, 372, 150, 122, body)                  # 大头
    a.ellipse(400, 392, 108, 78, cream, op=0.55)
    for i in range(6):                                   # 头冠棘
        x = 286 + i * 46
        a.poly([(x, 300 - abs(3 - i) * 10), (x + 14, 232 - abs(3 - i) * 18),
                (x + 28, 296 - abs(3 - i) * 10)], fill=dark)
    for s in (-1, 1):
        ex = 400 + s * 66
        a.circle(ex, 356, 40, "#fef9c3")
        a.ellipse(ex, 356, 10, 30, INK)
        a.circle(ex - 12, 344, 8, WHITE)
        for k in range(3):                               # 睫毛棘
            a.path("M %s %s L %s %s" % (
                fnum(ex + s * (30 + k * 4)), fnum(330 + k * 6),
                fnum(ex + s * (78 + k * 10)), fnum(300 + k * 10)),
                stroke=dark, sw=9)
    a.path("M 362 404 Q 400 424 438 404", stroke=dark, sw=5)
    a.path("M 400 412 L 400 418", stroke=dark, sw=4)


def rep_leopard(a):
    """豹纹守宫：黄色身体 + 密集黑斑 + 胡萝卜粗尾 + 微笑脸。"""
    body, spot, cream = "#fde047", "#854d0e", "#fef9c3"
    _ground(a, "#4d7c0f", cy=676, rx=164)
    a.path("M 350 552 Q 326 646 400 666 Q 474 646 450 552 Z", fill=body)  # 粗尾
    a.ellipse(400, 470, 122, 112, body)
    a.ellipse(400, 492, 80, 66, cream, op=0.7)
    for s in (-1, 1):                                    # 四肢
        a.path("M %s 452 Q %s 500 %s 516" % (
            fnum(400 + s * 82), fnum(400 + s * 132), fnum(400 + s * 138)),
            stroke=body, sw=22)
        a.path("M %s 570 Q %s 616 %s 624" % (
            fnum(400 + s * 70), fnum(400 + s * 120), fnum(400 + s * 126)),
            stroke=body, sw=22)
        for t in range(3):
            a.path("M %s %s L %s %s" % (
                fnum(400 + s * 138), fnum(520),
                fnum(400 + s * (150 + t * 6)), fnum(536 + t * 4)),
                stroke=cream, sw=6)
    a.ellipse(400, 302, 110, 88, body)                   # 头
    a.ellipse(400, 324, 74, 50, cream, op=0.6)
    for s in (-1, 1):
        a.circle(400 + s * 52, 286, 22, "#fef9c3")
        a.ellipse(400 + s * 52, 286, 7, 18, INK)
        a.circle(400 + s * 52 - 8, 278, 5, WHITE)
    a.path("M 366 330 Q 400 350 434 330", stroke=INK, sw=5)
    # 斑点：固定坐标保证形状可控，不落在轮廓外
    for sx, sy, sr in ((344, 250, 10), (452, 248, 9), (388, 262, 8),
                       (330, 330, 8), (470, 326, 9), (356, 420, 12),
                       (448, 418, 11), (400, 460, 12), (320, 470, 9),
                       (484, 466, 8), (380, 520, 11), (432, 540, 9),
                       (360, 586, 10), (444, 590, 9), (400, 612, 8),
                       (372, 638, 8), (428, 640, 7)):
        a.ellipse(sx, sy, sr, sr * 0.8, spot)


# ==========================================================================
# 用品 SUPPLIES —— 每张都画「可辨认的物件剪影 + 1~2 个语境细节」
# 用具沿用所属家族的背景/点缀色，形体用中性色，保证 20 张摆在一起成组。
# ==========================================================================
def _scatter(a, rng, count, cx, cy, rx, ry, rmin, rmax, colors, op=1.0):
    """在椭圆范围内确定性地撒点（粮粒、卵石、气泡等）。"""
    for _ in range(count):
        ang = rng.rand(0, 2 * math.pi)
        rad = math.sqrt(rng.rand(0.0, 1.0))
        x = cx + math.cos(ang) * rx * rad
        y = cy + math.sin(ang) * ry * rad
        r = rng.rand(rmin, rmax)
        a.ellipse(x, y, r, r * 0.78, rng.pick(colors), op=op)


def supply_dog_bowl(a):
    """犬碗：浅口大碗 + 高出碗沿的粮堆 + 碗身骨头图形。"""
    fam = FAMILIES["DOG"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=664, rx=214, ry=22)
    a.path("M 176 392 Q 186 620 400 630 Q 614 620 624 392 Z", fill=main)
    a.path("M 196 470 Q 212 592 400 600 Q 588 592 604 470 Q 560 560 400 566 "
           "Q 240 560 196 470 Z", fill=deep, op=0.28)
    a.ellipse(400, 392, 224, 62, deep)
    a.ellipse(400, 388, 206, 50, light)
    a.ellipse(400, 380, 186, 40, "#92400e")
    _scatter(a, _Rng("DOG-BOWL-26"), 12, 400, 378, 160, 30, 9, 15,
             ["#b45309", "#f59e0b", "#d97706", "#fcd34d"])
    a.rect(330, 500, 140, 26, rx=13, fill=light)          # 骨头图形
    for bx in (330, 470):
        a.circle(bx, 500, 21, light)
        a.circle(bx, 526, 21, light)
    a.rect(352, 512, 96, 12, rx=6, fill=deep, op=0.35)
    a.path("M 210 440 Q 240 520 330 556", stroke=light, sw=10, op=0.7)


def supply_dog_brush(a):
    """犬梳：手柄 + 金属箍 + 椭圆刷背 + 三排刷毛。"""
    fam = FAMILIES["DOG"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=636, rx=170, ry=20)
    a.rect(372, 140, 56, 250, rx=28, fill=deep)
    a.rect(388, 166, 14, 190, rx=7, fill=light, op=0.55)
    a.rect(346, 382, 108, 46, rx=14, fill=light)
    a.rect(360, 390, 80, 10, rx=5, fill=deep, op=0.5)
    a.ellipse(400, 492, 156, 80, main)
    a.ellipse(400, 478, 132, 58, light)
    for row in range(3):
        for col in range(7):
            x = 332 + col * 22 + row * 4
            y = 462 + row * 26
            a.line(x, y, x, y + 42, stroke="#fbbf24", sw=8)
            a.circle(x, y + 44, 5, "#fef3c7")
    a.circle(330, 470, 10, light, op=0.7)


def supply_dog_food(a):
    """犬粮袋：立式粮袋 + 顶部封口折边 + 标签爪印 + 撒出的狗粮。"""
    fam = FAMILIES["DOG"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=650, rx=186, ry=20)
    a.path("M 250 220 L 550 220 L 566 616 Q 400 640 234 616 Z", fill=main)
    a.path("M 500 220 L 550 220 L 566 616 Q 514 632 470 634 Z",
           fill=deep, op=0.25)
    a.rect(236, 184, 328, 46, rx=12, fill=deep)
    a.path("M 250 230 L 566 230", stroke=light, sw=4, op=0.6)
    for i in range(9):                                    # 封口锯齿
        a.path("M %s 184 L %s 206 L %s 184" % (
            fnum(252 + i * 36), fnum(268 + i * 36), fnum(284 + i * 36)),
            stroke=light, sw=4, op=0.6)
    a.rect(292, 312, 216, 196, rx=26, fill=light)
    a.ellipse(400, 442, 38, 30, deep)                     # 爪垫
    for px, py in ((354, 400), (384, 384), (416, 384), (446, 400)):
        a.circle(px, py, 15, deep)
    a.rect(292, 540, 216, 14, rx=7, fill=deep, op=0.3)
    _scatter(a, _Rng("DOG-FOOD-26"), 7, 300, 640, 70, 22, 10, 16,
             ["#b45309", "#d97706", "#92400e"])
    a.path("M 262 380 Q 274 470 300 530", stroke=light, sw=8, op=0.5)


def supply_dog_leash(a):
    """牵引绳：项圈圆环 + D 形环 + S 形绳 + 底端手环 + 缝线。"""
    fam = FAMILIES["DOG"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=656, rx=180, ry=16)
    a.ellipse(400, 240, 140, 50, "#ffffff", stroke=deep, sw=22)
    a.ellipse(400, 240, 140, 50, "none", stroke=main, sw=13)
    a.circle(400, 181, 16, light, stroke=deep, sw=5)      # 项圈扣
    a.circle(400, 300, 20, "none", stroke=main, sw=11)    # D 形环
    rope = smooth_d([(400, 320), (492, 356), (508, 452), (448, 520),
                     (356, 548), (330, 628), (404, 654), (486, 620)])
    a.path(rope, stroke=main, sw=26)
    a.path(rope, stroke=light, sw=4, op=0.85, dash="16 14")
    a.ellipse(400, 632, 78, 30, "none", stroke=main, sw=22)
    a.rect(384, 316, 32, 22, rx=8, fill=deep, op=0.8)


def supply_cat_bed(a):
    """猫窝：圆形厚垫 + 内凹睡窝 + 立体前唇 + 缝线。"""
    fam = FAMILIES["CAT"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=648, rx=220, ry=18)
    a.ellipse(400, 512, 248, 126, "#c4b5fd")
    a.ellipse(400, 486, 248, 120, main)
    a.ellipse(400, 470, 196, 88, light)
    a.ellipse(400, 486, 170, 70, "#f5f3ff")
    a.rect(326, 402, 148, 58, rx=24, fill="#ddd6fe")     # 叠好的小毯子
    a.rect(326, 402, 148, 18, rx=9, fill="#c4b5fd")
    a.path("M 356 420 Q 356 444 362 460", stroke="#c4b5fd", sw=5)
    a.path("M 444 420 Q 444 444 438 460", stroke="#c4b5fd", sw=5)
    a.path("M 152 486 Q 152 606 400 620 Q 648 606 648 486 Q 648 548 400 562 "
           "Q 152 548 152 486 Z", fill="#8b5cf6")
    a.ellipse(400, 486, 222, 104, "none", stroke=light, sw=4,
              op=0.75, dash="14 14")
    a.ellipse(400, 486, 200, 92, "none", stroke="#c4b5fd", sw=3, op=0.7,
              dash="8 12")
    a.path("M 190 512 Q 214 578 300 600", stroke=light, sw=8, op=0.6)
    a.ellipse(560, 566, 26, 14, light, op=0.8)
    a.ellipse(400, 562, 16, 13, "#c4b5fd")               # 前唇爪印
    for px, py in ((368, 540), (388, 532), (412, 532), (432, 540)):
        a.ellipse(px, py, 7, 9, "#c4b5fd")
    for px in (222, 300, 500, 578):
        a.circle(px, 606, 4, light, op=0.9)


def supply_cat_litter(a):
    """猫砂：立式猫砂袋 + 透视窗 + 侧边猫砂铲 + 漏出的砂粒。"""
    fam = FAMILIES["CAT"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=650, rx=200, ry=20)
    a.path("M 244 220 L 468 220 L 500 616 Q 360 636 224 616 Z", fill=main)
    a.path("M 436 220 L 468 220 L 500 616 Q 470 626 442 629 Z",
           fill=deep, op=0.2)
    a.rect(232, 188, 258, 42, rx=12, fill=deep)
    a.rect(292, 318, 132, 158, rx=18, fill=light)
    _scatter(a, _Rng("CAT-LITTER-26"), 9, 358, 398, 46, 56, 6, 10,
             ["#c4b5fd", "#a78bfa", "#ddd6fe"])
    a.line(552, 282, 494, 470, stroke=deep, sw=16)
    a.path("M 470 468 Q 514 484 554 498 Q 562 530 540 542 Q 500 534 464 508 Z",
           fill=light, stroke=deep, sw=5)
    _scatter(a, _Rng("CAT-LITTER-2"), 7, 300, 646, 70, 18, 5, 9,
             ["#c4b5fd", "#a78bfa"])


def supply_cat_scratch(a):
    """猫抓板：立柱 + 底座 + 缠绕麻绳 + 顶球玩具。"""
    fam = FAMILIES["CAT"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=668, rx=196, ry=20)
    a.rect(264, 586, 272, 64, rx=28, fill=deep)
    a.rect(264, 576, 272, 26, rx=13, fill=main)
    a.ellipse(400, 576, 136, 18, main)
    a.rect(360, 214, 80, 372, rx=28, fill="#c4b5fd")
    a.ellipse(400, 214, 40, 16, main)
    for i in range(9):                                    # 麻绳缠绕
        y = 250 + i * 36
        a.path("M 364 %s Q 400 %s 436 %s" % (fnum(y), fnum(y + 10), fnum(y)),
               stroke=main, sw=9, op=0.85)
    a.line(440, 232, 486, 344, stroke=deep, sw=4)
    a.circle(490, 370, 24, light, stroke=deep, sw=5)
    a.circle(484, 362, 7, WHITE, op=0.8)


def supply_cat_teaser(a):
    """逗猫棒：长杆 + 短握把 + 细绳 + 四片羽毛 + 小铃铛。"""
    fam = FAMILIES["CAT"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    a.line(210, 620, 264, 552, stroke="#4c1d95", sw=28)
    a.line(264, 552, 520, 260, stroke=main, sw=14)
    a.line(276, 540, 508, 276, stroke=light, sw=4, op=0.6)
    a.circle(520, 260, 9, light, stroke=deep, sw=3)
    a.path("M 520 260 Q 560 296 556 354", stroke="#8b5cf6", sw=4)
    a.path("M 556 354 Q 620 312 668 352 Q 624 392 584 404 Z", fill=light)
    a.path("M 556 354 Q 606 400 636 460 Q 588 438 552 394 Z", fill=main)
    a.path("M 556 354 Q 520 410 506 462 Q 546 418 570 384 Z", fill="#ddd6fe")
    a.path("M 556 354 Q 592 374 622 402 Q 586 410 560 388 Z", fill="#8b5cf6")
    a.path("M 556 354 L 600 368 M 556 354 L 592 386", stroke=deep, sw=3, op=0.35)
    a.circle(540, 470, 22, "#fde68a", stroke="#d97706", sw=4)
    a.path("M 536 452 L 536 486", stroke="#d97706", sw=4)
    a.circle(536, 490, 10, "#fde68a", stroke="#d97706", sw=4)


def supply_bird_bath(a):
    """鸟浴盆：浅口浴盆 + 水面涟漪 + 溅起的水珠 + 盆沿。"""
    fam = FAMILIES["BIRD"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=674, rx=222, ry=20)
    a.path("M 160 404 Q 172 620 400 632 Q 628 620 640 404 Z", fill=main)
    a.path("M 184 486 Q 206 600 400 608 Q 594 600 616 486 Q 560 566 400 572 "
           "Q 240 566 184 486 Z", fill=deep, op=0.25)
    a.ellipse(400, 404, 242, 66, deep)
    a.ellipse(400, 400, 208, 48, "#a7f3d0")
    a.ellipse(400, 400, 208, 48, "none", stroke=light, sw=5)
    a.path("M 330 396 Q 400 380 470 396", stroke=WHITE, sw=5, op=0.8)
    a.path("M 356 414 Q 400 424 444 414", stroke=WHITE, sw=4, op=0.6)
    for cx, cy, r in ((400, 300, 13), (330, 336, 10), (464, 322, 8),
                      (352, 274, 6), (478, 268, 7)):
        a.circle(cx, cy, r, "#6ee7b7", op=0.9)
        a.circle(cx - r * 0.3, cy - r * 0.3, r * 0.28, WHITE, op=0.9)
    a.path("M 292 448 Q 270 470 276 494", stroke=main, sw=7, op=0.7)


def supply_bird_cover(a):
    """笼布：罩住笼子的布罩 + 波浪下摆 + 两侧系带 + 底部笼座。"""
    fam = FAMILIES["BIRD"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=650, rx=200, ry=18)
    a.path("M 200 608 L 200 420 Q 200 196 400 196 Q 600 196 600 420 L 600 608 Z",
           fill=main)
    a.path("M 200 608 Q 220 584 240 608 Q 260 584 280 608 Q 300 584 320 608 "
           "Q 340 584 360 608 Q 380 584 400 608 Q 420 584 440 608 Q 460 584 480 608 "
           "Q 500 584 520 608 Q 540 584 560 608 Q 580 584 600 608 Z", fill=deep)
    a.path("M 292 612 L 292 410 Q 292 246 400 246", stroke=light, sw=8, op=0.55)
    a.path("M 508 612 L 508 410 Q 508 246 400 246", stroke=light, sw=8, op=0.55)
    a.path("M 400 612 L 400 250", stroke=light, sw=6, op=0.4)
    for s in (-1, 1):                                     # 两侧系带
        x = 400 + s * 210
        a.ellipse(x, 470, 22, 30, "#a7f3d0")
        a.path("M %s 500 Q %s 540 %s 556" % (fnum(x), fnum(x + s * 20), fnum(x + s * 6)),
               stroke="#a7f3d0", sw=6)
    a.rect(230, 612, 340, 26, rx=10, fill="#047857")      # 笼底托盘
    for x in (238, 550):                                  # 笼架立柱
        a.rect(x, 588, 12, 50, rx=6, fill="#047857")


def supply_bird_food(a):
    """鸟粮：种子袋 + 圆形透视窗 + 撒出的谷粒 + 旁边一穗粟米。"""
    fam = FAMILIES["BIRD"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=656, rx=200, ry=20)
    a.rect(252, 206, 252, 352, rx=30, fill=main)
    a.rect(474, 206, 30, 352, rx=15, fill=deep, op=0.18)
    a.rect(240, 176, 276, 42, rx=12, fill=deep)
    a.circle(378, 360, 72, light)
    _scatter(a, _Rng("BIRD-FOOD-26"), 11, 378, 360, 52, 52, 5, 9,
             ["#d97706", "#92400e", "#fcd34d", "#b45309"])
    _scatter(a, _Rng("BIRD-FOOD-2"), 8, 300, 628, 76, 18, 4, 8,
             ["#d97706", "#fcd34d", "#92400e"])
    a.line(540, 620, 566, 424, stroke="#a3a3a3", sw=6)
    for i in range(9):                                    # 粟米穗
        y = 440 + i * 22
        a.ellipse(566 + (6 if i % 2 else -6), y, 13, 9, "#fcd34d")
        a.ellipse(566 + (6 if i % 2 else -6), y, 13, 9, "#d97706", op=0.35)


def supply_bird_perch(a):
    """站杆：一根横枝 + 两端金属固定片 + 木纹与枝节。"""
    fam = FAMILIES["BIRD"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=600, rx=210, ry=18)
    for x in (198, 562):                                  # 固定片
        a.rect(x, 396, 40, 62, rx=10, fill=deep)
        a.rect(x + 8, 408, 24, 12, rx=6, fill=light, op=0.8)
    d = smooth_d([(160, 524), (280, 476), (400, 486), (516, 456), (640, 476)])
    a.path(d, stroke=deep, sw=50)
    a.path(d, stroke=main, sw=38)
    a.path("M 200 498 Q 300 486 390 470 M 430 480 Q 520 462 600 478",
           stroke="#b45309", sw=6, op=0.5)
    a.ellipse(330, 484, 15, 9, "#92400e", op=0.8)
    a.ellipse(524, 464, 13, 8, "#92400e", op=0.8)
    a.path("M 610 476 Q 636 440 660 446", stroke=main, sw=9)
    a.path("M 648 452 Q 664 428 656 408 Q 638 424 640 448 Z", fill=main)
    a.path("M 604 470 Q 618 400 608 336", stroke=main, sw=9)     # 嫩枝
    a.path("M 608 388 Q 636 356 664 362 Q 640 392 610 400 Z", fill=main)
    a.path("M 606 348 Q 580 318 552 322 Q 574 350 604 360 Z", fill=light)


def supply_fish_filter(a):
    """鱼缸过滤器：挂夹 + 方盒外壳 + 滤材分层 + 水体 + 出水管 + 气泡。"""
    fam = FAMILIES["FISH"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=650, rx=190, ry=20)
    a.path("M 270 236 L 234 236 Q 220 236 220 250 L 220 300 L 246 300 L 246 264 "
           "L 270 264 Z", fill=deep)                     # 挂在缸壁上的卡扣
    a.rect(266, 196, 258, 400, rx=26, fill=light, stroke=main, sw=6)
    a.rect(288, 220, 214, 106, rx=14, fill="#bae6fd")    # 滤材仓
    for i in range(3):
        a.line(302, 246 + i * 26, 488, 246 + i * 26, stroke="#0ea5e9", sw=6, op=0.7)
    a.rect(288, 338, 214, 50, rx=12, fill="#7dd3fc")
    a.rect(288, 406, 190, 168, rx=14, fill="#38bdf8", op=0.55)  # 水体
    a.line(288, 406, 478, 406, stroke=deep, sw=4, op=0.5)
    a.path("M 500 214 Q 556 218 564 258 L 540 266 Q 530 236 496 236 Z",
           fill=main)                                    # 出水口
    a.rect(516, 330, 34, 260, rx=16, fill="#7dd3fc")     # 进水管
    a.rect(524, 352, 8, 176, rx=4, fill=deep, op=0.3)
    _scatter(a, _Rng("FISH-FILTER-26"), 7, 566, 440, 34, 128, 5, 11,
             ["#bae6fd", "#e0f2fe", "#7dd3fc"], op=0.95)


def supply_fish_food(a):
    """鱼粮：小罐 + 螺旋盖 + 鱼形标签 + 飘落的薄片饲料。"""
    fam = FAMILIES["FISH"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=640, rx=170, ry=18)
    a.rect(306, 300, 188, 272, rx=26, fill=main)
    a.rect(322, 316, 26, 240, rx=13, fill=light, op=0.5)
    a.rect(294, 252, 212, 56, rx=16, fill=deep)
    a.rect(310, 264, 180, 12, rx=6, fill=light, op=0.45)
    a.rect(322, 380, 156, 122, rx=18, fill=light)
    a.ellipse(398, 442, 38, 22, deep)                     # 鱼形图标
    a.poly([(360, 442), (336, 422), (336, 462)], fill=deep)
    a.circle(414, 434, 4, WHITE)
    for i in range(6):                                    # 飘落的薄片
        x = 330 + i * 30
        y = 150 + (i % 3) * 30
        a.poly([(x, y), (x + 22, y + 8), (x + 7, y + 20)], fill="#fbbf24", op=0.9)
    a.path("M 500 190 Q 512 240 504 292", stroke="#fbbf24", sw=5, op=0.6)


def supply_fish_gravel(a):
    """底砂：卵石堆（大小与颜色分层）+ 少量高光，模拟河床底砂。"""
    fam = FAMILIES["FISH"]
    deep, light = fam["deep"], fam["light"]
    _ground(a, deep, cy=652, rx=226, ry=18)
    pebbles = [
        (228, 592, 64, 44, "#94a3b8"), (330, 604, 72, 48, main_color("FISH")),
        (444, 602, 66, 44, "#7dd3fc"), (556, 592, 62, 42, "#cbd5e1"),
        (398, 560, 58, 40, deep), (288, 542, 56, 40, "#bae6fd"),
        (504, 548, 56, 38, main_color("FISH")), (364, 502, 50, 34, "#94a3b8"),
        (462, 504, 48, 32, "#7dd3fc"), (300, 472, 42, 28, "#cbd5e1"),
        (520, 480, 40, 26, "#bae6fd"), (408, 452, 38, 26, main_color("FISH")),
        (230, 642, 52, 30, "#7dd3fc"), (582, 636, 48, 28, "#94a3b8"),
        (350, 652, 46, 26, "#bae6fd"), (482, 652, 42, 24, "#cbd5e1"),
    ]
    for cx, cy, rx, ry, color in pebbles:
        a.ellipse(cx, cy, rx, ry, color)
        a.ellipse(cx - rx * 0.28, cy - ry * 0.34, rx * 0.3, ry * 0.22,
                  WHITE, op=0.35)


def supply_fish_test(a):
    """水质测试：试管 + 显色液 + 刻度 + 比色卡 + 滴落的水滴。"""
    fam = FAMILIES["FISH"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=634, rx=180, ry=14)
    a.path("M 344 160 L 344 520 Q 344 592 400 592 Q 456 592 456 520 L 456 160 Z",
           fill=light)
    a.path("M 352 372 L 352 516 Q 352 578 400 578 Q 448 578 448 516 L 448 372 Z",
           fill="#fbbf24", op=0.85)
    a.ellipse(400, 372, 48, 10, "#fcd34d")
    a.path("M 344 160 L 344 520 Q 344 592 400 592 Q 456 592 456 520 L 456 160",
           fill="none", stroke=main, sw=5)
    a.rect(332, 128, 136, 44, rx=12, fill=deep)
    for y in (412, 444, 476, 508):                        # 刻度
        a.line(306, y, 338, y, stroke=deep, sw=4, op=0.8)
    a.rect(500, 330, 120, 190, rx=16, fill="#f0f9ff", stroke=main, sw=5)
    for i, color in enumerate(("#a3e635", "#facc15", "#fb923c", "#ef4444")):
        a.rect(516, 348 + i * 42, 88, 28, rx=6, fill=color)
    a.path("M 470 234 Q 484 258 470 272 Q 456 258 470 234 Z", fill=main)
    a.path("M 504 268 Q 516 288 504 300 Q 492 288 504 268 Z", fill=main, op=0.8)


def supply_rep_dish(a):
    """爬宠食盆：浅口陶盆 + 盆沿 + 面包虫 + 菜叶。"""
    fam = FAMILIES["REP"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=668, rx=224, ry=20)
    a.path("M 170 486 Q 182 632 400 638 Q 618 632 630 486 Z", fill=main)
    a.path("M 196 540 Q 220 610 400 616 Q 580 610 604 540 Q 552 588 400 592 "
           "Q 248 588 196 540 Z", fill=deep, op=0.25)
    a.ellipse(400, 486, 230, 60, deep)
    a.ellipse(400, 482, 196, 44, light)
    for i, (x, y) in enumerate(((352, 480), (400, 468), (448, 486))):
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(x - 34), fnum(y + 6), fnum(x), fnum(y - 18),
            fnum(x + 34), fnum(y + 2)), stroke="#d97706", sw=13)
        a.path("M %s %s Q %s %s %s %s" % (
            fnum(x - 34), fnum(y + 6), fnum(x), fnum(y - 18),
            fnum(x + 34), fnum(y + 2)), stroke="#fcd34d", sw=4, op=0.5)
    a.path("M 262 466 Q 300 440 330 462 Q 300 480 262 478 Z", fill="#4ade80")
    a.path("M 470 470 Q 500 442 538 458 Q 512 482 470 480 Z", fill="#4ade80")
    a.path("M 180 510 Q 210 566 290 592", stroke=light, sw=9, op=0.55)


def supply_rep_heat(a):
    """加热灯：圆顶灯罩 + 灯口 + 夹子 + 下方光晕与热辐射线。"""
    fam = FAMILIES["REP"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=640, rx=190, ry=18)
    a.path("M 276 402 Q 276 208 400 208 Q 524 208 524 402 Z", fill=deep)
    a.path("M 300 396 Q 306 250 400 232", stroke=light, sw=12, op=0.45)
    a.ellipse(400, 402, 130, 26, main)
    a.ellipse(400, 400, 98, 16, "#365314")
    a.rect(370, 402, 60, 36, fill="#365314")
    a.rect(352, 436, 96, 26, rx=10, fill=main)
    a.ellipse(400, 486, 170, 44, "#fef08a", op=0.55)
    a.ellipse(400, 524, 110, 30, "#fde047", op=0.7)
    for i in range(6):
        x = 306 + i * 38
        a.line(x, 470, x - 26 + i * 9, 594, stroke="#facc15", sw=9, op=0.9)
    a.path("M 258 600 Q 282 616 258 632 Q 234 616 258 600 Z", fill="#fde047", op=0.6)
    a.path("M 544 600 Q 568 616 544 632 Q 520 616 544 600 Z", fill="#fde047", op=0.6)


def supply_rep_hide(a):
    """躲避穴：岩石洞窟 + 拱形洞口 + 石纹 + 苔藓 + 洞口碎石。"""
    fam = FAMILIES["REP"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=664, rx=220, ry=20)
    a.path("M 170 628 Q 172 300 400 292 Q 628 300 630 628 Z", fill=main)
    a.path("M 300 628 Q 302 452 400 444 Q 498 452 500 628 Z", fill="#365314")
    a.path("M 320 620 Q 322 472 400 466 Q 478 472 480 620 Z", fill="#4d7c0f")
    a.path("M 200 520 Q 240 380 340 330", stroke=deep, sw=7, op=0.4)
    a.path("M 600 520 Q 560 380 460 330", stroke=deep, sw=7, op=0.4)
    a.path("M 240 600 Q 300 540 330 470", stroke=deep, sw=6, op=0.3)
    for cx, cy, rx, ry in ((330, 330, 34, 18), (420, 306, 28, 14),
                           (500, 340, 30, 16)):
        a.ellipse(cx, cy, rx, ry, light, op=0.85)
    a.ellipse(560, 620, 26, 16, "#a8a29e", op=0.9)
    a.ellipse(240, 626, 20, 12, "#a8a29e", op=0.9)


def supply_rep_thermo(a):
    """温湿度计：圆形表盘 + 彩色刻度 + 指针 + 下方探头。"""
    fam = FAMILIES["REP"]
    main, deep, light = fam["main"], fam["deep"], fam["light"]
    _ground(a, deep, cy=636, rx=180, ry=16)
    a.circle(400, 304, 166, light)
    a.circle(400, 304, 166, "none", stroke=main, sw=9)
    a.circle(400, 304, 138, WHITE)
    for i in range(11):                                   # 刻度：绿 -> 黄 -> 红
        ang = math.radians(210 + i * 12)
        r1, r2 = 104, 126
        color = "#4ade80" if i < 4 else ("#facc15" if i < 8 else "#ef4444")
        a.line(400 + r1 * math.cos(ang), 304 + r1 * math.sin(ang),
               400 + r2 * math.cos(ang), 304 + r2 * math.sin(ang),
               stroke=color, sw=9)
    ang = math.radians(210 + 7 * 12)
    a.line(400, 304, 400 + 96 * math.cos(ang), 304 + 96 * math.sin(ang),
           stroke="#dc2626", sw=7)
    a.circle(400, 304, 14, main)
    a.circle(400, 304, 6, light)
    a.rect(386, 470, 28, 132, rx=14, fill=deep)
    a.circle(400, 606, 18, deep)
    a.path("M 400 454 L 400 470", stroke=deep, sw=12)
    a.rect(382, 136, 36, 16, rx=8, fill=main)


def main_color(family):
    return FAMILIES[family]["main"]


# 主体整体纵向微调（只用于天然偏低、横向很宽的用品，单位为 px）
ART_DY = {
    "DOG-BOWL-26": -72,
    "CAT-BED-26": -82,
    "FISH-GRAVEL-26": -100,
    "REP-DISH-26": -96,
    "BIRD-BATH-26": -34,
    "REP-HIDE-26": -38,
    "BIRD-PERCH-26": -40,
    "CAT-LITTER-26": -14,
}


# ==========================================================================
# 组装 SVG：渐变背景 + 装饰圆 + 白色圆底板 + 主体
# ==========================================================================
def build_svg(slug, family, draw):
    art = Art()
    draw(art)
    fam = FAMILIES[family]
    bg_top, bg_bottom = fam["bg"]
    # 少数横向用品天然偏低，用一次性整体上移让主体在底板里更居中；
    # 该平移量会写进 transform，审计函数会据此折算包围盒。
    shift_y = ART_DY.get(slug, 0)
    art_tag = ('  <g id="art" transform="translate(0,%s)">' % fnum(shift_y)
               if shift_y else '  <g id="art">')

    lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 800">',
        "  <defs>",
        '    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">',
        '      <stop offset="0" stop-color="%s"/>' % bg_top,
        '      <stop offset="1" stop-color="%s"/>' % bg_bottom,
        "    </linearGradient>",
        "  </defs>",
        '  <rect width="800" height="800" fill="url(#bg)"/>',
        '  <g id="deco" fill="%s">' % fam["main"],
        '    <circle cx="686" cy="122" r="132" opacity="0.16"/>',
        '    <circle cx="96" cy="700" r="104" opacity="0.12"/>',
        '    <circle cx="708" cy="652" r="46" opacity="0.14"/>',
        "  </g>",
        '  <g id="plate">',
        '    <ellipse cx="400" cy="420" rx="310" ry="302" fill="%s" opacity="0.05"/>'
        % fam["deep"],
        '    <ellipse cx="400" cy="412" rx="304" ry="299" fill="%s" opacity="0.05"/>'
        % fam["deep"],
        '    <circle cx="400" cy="400" r="300" fill="#ffffff"/>',
        '    <circle cx="400" cy="400" r="300" fill="none" stroke="%s" '
        'stroke-width="5" opacity="0.22"/>' % fam["main"],
        "  </g>",
        art_tag,
    ]
    lines.extend("    " + element for element in art.els)
    lines.append("  </g>")
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


# ==========================================================================
# 自检：结构 / 禁用元素 / 体积 / 主体包围盒
# ==========================================================================
ALLOWED_TAGS = {
    "svg", "defs", "linearGradient", "stop", "rect", "g",
    "circle", "ellipse", "path", "line", "polygon",
}
PATH_ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "Z": 0}


def path_points(d):
    """解析仅含绝对命令的 path，返回所有锚点 + 控制点，用于包围盒自检。"""
    tokens = re.findall(r"[A-Za-z]|-?\d+(?:\.\d+)?", d)
    points = []
    index = 0
    command = None
    current = (0.0, 0.0)
    start = (0.0, 0.0)
    while index < len(tokens):
        token = tokens[index]
        if token.isalpha():
            if token.islower():
                raise ValueError("path 只允许绝对坐标命令: %r" % token)
            command = token
            index += 1
            if command == "Z":
                current = start
            continue
        if command is None:
            raise ValueError("path 缺少命令")
        arity = PATH_ARITY[command]
        args = [float(value) for value in tokens[index:index + arity]]
        if len(args) != arity:
            raise ValueError("path 参数数量不对: %s" % d[:48])
        index += arity
        if command == "M":
            current = (args[0], args[1])
            start = current
            points.append(current)
            command = "L"
        elif command == "L":
            current = (args[0], args[1])
            points.append(current)
        elif command == "H":
            current = (args[0], current[1])
            points.append(current)
        elif command == "V":
            current = (current[0], args[0])
            points.append(current)
        elif command == "C":
            points.append((args[0], args[1]))
            points.append((args[2], args[3]))
            current = (args[4], args[5])
            points.append(current)
        elif command == "S":
            points.append((args[0], args[1]))
            current = (args[2], args[3])
            points.append(current)
        elif command == "Q":
            points.append((args[0], args[1]))
            current = (args[2], args[3])
            points.append(current)
        elif command == "T":
            current = (args[0], args[1])
            points.append(current)
    return points


def _element_points(el):
    """返回元素的关键几何点（用于包围盒与“是否越出底板”的自检）。"""
    tag = el.tag.split("}")[-1]
    get = el.get
    if tag == "circle":
        cx, cy, r = float(get("cx", 0)), float(get("cy", 0)), float(get("r", 0))
        return [(cx - r, cy), (cx + r, cy), (cx, cy - r), (cx, cy + r)]
    if tag == "ellipse":
        cx, cy = float(get("cx", 0)), float(get("cy", 0))
        rx, ry = float(get("rx", 0)), float(get("ry", 0))
        return [(cx - rx, cy), (cx + rx, cy), (cx, cy - ry), (cx, cy + ry)]
    if tag == "rect":
        x, y = float(get("x", 0)), float(get("y", 0))
        w, h = float(get("width", 0)), float(get("height", 0))
        return [(x, y), (x + w, y), (x, y + h), (x + w, y + h)]
    if tag == "line":
        return [(float(get("x1", 0)), float(get("y1", 0))),
                (float(get("x2", 0)), float(get("y2", 0)))]
    if tag == "polygon":
        return [tuple(float(v) for v in pair.split(","))
                for pair in get("points", "").split()]
    if tag == "path":
        return path_points(get("d", ""))
    return []


def audit_svg(text):
    """返回 (问题列表, 主体包围盒)。包围盒为 None 表示主体为空。"""
    problems = []
    if 'viewBox="0 0 800 800"' not in text:
        problems.append("缺少 viewBox=\"0 0 800 800\"")
    if "http" in text.replace('xmlns="http://www.w3.org/2000/svg"', ""):
        problems.append("含外部资源链接")
    if "data:" in text or "base64" in text:
        problems.append("含内嵌位图数据")
    if "style=" in text or "class=" in text:
        problems.append("含内联样式")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        return ["XML 解析失败: %s" % exc], None

    points = []
    art_group = None
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag not in ALLOWED_TAGS:
            problems.append("出现禁用元素 <%s>" % tag)
        if tag == "g" and el.get("id") == "art":
            art_group = el
    if root.tag.split("}")[-1] != "svg":
        problems.append("根元素不是 <svg>")
    if art_group is None:
        problems.append("缺少 id=\"art\" 的主体分组")
    else:
        offset = (0.0, 0.0)
        transform = art_group.get("transform")
        if transform:
            match = re.match(
                r"\s*translate\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)\s*$",
                transform,
            )
            if match:
                offset = (float(match.group(1)), float(match.group(2)))
            else:
                problems.append("主体分组只允许单个 translate，实为 %r" % transform)
        for el in art_group:
            for px, py in _element_points(el):
                points.append((px + offset[0], py + offset[1]))

    size = len(text.encode("utf-8"))
    if not (2000 <= size <= 6000):
        problems.append("体积 %d 字节，超出 2~6KB" % size)

    if not points:
        problems.append("主体为空")
        return problems, None

    x0 = min(p[0] for p in points)
    y0 = min(p[1] for p in points)
    x1 = max(p[0] for p in points)
    y1 = max(p[1] for p in points)
    bbox = (x0, y0, x1, y1)
    width, height = x1 - x0, y1 - y0
    longest, shortest = max(width, height), min(width, height)
    if not (440 <= longest <= 520):
        problems.append("主体最长边 %.0fpx，不在 440~520" % longest)
    if shortest < 230:
        problems.append("主体短边 %.0fpx 过小" % shortest)
    center_x, center_y = (x0 + x1) / 2, (y0 + y1) / 2
    if not (365 <= center_x <= 445):
        problems.append("主体横向中心 %.0f 偏离画布中心" % center_x)
    if not (360 <= center_y <= 480):
        problems.append("主体纵向中心 %.0f 偏离画布中心" % center_y)
    if x0 < 0 or y0 < 0 or x1 > 800 or y1 > 800:
        problems.append("主体越出 800x800 画布")
    far_x, far_y = max(points, key=lambda p: math.hypot(p[0] - 400, p[1] - 400))
    far = math.hypot(far_x - 400, far_y - 400)
    if far > 300:
        problems.append("最远点 (%.0f,%.0f) 距圆心 %.0fpx，越出底板"
                        % (far_x, far_y, far))
    return problems, bbox


# ==========================================================================
# 40 个 SKU 注册表（文件名 = SKU，路径 = images/<分组>/<SKU>.svg）
# ==========================================================================
PETS = [
    ("DOG-CORGI-26", "DOG", dog_corgi),
    ("DOG-GOLDEN-26", "DOG", dog_golden),
    ("DOG-LABRADOR-26", "DOG", dog_labrador),
    ("DOG-SHIBA-26", "DOG", dog_shiba),
    ("CAT-AMERICAN-26", "CAT", cat_american),
    ("CAT-BRITISH-26", "CAT", cat_british),
    ("CAT-RAGDOLL-26", "CAT", cat_ragdoll),
    ("CAT-SIAMESE-26", "CAT", cat_siamese),
    ("BIRD-BUDGIE-26", "BIRD", bird_budgie),
    ("BIRD-COCKATIEL-26", "BIRD", bird_cockatiel),
    ("BIRD-FINCH-26", "BIRD", bird_finch),
    ("BIRD-LOVEBIRD-26", "BIRD", bird_lovebird),
    ("FISH-BETTA-26", "FISH", fish_betta),
    ("FISH-GOLDFISH-26", "FISH", fish_goldfish),
    ("FISH-GUPPY-26", "FISH", fish_guppy),
    ("FISH-NEON-26", "FISH", fish_neon),
    ("REP-BEARDED-26", "REP", rep_bearded),
    ("REP-CORN-26", "REP", rep_corn),
    ("REP-CRESTED-26", "REP", rep_crested),
    ("REP-LEOPARD-26", "REP", rep_leopard),
]

SUPPLIES = [
    ("DOG-BOWL-26", "DOG", supply_dog_bowl),
    ("DOG-BRUSH-26", "DOG", supply_dog_brush),
    ("DOG-FOOD-26", "DOG", supply_dog_food),
    ("DOG-LEASH-26", "DOG", supply_dog_leash),
    ("CAT-BED-26", "CAT", supply_cat_bed),
    ("CAT-LITTER-26", "CAT", supply_cat_litter),
    ("CAT-SCRATCH-26", "CAT", supply_cat_scratch),
    ("CAT-TEASER-26", "CAT", supply_cat_teaser),
    ("BIRD-BATH-26", "BIRD", supply_bird_bath),
    ("BIRD-COVER-26", "BIRD", supply_bird_cover),
    ("BIRD-FOOD-26", "BIRD", supply_bird_food),
    ("BIRD-PERCH-26", "BIRD", supply_bird_perch),
    ("FISH-FILTER-26", "FISH", supply_fish_filter),
    ("FISH-FOOD-26", "FISH", supply_fish_food),
    ("FISH-GRAVEL-26", "FISH", supply_fish_gravel),
    ("FISH-TEST-26", "FISH", supply_fish_test),
    ("REP-DISH-26", "REP", supply_rep_dish),
    ("REP-HEAT-26", "REP", supply_rep_heat),
    ("REP-HIDE-26", "REP", supply_rep_hide),
    ("REP-THERMO-26", "REP", supply_rep_thermo),
]


def iter_targets():
    for slug, family, draw in PETS:
        yield "pets", slug, family, draw
    for slug, family, draw in SUPPLIES:
        yield "supplies", slug, family, draw


def main(argv):
    check_only = "--check" in argv
    verbose = "--verbose" in argv

    for group in ("pets", "supplies"):
        os.makedirs(os.path.join(IMAGES, group), exist_ok=True)

    failures = []
    sizes = []
    bounds = []
    expected = {"pets": set(), "supplies": set()}

    for group, slug, family, draw in iter_targets():
        expected[group].add(slug)
        text = build_svg(slug, family, draw)
        path = os.path.join(IMAGES, group, slug + ".svg")
        problems, bbox = audit_svg(text)
        if bbox:
            bounds.append(bbox)
        sizes.append(len(text.encode("utf-8")))

        if check_only:
            if not os.path.exists(path):
                problems.append("目标文件不存在")
            else:
                with open(path, "r", encoding="utf-8") as handle:
                    if handle.read() != text:
                        problems.append("磁盘内容与生成结果不一致（非幂等/被手改）")
        else:
            with open(path, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(text)

        if verbose:
            box = "%.0f,%.0f - %.0f,%.0f" % bbox if bbox else "-"
            print("  %-22s %5d B  bbox %s" % (slug, sizes[-1], box))
        if problems:
            failures.append((group, slug, problems))

    # 覆盖性检查：目录里不应缺少目标文件
    for group in ("pets", "supplies"):
        folder = os.path.join(IMAGES, group)
        existing = {name[:-4] for name in os.listdir(folder) if name.endswith(".svg")}
        missing = expected[group] - existing
        extra = existing - expected[group]
        if missing:
            failures.append((group, "*", ["缺少文件: %s" % ", ".join(sorted(missing))]))
        if extra:
            print("[提示] %s/ 下还有未在注册表中的 SVG: %s" %
                  (group, ", ".join(sorted(extra))))

    print("生成方式: %s" % ("仅校验（--check）" if check_only else "写入磁盘"))
    print("文件数量: %d（pets %d + supplies %d）" %
          (len(sizes), len(PETS), len(SUPPLIES)))
    if sizes:
        print("文件体积: %d ~ %d 字节（要求 2000~6000）" % (min(sizes), max(sizes)))
    if bounds:
        widths = [b[2] - b[0] for b in bounds]
        heights = [b[3] - b[1] for b in bounds]
        print("主体尺寸: 宽 %.0f~%.0f，高 %.0f~%.0f px" %
              (min(widths), max(widths), min(heights), max(heights)))

    if failures:
        print("\n发现 %d 处问题：" % len(failures))
        for group, slug, problems in failures:
            print("  [%s/%s]" % (group, slug))
            for problem in problems:
                print("    - %s" % problem)
        return 1
    print("全部通过：XML 结构、viewBox、无文字/位图、体积与主体包围盒均符合约束。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
