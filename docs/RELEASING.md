# GitHub release process

Each release must identify the exact source commit and distinguish compilation/packaging from live account or
Store verification. Store publishing is separate. The repository Wiki is a separate Git repository.

## Prepare source

1. Review the working tree and preserve existing edits. Keep personal settings, credentials and generated files out
   of commits. Include only explicitly authorized source changes.
2. Read the shared version from `gradle.properties`; package scripts synchronize the edition manifests. Update the
   changelog and relevant user guides. Do not reuse an existing release tag for different source.
3. Build explicitly requested editions with the commands in [DEVELOPMENT.md](DEVELOPMENT.md). For a complete release,
   build the JetBrains, VS Code and Visual Studio editor packages, Windows portable app and MSIX, and browser packages.
   Use `-x test` on Gradle packaging when tests have not been requested. No login or installation is part of packaging.
4. Confirm all package versions and inspect archive integrity/manifests. Record actual warnings and limits.

## Select public assets

Publish the normal installable plugin ZIP, VS Code/Visual Studio VSIX files, browser extension ZIPs and a portable
Windows ZIP with its required companion files. The unsigned Windows MSIX may be included with an explicit Store
submission/sideloading notice. Keep per-Store submission bundles, listing drafts, reviewer correspondence and media
archives local. Automatically generated source archives are GitHub's own release downloads.

Generate `SHA256SUMS.txt` from the final public files. Use their exact asset basenames, without staging folder
prefixes. Remove entries when assets are removed. Old release descriptions must not instruct users to download
deleted bundles or link local-only reports.

## Publish and confirm

Commit/push the reviewed source, confirm the GitHub build check, then create the version tag/release at that exact
commit. Upload only the selected installation assets and checksum list. Explain each package's target application,
the unsigned MSIX status, and checks that actually ran. Do not claim live authentication or Store approval from a build.

Confirm the release is public, targets the intended commit and lists the expected files; compare uploaded asset
SHA-256 digests with the local checksum list. Update Wiki download/version references to the published release.

Repository metadata should link the Wiki and describe all five editions. The main-branch ruleset blocks branch
deletion and non-fast-forward updates; normal commits remain possible. The build workflow does not publish or commit.
