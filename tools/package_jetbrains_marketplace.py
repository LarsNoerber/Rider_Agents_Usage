"""Prepare the built Rider plugin and separate listing materials for Marketplace upload."""

from pathlib import Path
from shutil import copy2
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
properties = dict(
    line.split("=", 1)
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
    if "=" in line and not line.startswith("#")
)
version = properties["pluginVersion"]
descriptor = ET.parse(ROOT / "src/main/resources/META-INF/plugin.xml").getroot()
name = descriptor.findtext("name")
description = descriptor.findtext("description").strip()
changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
notes = changelog.split(f"## {version}", 1)[1].split("\n## ", 1)[0]
notes = notes.split("\n", 1)[1].strip()
release_notes = f"# {name} {version}\n\nJetBrains/Rider refresh and reliability improvements.\n\n{notes}\n"
gallery = [
    ("agents-usage-overview", "Five providers in one usage overview"),
    ("agents-usage-details", "Click a subscription badge for details and charts"),
    ("agents-usage-statusbar", "Provider quota colors in the status bar"),
    ("agents-usage-config", "Choose providers, Weekly and Games"),
    ("agents-usage-weekly-games", "Optional weekly boss battles and Tic-Tac-Toe"),
]
instructions = (
    f"# {name} {version} - JetBrains Marketplace upload\n\n"
    f"Plugin name: {name}\nPlugin ID: {descriptor.findtext('id')}\n"
    f"Plugin archive: agentmeter-{version}.zip (separate release asset)\n\n"
    "Use description.html as the listing description and release-notes.md as the release notes.\n"
    "Upload the PNGs under Media. Gallery images are 1280 x 800 illustrated examples, not live screenshots.\n"
    "The additional status bar excerpt is 1080 x 100; it is also included in the overview image.\n"
    "Keep the example label in gallery captions. Account data depends on installed providers and plans.\n\n"
    "Suggested captions:\n\n"
    + "\n".join(f"- {filename}.png: {caption} (illustrated example)" for filename, caption in gallery)
    + "\n\nThis bundle does not install or publish the plugin.\n"
)
destination = ROOT / "build/distributions"
destination.mkdir(parents=True, exist_ok=True)
plugin_archive = destination / f"agentmeter-{version}.zip"
if not plugin_archive.is_file():
    raise SystemExit(f"Build the Rider plugin first: missing {plugin_archive}")
archive = destination / f"agentmeter-marketplace-media-{version}.zip"
with ZipFile(archive, "w", ZIP_DEFLATED, compresslevel=9) as bundle:
    bundle.writestr("description.html", description + "\n")
    bundle.writestr("release-notes.md", release_notes)
    bundle.writestr("UPLOAD.md", instructions)
    for filename, _ in gallery:
        for suffix in (".png", ".svg"):
            bundle.write(ROOT / "docs/images" / (filename + suffix), "images/" + filename + suffix)
    for suffix in (".png", ".svg"):
        bundle.write(ROOT / "docs/images" / ("agents-usage-statusbar-excerpt" + suffix),
                     "images/agents-usage-statusbar-excerpt" + suffix)
    bundle.write(ROOT / "docs/images/agents-usage-logo.png", "images/agents-usage-logo.png")
(destination / f"agentmeter-{version}-release-notes.md").write_text(release_notes, encoding="utf-8")
upload_archive = destination / f"agentmeter-marketplace-{version}.zip"
copy2(plugin_archive, upload_archive)
print(f"Marketplace plugin upload: {upload_archive}")
print(f"Marketplace listing materials: {archive}")
