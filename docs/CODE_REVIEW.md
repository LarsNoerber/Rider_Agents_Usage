# Source review and reading guide

Review date: October 24, 2026. This is a static source and readability review, with no version change.

## Scope and evidence

The repository inventory covered 206 tracked source, build-script and layout files (17,894 lines before this
cleanup), including Kotlin, C#, JavaScript, TypeScript, PowerShell, Python, XAML, HTML and CSS. Generated outputs,
dependencies, binaries and artwork were excluded. Existing working-tree edits were included in the baseline and
preserved. The untracked Store artwork script was left untouched.

Repository-wide scans checked file sizes, dense lines, review markers and imports across core boundaries. Detailed
manual inspection concentrated on refresh coordination, provider transport/parsing, numeric caches and UI
lifetimes in all five applications, plus packaging entry points. The inventory and scans are not evidence that
every line or runtime path has been exhaustively verified.

The cleanup changes comments and layout in 25 source files. A comparison with the working-tree baseline checks
that their code tokens remain identical after ignoring comments, whitespace and optional Kotlin semicolons.
This is a textual check, not compilation or a semantic equivalence proof. All six edited browser JavaScript files
also passed `node --check`; Git's whitespace check passed with Windows CRLF line endings accounted for.
No tests, builds, application launches,
provider requests, account logins, installation or publication were performed. Rider reported the edited files
outside its current solution, so its analysis/formatter could not validate them; layout was edited directly.
There were no symbol renames, signature changes or package moves requiring semantic refactoring.

## Where to start reading

| Application       | Entry/composition                                            | Refresh and publication                                                                                                         | Presentation                                                                    |
|-------------------|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Rider / JetBrains | `src/main/resources/META-INF/plugin.xml`                     | `src/main/kotlin/io/github/larsnoerber/agentsusage/application/UsageRefreshCoordinator.kt`, then `core/refresh/UsagePolling.kt` | `ui/toolwindow/AgentsUsagePanel.kt`, `ui/components/` and provider-owned panels |
| Windows           | `windows/App.xaml.cs`, `windows/UI/MainWindow.xaml.cs`       | `windows/Application/UsageRefreshCoordinator.cs` and its `.Readers` / `.Login` parts                                            | `windows/UI/DesktopWidgetWindow.*`, `UI/Components/` and `MainWindow.*`         |
| Browser           | `browser/manifest.json`, `browser/application/background.js` | `browser/application/coordinator.js`, then `background-reader.js` / `offscreen.js`                                              | `browser/ui/popup.js`, `dashboard.js`, `page-bar.js`                            |
| VS Code           | `vscode/src/extension.ts`                                    | The activation function's shared `refresh` closure and `codex.ts` / `copilot.ts` readers                                        | `vscode/src/usageView.ts` and status items in `extension.ts`                    |
| Visual Studio     | `visualstudio/AgentsUsagePackage.cs`                         | `visualstudio/Application/UsageRefreshCoordinator.cs`                                                                           | `visualstudio/UI/UsageControl.cs`, `UI/StatusBarHost.cs`                        |

Paths in the Rider row after the full coordinator path are relative to its Kotlin package root. Windows partial
files deliberately keep the window namespace and XAML handler names. Read the main partial file for shared fields
and composition, then the part owning the action in question.

Build entry points are separate: `build.gradle.kts` for editor tasks, `windows/build.ps1` / `build-msix.ps1` for
Windows, `browser/scripts/package.mjs`, `vscode/scripts/package.mjs` and `visualstudio/build.ps1` for the respective
extensions. See [development commands](DEVELOPMENT.md) before running one.

## Reading the refresh paths

Follow a refresh through selection/access checks, background provider work, generation validation, cache handling
and UI delivery. The comments now explain the checks at the points where asynchronous work can become obsolete.

- Rider's `UsagePolling` serializes each reader. A forced refresh arriving during a read remains pending. The
  shared publication lock protects selection changes, history and publication; queued EDT delivery checks the
  generation again. Fresh failure metadata and retained numeric measurements are separate inputs.
- Windows uses one semaphore for full batches, targeted connection reads and Copilot sign-in. Providers inside a
  full batch run concurrently. The `.Readers` part applies numeric fallback and checks generation again on the
  dispatcher. The widget buffers those updates until the full batch finishes.
- The browser coalesces events into one read loop, prefers an existing Usage tab and otherwise uses the offscreen
  reader. It pins injected readers to a document, rechecks navigation/selection/access, sanitizes output and keeps
  numeric persistence separate from session state. Storage patches read current settings inside the write queue.
- VS Code shares one in-flight refresh promise. Visual Studio additionally tracks settings revisions and retries
  an obsolete batch. The difference is relevant to the first finding below.

`UsageInsightsService` keeps its persisted daily fields, provider event handling, battle damage, snapshot
composition and forecasting in their original order. Comments mark these responsibilities; expanded provider
field mappings make the start/low/time getter-setter pairs visible. The overview renders that state and owns
its transient animations without recording additional battle damage.

## Outstanding findings

These functional issues were found by source inspection and remain open. Fixing them would change runtime behavior
and is separate from this comment/layout cleanup. None has been reproduced in a running application here.

### VS Code: obsolete refresh results can publish after settings change or disposal

In `vscode/src/extension.ts`, `refresh` captures enabled providers and CLI path before awaiting the provider reads.
A configuration event during the read calls `refresh` again, which returns the existing promise. There is no
settings revision or pending rerun. The old batch then calls `view.setUsage` with its old selection and can leave
the sidebar showing a newly disabled provider until the next refresh. The same completion path has no lifetime
check before updating disposed status items.

Suggested correction: capture a settings generation, queue one replacement batch when choices change, and reject
publication after disposal. Coordinate reader cancellation with that lifetime. The Rider, Windows and Visual
Studio coordinators already contain examples of generation/revision checks.

### Windows Gemini: retained web quota carries the old successful authentication state

In `windows/Providers/Gemini/GeminiWebQuotaReader.cs`, `RetainReportedQuota` returns
`lastReported with { Notice = ... }`
for up to 24 hours. A successful parsed snapshot has `SignedIn = true` and `IsCached = false`; the fallback changes
neither field. `GeminiReader.ReadWorkspaceAsync` returns this fallback when quotas exist, and the snapshot store
treats its nonempty quotas as a measured result. A later failed web read can therefore publish old usage with a
successful authentication flag and without the cache flag, despite the warning notice and original timestamp.

Suggested correction: keep the original measurement time, mark retained values as cached, and take authentication
from the current read rather than the saved successful snapshot. Make sure stale fallbacks do not become fresh
measurements when passed through the shared cache path.

### VS Code Copilot: request lifetime and response size are unbounded by the reader

In `vscode/src/copilot.ts`, `fetch` has no abort signal or reader deadline, and `response.json()` reads the whole
body without a size limit. `dispose()` sets a flag for future reads but cannot abort an active request. A stalled
request can hold the shared refresh promise; a large response can consume excessive memory. Runtime/network
defaults may eventually fail the request, but AgentMeter supplies no explicit bound in this path.

Suggested correction: use a reader-owned cancellation source with a deadline, abort it during disposal and enforce
a response-byte limit before parsing, following the bounded transports in the other editions.

## Comment and layout conventions

Use English comments alongside the existing English product strings. Explain intent, ownership, units, trust
boundaries and ordering constraints that are not obvious from the statements themselves. Avoid restating simple
assignments or decorating every method with a heading.

Keep one state transition per line when several operations must occur in order. Wrap long provider lists and
getter/setter mappings so their relationships can be checked visually. Keep checks, work, publication and cleanup
visibly separated. Preserve string contents, IDs, settings names and event bindings during readability work.

Keep provider-specific behavior in its provider folder and shared behavior in its existing core/application/UI
layer. The import scan found no direct provider, UI or settings imports in the scanned core directories; it does
not establish the absence of indirect dependencies. Larger extractions should follow the
[architecture](ARCHITECTURE.md) and use indexed IDE refactoring where available.
