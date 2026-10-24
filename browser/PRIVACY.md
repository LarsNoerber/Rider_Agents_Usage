# Browser privacy

AgentMeter browser extension · maintained by Lars Nörber · updated October 9, 2026.
Privacy questions and support: https://github.com/larsnoerber/Rider_Agents_Usage/issues

AgentMeter queries only selected providers after browser website access is granted. Optional individual provider
permissions allow limited access; optional HTTP/HTTPS permission enables the display on ordinary websites and
allows the selected providers' requests. The first install opens Settings. Browser permission prompts require a
user click. Disabling a provider pauses its reads; removing access clears readings and removes the affected display.

Clicking the toolbar icon grants temporary activeTab access to identify the current provider from its URL.
This does not replace the website permission required for provider requests or the persistent page bar.
The popup can also display a selected provider when the current website is unrelated or its URL is unavailable.

The page bar runs in the extension's isolated world and displays sanitized quotas in a closed shadow root. It
reads no website content, cookies or conversations. It can request sanitized state, refresh, and fixed provider
links, and save only validated display coordinates after the user moves the bar. Website scripts cannot invoke
these controls or submit measurements, credentials or settings. There is no external-message interface.

Provider readers use browser-owned sessions only for requests to that same provider. An available provider tab
runs an isolated reader; otherwise a bundled offscreen document fetches the fixed usage destinations. OpenAI's
website session token is held briefly in memory for its own quota request, then discarded. Claude organization
identifiers and its selected-organization cookie are used only to route its own quota read. Gemini bootstrap
fields remain in the reader. No passwords or API keys are requested. Credentials, identifiers, raw responses and
page text are never returned to the display, logged or persisted. Redirects are rejected and responses bounded.
Provider sessions may expire or reject extension requests.

Local browser storage contains provider choices, appearance, warning threshold, the bar's relative position,
numeric quota measurements and their observation/reset times. Provider-page URLs are inspected in memory to
select a supported reader and display the appropriate quota; browsing history is not stored or sent to AgentMeter.
Session storage contains sanitized readings, public plan labels, fixed notices, fixed diagnostic read stages/HTTP
status codes and polling timestamps. Diagnostic fields contain no URLs, response text or identifiers. Storage
is not synchronized to a cloud account. **Clear saved usage** removes numeric and session readings. Uninstalling
removes extension storage. Saved values do not establish current sign-in and can belong to a previous account;
clear them after account changes.

There is no telemetry, remote code, AgentMeter backend or automatic credential transfer between providers.
Requests go only to the fixed selected provider origins; third-party page content is not a data source. The browser
must remain running for polling. Private website interfaces and markup may change; unsupported quotas remain
unavailable. Provider privacy policies continue to apply to their website sessions and usage services.

Provider quota readers process usage-page content and authentication/session information as described above.
Local-only storage does not mean that no data is accessed: quota requests contact the selected provider using
its existing session. Data is not sold, used for advertising, or transferred to unrelated third parties.

AgentMeter's use of information obtained from Google APIs complies with the Chrome Web Store User Data Policy,
including its Limited Use requirements. Data is used only for the disclosed quota-display function; it is not
used for advertising, credit scoring or unrelated profiling.
