param([string]$BrowserPath)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1')
$storeOutput = Join-Path $PSScriptRoot 'dist/edge-store'
New-Item -ItemType Directory -Force -Path $storeOutput | Out-Null
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Packaging/render-logo.ps1') -OutputDirectory $storeOutput
if ($LASTEXITCODE -ne 0)
{
    throw 'Store logo rendering failed.'
}
& node (Join-Path $PSScriptRoot 'Packaging/prepare-preview.mjs')
if ($LASTEXITCODE -ne 0)
{
    throw 'Store presentation preparation failed.'
}
if (-not $BrowserPath)
{
    $storeBrowsers = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe'),
        (Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe')
    )
    $BrowserPath = $storeBrowsers | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $BrowserPath -or -not (Test-Path -LiteralPath $BrowserPath))
{
    throw 'Pass an installed Chromium browser with -BrowserPath.'
}
$storePreview = Join-Path $PSScriptRoot 'dist/store-preview'
$storeProfile = Join-Path $PSScriptRoot 'dist/store-render-profile'
$storeImages = @(
    @{ Page = 'bar.html'; Image = 'screenshot-bar-1280x800.png' },
    @{ Page = 'ui/dashboard.html'; Image = 'screenshot-settings-1280x800.png' }
)
foreach ($storeImage in $storeImages)
{
    $storeUrl = ([System.Uri](Join-Path $storePreview $storeImage.Page)).AbsoluteUri
    $storeScreenshot = Join-Path $storeOutput $storeImage.Image
    $storeArguments = @('--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--hide-scrollbars', '--allow-file-access-from-files',
    '--window-size=1280,800', '--virtual-time-budget=3000',
    "--user-data-dir=`"$storeProfile`"", "--screenshot=`"$storeScreenshot`"", "`"$storeUrl`"")
    $storeProcess = Start-Process -FilePath $BrowserPath -ArgumentList $storeArguments -WindowStyle Hidden -PassThru -Wait
    if ($storeProcess.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $storeScreenshot))
    {
        throw 'Store screenshot rendering failed.'
    }
}
$storeVersion = (Get-Content -LiteralPath (Join-Path $PSScriptRoot '../gradle.properties') | Where-Object { $_ -match '^pluginVersion=' } | Select-Object -First 1) -replace '^pluginVersion=', ''
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "dist/AgentMeter-$storeVersion-chromium.zip") -Destination $storeOutput -Force
foreach ($storeDocument in @('edge-listing-en.md', 'edge-privacy-fields.md', 'edge-certification-notes.txt', 'edge-submission.md'))
{
    # Submission drafts are optional local files.
    $localDocument = Join-Path $PSScriptRoot "Packaging/$storeDocument"
    if (Test-Path -LiteralPath $localDocument)
    {
        Copy-Item -LiteralPath $localDocument -Destination $storeOutput -Force
    }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'PRIVACY.md') -Destination $storeOutput -Force
Write-Output "Edge submission files: $storeOutput"
