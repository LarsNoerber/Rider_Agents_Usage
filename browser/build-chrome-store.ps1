param([string]$BrowserPath = (Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'))
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build-store.ps1') -BrowserPath $BrowserPath
$chromeOutput = Join-Path $PSScriptRoot 'dist/chrome-store'
New-Item -ItemType Directory -Force -Path $chromeOutput | Out-Null
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Packaging/render-chrome-artwork.ps1') -OutputDirectory $chromeOutput
if ($LASTEXITCODE -ne 0)
{
    throw 'Chrome artwork rendering failed.'
}
# Use the same extension code, with Chrome's recommended padding for the 128px icon.
$chromeUnpacked = Join-Path $PSScriptRoot 'dist/chrome-unpacked'
$chromeDist = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'dist'))
$chromeUnpacked = [System.IO.Path]::GetFullPath($chromeUnpacked)
if ((Split-Path -Parent $chromeUnpacked) -ne $chromeDist -or (Split-Path -Leaf $chromeUnpacked) -ne 'chrome-unpacked')
{
    throw 'Unsafe Chrome package staging directory.'
}
if (Test-Path -LiteralPath $chromeUnpacked)
{
    Remove-Item -LiteralPath $chromeUnpacked -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $chromeUnpacked | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot 'dist/unpacked/*') -Destination $chromeUnpacked -Recurse -Force
foreach ($chromeIcon in @('icon.png', 'icon128.png'))
{
    Copy-Item -LiteralPath (Join-Path $chromeOutput 'logo-128.png') -Destination (Join-Path $chromeUnpacked "resources/$chromeIcon") -Force
}
$chromeVersion = (Get-Content -LiteralPath (Join-Path $PSScriptRoot '../gradle.properties') | Where-Object { $_ -match '^pluginVersion=' } | Select-Object -First 1) -replace '^pluginVersion=', ''
$chromeArchive = Join-Path $chromeOutput "AgentMeter-$chromeVersion-chrome.zip"
Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $chromeUnpacked | Select-Object -ExpandProperty FullName) -DestinationPath $chromeArchive -Force
foreach ($chromeScreenshot in @('screenshot-bar-1280x800.png', 'screenshot-settings-1280x800.png'))
{
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "dist/edge-store/$chromeScreenshot") -Destination $chromeOutput -Force
}
foreach ($chromeDocument in @('chrome-submission.md', 'edge-listing-en.md', 'edge-privacy-fields.md', 'edge-certification-notes.txt'))
{
    # Submission drafts are optional local files.
    $localDocument = Join-Path $PSScriptRoot "Packaging/$chromeDocument"
    if (Test-Path -LiteralPath $localDocument)
    {
        Copy-Item -LiteralPath $localDocument -Destination $chromeOutput -Force
    }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'PRIVACY.md') -Destination $chromeOutput -Force
Write-Output "Chrome submission files: $chromeOutput"
