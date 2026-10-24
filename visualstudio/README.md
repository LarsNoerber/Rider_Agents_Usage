# AgentMeter for Visual Studio

This edition supports Codex and Copilot. The standalone Windows app's extra providers and floating desktop bar
are separate from the Visual Studio VSIX. See [edition comparison](../docs/PROVIDERS.md) and
[documentation index](../docs/README.md). The shared source version is 1.2.1.

Native Visual Studio extension for **Visual Studio 2022 and 2026**, in Community, Professional and Enterprise on
Windows x64. It uses stable Visual Studio 2022 SDK APIs and an open installation version range (`[17.0,)`).
Older 32-bit Visual Studio releases are not targeted. A declared installation target is not a substitute for
checking the extension in each IDE version.

## Features

- OpenAI Codex remaining 5-hour and weekly quotas with colored bars.
- Provider-reported subscription plan, credits and local reset times.
- GitHub Copilot consumed quota, plans, reset times and colored bars through Visual Studio's own quota service.
- Manual refresh and automatic refresh while the view is open or the status indicator is enabled.
- Provider visibility, CLI path and refresh interval under **Tools > Options > AgentMeter > General**.
- Colored AI logo in the Standard toolbar, tool window and clickable quota status indicator.
- Visible package version, GitHub repository link, and ready-to-upload Marketplace descriptions and images.

The Codex CLI must already be installed and signed in (`codex login`). This extension reads only provider-reported
quota from the local app-server; it does not read credentials or estimate usage from local activity.
Copilot must already be signed in in Visual Studio. The reader uses the installed integration's optional quota
service, without a separate GitHub token. Open Copilot once if its service is not loaded; older versions may not
expose quotas. Free plans use chat and paid plans use premium allowance when reported. No quota is invented from
an empty placeholder; unlimited categories are labeled explicitly. Copilot shows consumption (0% unused,
100% exhausted). JetBrains AI is specific to the JetBrains package.
The optional colored status indicator uses Visual Studio's WPF shell layout; check it manually after IDE updates.

## Build

Use Windows with Visual Studio 2022 or 2026 MSBuild and a compatible .NET SDK. NuGet restores the Visual Studio SDK,
VSIX build tools and .NET Framework 4.7.2 reference assemblies; runtime code targets .NET Framework 4.7.2.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\visualstudio\build.ps1
```

For a custom Visual Studio installation, add `-MSBuildPath 'C:\path\to\MSBuild.exe'`.
The script reads the shared version from `gradle.properties` and writes
`visualstudio/dist/agents-usage-visualstudio-<version>.vsix`. It does not install the extension.
The same build prepares `visualstudio/dist/marketplace-<version>/` and a Marketplace ZIP with the VSIX, logo,
Markdown/HTML description, two illustrated images, upload details and `publishManifest.json`.
See [Marketplace upload instructions](Marketplace/README.md). Web uploads require pasting the overview separately;
the publishing manifest can supply it and image assets to Microsoft's command-line publisher.

## Install and open

**Migration in 1.0.15:** the Visual Studio VSIX uses a new identity after deletion of the previous Marketplace entry.
Uninstall AgentMeter 1.0.14 or earlier and complete its uninstall before installing this package. This is a new
extension identity; existing installations do not receive it as an automatic update. Keep only the new identity
installed.

1. Close Visual Studio and open the Visual Studio VSIX package with Visual Studio's VSIX Installer.
2. Select your Visual Studio installation and install the extension.
3. Open Visual Studio and choose **View > Other Windows > AgentMeter**.
4. Use **Settings** to configure the CLI path or refresh interval.

The Visual Studio and VS Code packages both use the `.vsix` suffix but have different manifests and runtimes.
Choose the package whose filename contains **visualstudio** for Visual Studio Community.

Codex is a trademark of OpenAI. This independent extension is not affiliated with or endorsed by OpenAI.
