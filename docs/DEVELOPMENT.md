# Local development

## Chrome / Edge browser extension

Use Node.js 22+ and Windows PowerShell. Build only the explicitly requested browser edition:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\browser\build.ps1
```

The script reads `pluginVersion` from `gradle.properties`, checks JavaScript syntax and module/asset references,
then creates `browser/dist/unpacked/` and `browser/dist/AgentMeter-<version>-chromium.zip`. Load the unpacked directory
manually through Chrome/Edge's Extensions page in Developer mode. It does not install/publish the extension or build
Windows/editor packages. Live signed-in page/API reads and popup UI are not established by successful packaging.
See [browser setup and limitations](../browser/README.md). Tests remain opt-in.

Prepare the Edge Add-ons submission files with `powershell -NoProfile -ExecutionPolicy Bypass -File
.\browser\build-store.ps1`. It builds only the browser package, renders a 300×300 logo from the shared SVG using
the existing supported-subset WPF renderer, and captures the actual UI with explicitly labelled demo data using
a separately profiled headless Edge/Chrome. The generated fixtures and screenshots never enter the extension ZIP.
The output is `browser/dist/edge-store/`; code/listing/privacy sources are versioned, generated files are ignored.
This is store artwork rendering, not a provider authentication check or test run.

Use JDK 25. The JetBrains Runtime bundled with a compatible IDE can provide it.
The project targets IntelliJ IDEA 2026.1.2 and produces Java 21 bytecode. Plugin verification also checks Rider
2026.2.3.1.
Commands below are run from the repository root; replace the sample JDK path with your own installation.

Build requests default to the Rider plugin only (`buildPlugin`). Build Visual Studio Code and Visual Studio
packages only on explicit request. Use `buildAllExtensions` only when all editor packages are explicitly requested.

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-25'
.\gradlew.bat buildPlugin
```

Run `.\gradlew.bat verifyPlugin` to check binary compatibility with the configured Rider build.

The installable archive is `build/distributions/agentmeter-<version>.zip`.
Install it through **Settings > Plugins > Install Plugin from Disk**.

## Visual Studio Code extension

Install Node.js 22 or later, then run these commands from the repository root:

```powershell
Set-Location vscode
npm install
npm run compile
npm run package
```

The installable VS Code package is `vscode/agents-usage-vscode-<version>.vsix`. Install it using **Extensions: Install
from VSIX...**. `npm run package` compiles the TypeScript extension and creates the VSIX.
The VS Code Marketplace publisher ID is `lanoerber`, so the extension ID is `lanoerber.agents-usage-vscode`.
The manifest author is `nightevil`, matching the publisher's Marketplace display name.
The GitHub repository remains `larsnoerber/Rider_Agents_Usage`.

To build all editor packages supported on the current operating system from the repository root, run:

```powershell
.\gradlew.bat buildAllExtensions
```

This produces the JetBrains plugin ZIP under `build/distributions/` and the VS Code VSIX under `vscode/`.
On Windows it also builds the Visual Studio VSIX, requiring Visual Studio MSBuild as described below.
The extensions use the same version from `gradle.properties`; keep `vscode/package.json` synchronized when
preparing a release.

## Visual Studio extension

Use Windows with Visual Studio 2022 or 2026 MSBuild and a compatible .NET SDK. The package uses .NET Framework
4.7.2, stable Visual Studio 2022 SDK APIs and NuGet-provided reference assemblies and VSIX build tools.
The project explicitly imports the VSSDK targets after the .NET SDK targets to generate the VSIX during Build.
Newtonsoft.Json is explicitly included in the package because the VSSDK normally excludes this assembly.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\visualstudio\build.ps1
```

The script reads `pluginVersion` from `gradle.properties`, builds without installing, and writes
`visualstudio/dist/agents-usage-visualstudio-<version>.vsix`. A custom MSBuild path can be passed with `-MSBuildPath`.
It also creates `visualstudio/dist/marketplace-<version>/` and
`agents-usage-visualstudio-marketplace-<version>.zip`, containing the VSIX, logo, Markdown/HTML overview, illustrated
images, publish manifest and ready-to-copy web upload fields. See `visualstudio/Marketplace/README.md` for web/CLI
publishing. The web uploader does not automatically replace the full overview from a VSIX.
Regenerate illustrated images with `python tools/render_visualstudio_preview.py` (Pillow required).
The corresponding Gradle task is `buildVisualStudioExtension`. Open the package with Visual Studio's VSIX Installer,
then use **View > Other Windows > AgentMeter**. See [../visualstudio/README.md](../visualstudio/README.md).
The manifest targets 2022/2026 Community, Professional and Enterprise on x64. Other versions and architectures are
not claimed. Codex and optional Copilot quota reporting are supported. No live account or cross-version IDE check is
implied by a successful build.
The Visual Studio VSIX `Identity.Publisher` is `nightevil`, matching the Marketplace publisher display name.
The Marketplace publisher ID remains `lanoerber`. Starting with 1.0.15, the VSIX ID is
`lanoerber.AgentsUsage.VisualStudio.82441438-356a-4f3a-a82f-47bc9e090b7e` and the listing internal name is
`agents-usage-visualstudio`. This migration was explicitly approved after deletion of the old listing and rejection
of its legacy ID. Old installations must be uninstalled before installing the new identity. Preserve the new ID and
listing name for future updates. The build derives upload identifiers from the actual source/publishing manifests.

To launch the isolated development IDE with the current plugin:

```powershell
.\gradlew.bat runIde
```

The development IDE uses its own sandbox settings. Open a project to see the status widgets.
JetBrains AI and Copilot must be installed and signed in within that instance to expose their optional widgets.

To compile Kotlin without packaging:

```powershell
.\gradlew.bat compileKotlin
```

## Standalone Windows app

To diagnose Gemini quota access from the published executable itself, run
`AgentMeter.exe --diagnose-gemini`. It performs one read without opening the dashboard and writes
`%LOCALAPPDATA%/AgentMeter/gemini-diagnostic.json`, containing quota percentages, sign-in state, update time and a safe
notice (or exception types). It never exports credentials, raw responses or exception messages. Exit code 0 means
quota values were reported; 1 means unavailable. Leave Gemini running to exercise locked-store access.

Use Windows and .NET SDK 10. Build the portable, self-contained WPF executable explicitly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\windows\build.ps1
```

The output is the self-contained `windows/dist/AgentMeter-<version>-win-x64/AgentMeter.exe`; no ZIP or
runtime-dependent variant is created. Use `-OutputDirectory` for a separate output folder or `-Runtime win-arm64`
for the ARM64 build.
The shared version comes from `gradle.properties`. This command builds only the Windows app; Gradle's editor
extension tasks keep their existing scope. No IDE or separately installed .NET runtime is needed to launch the
published executable. Official signed-in agents are needed for quota data.
See [../windows/README.md](../windows/README.md).
For Rider's C# indexing and semantic refactoring, open `windows/AgentMeter.Windows.csproj` as a .NET project;
opening the repository's Kotlin/Gradle project does not include these WPF sources in its project model.
Window behavior is organized into partial files beside the original XAML; retain their namespace and event handler
names. App/tray artwork comes from `windows/Packaging/AgentMeter.svg`; the asset renderer supports the shape and
gradient subset used by this icon.
Build success does not verify live authentication, quota endpoints or UI behavior. Do not run account logins
automatically.

For the Microsoft Store package, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\windows\build-msix.ps1`.
It writes `windows/dist/AgentMeter-<version>-win-x64.msix`, using the reserved identity in
`windows/Packaging/StoreIdentity.json`. MakeAppx validates and packages the self-contained desktop app. The pinned
Microsoft SDK BuildTools NuGet dependency supplies the packaging executable. The unsigned package is for Store
upload; local sideloading needs a matching trusted certificate. Store submission is separate from build and local
checks.

## Gradle build files

| File                                       | Purpose                                                                                           |
|--------------------------------------------|---------------------------------------------------------------------------------------------------|
| `gradlew.bat`                              | Windows entry point; downloads and starts the pinned Gradle distribution.                         |
| `gradlew`                                  | Equivalent entry point for Linux/macOS; invoke with `sh ./gradlew buildPlugin` if needed.         |
| `gradle/wrapper/gradle-wrapper.jar`        | Wrapper bootstrap required by both scripts.                                                       |
| `gradle/wrapper/gradle-wrapper.properties` | Pins Gradle 9.3.0, its distribution SHA-256, download locations, and a 30-second network timeout. |
| `gradle.properties`                        | Central project name, plugin group/version, Kotlin code style, and Gradle daemon settings.        |
| `.gitattributes`                           | Keeps the POSIX wrapper in LF and the Windows wrapper in CRLF.                                    |

These files are required for building from a clean checkout without a global Gradle installation.
The wrappers are generated by Gradle; regenerate them rather than changing their startup logic manually.
`build.gradle.kts` and `settings.gradle.kts` read the project identity from `gradle.properties`.
For a release, update `pluginVersion` there and the matching `CHANGELOG.md` entry.
Avoid committing machine-specific JDK paths; set `JAVA_HOME` in your local environment instead.

## Before editing

Chrome store preparation: `powershell -NoProfile -ExecutionPolicy Bypass -File .\browser\build-chrome-store.ps1`.
It uses the same checked MV3 sources, renders the shared SVG with Chrome's recommended icon padding and a
440×280 promotional tile, and places generated upload files under `browser/dist/chrome-store/`.
No store submission, payment, version change or release is performed.

Read [ARCHITECTURE.md](ARCHITECTURE.md).
Keep provider details within their feature folders, and update `plugin.xml` whenever a registered class moves.
Preserve existing user edits and the persisted settings identifiers.

Rider's semantic refactoring requires indexed source files in a loaded solution. If the IDE cannot index this Kotlin
Gradle project, source refactoring must update package declarations, imports, and plugin registrations together.
The Gradle compiler is the source of build diagnostics in that case.

## Provider integrations

### OpenAI Codex

`providers/codex/CodexUsageReader` runs the local `codex app-server --stdio` process.
It initializes the connection, reads account information, and requests `account/rateLimits/read`.
`CodexUsageParser` converts the response to the displayed quota snapshot.
The CLI must be installed and signed in. The current transport uses Windows `where.exe` and `cmd.exe` when needed.
The reader owns the active process and its timeout; disposal also stops child processes.

### JetBrains AI

`providers/jetbrainsai/JetBrainsAiUsageReader` reads the loaded AI Assistant quota service.
It handles both direct quota values and the current `Available`/`Reached` wrappers around `JbaiQuota`, as well as
the `Known` refill wrapper. Credit conversion comes from AI Assistant itself. The reader isolates subscription
metadata lookup, mangled Kotlin getters, and module class-loader compatibility. API failures become an unavailable
balance rather than an invented quota.

`JetBrainsAiSubscriptionReader` prefers the activation manager's selected license: AI Access exposes the product
display name through `JcpLicense.reported`, while legacy activation exposes `AipLicense.productType` and trial/pack
flags. The auth facade is a compatibility fallback; its generic `JbaiOther` marker is not a concrete subscription.
Only product metadata is read. Neither credentials nor account identifiers are accessed or logged.
Activation manager, auth facade, and protocol metadata can each live in separate content modules. The subscription
reader resolves their loaded module class loaders, using the same shared compatibility helper as the quota reader.
Registered application-service instances are preferred over the Compose service-locator facade. Plan lookup uses
the published activation snapshot, with the live manager as a compatibility fallback. It reads the selected
license's display name or product code, legacy active license journeys, and concrete subscription tiers. Workspace
access is labeled AI Workspace because it does not expose a personal tier. A failed lookup includes only safe API
metadata and exception types in the overview details, so a missing name can be diagnosed without accessing account
identifiers, tokens, headers, or license secrets.

### GitHub Copilot

`providers/copilot/GitHubCopilotUsageReader` reads Copilot's quota service and its last report time.
If that IDE service is unavailable, it runs the native language server from Rider's installed `github-copilot`
ACP package and requests `checkQuota` after LSP initialization. The server reads its own authentication store.
This fallback does not start an agent session or prompt and does not export a token to AgentMeter. Native processes
are bounded by a timeout and disposed after each quota request. The protocol is internal and may change.
Premium request billing can expose exact counts; credit billing uses the reported remaining percentage.
Only quota categories actually reported by Copilot are shown.
Free plans use the chat quota as their included allowance, matching Copilot's own quota dialog. Under token billing
this is labeled AI credits. The premium field is omitted for Free plans because it can report a zero placeholder
while the included allowance is fully available. Paid plans use premium interactions as their primary quota.
`CopilotQuota` preserves those raw remaining values and derives `percentUsed = 100 - percentLeft` for presentation.
Both the status indicator and overview show consumed quota: 0% unused, 100% exhausted. Used/remaining counts stay in
tooltips, colors warn as the balance decreases, and unlimited quotas are not given an exhaustion percentage.

AI Assistant and Copilot APIs are internal and may change with provider updates.
`core/reflection/` caches getter lookup and discovers only loaded provider plugins.
These integrations use the installed plugins' existing connections; no additional usage-reporting server is introduced.

### Cursor and Claude (JetBrains)

Configuration lists missing ACP packages beneath their provider checkboxes and directs the user to Rider's ACP
Registry. There are no installation buttons. Cursor uses its ACP package; Claude can also use their
native IDE integrations. Installing a package does not authenticate the account.
The Usage overview hides providers without an installed agent package; Claude can also use their loaded
native IDE plugins. Status widgets additionally require their reader's usage source.
The quota/history readers reuse provider-local sources: Cursor Agent auth and
Claude Code's subscription credentials and session files. Tokens are read anew for
quota requests and are never refreshed,
logged or persisted.
Cursor Agent stores auth under `%APPDATA%/Cursor/auth.json` on Windows, `~/.cursor/auth.json` on macOS,
and `$XDG_CONFIG_HOME/cursor/auth.json` or `~/.config/cursor/auth.json` on Linux.
Reader destinations are fixed to `cursor.com`, `api2.cursor.sh`, or `api.anthropic.com`, with no
redirects.
Quota requests use internal APIs and need signed-in account checks after compilation. Claude's request includes the
CLI-compatible user-agent header described by the referenced Claude usage implementation. macOS Keychain-only Claude
subscription quota reads require a readable official credentials file. A read-only `auth status --json` probe of
the installed native Claude SDK distinguishes API-key ACP logins from subscription OAuth. API-key accounts show
local monthly tokens and their authentication mode; Pro/Max quota percentages are not applicable.

Provider protocols and storage formats are checked against the installed official packages. The provider-detail
layout uses the supplied screenshot and independently implemented Swing charts.

Chart history is collected in background provider services, independently of provider panels, and retains only
numeric observations for 24 hours. Refresh state reports successful local reads; it cannot verify completion of an
internal IDE provider's asynchronous network update.
Clicking a provider's subscription badge toggles its details and starts a countdown repaint timer when shown;
hiding/disposal stops it. Live IDE checks should cover
narrow/light/dark layouts, absent quotas, reset boundaries, 1/6/24-hour charts, visibility toggles, and repeated
refreshes
without boss damage. Weekly/Game activation choices and section expansion choices are persisted independently.

### Visual Studio Code providers

The VS Code package reads Codex quota through the local Codex CLI app-server and reads Copilot quota from
`https://api.github.com/copilot_internal/user` using the VS Code GitHub authentication session. This Copilot endpoint
is internal, not a supported public extension API. The extension shows only provider-reported quota, with no local
activity estimate. Its quota sidebar is a `WebviewView` to allow theme-aware colored percentages and bars; provider
selection, CLI path, refresh interval, and status-bar visibility are managed through contributed VS Code settings.

## Checks and delivery

Standalone Windows checks are documented in [../windows/TESTING.md](../windows/TESTING.md).
Run the smoke project only when tests are requested. It uses synthetic quota snapshots; account login, interactive
desktop actions and packaged installation require separate manual verification.

### Visual Studio providers and status UI

Copilot uses the optional installed brokered quota service via reflection against loaded contracts. Missing or
changed APIs return an unavailable state. No authentication/token properties are read. Known paid plans use premium
quota without substituting unlimited basic chat for a missing premium balance. AI credit billing suppresses counts.
The application coordinator refreshes selected providers while the view is open or status display is enabled.
The colored status control uses a shell WPF insertion point isolated in `UI/StatusBarHost`, and requires a manual IDE
check. If the insertion point changes it does not replace the shell's normal status text. Toolbar/toolwindow images
use `AgentsUsage.imagemanifest` and stable image GUID/ID values.

Use the Gradle task appropriate to the requested work and report its actual result.
Do not add or run tests unless requested. The existing standalone Windows smoke project is opt-in; its historical
results and current limitations are documented in [Windows verification](../windows/TESTING.md).
Build success checks compilation and packaging; it does not verify live quota APIs, account state, or UI behavior in
Rider.
Do not publish, install, commit, or restart the user's IDE unless requested.

## Source-only GitHub updates

An authorized source update can commit/push code and documentation without changing `gradle.properties`, creating a
version tag or publishing a release. Exclude generated build/dist folders, portable EXEs, MSIX files and personal IDE
settings. Local Windows build artifacts and source commits are separate deliverables. See the
[documentation index](README.md) and [provider matrix](PROVIDERS.md).

Keep ideas, concept artwork and working notes local: use the ignored root folders `ideas/`, `concepts/`, `notes/`
or `local/`. The existing `docs/IDEAS.md`, `design/icon-concepts/`, `filled-usage-preview.png` and old
`browser/downloads/` packages are also local-only. Keep source assets, user guides, architecture, build instructions
and Store publishing sources versioned. Distribute generated packages through release assets rather than source commits.

Coding-agent instruction files, configuration folders and sessions are local-only and ignored by Git.

## Consolidated 1.2.1 delivery

The requested release packages are collected under `dist/releases/1.2.1/` (ignored by Git), with one folder per
Store plus `windows-portable/`. The root contains a ZIP of the upload materials for each Store, the portable
Windows ZIP, `AgentMeter-1.2.1-all-store-packages.zip`, upload instructions and SHA-256 checksums. Upload the inner
plugin ZIP, browser ZIP, VSIX or MSIX specified by each folder's instructions; the material bundles themselves
are for transfer/extraction. The unsigned Microsoft Store MSIX uses version `1.2.1.0`.

`buildAllExtensions -x test`, `windows/build.ps1`, `windows/build-msix.ps1` and
`browser/build-chrome-store.ps1` built the current release. Compilation/packaging completed; Windows emitted
nullable warnings from the shared Visual Studio transport and Gradle's headless platform emitted compatibility/
theme warnings. No test suite or live provider authentication check was run. Store certification and actual Store
submission are separate from the GitHub release.
