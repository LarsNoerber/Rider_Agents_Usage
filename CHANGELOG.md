# Changelog

## 1.2.1 ? 2026-10-24

- Align Rider/JetBrains, Windows, Chrome/Edge, Visual Studio Code and Visual Studio packages on version 1.2.1.
- Improve comments and source layout across all five applications, with a reading guide and documented open review findings.
- Include the current Rider refresh reliability, quota compatibility, background history and bounded response handling improvements.
- Include the current browser popup provider selector, active-tab context and store packaging improvements.
- Prepare separate Store upload packages and listing materials, plus a GitHub release with checksums.

## 1.2.0 — 2026-10-22

- Improve source readability across the five applications: explain refresh generations, cache/authentication
  boundaries and UI lifetimes; expand dense state changes and provider mappings. Add a source reading guide and
  record outstanding static-review findings. Runtime behavior and persisted identifiers are unchanged.

- Make the JetBrains Marketplace upload ZIP a copy of the built plugin with its required `agentmeter/lib/`
  structure. Package listing text and artwork separately as `agentmeter-marketplace-media-1.2.0.zip`.
- Fix opening the JetBrains overview/configuration after the refresh-status update: initialize the Swing
  accessibility context through its getter before assigning the status label name.
- Restore JetBrains AI quota readings for current AI Assistant versions, which wrap credit values in `Available` /
  `Reached` quota states and refill data in `Known`.

- Improve JetBrains/Rider refresh handling: discard obsolete selections, show refresh progress and last successful
  readings, and retain clearly marked stale values across provider failures. Distinguish known sign-in and API
  compatibility problems without treating cached measurements as authentication.
- Collect numeric chart history in provider services, including when only status widgets are used.
- Read appended Claude session logs incrementally, with bounded lines, less frequent directory scans and handling
  for partial writes, truncation and file replacement.
- Limit JetBrains quota HTTP responses to 2 MiB while receiving the response.
- Add an illustrated status bar excerpt to the JetBrains Marketplace overview and as separate media in the upload
  bundle.

## 1.1.1 — 2026-10-13

- Bind the browser popup to its own active tab and restrict background active-window lookup to normal browser
  windows. Use temporary activeTab access when the toolbar icon is clicked. Add a provider selector and
  selected-provider
  fallback so quota access does not depend on recognizing the current site; refresh after website access is granted.
  Update Edge reviewer guidance following the October 9 certification report. Live Store reproduction remains pending.

- Prepare Chrome Web Store submission guidance and a local Chrome package/artwork build with a padded 128px icon,
  required 440×280 promotional tile and labelled UI screenshots. Retain version 1.1.0 and clarify transient tab-URL
  inspection in the browser configuration, listing material and Limited Use privacy statement.

- Document provider-account prerequisites and manual review steps centrally for every AgentMeter edition, including
  reviewer-account handling, privacy disclosures and current evidence limitations. Link the guide from the project
  overview, documentation index and provider matrix; shorten Edge reviewer notes for the 2,000-character form.

- Prepare Edge Add-ons listing text, privacy/permission disclosures, reviewer notes and store submission guidance
  for the existing Partner Center entry. Add a local store build with a shared-SVG logo and actual UI screenshots
  labelled as demonstration data. Keep generated submission files outside source commits and retain version 1.1.0.
- Clarify browser privacy for transient provider-page URL inspection, session authentication and saved display
  position. Add a homepage URL and remove the unused activeTab permission from the browser manifest.

- Unify browser provider activation and bar visibility into one selection, as requested. Retain the persisted
  barProviders identifier synchronized with selected providers. Replace the Save button with automatic saves for
  every configuration control, a visible status and serialized merged patches; simplify the configuration sections.
- Add per-provider observation/cache/error tooltips, live reset countdowns and an adjustable remaining-quota
  warning threshold shared by the browser bar, popup and toolbar badge. Due resets never invent new values.
- Add a draggable, keyboard-accessible bar handle with a remembered relative position, viewport clamping and
  a reset-position action. Permit isolated bars to update only validated display coordinates.
- Explain Claude's missing numeric quotas and possible Free-plan limitations without inferring the account plan.

- Save browser provider activation immediately and select newly enabled providers for the page bar. Show a
  "Hidden in page bar" link beside enabled providers omitted by the independent bar visibility selection.

- Save browser Page bar visibility, position and provider selections immediately on change. Serialize settings
  writes and apply the bar before confirming a save; quota requests continue without delaying the settings reply.

- Read GitHub Features' Included credits and Inline suggestions quotas, matching the supplied Free-plan page.
  Show consumed included credits as the bar's primary quota and keep inline suggestions as a separate detail row.
  Accept quota labels in bold/table/description elements and read explicit plan badges in detached usage HTML.

- Name the browser provider GitHub in the bar, popup, Settings and diagnostic labels. Retain its persisted
  provider ID and quota interpretation.

- Prevent offscreen quota HTML from loading provider scripts, module preloads, stylesheets or font resources.
  Remove executable/resource markup before parsing into detached template contents; retain inert JSON quota
  data and passive GitHub fragment references. Keep the existing extension CSP unchanged.

- Use GitHub Copilot Features as the primary browser usage link and first request. Prefer open provider Usage
  tabs over unrelated tabs on the same domain, including inactive Usage tabs, and read their quota fragments.
  Keep the active provider's badge synchronized when a different tab supplies its quota.

- Remove the trailing generic unavailable/saved label from the browser bar. Keep missing values as dashes and
  report provider-specific availability/cache state in link tooltips and quota details. Action errors open details.

- Extend Claude browser reads with explicit JSON negotiation, numeric quota-string parsing, authentication-error
  envelope detection and a fixed Usage-page fallback even when that page is closed. Distinguish null/unreported
  allowances from an unsupported response schema. Read quota-related GitHub include-fragment references from
  billing pages with same-origin/path restrictions and a four-fragment bound; retain inert HTML resource blocking.
  Signed-in validation of Claude and Copilot is still pending.

- Add independent provider visibility checkboxes under Page bar in browser Settings. Hidden providers continue
  refreshing when enabled under Providers. Persist the bar selection, preserve all providers for existing profiles,
  and allow an empty visibility selection without resetting it to defaults.

- Group browser bar visibility, all-site display and position under Page bar on the configuration page.
  Add a gear button on the bar that opens this section directly, and distinguish popup style from bar appearance.

- Open provider links in the browser bar with native hyperlinks in a new tab. Keep the clicked element in place
  during navigation, support middle-click/context-menu opening, and use fixed catalog destinations without a
  background-message dependency.

- Resolve Claude chat organizations separately from Anthropic Console organizations in browser quota reads.
  Route Codex requests with the account bound to its website session when available. Read Copilot's explicit
  consumed/allowance count pairs and embedded quota snapshots on fixed billing pages. Keep background reads
  across tab/focus changes and expose fixed read stages/HTTP status codes. Live account validation is pending.

- Add first-install website access setup and an optional persistent AgentMeter overlay on ordinary websites.
  Show selected providers together; clicking a provider opens its website, with separate quota details controls.
  Read selected providers from open/background tabs or a bundled offscreen reader when provider tabs are closed.
  Add OpenAI Codex quota and Claude organization usage requests using the browser session, retain Gemini weekly
  values, throttle reads and keep credentials/raw responses out of storage. Live account validation is pending.

- Read Copilot's personal billing overview and Features page in the browser edition, recognize GitHub AI Credits
  headings, and use the documented billing overview for the usage link. Reconcile page-bar scripts on worker reload
  and distinguish missing sections, percentages and quota direction using fixed notices without exporting page text.

- Show Gemini Apps 5-hour and weekly consumption together in the browser page bar and toolbar tooltip, including
  an explicit dash for any unreported period.

- Add an automatic collapsible AgentMeter bar inside granted provider websites, with colored session/weekly
  percentages, expandable quota bars/plans/reset times, refresh and usage-page actions. Support top/bottom placement,
  theme and an off switch. Use the existing measurements/cache, label saved/unavailable data, remove the bar on
  provider deselection/access revocation, and isolate it from page styles and privileged extension commands.

- Keep the browser toolbar percentage visible on a provider's ordinary pages after a usage read. Show both
  Codex session/weekly values and the observation time in the icon's hover title. Color percentage text directly,
  distinguish saved values in gray, and document pinning the extension beside the address bar.

- Recognize German Codex usage-limit headings and Unicode quota direction words in the browser edition. Read
  through deeper bounded card wrappers, queue a new active-page read after an interrupted tab switch, and show
  an explicit notice when no reading was published. Live OpenAI page validation remains pending.

- Add a separate Manifest V3 Chrome/Edge edition showing usage for the active provider page. Include optional
  per-provider access, Codex/Claude/Copilot page adapters, Cursor/Gemini same-origin web readers, colored quota bars,
  compact/circle views, theme/refresh settings and numeric saved-value fallbacks. Package locally without installing
  or publishing. Signed-in provider reads and browser UI still require manual validation.

- Refresh repository/edition guides, provider comparison, architecture, build instructions, privacy and publishing
  drafts for the current Windows code. Distinguish historical smoke reports from current build evidence. Keep shared
  version 1.1.0; source-only GitHub updates exclude generated EXE/MSIX artifacts and personal IDE settings.

- Reorganize Windows window/coordinator code by responsibility, centralize provider IDs and presentation metadata,
  separate settings persistence and OpenCode parsing, and move the Copilot device protocol into its provider folder.
  Add comments explaining cancellation generations, cached authentication, batch presentation and cleanup; preserve
  saved settings and existing XAML bindings. Detach bar-selection handlers during window shutdown.
- Use the supplied SVG as the Windows EXE/window/tray icon and as the source for Store logo assets. Render icon
  sizes directly from the vector source instead of resizing the previous raster image.

- Show themed refresh text and a progress bar in the Windows desktop widget. Hide provider segments during each
  full refresh and publish the completed batch together. Keep unavailable/saved-value results visible after the
  batch completes, prevent auto-collapse while loading, and avoid showing partial startup data.

- Detect OpenCode Desktop's bundled/versioned CLI and support its version 2 JSON connection/statistics formats
  alongside version 1 text reports. During Junie/OpenCode/Cline/Kilo sign-in, poll the selected provider briefly in
  the background so Connected updates without waiting for the interactive login terminal to close.

- Add OpenCode and Junie to the standalone Windows dashboard, provider settings tree, independent bar selection
  and every widget layout. Detect installed official CLIs and Rider ACP packages. OpenCode uses official auth
  inventory and 30-day local statistics; Junie uses an advertised built-in ACP statistics command for reported
  balance and saved project usage. Add official sign-in actions and optional executable/project paths.
  Authentication stays in each official CLI; Junie polling never submits a model task.
- Retain the last measured Windows usage values locally across restarts and temporary read failures. Restore
  them immediately at startup, show their original time and label them as saved values. Never restore connection
  status from cached usage; credentials, account details and raw provider responses are excluded.

- Detect Rider's bundled ACP Node runtime for the Windows Cline connection probe, even when Node is absent from
  the standalone app's PATH. Also recognize standard Node installation folders.

- Keep Windows configuration usable during official CLI sign-in. Detach the UI from the external login window,
  refresh immediately and again when the command ends, and bound the background wait without terminating the
  user's CLI. Use cmd /c for command wrappers so a completed auth command does not leave an idle shell keeping
  AgentMeter in its login state.

- Persist explicitly entered Windows OpenRouter keys in the current user's Windows Credential Manager, as requested.
  Load the saved key after app restarts, keep it out of settings files, and replace Clear session key with Remove
  saved key. Connect and Save settings both save it; report vault write/delete failures explicitly. Existing OpenCode
  authentication remains a fallback after removing the AgentMeter credential.

- Add a separate Providers shown in bar checkbox list under Windows Settings > Desktop widget > Appearance,
  persisted independently of provider activation. Apply selection immediately and preserve existing bar selection
  until the user customizes it; inactive providers remain disabled in the bar selection.
- Fix Cline/Kilo connection labels independently of usage availability: query Cline's installed official SDK for
  enabled, configured providers and Kilo's official credential inventory, including BYOK connections. Show Connected
  without requiring local usage or a Gateway balance; keep credentials within the official agent process.

- Keep a connection action on every Windows provider settings page, including Cline. Launch Cline's official
  `auth` flow through PATH or an installed Rider ACP package; allow an optional executable path. Existing Cline
  history never substitutes for verified authentication or hides its sign-in action.

- Add Windows Kilo and Cline providers with separate configuration pages, selection and all desktop widget layouts.
  Kilo uses the installed official CLI for active account/team balance and 30-day local costs/tokens, without reading
  credentials into AgentMeter. Cline reads only numeric usage from its local session database in read-only mode.
  Show money/tokens without invented percentage limits; local history is not labeled as a verified connection.
  Preserve saved provider selections and auto-detect Kilo from existing Rider ACP installations.

- Show Connected on Windows provider configuration pages after verified sign-in, preserving known connection
  state across transient failures. Accept pending OpenRouter keys via Save settings as well as Connect.
- Display OpenRouter daily/weekly/monthly spending directly on the overview. Label uncapped key budgets No limit
  in all widget layouts instead of an empty percentage, without treating account credits as unlimited.

- Replace the long Windows settings form with a dark tree navigation: General, Desktop widget Appearance/Behavior,
  and an individual page for each provider. Keep inputs intact across page changes and route OpenRouter Connect
  directly to its masked key input. Add provider-specific official sign-in actions to connection pages.

- Add a masked OpenRouter API-key field with Connect and Remove saved key controls. Use disposable session copies
  and explicitly saved current-user Windows credentials, activate OpenRouter on Connect, and refresh automatically.
  Legacy OpenCode credentials remain a fallback; no API key is serialized into settings.

- Add an optional Windows OpenRouter integration using its documented current-key API and the exact OpenRouter
  entry in OpenCode's official credential store. Show remaining key-budget percentages, reported dollar limits,
  daily/weekly/monthly spend and reset policy without inventing account balances or reset timestamps.
- Include OpenRouter in provider selection, widget layouts, ordering and click details, with a connection guide
  when no local key is available. Preserve existing selected providers and keep credentials out of settings/cache,
  with explicitly entered keys retained only in the current-user Windows credential vault.

- Render Windows context-menu color swatches as visual content instead of displaying the control type name.

- Add a Windows Mini widget layout, optional reset countdowns and explicit stale/unavailable data labels with
  timestamps and provider notices in click details.
- Add persisted screen-edge snapping, position locking, provider ordering and delayed hover expansion/collapse;
  keep expanded widgets open while their menu/details are in use and stop collapse timers on shutdown.
- Show palette swatches in Windows widget color dropdowns and context menus. Keep display preferences independent
  of provider polling and use the existing dashboard timer for time labels.

- Respect dropdown display labels in the Windows dark theme, showing only layout/color style names instead of
  internal record fields and color values.

- Keep Windows Cards and Circles provider badges at equal heights and align circle meters consistently.
- Add five persisted desktop bar color styles (Midnight, Ocean, Forest, Plum and Graphite), independently selectable
  from the layout in Settings or the context menu. Apply palettes to bar surfaces, badges, tracks and quota meters
  while preserving amber/red exhaustion warnings.

- Add five persisted Windows desktop widget styles: Classic, Compact, Cards, Circles and Vertical, selectable
  immediately from Settings or the bar's context menu. Use a fully dark context-menu template, including checkmarks,
  separators and nested style selection, without the system's white icon gutter.
- Add a Gemini diagnostic mode to the actual Windows executable, reporting only safe quota metadata and exception
  types, so locked-store access can be checked in the packaged runtime.
- Refresh Gemini Apps quotas while its desktop app remains open, using a read-only duplicate of the app's cookie
  file handle and a bounded SQLite snapshot in RAM. Retry temporary failures automatically and retain last reported
  quota values with their original timestamp and a stale-data notice; keep unrelated CLI setup advice in details.
- Preserve known Gemini sign-in state when its running desktop app locks the local cookie store.
- Open the installed Gemini desktop app from its Sign in button (Edge/web fallback if absent), with a separate CLI
  sign-in action in details. Distinguish rejected quota cookie requests from the desktop app's own login state.
- Let the Gemini Apps quota response establish authentication when the page omits its session token; report rejected
  sessions accurately and try other local Edge profiles instead of stopping at rejected Gemini app cookies.
- Use software rendering for the Windows dashboard and transparent widget, and defer tray/quota startup until the
  window can paint its first frame to avoid blank black startup windows.
- Match the Windows quota cards to the compact Rider layout, with broad horizontal bars, visible two-column details,
  distinct subscription badges, charcoal surfaces and a high-contrast history range dropdown.
- Keep Gemini marked as signed in when its local app session exists, and explain when Google no longer supplies the
  undocumented usage-page session token needed to read quota data.
- Limit Windows release outputs to the normal self-contained app and Store MSIX; stop producing portable ZIPs,
  lightweight runtime-dependent EXEs and duplicate Store staging packages.
- Use the transparent Quota Portal artwork for Windows app, tray and Store icons.
- Refresh the Windows dashboard with a compact black theme, grouped settings, quieter controls and tighter quota cards.
- Keep supplemental badge details free of duplicated current quota values and remove Grok from the Windows app.
- Fall back to Gemini Apps web usage from only the installed Gemini app's session cookies when CLI quotas are absent.
- Read Gemini session cookies from Edge profiles too, and correctly convert Chromium cookie expiry timestamps.
- Read Gemini CLI Google/Workspace model quota buckets, remaining counts, reset times and reported Code Assist plan.
  Reuse only the official local CLI OAuth store; add CLI sign-in and configurable Workspace Cloud project ID.
- Add a ridged left-hand desktop drag grip; dismiss quota details before dragging and save the new position.
- Keep desktop agent buttons borderless after clicks and highlight them only on hover.
- Remove the dark logo tile and retain transparency in EXE, window, tray and packaged logo assets.
- Open desktop quota details below the bar when there is insufficient room above it on the current monitor.
- Open desktop quota details on agent click above the bar; disable hover popups and retain live refresh while open.
- Add an optional small Windows EXE that uses an installed .NET 10 Windows Desktop Runtime.
- Add expandable Windows subscription badges with quota detail tiles, reset countdowns and observed 1/6/24-hour charts.
- Improve Windows dropdown/menu contrast and desktop tooltips with dark surfaces and colored quota bars.
- Separate desktop agent segments with dividers and save adjustable status-bar opacity.
- Hide Windows sign-in actions for authenticated agents and reuse their persisted official sessions after restart.
- Use AgentMeter branding for the EXE, dashboard and system tray; close/minimize to tray and add explicit Exit actions.
- Refresh the Windows dashboard with dark window chrome and remove JetBrains AI from the standalone provider list.

- Clarify consumed quota with the available percentage in standalone provider cards and desktop tooltips.

- Package the standalone app as a Microsoft Store MSIX with the reserved AgentMeter identity.
- Add a movable desktop status bar with colored percentages, provider selection, saved position and an always-on-top
  option.

- Add a portable standalone Windows app with OpenAI, Copilot, Claude and Cursor quota cards, plans, reset countdowns,
  provider selection, automatic refresh and an always-on-top option.
- Reuse official local agent sign-ins and provide official agent login buttons, including Copilot device authorization.
  Missing agents open installation documentation.

## 1.1.0 — First stable Marketplace release

- Monitor OpenAI Codex, JetBrains AI, GitHub Copilot, Claude and Cursor in the usage overview and status bar.
- Show subscription plans, provider-reported quotas, reset countdowns and local usage charts; click a badge to toggle
  details.
- Configure provider visibility and refresh intervals. New Rider installations enable only JetBrains AI.
- Optional weekly insights, quota forecasts, boss battles with visible hits, and Tic-Tac-Toe; Weekly and Games are off
  by default.
- Use existing local provider sign-ins without telemetry or credential storage in plugin settings.

## Development previews

Versions below document development builds before the first stable Marketplace release.

## 1.0.19

- Reuse ACP sign-ins for Copilot quotas, Cursor usage and Cline account credits. Show Claude's API connection or
  local tokens when subscription quotas do not apply.
- Default new Rider installations to JetBrains AI only, with Weekly and Games disabled. Preserve saved choices.
- Hide missing agents from Usage and the status bar; show missing ACP package information in configuration.
  Remove installation buttons.
- Toggle provider details and usage charts by clicking the subscription badge; remove the separate details link.
- Visualize boss hits with the provider and points, an impact flash and shake. Persist damage and count each observed
  quota point as 4 damage points without replaying repeated refreshes.
- Add Tic-Tac-Toe with saved scores and configurable Games visibility.
- Fix the usage chart initialization crash and reading Cline's local session history.

## 1.0.18

- Improve wrapping of weekly forecasts, battle events and help text in narrow Tool Windows.

## 1.0.17

- Preserve weekly boss progress across plugin updates and restarts.
- Refresh selected providers when Usage opens and improve Marketplace previews.

## 1.0.16

- Add collapsible weekly quests with rotating bosses, quota forecasts, provider parties and reset celebrations.
- Add Quota Portal branding and improve the weekly view at narrow widths.

## 1.0.15

- Change the Visual Studio extension identity for the new Marketplace listing. Uninstall version 1.0.14 or earlier
  before installing this version.

## 1.0.14

- Add Visual Studio Copilot quotas, subscription plans and reset times.
- Add the Visual Studio toolbar icon, clickable status indicator and Marketplace package.

## 1.0.13

- Add a native Visual Studio 2022/2026 extension with Codex quotas and provider settings.
- Fix Visual Studio packaging and Marketplace publisher identifiers.

## 1.0.12

- Add the packaged VS Code extension with Codex/Copilot usage views and status indicators.
- Improve settings navigation, quota selection and colored usage bars.

## 1.0.11

- Add Copilot usage reporting and provider settings to VS Code.

## 1.0.10

- Reduce unnecessary Codex restarts and UI updates.

## 1.0.9

- Verify Rider 2026.2.3.1 compatibility and improve plugin unload/reload handling.

## 1.0.8

- Add compact provider summaries and expandable account details.
- Improve JetBrains AI subscription detection and unavailable-state explanations.

## 1.0.7

- Correct Copilot Free quota selection and keep enabled status widgets together.
- Improve compatibility with JetBrains AI subscription APIs.

## 1.0.6

- Add provider cards, subscription badges and persisted visibility checkboxes.
- Show quota-colored percentages directly in compact status widgets.

## 1.0.5

- Improve status indicators and feedback for stale OpenAI usage.

## 1.0.4

- Show Copilot consumption from 0% unused to 100% exhausted, including unlimited quota categories.

## 1.0.3

- Show remaining OpenAI and JetBrains AI quota percentages in the status bar.

## 1.0.2

- Improve JetBrains AI plan detection and compatibility with optional provider plugins.
- Organize provider integrations and shared UI components.

## 1.0.1

- Add subscription labels and compact usage rows with smaller status widgets.

## 1.0.0

- Monitor OpenAI Codex, JetBrains AI and GitHub Copilot quotas in Rider and IntelliJ IDEA.
- Show subscription plans, quota bars, reset times and independent status widgets.
- Support manual refresh, configurable intervals and automatic Codex CLI discovery.
