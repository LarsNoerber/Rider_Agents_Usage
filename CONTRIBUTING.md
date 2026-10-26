# Contributing to AgentMeter

Start with the [Wiki](https://github.com/LarsNoerber/Rider_Agents_Usage/wiki),
[provider matrix](docs/PROVIDERS.md) and [architecture](docs/ARCHITECTURE.md).

## Report a problem or request a feature

Use the repository's issue forms. Include the edition, installed version, OS and IDE/browser version,
provider integration version, reproduction steps, expected result and actual safe notice. State whether a value
is saved/stale and whether the official provider view reports a quota. Identify the account mode/tier without
including account identifiers. Search existing issues first.

Never attach credentials, cookies, access tokens, authorization headers, raw provider responses or conversation
content. Redact screenshots. Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Prepare a code change

1. Create a branch from current `main` and keep the change focused.
2. Read [local development](docs/DEVELOPMENT.md) for the target edition's prerequisites and build command.
3. Keep provider logic in its provider folder, reusable contracts in core, orchestration in application and
   presentation in UI. Preserve plugin/provider IDs, settings names and the quota direction of each provider.
4. Run provider work off the UI thread and remove listeners/processes during disposal. Optional providers must stay
   optional. A saved numeric measurement must not establish authentication.
5. Update user guides and `CHANGELOG.md` for user-facing changes; update architecture documentation for structural
   changes. Product strings and comments are English.
6. Build the affected edition when build work is requested. Tests are opt-in under this project's workflow;
   request/agree the test scope before running them. Report the checks actually performed.
7. Open a pull request using the template. Explain the problem, resulting behavior, validation and known limits.

Do not commit generated build/dist output, EXE/MSIX/VSIX files, personal IDE settings, provider credentials,
local working notes or Store submission drafts. Packages belong in explicitly authorized GitHub releases.
Preserve other contributors' uncommitted edits. Do not publish to a Store or install a plugin as part of a build.

## Automated checks and releases

The **Build Rider plugin** workflow packages the JetBrains plugin with JDK 25 on Windows, without running tests.
CodeQL runs separately. Build success does not validate account authentication, every IDE version or Store approval.
See [release process](docs/RELEASING.md) for package publication.

Be respectful, give reproducible evidence and keep discussions focused on the project. See
[Code of Conduct](CODE_OF_CONDUCT.md).
