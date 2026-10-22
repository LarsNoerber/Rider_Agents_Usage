"""Render illustrated overview, details and status bar media. Requires Pillow; no account data is read."""

from html import escape
from math import cos, pi, sin
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "images"
WIDTH, HEIGHT, SCALE = 1280, 1320, 2
BACKGROUND = "#1E1F22"
PANEL = "#2B2D30"
TEXT = "#DFE1E5"
MUTED = "#A6A9B1"
GREEN = "#64CD87"
AMBER = "#FFB94B"
TRACK = "#3D4148"

image = Image.new("RGB", (WIDTH * SCALE, HEIGHT * SCALE), BACKGROUND)
draw = ImageDraw.Draw(image)
svg = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
    '<title>AgentMeter illustrated overview with example balances</title>',
    '<desc>Compact usage bars for OpenAI, JetBrains AI and GitHub Copilot, a weekly quest recap, and status indicators. '
    'This is an illustration, not a live account screenshot.</desc>',
]


def rectangle(x, y, width, height, color, radius=0):
    box = tuple(int(value * SCALE) for value in (x, y, x + width, y + height))
    draw.rounded_rectangle(box, radius=radius * SCALE, fill=color)
    svg.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" '
               f'rx="{radius}" fill="{color}"/>')


def label(x, y, value, size=24, color=TEXT, bold=False, right=False):
    # y is the top of the text, shared by both outputs.
    filename = "segoeuib.ttf" if bold else "segoeui.ttf"
    font = ImageFont.truetype(str(Path("C:/Windows/Fonts") / filename), size * SCALE)
    draw.text((x * SCALE, y * SCALE), value, font=font, fill=color, anchor="rt" if right else "lt")
    svg.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="Segoe UI, sans-serif" '
               f'font-size="{size}" font-weight="{700 if bold else 400}" '
               f'dominant-baseline="text-before-edge" text-anchor="{"end" if right else "start"}">'
               f'{escape(value)}</text>')


def circle(x, y, radius, color):
    draw.ellipse(tuple(int(v * SCALE) for v in (x - radius, y - radius, x + radius, y + radius)), fill=color)
    svg.append(f'<circle cx="{x}" cy="{y}" r="{radius}" fill="{color}"/>')


def quota_wraith_icon(x, y, size=28, accent="#C397FA", cracked=True):
    shape = [(5, 11), (4.2, 8), (4.8, 5.7), (7, 3.7), (11, 2.8), (15, 3),
             (11, 0.5), (20, 4), (28, 0.5), (25, 7), (27.5, 9.5), (29, 14),
             (29, 20), (28, 26), (22, 23), (16, 29), (10, 24), (4, 28),
             (2.5, 22), (3.5, 16)]
    factor = size / 32
    points = [(round((x + px * factor) * SCALE), round((y + py * factor) * SCALE)) for px, py in shape]
    draw.polygon(points, fill=accent)
    svg.append(f'<path d="M5 11 C4 5 9 2 15 3 L11 .5 L20 4 L28 .5 L25 7 '
               f'C30 12 29 21 28 26 L22 23 L16 29 L10 24 L4 28 C2 22 2 15 5 11 Z" '
               f'fill="{accent}" transform="translate({x} {y}) scale({factor})"/>')
    for eye_x in (11, 22):
        eye = (x + (eye_x - 3) * factor, y + 10 * factor,
               x + (eye_x + 3) * factor, y + 18 * factor)
        draw.ellipse(tuple(round(value * SCALE) for value in eye), fill="#FFFFFF")
    svg.append(f'<g transform="translate({x} {y}) scale({factor})" fill="#FFFFFF">'
               '<ellipse cx="11" cy="14" rx="3" ry="4"/><ellipse cx="22" cy="14" rx="3" ry="4"/></g>')
    for eye_x in (11, 22):
        pupil = (x + (eye_x - 1.5) * factor, y + 13 * factor,
                 x + (eye_x + 1.5) * factor, y + 17 * factor)
        draw.ellipse(tuple(round(value * SCALE) for value in pupil), fill="#37284C")
    svg.append(f'<g transform="translate({x} {y}) scale({factor})" fill="#37284C">'
               '<ellipse cx="11.5" cy="15" rx="1.5" ry="2"/>'
               '<ellipse cx="20.5" cy="15" rx="1.5" ry="2"/></g>')
    if cracked:
        crack = [(x + 15 * factor, y + 18 * factor), (x + 12 * factor, y + 22 * factor),
                 (x + 17 * factor, y + 25 * factor)]
        draw.line([(round(px * SCALE), round(py * SCALE)) for px, py in crack],
                  fill="#FFE782", width=max(1, round(2 * factor * SCALE)))
        svg.append(f'<path d="M15 18 L12 22 L17 25" fill="none" stroke="#FFE782" '
                   f'stroke-width="2" transform="translate({x} {y}) scale({factor})"/>')


def line(points, color=MUTED, width=2):
    draw.line([(round(x * SCALE), round(y * SCALE)) for x, y in points], fill=color, width=width * SCALE)
    svg.append(f'<polyline points="{" ".join(f"{x},{y}" for x, y in points)}" '
               f'fill="none" stroke="{color}" stroke-width="{width}"/>')


def row(y, title, percent, remaining, consumed=False):
    balance = 100 - percent if consumed else percent
    color = GREEN if balance >= 50 else (AMBER if balance >= 20 else "#FF6E6E")
    label(132, y, title, size=24, color=MUTED)
    rectangle(362, y + 7, 530, 16, TRACK)
    if percent > 0:
        rectangle(362, y + 7, round(530 * percent / 100), 16, color)
    label(1148, y, remaining, size=24, color=color, bold=True, right=True)


def provider(y, name, plan, accent):
    rectangle(100, y - 12, 1080, 183, PANEL, radius=8)
    rectangle(106, y - 3, 4, 165, accent, radius=2)
    label(132, y, name, size=26, bold=True)
    rectangle(1025, y, 123, 30, TRACK, radius=4)
    label(1087, y + 2, plan, size=20, color=MUTED, right=True)


def facts(y, entries):
    font = ImageFont.truetype(str(Path("C:/Windows/Fonts/segoeui.ttf")), 18 * SCALE)
    for index, (name, value) in enumerate(entries):
        x = 132 if index % 2 == 0 else 680
        row_y = y + index // 2 * 26
        key = name + ":"
        label(x, row_y, key, size=18, color=MUTED)
        label(x + font.getlength(key) / SCALE + 9, row_y, value, size=18, bold=True)


def start_preview(width, height, title, description):
    global WIDTH, HEIGHT, image, draw, svg
    WIDTH, HEIGHT = width, height
    image = Image.new("RGB", (WIDTH * SCALE, HEIGHT * SCALE), BACKGROUND)
    draw = ImageDraw.Draw(image)
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        f'<title>{escape(title)}</title>',
        f'<desc>{escape(description)} This is an illustration with example balances, not a live account screenshot.</desc>',
    ]


def save_preview(name):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / f"{name}.svg").write_text("\n".join(svg + ["</svg>"]) + "\n", encoding="utf-8")
    image.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS).save(OUTPUT / f"{name}.png", optimize=True)
    print(f"Preview written: {name}.png / .svg")


def preview_heading(subtitle):
    rectangle(100, 64, 62, 62, "#111827", radius=12)
    label(114, 74, "AI", size=30, bold=True)
    for x, color in ((114, "#5EEAD4"), (129, "#60A5FA"), (144, "#A78BFA")):
        rectangle(x, 113, 10, 3, color, radius=1)
    label(182, 64, "AgentMeter", size=42, bold=True)
    label(184, 117, subtitle, size=24, color=MUTED)


def text_width(value, size=18, bold=False):
    filename = "segoeuib.ttf" if bold else "segoeui.ttf"
    return ImageFont.truetype(str(Path("C:/Windows/Fonts") / filename), size * SCALE).getlength(value) / SCALE


def detail_card(x, name, plan, accent, bars, summary, details):
    rectangle(x, 210, 450, 764, PANEL, radius=8)
    rectangle(x + 5, 223, 4, 738, accent, radius=2)
    label(x + 18, 232, name, size=26, bold=True)
    rectangle(x + 344, 233, 88, 28, TRACK, radius=4)
    label(x + 388 + text_width(plan) / 2, 236, plan, size=18, color=MUTED, right=True)
    for index, (title, percent, value, consumed) in enumerate(bars):
        y = 280 + index * 32
        balance = 100 - percent if consumed else percent
        color = GREEN if balance >= 50 else AMBER if balance >= 20 else "#FF6E6E"
        label(x + 18, y, title, size=18, color=MUTED)
        rectangle(x + 180, y + 5, 126, 12, TRACK)
        if percent:
            rectangle(x + 180, y + 5, round(126 * percent / 100), 12, color)
        label(x + 432, y, value, size=18, color=color, bold=True, right=True)
    for index, (key, value) in enumerate(summary):
        cell_x = x + (18 if index % 2 == 0 else 232)
        y = 368 + index // 2 * 28
        label(cell_x, y, key + ":", size=17, color=MUTED)
        label(cell_x + text_width(key + ":", size=17) + 7, y, value, size=17, bold=True)
    for index, (key, value) in enumerate(details):
        y = 452 + index * 34
        label(x + 18, y, key + ":", size=18, color=MUTED)
        label(x + 18 + text_width(key + ":") + 7, y, value, size=18, bold=True)


def status_bar(y, groups, size=28, height=68, x=100, width=1080):
    rectangle(x, y, width, height, PANEL, radius=6)
    x += 24
    for parts in groups:
        for value, color in parts:
            label(x, y + (height - size) / 2 - 2, value, size=size, color=color)
            x += text_width(value, size=size)
        x += 26


# All gallery assets use the Marketplace's recommended 1280 x 800 format.
PRODUCT = "AgentMeter"
PROVIDERS = [
    ("OpenAI", "Plus", "#39AE99", [("5 hours remaining", 78, "78%", False), ("Weekly remaining", 42, "42%", False)], "5h reset: 16:30  ·  Updated: 09:00"),
    ("JetBrains AI", "AI Pro", "#9D77DC", [("Subscription", 65, "6.5 / 10", False), ("Top-up", 80, "4 / 5", False)], "Credits left: 10.5  ·  Available: 70%"),
    ("GitHub Copilot", "Pro", "#5297E6", [("Premium used", 38, "38%", True), ("Chat", 0, "Unlimited", True)], "Used: 114 / 300  ·  Reset: Nov 1"),
    ("Claude", "Pro", "#CF754D", [("Session remaining", 84, "84%", False), ("Weekly remaining", 62, "62%", False)], "Monthly tokens: 128K  ·  Updated: 09:00"),
    ("Cursor", "Pro", "#8291A4", [("Plan used", 24, "24%", True), ("Auto used", 18, "18%", True)], "Included plan  ·  Updated: 09:00"),
]


def canvas(subtitle):
    start_preview(1280, 800, PRODUCT + " · " + subtitle, subtitle)
    preview_heading(subtitle)
    # Replace the legacy heading in the shared renderer without affecting IDs or filenames.


def footer(note="Example balances · Illustrated preview, not a live account screenshot"):
    label(100, 755, note, size=17, color=MUTED)


def badge(x, y, plan, expanded=False):
    width = max(100, text_width(plan, 18) + 50)
    rectangle(x - width, y, width, 30, TRACK, radius=5)
    label(x - width + 12, y + 3, plan, size=18, color=TEXT)
    center = x - 17
    line([(center - 4, y + (18 if expanded else 12)), (center, y + (12 if expanded else 18)),
          (center + 4, y + (18 if expanded else 12))], width=2)


def compact_card(x, y, entry, width=528):
    name, plan, accent, bars, summary = entry
    rectangle(x, y, width, 160, PANEL, radius=8)
    rectangle(x + 5, y + 10, 4, 140, accent, radius=2)
    label(x + 20, y + 13, name, size=24, bold=True)
    badge(x + width - 18, y + 13, plan)
    for i, (title, percent, value, consumed) in enumerate(bars):
        yy = y + 54 + i * 30
        balance = 100 - percent if consumed else percent
        color = GREEN if balance >= 50 else AMBER if balance >= 20 else "#FF6E6E"
        label(x + 20, yy, title, size=18, color=MUTED)
        rectangle(x + 213, yy + 5, 156, 12, TRACK, radius=3)
        if percent: rectangle(x + 213, yy + 5, round(156 * percent / 100), 12, color, radius=3)
        label(x + width - 18, yy, value, size=18, color=color, bold=True, right=True)
    label(x + 20, y + 125, summary, size=17, color=MUTED)


openai_parts = [("OpenAi | D=", TEXT), ("78%", GREEN), (" - W=", TEXT), ("42%", AMBER)]
jetbrains_parts = [("JetBrainAi | ", TEXT), ("70%", GREEN)]
copilot_parts = [("Copilot | ", TEXT), ("38%", GREEN)]

canvas("Five providers. One usage overview.")
for index, entry in enumerate(PROVIDERS):
    compact_card(100 if index < 3 else 652, 194 + (index if index < 3 else index - 3) * 172, entry)
rectangle(652, 538, 528, 160, PANEL, radius=8)
label(676, 558, "Make it your own", size=24, bold=True)
label(676, 601, "Choose visible providers in configuration.", size=19, color=MUTED)
label(676, 634, "Click a plan badge for details and charts.", size=19, color=MUTED)
label(676, 667, "Weekly and Games are optional.", size=18, color="#C397FA")
status_bar(710, [openai_parts, jetbrains_parts, copilot_parts], size=20, height=34)
footer("Example balances \u00b7 Includes status bar excerpt \u00b7 Illustrated preview")
save_preview("agents-usage-overview")

canvas("Click a subscription badge to show or hide details")
compact_card(100, 194, PROVIDERS[0])
badge(610, 207, "Plus", expanded=True)
rectangle(100, 354, 528, 350, PANEL, radius=8)
label(120, 370, "OpenAI details", size=23, bold=True)
for i, (key, value) in enumerate([( "Subscription", "Plus"), ("5h reset", "Oct 4, 16:30"), ("Week reset", "Oct 8, 09:00"), ("Refresh", "Every 60 seconds")]):
    label(120, 411 + i * 30, key, size=18, color=MUTED)
    label(607, 411 + i * 30, value, size=18, bold=True, right=True)
label(120, 553, "Usage history · consumed %", size=18, bold=True)
label(607, 553, "1h   6h   24h", size=17, color=MUTED, right=True)
for yy in (590, 630, 670): line([(138, yy), (599, yy)], color=TRACK, width=1)
line([(138, 670), (220, 661), (300, 640), (400, 633), (490, 611), (599, 605)], color="#39AE99", width=3)
line([(138, 648), (220, 640), (300, 625), (400, 604), (490, 593), (599, 583)], color="#5297E6", width=3)
label(652, 211, "Details when you need them", size=29, bold=True)
for i, text in enumerate(["Subscription plan and quota categories", "Reset dates and countdowns", "Local 1 / 6 / 24-hour usage charts", "Connection and refresh status"]):
    circle(664, 291 + i * 61, 4, "#39AE99")
    label(681, 278 + i * 61, text, size=21, color=MUTED)
rectangle(652, 557, 528, 147, PANEL, radius=8)
label(676, 579, "Click again to collapse", size=25, bold=True)
label(676, 621, "Keep the overview compact while you work.", size=20, color=MUTED)
label(676, 661, "Chart history starts with local observations.", size=18, color=MUTED)
footer()
save_preview("agents-usage-details")

canvas("Quota colors, right in your status bar")
label(100, 213, "Your selected providers stay together", size=25, bold=True)
status_bar(257, [openai_parts, jetbrains_parts, copilot_parts])
label(100, 364, "OpenAI and JetBrains AI show remaining quota.", size=24, color=MUTED)
label(100, 409, "Copilot shows consumed quota: 0% unused, 100% exhausted.", size=24, color=MUTED)
label(100, 500, "Fresh installation: only JetBrains AI enabled", size=25, bold=True)
status_bar(544, [jetbrains_parts])
label(100, 662, "Click a provider widget to open " + PRODUCT + ".", size=22, color=MUTED)
footer()
save_preview("agents-usage-statusbar")

canvas("Configuration · choose your providers and extras")
rectangle(100, 193, 580, 520, PANEL, radius=8)
label(124, 215, "Visible agents", size=25, bold=True)
for i, (name, selected) in enumerate([( "OpenAI", False), ("JetBrains AI", True), ("GitHub Copilot", False), ("Claude", False), ("Cursor", False)]):
    y = 265 + i * 42
    rectangle(124, y + 3, 22, 22, "#3574F0" if selected else TRACK, radius=3)
    if selected: line([(129, y + 13), (134, y + 18), (142, y + 8)], color="#FFFFFF", width=2)
    label(162, y, name, size=22)
label(124, 487, "Overview sections", size=25, bold=True)
for i, name in enumerate(["Weekly", "Games"]):
    y = 535 + i * 40
    rectangle(124, y + 3, 22, 22, TRACK, radius=3)
    label(162, y, name, size=22)
label(124, 632, "Refresh interval", size=20, color=MUTED)
rectangle(332, 626, 180, 34, TRACK, radius=4)
label(348, 631, "60 seconds", size=20)
label(732, 217, "Start with a quiet setup", size=27, bold=True)
label(732, 264, "JetBrains AI is enabled by default.", size=21, color=MUTED)
label(732, 308, "Weekly and Games start disabled.", size=21, color=MUTED)
label(732, 390, "Missing ACP package?", size=27, bold=True)
label(732, 439, "Configuration explains what is missing.", size=20, color=MUTED)
label(732, 482, "Install from Rider?s ACP Registry.", size=20, color=MUTED)
label(732, 564, "Selected and installed providers", size=21)
label(732, 599, "appear in Usage and the status bar.", size=21)
label(732, 653, "Deselected provider reads pause.", size=20, color=MUTED)
footer("Illustrated fresh-install configuration · Optional provider package required")
save_preview("agents-usage-config")

canvas("Optional weekly insights and a little downtime")
rectangle(100, 194, 680, 506, PANEL, radius=8)
label(124, 216, "WEEKLY QUESTS", size=23, color="#D1B5F2", bold=True)
label(124, 270, "YOUR AI PARTY", size=17, color=MUTED, bold=True)
label(124, 309, "OpenAI 78%    JetBrains AI 70%    Claude 62%", size=21, color="#C397FA")
label(124, 357, "Forecast based on your local quota observations", size=20, color=MUTED)
rectangle(124, 418, 632, 155, "#34303B", radius=8)
quota_wraith_icon(142, 451, size=56)
label(221, 440, "THE QUOTA WRAITH", size=24, color="#C397FA", bold=True)
label(732, 481, "70% HP", size=19, color=MUTED, right=True)
rectangle(221, 518, 511, 13, TRACK, radius=5)
rectangle(221, 518, 358, 13, "#9D77DC", radius=5)
label(221, 550, "Hit by Claude · 4 Points", size=20, color="#CF754D", bold=True)
label(124, 612, "Quota use lands visible hits on the weekly boss.", size=21)
label(124, 654, "Damage persists across refreshes and restarts.", size=19, color=MUTED)
rectangle(804, 194, 376, 506, PANEL, radius=8)
label(828, 216, "GAMES", size=23, color="#D1B5F2", bold=True)
label(828, 269, "Tic-Tac-Toe", size=25, bold=True)
for i in range(1, 3):
    line([(848 + i * 90, 337), (848 + i * 90, 607)], width=2, color=TRACK)
    line([(848, 337 + i * 90), (1118, 337 + i * 90)], width=2, color=TRACK)
for column, row_index in [(0,0), (1,1), (2,2)]:
    x, y = 872 + column * 90, 361 + row_index * 90
    line([(x, y), (x + 42, y + 42)], color="#60A5FA", width=4)
    line([(x + 42, y), (x, y + 42)], color="#60A5FA", width=4)
for column, row_index in [(2,0), (0,1)]:
    x, y = 893 + column * 90, 382 + row_index * 90
    circle(x, y, 23, "#C397FA"); circle(x, y, 18, PANEL)
label(828, 650, "Wins 3    Draws 2    Losses 1", size=20, color=MUTED)
footer("Illustrated example · Enable Weekly and Games in configuration · Both off by default")
save_preview("agents-usage-weekly-games")

# A tight, separately uploadable excerpt shares exactly the gallery status rendering.
start_preview(1080, 100, "AgentMeter status bar excerpt", "Illustrated example; fictional quota balances.")
status_bar(0, [openai_parts, jetbrains_parts, copilot_parts], size=24, height=58, x=0)
label(24, 72, "Illustrated status bar excerpt \u00b7 Example balances, not a live account screenshot", size=17, color=MUTED)
save_preview("agents-usage-statusbar-excerpt")
