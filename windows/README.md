# AgentMeter for Windows

A standalone .NET 10 WPF window showing provider-reported quotas, balances and recorded local usage without a running
editor or IDE.
The self-contained executable includes the .NET runtime. Start `AgentMeter.exe`; no installer or administrator rights
are needed. The Usage tab stacks compact quota cards; provider selection, polling and widget options are under
Settings.

Choose bar providers under **Settings > Desktop widget > Appearance > Providers shown in bar**. Each enabled provider
has a checkbox; changes save and apply immediately. Disabled providers can first be enabled under **Settings >
General**.
Bar selection does not remove a provider from the dashboard or pause its reads. Older settings keep the existing bar
selection until you customize it.

## Providers and sign-in

OpenRouter, Kilo, Cline, OpenCode and Junie have individual Settings pages. On upgrades, existing
provider selections are preserved; enable the new providers under Settings > General. Reads then refresh automatically.

- **Kilo:** detects the official CLI on PATH or in Rider's existing ACP installation. Uses `profile --json` for the
  active account/team credit balance and `stats --days 30` for recorded local costs and tokens. Kilo owns
  authentication;
  AgentMeter does not read its credentials. The Settings sign-in action runs `kilo auth login`. A balance request
  failure
  is unavailable, never a fabricated zero. Token counts retain the CLI's reported rounding.
  Connection status separately uses `auth list`, including saved BYOK connections; a missing Gateway balance does not
  imply the agent is disconnected. Saved credentials are not remotely validated by this inventory command.
- **Cline:** reads numeric usage from `~/.cline/data/db/sessions.db` in read-only mode, respecting `CLINE_DIR`,
  `CLINE_DATA_DIR` and `CLINE_DB_DATA_DIR`. Includes sessions started in the last 30 days and sums their own usage once,
  including subagents. **Sign in to Cline** runs the official `cline auth` flow to authenticate or configure a model
  provider. The optional CLI path overrides PATH/Rider ACP detection. No key entry in AgentMeter is needed.
  Local history is distinct from connection status, account credits and
  subscription quotas; costs are the agent's recorded estimates.
  Connection status comes from the installed official SDK's enabled/configured provider flags via an isolated Node
  helper. Only a boolean leaves that process; it does not send prompts or expose credentials to AgentMeter. This reports
  configured provider readiness, not remote API-key validation. A missing SDK/Node returns an unverified connection.

Money and tokens appear directly in the dashboard, widget and details. Percentage meters are shown only for reported
percentage quotas. Kilo's CLI output and Cline's database schema can change with agent updates; incompatible data is
reported as unavailable. These integrations do not start prompts or consume model tokens.

- **OpenCode:** detects the official CLI, including OpenCode Desktop's bundled/versioned `opencode-cli.exe`.
  Uses version 1 text reports or version 2 JSON inventory/statistics commands for configured connections and local
  costs/tokens across all projects over the last 30 days. **Sign in to OpenCode** runs `auth login`. Local estimates
  are separate from remaining account credit and OpenRouter spending. Inventory does not remotely validate every key.
- **Junie:** detects the installed native CLI or Rider ACP package. Sign in through the official CLI; `/account`
  manages connections. Rider login may not provide a standalone CLI session. Configure **Project directory for saved
  usage** to see that project's all-time local costs/tokens; an empty path uses your home folder. The bounded ACP
  session uses only Junie's advertised built-in `/stats` command, not an ordinary model task. Credit balance appears
  only when reported; BYOK may have no JetBrains
  balance. [Junie commands](https://junie.jetbrains.com/docs/slash-commands.html).
- **OpenRouter:** connect a masked API key for remaining key budget and reported spending; see
  [key storage](#openrouter). No management key or account balance is requested.

| Provider       | Quota source                                                                                         | Sign in button                                                                                                            |
|----------------|------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| OpenAI Codex   | Official Codex CLI `account/rateLimits/read`                                                         | Runs `codex login` in the official CLI. Use ChatGPT sign-in for subscription quotas.                                      |
| Claude Code    | Official `.claude/.credentials.json` subscription OAuth store and Anthropic usage endpoint           | Runs `claude auth login`. API-key logins do not expose Pro/Max subscription quotas.                                       |
| Cursor         | Official `%APPDATA%/Cursor/auth.json` Agent store and Cursor usage endpoints                         | Runs Cursor Agent `login` (`agent` or older `cursor-agent`).                                                              |
| GitHub Copilot | Official Copilot Language Server `checkQuota`; the server owns its authentication                    | Uses the server's `signIn` device flow and opens GitHub's device authorization page.                                      |
| Gemini         | Gemini CLI / Workspace Code Assist; Gemini Apps web usage as fallback from the installed app session | Opens the installed Gemini app, or Edge/web if absent. **Sign in to Gemini CLI** in details opens the separate CLI login. |

### Gemini CLI and Gemini Apps usage

1. Install the official Gemini CLI if needed, start `gemini`, and choose **Sign in with Google**.
2. Use the same Cloud project as your CLI. Workspace accounts require a configured Google Cloud project, the
   Gemini for Cloud API and the required permissions/license for their organization.
3. In **Settings > General**, enable **Gemini**. On its provider page enter **Workspace Cloud project ID** (the string
   ID,
   not the numeric project number). Alternatively inherit `GOOGLE_CLOUD_PROJECT` / `GOOGLE_CLOUD_PROJECT_ID`, or use
   these keys in the official CLI's user `.gemini/.env`. A project-only `.env` in your working repository is not
   scanned.
4. Select **Sign in to CLI** on the Gemini provider page. If executable detection fails, enter the CLI's
   `gemini.cmd` or `gemini.exe` path in Settings. Select **Refresh** after CLI sign-in.

The card's main **Sign in** button opens the installed Gemini desktop app, or Gemini Apps in Edge/web if the app is
absent. Desktop app sign-in and CLI sign-in are separate. A rejected cookie quota request does not establish that
the desktop app is signed out; the app can remain authenticated while this private quota interface rejects the request.
Gemini can remain open during automatic refresh. If it locks its cookie file, AgentMeter reads a bounded encrypted
snapshot through a read-only duplicate of the app's existing file handle, entirely in memory. It leaves Gemini running.
Temporary transaction/read failures are retried on the next configured refresh. Last reported quota values remain
visible for up to 24 hours, with their original update time and a notice that they are awaiting a successful refresh.
An inaccessible cookie store does not reset a previously known sign-in state.

AgentMeter first reads `.gemini/oauth_creds.json` from your user home, or `GEMINI_CLI_HOME/.gemini` when configured,
and uses the fixed Google Code Assist quota endpoint. If the CLI has no reported quotas, it can use the local session
from the standalone Gemini app profile under `%APPDATA%\Gemini` and then Edge's `Default` / `Profile *` profiles under
`%LOCALAPPDATA%\Microsoft\Edge\User Data`. This web fallback reads only `__Secure-1PSID` and `__Secure-1PSIDTS`,
keeps them in memory, and sends them only to `gemini.google.com`. It uses an undocumented Gemini Apps web endpoint
and can stop working when Google changes it. The Gemini desktop app's locked store has an automatic read-only
fallback; inaccessible Edge profiles are skipped. CLI model quotas, Gemini Apps web limits,
AI Studio API quotas and Vertex AI project quotas are separate. AgentMeter does not refresh, copy, log or persist
Google credentials. Expired CLI access tokens must be renewed by opening the official CLI.
References: [CLI authentication](https://geminicli.com/docs/get-started/authentication/),
[CLI quotas and Workspace plans](https://geminicli.com/docs/resources/quota-and-pricing/),
[official quota implementation](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/code_assist/server.ts).

The Gemini details also link to AI Studio API limits. API quotas are separate from free web access; token metadata
returned by a chat request describes that request, not the remaining account allowance. Gemini requires project/model
quota data; project quotas are not the same as consumer web limits.
References: [Gemini Apps limits](https://support.google.com/gemini/answer/16275805?hl=en),
[Gemini API limits](https://ai.google.dev/gemini-api/docs/rate-limits).

When an agent is not installed, **Sign in** opens the official installation guide. Nothing is installed automatically.
CLI executables are detected from PATH, common user installation folders and existing official JetBrains ACP packages.
Those packages can run while the IDE is closed. Paths can also be entered in **Settings**.
Copilot requires a quota-capable official Language Server, such as an existing ACP package or the executable distributed
by [`@github/copilot-language-server`](https://github.com/github/copilot-language-server-release).
A login to the GitHub website alone does not create the Language Server's local authentication.
Enter the Cursor Agent executable, not the Cursor editor executable, for the Cursor login path.

Complete the official console/browser login. AgentMeter refreshes after launch and process exit. For
Junie/OpenCode/Cline/Kilo, a short background readiness monitor checks every few seconds for up to three minutes;
the terminal can remain open. Normal polling continues afterward. Missing or expired sessions show an explanation;
missing quotas are never replaced with zero or an estimated balance. **Sign in** disappears once the official agent
confirms an authenticated account, including accounts without a finite
quota. Official agents save their own sessions; AgentMeter reuses them after restart. A temporary quota request failure
does not reset a known login. Missing or expired sessions restore the login action. Claude API-key authentication is
recognized through the official CLI's read-only status command; subscription quotas do not apply to that mode.

OpenAI, Claude and Gemini CLI percentages show **remaining** quota. Copilot and Cursor show **consumed** quota.
Quota bars are compact; percentage text changes color as exhaustion approaches. Cards include reported plans and reset
times. Consumption cards and desktop tooltips also state the available percentage: Copilot with 97% available displays
3% used.
Automatic refresh is configurable from 30 to 3600 seconds (default: five minutes). Deselected providers are not read.
Use **Always on top** to keep the window visible next to your editor.
Click a subscription badge to expand supplemental details, reset countdowns and a consumption chart with 1/6/24-hour
ranges. Expansion survives refreshes. Charts collect actual numeric observations in memory while the app is running;
they start fresh after exit. JetBrains AI Assistant's IDE quota service is excluded; Junie CLI is a separate provider.

## Window and tray

Closing or minimizing the dashboard hides it to the system tray. Polling continues for selected providers.
Double-click the AgentMeter tray icon or choose **Open dashboard** to restore it. **Exit** in the dashboard or
tray/widget
menu shuts down the app, timers and provider processes. EXE, dashboard and tray share the ICO generated from
`Packaging/AgentMeter.svg`, the supplied vector icon. Each icon size is rendered directly from vector geometry.
Enable **Start AgentMeter with Windows** under **Settings > General** to launch the app at sign-in for the current
Windows user. The floating bar restores its last position and clamps it to the current screen's work area.

## Desktop status widget

Choose **Bar style** in Settings or the desktop bar's right-click menu. Six styles are available:
**Classic** (horizontal text), **Compact** (small rounded strip), **Cards** (provider tiles with quota bars),
**Circles** (colored circular quota meters), **Vertical** (providers stacked in a column) and **Mini** (short provider
labels).
Style changes apply immediately and are saved. Circle percentages follow the same remaining/consumed meaning as
the other views; OpenAI and Gemini Apps also show their weekly quota. The right-click menu is dark throughout,
including its checked items and style submenu.

Select **Desktop widget** to switch to a compact floating status bar. It uses the same snapshots and selected
providers as the dashboard, including `OpenAi | D=<percent>% - W=<percent>%` and
`Copilot | <percent>%`. Unavailable quotas show a dash. Percentage text is colored directly, without dots.
Drag the ridged grip on the left to move the strip; open details close before dragging. Double-click a segment to
reopen the dashboard. Right-click for refresh, pin, dashboard, hide
and exit actions. Position, pin and desktop mode are saved. **Show desktop status bar** in Settings displays it
alongside the dashboard. It is a floating desktop window, independent of the Windows Widgets board and taskbar.
The desktop status bar stays active while the dashboard is open or hidden. The **Show dashboard** checkbox controls
only the dashboard; its change applies after **Save settings**. Closing or minimizing the dashboard hides it while the
bar remains available to reopen it.
Agent segments have subtle dividers and hover highlighting without a persistent click frame. Click an agent to open
its dark details popup above the strip (below when there
is insufficient room above on the current screen), with plans, colored
quota bars, available percentages and reset times. Hovering does not open it. Click the same agent again or anywhere
outside the popup to dismiss it. Automatic refresh updates an open popup. Dragging and double-click to reopen the
dashboard remain available. The **Status bar opacity** slider in Settings adjusts opacity from 20% to 100% immediately
and saves it.
The desktop bar refreshes automatically using the dashboard's configured polling interval, including while hidden to
tray. During a full refresh it shows **Updating providers…**, a progress bar and completed-provider count. Segments
are hidden and then revealed together after completion, including unavailable or saved-value results. Failed reads
do not keep the bar loading indefinitely. Auto-collapse is paused while loading.

## Saved usage

`%LOCALAPPDATA%/AgentMeter/saved-usage.json` retains the last measured numeric usage values and original
observation/reset
times across restarts. The dashboard restores these immediately; the bar waits for the first completed refresh.
Saved values are a fallback, not permanent access to a provider API, and always carry a saved timestamp label.
The cache excludes credentials, account details, raw responses, plan text and authentication state. A saved quota
never proves a current connection. In-session charts remain separate and start fresh after exit.
Gemini's own 24-hour in-memory fallback is distinct from this persistent cache.

## Privacy and limitations

AgentMeter never asks for passwords, stores tokens in its settings, refreshes OAuth tokens, logs credentials,
or sends credentials to another provider. Codex and Copilot own their authentication in their native processes.
Claude and Cursor credentials are read only from their own official stores for requests to fixed provider destinations;
HTTP redirects are disabled. Settings under `%LOCALAPPDATA%/AgentMeter/windows-settings.json` contain only selections,
interval, executable/project paths and desktop widget preferences. Explicitly entered OpenRouter keys are saved only
in the current user's Windows Credential Manager, with a separate Remove saved key action. Numeric saved usage is
stored separately; deleting settings files does not remove a vault entry. The app has no telemetry and does not read
prompt/conversation bodies. See the [privacy policy](Packaging/Privacy.md).

Quota endpoints and language-server extensions can change. Account access, live sign-in and quota reporting require
manual checks with the installed official agents. A successful build verifies compilation and packaging only.
The IDE plugin's weekly games are not included.

## Microsoft Store MSIX

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\windows\build-msix.ps1` on Windows with .NET SDK 10.
This packages a self-contained desktop app with MakeAppx validation enabled. Pinned Microsoft SDK build tools
are restored through NuGet; a full Windows SDK installation is not required. Vector logo assets are rendered
at 50, 44 and 150 pixels. `Packaging/StoreIdentity.json` contains the reserved Partner Center identity
`nighteviL.AgentMeter` (Store ID `9NTF08GG5569`) and its matching publisher DN.

Output: `windows/dist/AgentMeter-<version>-win-x64.msix`. The MSIX is unsigned for Store submission; local sideloading
requires a matching trusted certificate. The script does not create certificates, change certificate trust,
install the app or submit it. The build output is the MSIX file; intermediate packaging files are removed after a
successful package build. Store submission requires actual app screenshots, publicly accessible privacy/support URLs and
completed Partner
Center forms. Packaging does not submit anything to the Store.

## Build

Use Windows and .NET SDK 10:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\windows\build.ps1
```

Output: `windows/dist/AgentMeter-<version>-win-x64/AgentMeter.exe`. This is the normal self-contained Windows version;
the build does not create ZIP or runtime-dependent variants.
Pass `-Runtime win-arm64` when building for ARM64. Auto-detected ACP binaries currently target x64; ARM64 users must
configure compatible native CLI paths. The script reads the shared version from `gradle.properties` and builds only
the Windows app. It does not install, publish or build editor extensions.
See [Microsoft's single-file deployment documentation](https://learn.microsoft.com/en-us/dotnet/core/deploying/single-file/overview).

## Local checks

Only when tests are requested, run
`dotnet run --project .\windows\tests\AgentMeter.Windows.SmokeTests.csproj -c Release -- .\windows\dist\smoke`.
This runs local checks of quota field validation, destination restrictions, missing agents, disabled/disposed polling,
WPF card/status formatting, consumption/remaining colors and dashboard loading. It also renders WPF sample surfaces.
It does not read provider credentials, request real quotas, sign in, install the MSIX or submit to the Store.
Interactive sign-in, dragging, refresh timing and packaged installation need manual checks in the actual Windows
session.

Local verification reports are kept outside source control. Open
`windows/AgentMeter.Windows.csproj` as a .NET project for Rider C# indexing; the repository's Gradle project does not
index these WPF symbols. See [source structure](../docs/ARCHITECTURE.md) and [documentation index](../docs/README.md).

Login command references: [OpenAI authentication](https://developers.openai.com/codex/auth),
[Claude CLI](https://code.claude.com/docs/en/cli-reference),
[Cursor authentication](https://cursor.com/docs/cli/reference/authentication), and
[Copilot Language Server](https://github.com/github/copilot-language-server-release#authentication).

Desktop bar color styles: Midnight, Ocean, Forest, Plum and Graphite. Choose Color style in Settings or the bar context
menu; it is saved independently of the layout. Cards and Circles keep provider badges at equal heights.

### Desktop widget options

- Mini displays provider initials and percentages; detailed reset times remain available on click.
- Snap to screen edges uses an 18-pixel layout threshold on the current monitor when a drag finishes.
- Lock position disables dragging from the grip, bar background and provider segments.
- Show reset countdowns uses provider-reported reset timestamps and updates every minute.
- Show data freshness labels overdue snapshots as Stale, missing quotas as Unavailable, and unknown timestamps as
  Age unknown. Click a provider to see its update timestamp and reported failure notice.
- Collapse when not in use reduces the widget to an AgentMeter handle after a short delay. Hover expands it;
  open menus/details, loading and active dragging keep it expanded. Auto-collapse is initially off.
- Provider order in Settings offers Move up / Move down and retains disabled providers for later re-enabling.
- Color style selections include palette swatches in Settings and the right-click menu.

All display preferences save immediately and preserve provider selections and polling.

### OpenRouter

Paste your key into Settings > Providers > OpenRouter > API key and select Connect. The masked input clears immediately;
OpenRouter is enabled and refreshed automatically. Connect or Save settings stores the entered key in the current
Windows user's Credential Manager under `AgentMeter/OpenRouter`, so it is loaded after app and Windows restarts.
Remove saved key deletes that vault entry and the session copy. AgentMeter never writes the key into settings files.
Keys entered in older, session-only versions need to be entered and connected once in this version.

Alternatively connect OpenRouter through OpenCode's official `/connect` workflow; AgentMeter automatically reads
that exact entry from OpenCode's official local credential store on each refresh when neither a session key nor a saved
Windows credential is available. Removing the saved AgentMeter key does not remove OpenCode's provider connection.
The dashboard card's Connect button opens AgentMeter's key input in Settings.

A configured key budget appears as remaining percent plus remaining/limit amounts. Daily, weekly and monthly
spending and the reported reset policy appear in details. This is the budget of the connected key, potentially
shared by multiple coding clients, rather than an OpenCode-only spend total or the account credit balance.
Keys without a spending cap show No limit for the key budget, plus reported spending values; this does not indicate
unlimited account credits. Account balance requires a
separate management key, which this integration does not request. Failed reads retry at the configured interval.

References: [OpenRouter current-key API](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key)
and [OpenCode official provider credential store](https://opencode.ai/docs/providers/#credentials).

### Settings navigation

Use the tree on the left: General selects visible providers and the refresh interval; Desktop widget splits
Appearance from Behavior & order; Providers has a separate connection page for each agent/provider. Changing
pages preserves unsaved input. Widget choices and provider checkboxes apply immediately. Save settings applies
refresh intervals, executable paths and Gemini/Junie project changes. OpenRouter Connect applies the key immediately.

Provider settings pages show Connected when the reader establishes a usable or configured official connection.
For Cline/Kilo/OpenCode, inventory readiness is distinct from remote API-key validation. A pending OpenRouter key is
applied by either
Connect or Save settings. Dollar-spend rows are visible directly on the overview.
