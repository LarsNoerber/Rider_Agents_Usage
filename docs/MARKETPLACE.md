# AgentMeter Marketplace materials

## JetBrains Marketplace — version 1.2.1

AgentMeter monitors OpenAI Codex, JetBrains AI, GitHub Copilot, Claude and Cursor in Rider.
Version 1.2.1 improves refresh reliability, background history and resource use. Version 1.1.0 was the first stable release.
The existing plugin ID remains `io.github.larsnoerber.agentsusage` for upgrade compatibility.

The listing description is in `src/main/resources/META-INF/plugin.xml`. It introduces the current features,
requirements and privacy behavior without development changelog or migration text.
The 1.2.1 description includes the overview and a separate status bar excerpt from GitHub's main branch.
These source-hosted previews follow later artwork updates; the local media ZIP contains this build's image copies.

### Gallery images

All five images are **1280 × 800 PNG**, with editable SVG counterparts. They are illustrated examples,
not live account screenshots. Balances are fictional; provider cards are arranged for gallery readability.
No account data or credentials are read when rendering these images.

| File                                                     | Suggested caption                                                       |
|----------------------------------------------------------|-------------------------------------------------------------------------|
| [Overview](images/agents-usage-overview.png)             | AgentMeter — five providers in one usage overview (illustrated example) |
| [Details](images/agents-usage-details.png)               | Click a subscription badge for details and charts (illustrated example) |
| [Status bar](images/agents-usage-statusbar.png)          | Provider quota colors in the status bar (illustrated example)           |
| [Configuration](images/agents-usage-config.png)          | Choose providers, Weekly and Games (illustrated fresh installation)     |
| [Weekly and Games](images/agents-usage-weekly-games.png) | Optional weekly boss battles and Tic-Tac-Toe (illustrated example)      |

The overview includes an illustrated status bar excerpt beneath the provider cards. The additional
`images/agents-usage-statusbar-excerpt.png` (1080 x 100) and editable SVG are included in the media bundle as a
separate cropped composition. Fictional balances and the illustrated-example notice remain visible. The five
full gallery images retain their 1280 x 800 dimensions.

Regenerate with `python tools/render_marketplace_preview.py` (Pillow and Windows Segoe UI required).
The existing Quota Portal logo is `images/agents-usage-logo.png`; the plugin's SVG logo stays in `META-INF`.

### Upload preparation

Create the media bundle with `python tools/package_jetbrains_marketplace.py` after building the Rider plugin.

The local build provides `agentmeter-1.2.1.zip` for plugin upload. The packaging script copies it unchanged to
`agentmeter-marketplace-1.2.1.zip`, which is also ready for the plugin upload and retains the `agentmeter/lib/`
structure. The separate `agentmeter-marketplace-media-1.2.1.zip` contains the five gallery PNGs, status bar excerpt,
logo, HTML description, release notes and upload instructions; extract it for the listing fields and Media uploads.

1. Upload the Rider plugin ZIP to JetBrains Marketplace with name **AgentMeter**.
2. Use the packaged `plugin.xml` description (or the matching `description.html` in the media bundle).
3. Upload the five PNGs under **Media**, retaining the illustrated-example captions.
4. Use the packaged 1.2.1 release notes for the refresh, history and resource-use improvements.

Creating the ZIP and GitHub release does not submit the listing to JetBrains Marketplace.
The 1280 × 800 image format
follows [JetBrains Marketplace guidance](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).

## Other editors

The repository also contains separate VS Code and Visual Studio sources. This Rider release does not build their
packages. Their preview renderers and publishing materials remain in their respective directories.

## Standalone Windows Store materials

The standalone Windows app is a separate product/package, with ten provider choices and its own SVG-backed icon.
Use [Windows Store listing](../windows/Packaging/StoreListing.md), [privacy policy](../windows/Packaging/Privacy.md)
and [Windows verification status](../windows/TESTING.md). Its provider matrix differs from the Rider listing.
The MSIX is unsigned until a separate signing/Store process; generating it does not submit or install the app.
Source-only GitHub updates do not change the shared version, create a release or upload EXE/MSIX artifacts.
