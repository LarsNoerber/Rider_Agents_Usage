# Refresh all platform branding from the same SVG; no application packages are built.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
$repository = Split-Path -Parent $PSScriptRoot
. (Join-Path $repository 'windows/Packaging/read-icon.ps1')
$source = Join-Path $repository 'src/main/resources/icons/agentmeter-logo.svg'
$svg = [regex]::Replace([IO.File]::ReadAllText($source), '(?s)<!--.*?-->\s*', '').Trim()
$utf8 = [Text.UTF8Encoding]::new($false)
$svgTargets = @{
    'windows/Packaging/AgentMeter.svg' = 64
    'src/main/resources/META-INF/pluginIcon.svg' = 40
    'src/main/resources/icons/agentsUsage.svg' = 24
    'vscode/resources/icon.svg' = 64
    'vscode/resources/agents-usage.svg' = 24
    'vscode/resources/activity-icon.svg' = 24
}
foreach ($target in $svgTargets.GetEnumerator())
{
    $size = $target.Value
    $content = $svg.Replace('width="64" height="64"', "width=`"$size`" height=`"$size`"")
    [IO.File]::WriteAllText((Join-Path $repository $target.Key), $content + "`n", $utf8)
}
$artwork = Read-AgentMeterIcon $source
function Save-BrandingPng([string]$RelativePath, [int]$Size)
{
    $view = [System.Windows.Controls.Image]::new()
    $view.Source = $artwork
    $view.Width = $Size
    $view.Height = $Size
    $view.Measure([System.Windows.Size]::new($Size, $Size))
    $view.Arrange([System.Windows.Rect]::new(0, 0, $Size, $Size))
    $view.UpdateLayout()
    $bitmap = [System.Windows.Media.Imaging.RenderTargetBitmap]::new($Size, $Size, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
    $bitmap.Render($view)
    $encoder = [System.Windows.Media.Imaging.PngBitmapEncoder]::new()
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $stream = [IO.File]::Create((Join-Path $repository $RelativePath))
    try { $encoder.Save($stream) } finally { $stream.Dispose() }
}
foreach ($target in @('vscode/resources/icon.png', 'browser/resources/icon.png',
    'docs/images/agents-usage-logo.png', 'windows/Packaging/QuotaPortalTransparent.png',
    'src/main/resources/icons/agentmeter-logo.png'))
{
    Save-BrandingPng $target 256
}
foreach ($size in @(16, 32, 48, 128))
{
    Save-BrandingPng "browser/resources/icon$size.png" $size
}
# Retain the existing standalone XAML preview path with the same new vector artwork.
$preview = [System.Windows.Controls.Image]::new()
$preview.Source = $artwork
$preview.Width = 40
$preview.Height = 40
[IO.File]::WriteAllText((Join-Path $repository 'windows/Packaging/Icon.xaml'),
    [System.Windows.Markup.XamlWriter]::Save($preview) + "`n", $utf8)
& (Join-Path $repository 'windows/Packaging/render-assets.ps1') -OutputDirectory (Join-Path $repository 'windows/obj/Branding')
Write-Output 'Updated Rider, VS Code/Visual Studio, Windows, browser and documentation icons.'
