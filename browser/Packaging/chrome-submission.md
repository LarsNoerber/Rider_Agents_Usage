# Chrome Web Store submission — AgentMeter 1.2.1

1. Open https://chrome.google.com/webstore/devconsole with the Google account that will own the extension.
2. Register as a developer, pay the one-time registration fee, verify required contact details and enable
   two-step verification as required by the developer dashboard.
3. Create a new item and upload the prepared Chrome ZIP. Edge and Chrome have separate store listings and IDs.
4. Complete the Store listing, Privacy practices, Distribution and reviewer/testing instructions.
5. Submit for review after confirming the account-dependent behavior with real provider sessions.

## Local submission files

Build: `powershell -NoProfile -ExecutionPolicy Bypass -File browser/build-chrome-store.ps1`.
An installed Chrome is used by default; `-BrowserPath` can specify another Chromium executable for rendering.
Output: `browser/dist/chrome-store/`.

| Field                      | File/value                                                                                   |
|----------------------------|----------------------------------------------------------------------------------------------|
| Package                    | AgentMeter-1.2.1-chrome.zip                                                                  |
| Name                       | AgentMeter                                                                                   |
| Language                   | English                                                                                      |
| Description                | Store description plus Additional privacy wording in edge-listing-en.md; both browsers apply |
| Category                   | Select the current category that best fits developer productivity                            |
| Icon                       | logo-128.png                                                                                 |
| Small promotional tile     | promo-small-440x280.png                                                                      |
| Screenshots                | screenshot-bar-1280x800.png and screenshot-settings-1280x800.png                             |
| Homepage                   | https://github.com/larsnoerber/Rider_Agents_Usage/tree/main/browser                          |
| Support                    | https://github.com/larsnoerber/Rider_Agents_Usage/issues                                     |
| Privacy policy             | https://github.com/larsnoerber/Rider_Agents_Usage/blob/main/browser/PRIVACY.md               |
| Single purpose/permissions | edge-privacy-fields.md; the same packaged permissions apply                                  |
| Remote code                | No; executable code is bundled                                                               |
| Testing instructions       | edge-certification-notes.txt; provider account requirements apply to Chrome too              |

Disclose locally processed data, even without an AgentMeter server: provider account/organization identifiers,
authentication/session information, transient tab-URL inspection and provider usage-page content. Select the
corresponding personal information, authentication information, web history and website content categories under
the current dashboard definitions. No conversation collection, advertising, sale or credit scoring occurs.
Review exact form wording before certifying the disclosures.

The Chrome ZIP uses the same MV3 code and version as the Edge build. Only the 128px icon artwork gains transparent
padding recommended by Chrome. The package includes no Microsoft store identity or manifest key.
Screenshots render the actual UI with labelled sample state, not live quota evidence. Generated files remain
ignored build output. No GitHub release, version tag, store submission or account payment is performed by the build.

References: [registration](https://developer.chrome.com/docs/webstore/register),
[publishing](https://developer.chrome.com/docs/webstore/publish),
[image requirements](https://developer.chrome.com/docs/webstore/images),
[privacy disclosures](https://developer.chrome.com/docs/webstore/cws-dashboard-privacy).
