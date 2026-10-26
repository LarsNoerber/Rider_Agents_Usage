# Source reading guide

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
  an obsolete batch.

`UsageInsightsService` keeps its persisted daily fields, provider event handling, battle damage, snapshot
composition and forecasting in their original order. Comments mark these responsibilities; expanded provider
field mappings make the start/low/time getter-setter pairs visible. The overview renders that state and owns
its transient animations without recording additional battle damage.

## Comment and layout conventions

Use English comments alongside the existing English product strings. Explain intent, ownership, units, trust
boundaries and ordering constraints that are not obvious from the statements themselves. Avoid restating simple
assignments or decorating every method with a heading.

Keep one state transition per line when several operations must occur in order. Wrap long provider lists and
getter/setter mappings so their relationships can be checked visually. Keep checks, work, publication and cleanup
visibly separated. Preserve string contents, IDs, settings names and event bindings during readability work.

Keep provider-specific behavior in its provider folder and shared behavior in its existing core/application/UI
layer. Larger extractions should follow the
[architecture](ARCHITECTURE.md) and use indexed IDE refactoring where available.
