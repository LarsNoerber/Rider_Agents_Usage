"""Render VS Code listing illustrations with example data. Requires Pillow; reads no account data."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "vscode" / "package.json").read_text(encoding="utf-8"))
OUTPUT = ROOT / "vscode" / "resources" / "previews"
SCALE = 2
TEXT, MUTED = "#CCCCCC", "#A0A0A0"
GREEN, AMBER = "#73C991", "#CCA700"


def render(name):
    image = Image.new("RGB", (1280 * SCALE, 900 * SCALE), "#181818")
    draw = ImageDraw.Draw(image)

    def box(x, y, w, h, color, radius=0, outline=None):
        draw.rounded_rectangle(tuple(round(v * SCALE) for v in (x, y, x + w, y + h)),
                               radius=radius * SCALE, fill=color, outline=outline, width=SCALE)

    def text(x, y, value, size=22, color=TEXT, bold=False):
        font = ImageFont.truetype(str(Path("C:/Windows/Fonts") / ("segoeuib.ttf" if bold else "segoeui.ttf")),
                                  size * SCALE)
        draw.text((x * SCALE, y * SCALE), value, font=font, fill=color)

    text(48, 32, "AgentMeter 1.2.4", 34, bold=True)
    text(48, 82, "Visual Studio Code  /  " + ("Usage overview" if name == "usage" else "Configuration"), 23, MUTED)
    text(48, 837, "Illustrated preview with example balances. No live account data.", 20, MUTED)
    box(48, 139, 1184, 673, "#202020", 9, "#3D3D3D")
    box(49, 140, 1182, 45, "#2B2B2B", 8)
    text(75, 150, "Visual Studio Code", 18)
    box(49, 185, 57, 586, "#181818")
    draw.arc((64*SCALE, 205*SCALE, 91*SCALE, 232*SCALE), start=0, end=280, fill=TEXT, width=4*SCALE)
    draw.ellipse((73*SCALE, 214*SCALE, 82*SCALE, 223*SCALE), fill=TEXT)
    box(49, 200, 3, 40, TEXT)
    box(49, 771, 1182, 40, "#181818")

    def button(x, y, width, label):
        box(x, y, width, 35, "#0E639C", 2)
        text(x + 12, y + 3, label, 18, "#FFFFFF")

    if name == "usage":
        text(128, 201, "AGENT USAGE", 19, bold=True)
        text(128, 239, "Usage", 19, bold=True)
        button(351, 237, 89, "Refresh")
        button(450, 237, 93, "Settings")
        box(123, 290, 420, 258, "#202020", 6, "#454545")
        text(143, 304, "OpenAI Codex", 21, bold=True)

        def quota(y, label, value, remaining=True):
            color = GREEN if (100 - value if remaining else value) < 70 else AMBER
            text(143, y, label, 19)
            text(343, y, f"{value}% " + ("remaining" if remaining else "used"), 19, color, True)
            box(143, y + 34, 380, 8, "#3C3C3C", 4)
            box(143, y + 34, round(380 * value / 100), 8, color, 4)

        quota(352, "5-hour quota", 94)
        text(143, 401, "Resets: Oct 3, 2026, 4:30 PM", 17, MUTED)
        quota(437, "Weekly quota", 42)
        text(143, 485, "Resets: Oct 8, 2026, 10:00 AM", 17, MUTED)
        text(143, 515, "Plan: Plus", 17, MUTED)
        box(123, 566, 420, 172, "#202020", 6, "#454545")
        text(143, 580, "GitHub Copilot", 21, bold=True)
        quota(625, "Premium requests", 38, False)
        text(143, 673, "Plan: Pro  /  Premium requests: 114 / 300", 16, MUTED)
        text(143, 704, "Resets: Nov 1, 2026, 12:00 AM", 16, MUTED)
        box(566, 185, 665, 586, "#1F1F1F")
        text(618, 272, "Your provider quotas at a glance", 28, bold=True)
        text(618, 346, "OpenAI Codex", 23, bold=True)
        text(618, 388, "Remaining 5-hour and weekly allowance", 21, MUTED)
        text(618, 462, "GitHub Copilot", 23, bold=True)
        text(618, 504, "Consumed quota, plan and reset date", 21, MUTED)
        text(618, 570, "Claude Code  |  Pro", 23, bold=True)
        text(618, 610, "Session: 76% remaining / Weekly: 62%", 20, GREEN)
        text(618, 665, "Cursor  |  Pro", 23, bold=True)
        text(618, 705, "Included plan: 24% used", 20, GREEN)
        text(129, 779, "OpenAi | D=94% - W=42%", 19, AMBER)
        text(468, 779, "Copilot | 38%", 19, GREEN)
    else:
        text(132, 205, "Settings", 24, bold=True)
        box(132, 253, 1060, 41, "#313131", 3, "#515151")
        text(150, 258, f"@ext:{MANIFEST['publisher']}.{MANIFEST['name']}", 20)
        text(132, 322, "AgentMeter", 27, bold=True)

        def setting(y, title, description, checked=None, value=None):
            text(132, y, title, 21, bold=True)
            text(132, y + 27, description, 18, MUTED)
            if checked is not None:
                box(132, y + 52, 23, 23, "#0E639C", 3)
                draw.line([(137 * SCALE, (y + 64) * SCALE), (141 * SCALE, (y + 69) * SCALE),
                           (150 * SCALE, (y + 57) * SCALE)], fill="#FFFFFF", width=2 * SCALE)
                text(171, y + 50, "Enabled", 18)
            if value is not None:
                box(990, y + 2, 190, 34, "#313131", 2, "#515151")
                text(1002, y + 4, value, 18)

        setting(360, "Providers: Codex: Enabled", "Show OpenAI Codex usage in the overview and status bar.", True)
        setting(440, "Providers: Copilot: Enabled", "Show GitHub Copilot usage in the overview and status bar.", True)
        setting(520, "Providers: Claude: Enabled", "Read subscription quotas from the official local session.", True)
        setting(600, "Providers: Cursor: Enabled", "Read included-plan consumption from the official local session.", True)
        setting(695, "Refresh Interval Seconds", "How often to refresh provider-reported usage.", value="60")
        text(132, 779, "Show Status Bar: Enabled", 19, GREEN)

    OUTPUT.mkdir(parents=True, exist_ok=True)
    image.resize((1280, 900), Image.Resampling.LANCZOS).save(OUTPUT / f"{name}.png", optimize=True)
    print(f"Preview written: {OUTPUT / (name + '.png')}")


if __name__ == "__main__":
    render("usage")
    render("settings")
