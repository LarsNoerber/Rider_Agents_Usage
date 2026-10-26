# Documentation index

Source documentation updated for version 1.2.1 on October 24, 2026. Store submission remains separate from
building and publishing the GitHub release. Generated build/dist folders are not source documentation.

## User guides

- [Chrome / Edge browser extension](../browser/README.md): background quotas, persistent page bar, website access and
  local setup
- [Browser privacy](../browser/PRIVACY.md)
- [Edge Add-ons submission](../browser/Packaging/edge-submission.md): package, store assets, copy-ready fields and
  existing Partner Center product identity

- [Repository overview and Rider setup](../README.md)
- [Standalone Windows app](../windows/README.md): ten providers, connections, saved usage, bar layouts and tray
- [Provider/data comparison](PROVIDERS.md): edition availability and quota versus recorded usage
- [Visual Studio Code](../vscode/README.md)
- [Visual Studio](../visualstudio/README.md)
- [Windows privacy](../windows/Packaging/Privacy.md)
- [Rider plugin EULA](../EULA.md) and [source license](../LICENSE)

## Development and delivery

- [Accounts, authentication and review](AUTHENTICATION_AND_REVIEW.md): prerequisites and manual review steps for
  every edition, reviewer accounts, credential handling and evidence limitations

- [Architecture](ARCHITECTURE.md): package boundaries and Windows partial-class responsibilities
- [Source review and reading guide](CODE_REVIEW.md): application entry points, readability conventions and open findings
- [Build/development](DEVELOPMENT.md): separate editor/Windows build commands and Rider C# indexing
- [Windows verification](../windows/TESTING.md): current evidence, historical checks and opt-in commands
- [Changelog](../CHANGELOG.md)

## Publishing material

- [Chrome Web Store submission](../browser/Packaging/chrome-submission.md)

- [Marketplace overview](MARKETPLACE.md)
- [Visual Studio upload workflow](../visualstudio/Marketplace/README.md)
- [Visual Studio listing copy](../visualstudio/Marketplace/overview.md)
- [Windows Store draft](../windows/Packaging/StoreListing.md)

Build, installation and publication are separate actions. A successful build does not validate live provider APIs,
Store certification or every desktop interaction. Run/add tests only when requested. Generated EXE/MSIX packages stay
out of source commits; release binaries are attached to the GitHub release.
