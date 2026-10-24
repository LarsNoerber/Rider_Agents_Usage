# Microsoft Store listing draft

Product: **AgentMeter** · Store ID: **9NTF08GG5569** · Suggested price: **Free** · Category: **Developer tools**

## Description (en-US)

Keep your AI subscription quotas in view with AgentMeter for Windows.

See Codex, Copilot, Claude Code, Cursor and Gemini quota reports alongside OpenRouter key budgets, Kilo balances,
Cline/OpenCode local usage and Junie CLI statistics. Switch to a movable desktop status bar while you work.
Percentages change color as limits approach; local costs/tokens appear without invented quota limits.

- Reported quota percentages, subscription plans and reset times.
- Floating desktop status bar with an optional always-on-top mode.
- Expandable subscription details and observed consumption charts.
- Adjustable status-bar opacity, quota-bar tooltips and a branded system tray icon.
- Provider selection and configurable automatic refresh.
- Individual provider configuration pages and independent provider selection for the bar.
- Six bar layouts, five palettes, refresh progress and simultaneous provider reveal after a batch completes.
- Last measured numeric usage retained across restarts with original timestamps; saved values do not prove login.
- Sign-in through official agents; no separate AgentMeter account.
- Existing local agent sessions, no telemetry, and no credential storage in AgentMeter settings.
- User-entered OpenRouter keys saved explicitly in current-user Windows Credential Manager, with a removal action.

Official agents must be installed and signed in. Available quotas depend on your subscription and the data reported
by the provider. OpenAI, Claude and Gemini CLI show remaining quota; Copilot and Cursor show consumed quota.
The desktop status bar is a movable Windows window. It is not registered in the Windows Widgets board.
Configured connection inventory is not remote validation of every key. Junie CLI authentication/data are separate
from JetBrains AI Assistant's IDE quota service, and Junie saved usage applies to the selected project.

AgentMeter is an independent utility and is not affiliated with OpenAI, GitHub, Anthropic, Cursor, Google, xAI or
JetBrains.

## Search terms

AI quota, Codex, Copilot, Claude Code, Cursor, Gemini, OpenRouter, OpenCode, Junie, desktop status, developer tools

## Release notes

Current 1.2.1 source includes ten providers, per-provider settings, persistent numeric usage, official sign-in,
bar refresh progress, six layouts/five palettes and the supplied SVG-backed app/tray icon. This draft does not
establish that a corresponding Store release has been submitted or accepted.

## Certification notes: runFullTrust

AgentMeter is a native WPF desktop utility. Full trust is needed to launch users' installed official agent executables
for quota queries and sign-in, and to read Claude/Cursor/Gemini CLI's official local sign-in files for
requests to that same provider. For Gemini Apps web usage, it reads only the two session cookies from the installed
Gemini app profile and sends them only to Gemini. It creates a movable desktop status window. The application does not
require administrator rights, install agents automatically, log credentials, transmit credentials to other providers
or collect telemetry.
Missing agents and sessions are displayed explicitly. No prompts or agent tasks are sent to obtain quota reports.
The built-in Junie ACP statistics command is the only submitted monitoring command; no model task is started.
Kilo/OpenCode delegate authentication to their official CLIs; Cline readiness returns only a boolean from the SDK,
and its local database reads select numeric usage. OpenRouter quota access uses an entered current-key credential;
the user's explicit Connect/Save action retains it in Windows Credential Manager. Credentials do not enter JSON
settings/cache/diagnostics. Saved usage contains constrained measurements and original observation times only.
Basic UI and settings can be inspected without provider accounts; actual balances require the reviewer's own eligible
provider accounts. No shared reviewer passwords or tokens are provided.

## Submission prerequisites

Upload the generated unsigned MSIX with the reserved identity to this Partner Center product. Store submission signs
the accepted package; a directly downloaded unsigned MSIX is not a sideload installer.
Provide genuine screenshots of this Windows build, a publicly accessible privacy-policy URL and a support URL before
submitting. Use the privacy-policy draft in `Privacy.md` after hosting it at a publisher-controlled public URL.
Age rating, availability, pricing and any Store questions must be completed in Partner Center. Store acceptance and
account-based sign-in/quotas have not been verified by compilation or MSIX packaging.
See [privacy](Privacy.md), [user guide](../README.md) and [verification status](../TESTING.md). Build outputs remain
local for a source-only GitHub update; no release, version bump or EXE/MSIX upload is implied.
