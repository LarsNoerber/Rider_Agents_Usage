# Providers and data by edition

This matrix describes the checked-in code, not promises of availability from external services. Missing integrations,
expired sessions or changed provider interfaces produce unavailable results. A connection can exist without usage.

| Provider               | Rider / JetBrains                    | Windows desktop                     | VS Code         | Visual Studio   |
|------------------------|--------------------------------------|-------------------------------------|-----------------|-----------------|
| OpenAI Codex           | Remaining quota                      | Remaining quota                     | Remaining quota | Remaining quota |
| JetBrains AI Assistant | IDE remaining credit quota           | Not included                        | Not included    | Not included    |
| GitHub Copilot         | Consumed quota                       | Consumed quota                      | Consumed quota  | Consumed quota  |
| Claude Code            | Subscription quota / recorded tokens | Subscription quota / connection     | Subscription quota | Subscription quota |
| Cursor                 | Consumed quota                       | Consumed quota                      | Consumed quota | Consumed quota |
| Gemini                 | Android Studio chat remaining quota  | CLI/Workspace / Apps quota          | Not included    | Not included    |
| OpenRouter             | Not included                         | Key budget / spending               | Not included    | Not included    |
| Kilo                   | Not included                         | Gateway balance / local usage       | Not included    | Not included    |
| Cline                  | Not included                         | Configured connection / local usage | Not included    | Not included    |
| OpenCode               | Not included                         | Configured connection / local usage | Not included    | Not included    |
| Junie CLI              | Not a separate quota reader          | Reported balance / project usage    | Not included    | Not included    |

## Meaning of the values

Account prerequisites and manual reviewer steps across all editions are documented in
[Accounts, authentication and review](AUTHENTICATION_AND_REVIEW.md).

The separate [Chrome/Edge edition](../browser/README.md) reads selected providers after website permission is granted:
Codex/Claude remaining quota, Copilot/Cursor consumed quota, and Gemini Apps web consumption. It uses provider website
sessions, private quota requests and explicit DOM fallback. Background and closed provider tabs are supported via a
bundled offscreen reader. Live signed-in account validation remains pending. It does not reuse local CLI credentials,
Windows settings, desktop cache or local agent statistics. An optional overlay displays providers on ordinary websites.

- Remaining percentages shrink with consumption; Copilot/Cursor consumption grows toward exhaustion.
- Costs/tokens from local agent records are usage estimates, not remaining subscription allowance.
- OpenRouter reports the connected key's budget/spending, potentially shared across clients. No limit means no
  spending cap on that key; it does not mean unlimited account credit.
- Kilo Gateway balance is separate from BYOK provider connections and local session costs.
- Junie's all-time statistics apply to the selected project on this machine; balance is shown only when reported.
  Junie CLI login/data are distinct from Rider AI Assistant's internal quota service.
- Cline/Kilo/OpenCode inventory can establish configured readiness without remotely validating every credential.
- Saved Windows usage retains its original time and never establishes current sign-in. Charts are session-local.

## Windows connection paths

Official CLIs own their authentication. Discovery includes PATH/common folders, existing JetBrains ACP packages,
OpenCode Desktop's bundled/versioned CLI and Rider's bundled Node runtime. Missing agents open installation guides;
nothing is installed automatically. Provider pages keep a sign-in action even when no local account exists.
Entered OpenRouter keys are the explicit exception: the current-user Windows Credential Manager retains them.

The bar has independent visibility, six layouts and five palettes. During a full refresh it hides segments, displays
progress, and then reveals the completed batch, including unavailable/saved results. Deselecting a provider in General
pauses its reads; hiding only its bar segment does not.

See [Windows setup](../windows/README.md), [privacy](../windows/Packaging/Privacy.md), [architecture](ARCHITECTURE.md)
and [documentation index](README.md).
