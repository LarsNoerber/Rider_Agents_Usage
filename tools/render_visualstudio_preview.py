"""Render Visual Studio Marketplace illustrations using example quotas. Requires Pillow; no account access."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "visualstudio" / "Marketplace" / "images"
SCALE = 2
TEXT, MUTED = "#EEEEEE", "#B5B5B5"
GREEN, AMBER = "#64CD87", "#FFB94B"


def render(name):
    canvas = Image.new("RGB", (1280 * SCALE, 900 * SCALE), "#181818")
    draw = ImageDraw.Draw(canvas)

    def box(x, y, width, height, fill, radius=0, outline=None):
        draw.rounded_rectangle(tuple(round(v * SCALE) for v in (x, y, x + width, y + height)),
                               radius=radius * SCALE, fill=fill, outline=outline, width=SCALE)

    def text(x, y, value, size=20, color=TEXT, bold=False):
        font = ImageFont.truetype(str(Path("C:/Windows/Fonts") / ("segoeuib.ttf" if bold else "segoeui.ttf")), size * SCALE)
        draw.text((x * SCALE, y * SCALE), value, font=font, fill=color)

    def icon(x, y, size):
        logo = Image.open(ROOT / "vscode" / "resources" / "icon.png").convert("RGBA")
        logo = logo.resize((size * SCALE, size * SCALE), Image.Resampling.LANCZOS)
        canvas.paste(logo, (x * SCALE, y * SCALE), logo)

    def button(x, y, width, label):
        box(x, y, width, 32, "#363636", 2, "#636363")
        text(x + 10, y + 3, label, 17)

    text(48, 30, "AgentMeter", 34, bold=True)
    text(48, 82, "Visual Studio  /  " + ("Usage and status bar" if name == "usage" else "Provider configuration"), 23, MUTED)
    text(48, 840, "Illustrated preview with example balances. No live account data.", 20, MUTED)
    box(48, 140, 1184, 676, "#252526", 7, "#515151")
    box(49, 141, 1182, 41, "#303030", 6)
    text(69, 149, "Visual Studio", 18)
    text(284, 149, "File    Edit    View    Git    Project    Build    Debug    Tools    Extensions", 16, MUTED)

    if name == "usage":
        box(49, 182, 1182, 40, "#303030")
        text(72, 192, "Standard", 16, MUTED)
        icon(193, 187, 27)
        text(242, 192, "Debug    |    Any CPU    |    Start", 16, MUTED)
        box(49, 222, 479, 552, "#252526", outline="#515151")
        text(65, 229, "AgentMeter", 16, bold=True)
        icon(69, 265, 30)
        text(109, 265, "AgentMeter", 22, bold=True)
        button(69, 313, 91, "Refresh")
        button(168, 313, 91, "Settings")
        button(267, 313, 86, "GitHub")
        box(69, 362, 438, 240, "#252526", 6, "#636363")
        text(86, 376, "OpenAI Codex", 21, bold=True)
        text(86, 409, "Plan: Plus", 17, MUTED)

        def quota(y, title, amount, remaining=True):
            color = GREEN if (100 - amount if remaining else amount) < 50 else AMBER
            text(86, y, title, 17)
            text(330, y, f"{amount}% " + ("remaining" if remaining else "used"), 17, color, True)
            box(86, y + 30, 402, 7, "#464646", 3)
            box(86, y + 30, round(402 * amount / 100), 7, color, 3)

        quota(449, "5-hour quota", 94)
        text(86, 493, "Resets: Oct 3, 2026, 4:30 PM", 15, MUTED)
        quota(523, "Weekly quota", 42)
        text(86, 566, "Resets: Oct 8, 2026, 10:00 AM", 15, MUTED)
        box(69, 615, 438, 134, "#252526", 6, "#636363")
        text(86, 626, "GitHub Copilot", 21, bold=True)
        text(86, 657, "Plan: Pro", 17, MUTED)
        quota(686, "Premium requests", 38, False)
        text(566, 300, "Provider quotas in one view", 28, bold=True)
        text(566, 377, "OpenAI Codex", 23, bold=True)
        text(566, 418, "Remaining 5-hour and weekly allowance", 21, MUTED)
        text(566, 496, "GitHub Copilot", 23, bold=True)
        text(566, 537, "Consumed quota from Visual Studio's service", 21, MUTED)
        text(566, 591, "Claude Code  |  Pro", 23, bold=True)
        text(566, 630, "Session: 76% remaining / Weekly: 62%", 20, GREEN)
        text(566, 682, "Cursor  |  Pro", 23, bold=True)
        text(566, 721, "Included plan: 24% used", 20, GREEN)
        box(49, 774, 1182, 41, "#303030")
        text(68, 782, "Ready", 17, MUTED)
        icon(472, 780, 24)
        text(507, 782, "OpenAi | D=", 17)
        text(601, 782, "94%", 17, GREEN)
        text(639, 782, " - W=", 17)
        text(687, 782, "42%", 17, AMBER)
        text(756, 782, "Copilot |", 17)
        text(832, 782, "38%", 17, GREEN)
    else:
        text(80, 204, "Options", 24, bold=True)
        box(75, 252, 270, 488, "#202020", 2, "#515151")
        text(95, 270, "Environment", 19, MUTED)
        text(95, 307, "Text Editor", 19, MUTED)
        text(95, 344, "GitHub", 19, MUTED)
        text(95, 390, "AgentMeter", 20, bold=True)
        box(89, 427, 239, 37, "#3F3F46", 2)
        text(115, 432, "General", 19)
        text(382, 256, "AgentMeter > General", 23, bold=True)
        rows = [("Agents", None), ("Show GitHub Copilot", "True"), ("Show OpenAI Codex", "True"),
                ("Show Claude Code", "True"), ("Show Cursor", "True"), ("Display", None), ("Show status bar", "True"), ("Refresh", None),
                ("Codex CLI path", "(discover on PATH)"), ("Refresh interval (seconds)", "60")]
        for index, (label, value) in enumerate(rows):
            y = 313 + index * 34
            box(381, y, 802, 34, "#303030" if value is None else "#252526", outline="#454545")
            text(394, y + 5, label, 18, bold=value is None)
            if value is not None:
                text(851, y + 5, value, 18, MUTED)
        text(382, 696, "Selected providers refresh in the background.", 18, MUTED)
        button(979, 762, 85, "OK")
        button(1083, 762, 100, "Cancel")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    canvas.resize((1280, 900), Image.Resampling.LANCZOS).save(OUTPUT / f"{name}.png", optimize=True)
    print(f"Preview written: {OUTPUT / (name + '.png')}")


if __name__ == "__main__":
    render("usage")
    render("settings")
