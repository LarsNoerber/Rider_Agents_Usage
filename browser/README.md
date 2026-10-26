# AgentMeter for Chrome and Edge

A standalone Manifest V3 extension with a floating AgentMeter bar, quota details and a compact toolbar badge.
The bar shows selected, permitted providers together. Click a provider to open its website; use **▾** for quota
details and **›** to cycle the details provider. **−** collapses the bar. Settings control theme, top/bottom position,
visibility, provider activation and refresh interval. The bar's **⚙** opens the **Page bar** configuration section.
Missing measurements show dashes, never invented zeroes.
The toolbar popup identifies the tab in the window where it was opened. Use its **Provider** selector to view
any enabled provider, including on unrelated sites. If no current provider is recognized, the popup defaults to
the first selected provider. Clicking the toolbar icon grants temporary active-tab URL access; quota reads still
require the separate website permission and a valid provider session.
The bar has no trailing generic availability label; hover a provider or open its details for availability and
saved-value information.
In **Providers & page bar**, select providers once for both refresh and display. Deselecting a provider hides it
and stops its reads. All settings save automatically, with a visible saving/saved/error status; there is no Save
button. Existing activated providers become visible after website access is granted, including providers previously
hidden by the separate bar selection. An empty selection hides the bar.
Drag the **⠿** handle to move the bar, or focus it and use arrow keys (Shift moves farther). Its relative position
is remembered across websites and clamped to the viewport on resize. **Reset bar position** restores the selected
top/bottom default; changing that default also clears the custom position.
Hover a provider for its last observation time, cache state and missing-value notice. Details show a live reset
countdown with the exact time in its tooltip. A due reset prompts a refresh; it never fabricates a new quota.
Configure **Warn at remaining quota (%)** to change percentage colors in the bar, popup and toolbar badge.
Amber starts at the selected threshold; red starts at 10% or the warning threshold if lower. GitHub still shows
consumption, while its warning level uses the remaining allowance. Warnings are visual, not system notifications.
Gemini and Claude show session and weekly values; Codex keeps `OpenAi | D=<percent>% - W=<percent>%`.

## Installation / updating

### Release downloads

All editions share version **1.2.2**. Download the browser packages from the
[GitHub release](https://github.com/larsnoerber/Rider_Agents_Usage/releases/tag/v1.2.2):

- `AgentMeter-1.2.2-chromium.zip` for Edge.
- `AgentMeter-1.2.2-chrome.zip` for Chrome.

For manual installation, extract the package and select its folder with **Load unpacked** on the browser's
Extensions page after enabling Developer mode. Store acceptance is separate from these release downloads.

Chrome Web Store submission drafts are kept locally.
`powershell -NoProfile -ExecutionPolicy Bypass -File browser/build-chrome-store.ps1` creates the Chrome ZIP,
padded 128px icon, required 440×280 promotional tile, screenshots and submission documents in
`browser/dist/chrome-store/`. It does not register/pay for an account or submit the extension.

Edge Add-ons submission drafts and reviewer correspondence are kept locally.
`powershell -NoProfile -ExecutionPolicy Bypass -File browser/build-store.ps1` creates the extension ZIP, store logo,
labelled UI demonstration screenshots and copy-ready submission documents in `browser/dist/edge-store/`.
This prepares local files; it does not submit to Microsoft, create a GitHub release or upload build artifacts.

1. Build from the repository root:
   `powershell -NoProfile -ExecutionPolicy Bypass -File .\browser\build.ps1` (Node.js 22+ and PowerShell).
2. Enable Developer mode at `edge://extensions` or `chrome://extensions`.
3. **Load unpacked**: select `browser/dist/unpacked`. For updates, click the extension's Reload button.
4. First install opens Settings. Choose providers, then click **Allow website access & show bar everywhere** and
   accept the browser permission prompt. Existing installations can open Settings from the extension popup.
5. Sign in on the selected providers' websites. Reload existing website tabs after updating the extension.

The same package works with Chrome and Edge. Output: `browser/dist/AgentMeter-<version>-chromium.zip`.
This is an unpacked developer package; building does not install or publish it. The shared version is retained.

## Website permission and the persistent bar

All-site display is optional and requires HTTP/HTTPS host access granted by a user click. It allows the bar to
appear on ordinary websites, even when no provider page is open. Only selected providers are queried, and the
bar reads no conversations or page content on other websites. All-site permission also covers those providers'
usage requests. Manage broad website access in the browser's extension settings. Individual provider permissions remain
available for users who want the display only on provider websites. Deselecting a provider pauses its reads.

Protected browser pages (`edge://`, `chrome://`, extension stores and some built-in viewers) do not allow overlays.
The bar is inside each website and may cover content; collapse it or change its position when needed. It is not
a native browser toolbar strip. Pinning the extension adds a short numeric badge; the native badge cannot hold
the full multi-provider bar.

## Quota reads

Selected, permitted providers refresh even if their Usage page is not open. One open provider tab is used when
available, including background tabs. When provider tabs are closed, a bundled offscreen extension document
uses the browser session for fixed quota requests and detached HTML parsing. No hidden provider tab is opened.
The browser must remain running. Expired sessions, provider protection or browser cookie policies can reject reads.

| Provider       | Reader                                                                                      | Meaning                                    |
|----------------|---------------------------------------------------------------------------------------------|--------------------------------------------|
| OpenAI Codex   | Website session + `/backend-api/wham/usage`, explicit Usage-page fallback                   | Remaining session/weekly quota             |
| Claude         | `/api/organizations` and the selected organization's `/usage`, explicit Usage-page fallback | Remaining session/weekly/Sonnet/Opus quota |
| Cursor         | `/api/usage-summary`, explicit DOM fallback                                                 | Consumed quota                             |
| GitHub Copilot | Fixed personal billing and Copilot Features pages; explicit reported percentages            | Consumed quota                             |
| Gemini Apps    | Private usage RPC bootstrapped from `/usage`, explicit DOM fallback                         | Consumed session/weekly quota              |

Claude background reads require one unambiguous chat-capable organization; Console organizations are excluded.
With multiple chat organizations, select the organization
and keep a Claude tab open. GitHub's dynamically loaded or changed billing pages may still provide no supported
quota. Copilot also accepts explicit consumed/allowance count pairs and embedded quota snapshots. Its
Features reader recognizes **Included credits** and **Inline suggestions**, including Free plans. The bar shows
the included-credits consumption first; inline suggestions remain a separate quota in the details. Its
quota-related lazy HTML fragments referenced by GitHub itself are read with same-origin/path limits. Claude's
fixed Usage page is also checked when its JSON response supplies no supported value. A null/unreported quota
is distinguished from an unsupported response format; neither becomes a fabricated zero. Claude's missing-quota
notice explains
that [its documented usage bars](https://support.claude.com/en/articles/9797557-usage-limit-best-practices)
apply to paid subscription plans and a Free account may not report percentages. Missing data does not establish
the subscription plan or authentication status. Billing costs and
activity totals without an explicit allowance are not converted into quota percentages. Unlimited/unreported quotas
stay unavailable. DOM fallback requires a recognized quota label, one percent and explicit used/remaining wording.

Polling defaults to five minutes (configurable one minute to one hour). All reads have a minimum one-minute interval;
HTTP 429 pauses a provider for 15 minutes. Browser sleep may delay alarms. Saved fallback measurements keep their
original time and do not establish authentication. Clear saved usage after switching accounts.

## Checks and limits

Packaging checks JavaScript syntax, local module references and assets. Live signed-in account reads and UI behavior
have not been validated here; no tests were requested or run. Private provider interfaces can change or reject reads.
Local CLI history, native messaging, OpenRouter keys, JetBrains AI and Windows integration are not included.

See [privacy](PRIVACY.md), [architecture](../docs/ARCHITECTURE.md) and [development](../docs/DEVELOPMENT.md).
Browser references: [optional permissions](https://developer.chrome.com/docs/extensions/reference/api/permissions),
[offscreen documents](https://developer.chrome.com/docs/extensions/reference/api/offscreen),
[script injection](https://developer.chrome.com/docs/extensions/reference/api/scripting).
