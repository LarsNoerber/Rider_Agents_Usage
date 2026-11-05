$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
$repository = Split-Path -Parent $PSScriptRoot
. (Join-Path $repository 'windows/Packaging/read-icon.ps1')
$source = Join-Path $repository 'windows/Packaging/AgentMeter.svg'
$resources = Join-Path $repository 'vscode/resources'
Copy-Item -LiteralPath $source -Destination (Join-Path $resources 'icon.svg') -Force
Copy-Item -LiteralPath (Join-Path $repository 'src/main/resources/icons/agentsUsage.svg') -Destination (Join-Path $resources 'agents-usage.svg') -Force
$view = New-Object System.Windows.Controls.Image
$view.Source = Read-AgentMeterIcon $source
$view.Width = 256
$view.Height = 256
$view.Measure((New-Object System.Windows.Size(256, 256)))
$view.Arrange((New-Object System.Windows.Rect(0, 0, 256, 256)))
$view.UpdateLayout()
$bitmap = New-Object System.Windows.Media.Imaging.RenderTargetBitmap(256, 256, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
$bitmap.Render($view)
$encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
$stream = [System.IO.File]::Create((Join-Path $resources 'icon.png'))
try
{
    $encoder.Save($stream)
}
finally
{
    $stream.Dispose()
}
