# Accounts, authentication and review

Applies to AgentMeter 1.1.0 across Rider/JetBrains, Windows desktop, Chrome/Edge, Visual Studio Code and
Visual Studio. These editions have different integrations; their login sessions are not interchangeable.

## What can be reviewed without a provider account?

AgentMeter has no separate AgentMeter account or central login service. Basic settings, provider selection,
appearance, unavailable states and other account-independent UI can be reviewed without provider credentials.
Real subscription quota reads require a supported provider account, the corresponding sign-in and any required
subscription, license or organization access. A signed-in connection does not guarantee reported quota values.

For a store form asking whether reviewers need accounts or additional information to test all functionality,
answer **Yes** when submitting a quota-reading edition. Explain the provider prerequisites and give the relevant
steps below. Do not claim that all functionality can be checked without authentication.

No shared provider accounts or passwords are distributed with this repository. Reviewers can use their own
provider-controlled accounts. Explain this in certification notes: provider accounts may be tied to personal
subscriptions, organization access and multi-factor authentication. A store can still request dedicated test
accounts or further evidence; this explanation does not guarantee certification. If a test account is provided,
share its credentials only through the store's private reviewer channel, never through GitHub or public listings.

## Prerequisites by edition

| Edition            | Quota access / sign-in requirements                                                                                                                                                                                    | Setup                                                                                             |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Rider / JetBrains  | Codex CLI installed and signed in; AI Assistant/Copilot installed and signed in for their optional IDE quota services. Claude/Cursor integrations need the supported local agent/integration and its provider session. | [Rider guide](../README.md), [provider integration details](DEVELOPMENT.md#provider-integrations) |
| Windows desktop    | Official CLIs, supported local provider stores or installed app sessions, depending on provider. OpenRouter uses an explicitly entered API key. Each provider has its own sign-in path.                                | [Windows provider setup](../windows/README.md#providers-and-sign-in)                              |
| Chrome / Edge      | Sign in on the provider's official website and grant AgentMeter access to that website. Optional all-site access enables the overlay on ordinary websites.                                                             | [Browser setup](../browser/README.md), [Edge submission](../browser/Packaging/edge-submission.md) |
| Visual Studio Code | Codex CLI installed and signed in; GitHub authentication through VS Code for Copilot quota requests.                                                                                                                   | [VS Code guide](../vscode/README.md)                                                              |
| Visual Studio      | Codex CLI installed and signed in; Copilot signed in within Visual Studio and its optional quota service available.                                                                                                    | [Visual Studio guide](../visualstudio/README.md)                                                  |

Optional providers must not be treated as installed merely because AgentMeter is installed. Browser sign-in does
not establish a CLI or IDE session. An API-key login does not necessarily expose subscription allowance.
See the [provider/data matrix](PROVIDERS.md) for the actual integrations in each edition.

### Windows provider distinctions

- Codex: sign in through the official CLI; subscription quotas require the appropriate account mode.
- Claude Code: use its official authentication flow; API-key access and subscription OAuth are distinct.
- Cursor: use the official Cursor Agent session.
- Copilot: use the supported local provider connection described in the Windows guide.
- Gemini: CLI/Workspace, Gemini Apps and API quotas are separate. Workspace access may need a Cloud project and
  organization permissions; signing into the desktop app does not sign into the CLI.
- OpenRouter: enter a key with access to the reported key budget/spending. With the user's explicit authorization,
  the Windows app can retain it in current-user Windows Credential Manager under `AgentMeter/OpenRouter`.
  Use **Remove saved key** to remove it. Do not place keys in settings files, logs, screenshots or review documents.
- Kilo, Cline and OpenCode: official agent configuration/sign-in establishes their connections; recorded local
  costs/tokens are separate from remaining subscription quota. A configured connection is not remote validation.
- Junie CLI: use its own CLI connection and the appropriate project directory for saved statistics. A Rider login
  may not establish a standalone Junie session. Only reported balances/statistics are shown.

## Manual review procedure

These are instructions, not a claim that these checks have passed.

1. Use the package for the target edition and follow its installation guide. Configure the required official
   integrations or executable paths; signing in and installing providers remain separate actions.
2. Select one supported provider and authenticate through its official application, website or CLI as appropriate.
   For the browser edition, grant its website access as well.
3. Refresh AgentMeter and compare values, direction, plan and reset times with the same provider's own quota view.
   Codex/Claude quota is remaining; Copilot/GitHub and Cursor quota is consumed. See the provider matrix for
   edition-specific Gemini values and local usage/budget distinctions.
4. Check missing or unreported quotas. They must not become invented zeroes or inferred allowances. For Claude's
   web integration, a Free account may not report percentages; missing quota alone does not identify its plan.
5. Change provider selection and verify that deselected providers stop reading. Browser selection also controls
   bar visibility. Windows activation and segment visibility remain separate choices.
6. Check the target edition's settings, themes, warnings, detail views and available status/bar controls. For the
   browser, check automatic saving, move/reset position, collapse/expand and provider links on a permitted website.
7. Check saved readings and account changes. Original observation times must remain visible; a cached number must
   not establish authentication. Clear saved usage after switching accounts before comparing results.
8. Check expired/rejected sessions and unavailable integrations. Permission failures, API changes, rate limits and
   unavailable quota services should produce a clear notice. Protected browser pages cannot show the page bar.
9. Record the edition/build, provider/integration version, account tier (without account identity), action and
   observed result. Redact credentials, personal identifiers and private content from evidence.

Real accounts can incur provider usage or costs. Reviewing AgentMeter's quota display does not require submitting
model prompts or generating extra usage merely to change a quota percentage.

## Evidence and limitations

- Build/package success checks compilation or packaging, not live authentication, every UI interaction or store
  acceptance. Editions must be reviewed separately.
- Browser packaging checks JavaScript syntax/local references/assets. The user reported working OpenAI and GitHub
  readings during development; this does not validate every plan, account, browser version or provider.
- Store presentation screenshots render the actual browser UI with explicitly labelled demo state. They are not
  evidence of authenticated quota reads, persistence or successful drag interactions.
- Latest browser drag/settings behavior and complete signed-in provider coverage still need live review.
- [Windows verification](../windows/TESTING.md) separates current evidence from historical automated results.
- Existing tests may be run only when requested. No new test execution is implied by this documentation update.

## Authentication, data and privacy disclosures

Do not describe the project as processing no user data just because it has no AgentMeter server. Depending on
edition, readers process provider quota data, official sessions and temporary account/organization identifiers.
The browser additionally inspects permitted tab URLs to select a reader and reads provider usage-page content.
Disclose the relevant categories and purpose in the store's actual form. No conversation collection, credential
logging or cross-provider credential forwarding is part of AgentMeter's quota function.

Provider sessions remain owned by the official provider. Credentials are not committed, included in screenshots,
or persisted by AgentMeter, except for the explicitly authorized Windows OpenRouter Credential Manager feature.
See [browser privacy](../browser/PRIVACY.md), [Windows privacy](../windows/Packaging/Privacy.md), and the applicable
provider's own terms/privacy information. Do not transfer authentication assumptions between editions.

## Store reviewer notes

Use edition-specific notes with installation, account dependencies and quota limitations. The ready-to-paste
[Edge certification notes](../browser/Packaging/edge-certification-notes.txt) fit the observed 2,000-character
Partner Center field. Their explanation is specific to the browser package; do not paste browser permissions or
controls into the Rider, desktop or Visual Studio submissions.

Reference: [Microsoft Edge reviewer-account policy](https://learn.microsoft.com/en-us/legal/microsoft-edge/extensions/developer-policies#13-product-is-testable).
