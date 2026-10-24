# Partner Center privacy fields

## Single purpose

Display provider-reported AI subscription usage quotas and reset times in a browser bar and popup, with
user-selected providers and visual warnings for low remaining quota.

## Permission justifications

### activeTab

Identify the current provider from the active tab URL when the user clicks the AgentMeter toolbar icon.
This temporary access avoids treating an unreadable URL as an unsupported site. Background provider requests and
the persistent bar still require separately granted website access. No browsing history is saved or exported.

### storage

Save provider selection, appearance, bar position, warning threshold and refresh interval locally. Cache only
numeric quota observations and reset times. Session storage holds sanitized readings and polling metadata.
No credentials, account identifiers, conversations or raw responses are persisted.

### alarms

Schedule periodic background quota refreshes at the user's selected interval while the browser is running.

### scripting

Register and inject the bundled, isolated page-bar script on permitted websites. Inject provider-specific,
bundled quota readers into permitted provider tabs to read usage information using the existing website session.

### offscreen

Use a bundled offscreen document to request and parse provider quota data when no suitable provider tab is open.
It runs packaged code only and returns sanitized quota values and fixed diagnostic notices.

### Optional provider host permissions

Access chatgpt.com, claude.ai, cursor.com, github.com and gemini.google.com only after the user grants access.
Each enabled provider's reader requests that provider's fixed quota endpoints using its existing browser session
and can read recognized quota sections on its usage pages. Credentials are not sent to another provider.

### Optional https:// */* and http:// */*

Display the same movable quota bar on ordinary websites when the user explicitly requests all-website display.
The overlay does not read content, forms, conversations or cookies on unrelated websites. Broad access also
covers the selected provider origins, but network requests remain restricted to their fixed quota endpoints.
Browser internal pages and extension stores are not supported.

## Remote code

No. All executable code is packaged with the extension. Provider responses are data, not executable code.
Fetched HTML is stripped of executable and external-resource markup and parsed in detached template contents.
The extension CSP allows only packaged scripts. There is no eval, downloaded executable script or remote backend.

## Data usage — disclose access even when nothing is sent to an AgentMeter server

If the portal asks which categories are accessed, collected, used or transmitted, disclose **website content**
(provider usage pages/quotas) and **authentication information** (provider session cookies and the transient
OpenAI website session token). Do not claim that no user data is processed merely because storage is local.
Provider URLs are inspected locally to choose a quota reader; no browsing history is persisted or exported.
If asked specifically about browsing activity, disclose this transient URL inspection with that limitation.

The data supports the stated quota-display purpose only. No sale, advertising, unrelated profiling, telemetry,
external analytics or unrelated third-party transfer occurs. Credentials and raw responses are not logged or stored.
Read the portal's exact category definitions before selecting its checkboxes and certify only accurate statements.

## Privacy policy URL

https://github.com/larsnoerber/Rider_Agents_Usage/blob/main/browser/PRIVACY.md

## Support URL

https://github.com/larsnoerber/Rider_Agents_Usage/issues
