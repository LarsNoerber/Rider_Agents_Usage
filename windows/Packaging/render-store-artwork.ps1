param([string]$OutputDirectory = (Join-Path $PSScriptRoot '../dist/store-artwork'))

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
. (Join-Path $PSScriptRoot 'read-icon.ps1')
$icon = Read-AgentMeterIcon (Join-Path $PSScriptRoot 'AgentMeter.svg')
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Draw-Text($context, [string]$value, [double]$size, [double]$y, [double]$width, [string]$color, [int]$weight = 400)
{
    $fontWeight = if ($weight -ge 600)
    {
        [System.Windows.FontWeights]::Bold
    }
    else
    {
        [System.Windows.FontWeights]::Normal
    }
    $typeface = New-Object System.Windows.Media.Typeface(
    (New-Object System.Windows.Media.FontFamily('Segoe UI')),
    [System.Windows.FontStyles]::Normal,
    $fontWeight,
    [System.Windows.FontStretches]::Normal)
    $brush = New-Object System.Windows.Media.SolidColorBrush(([System.Windows.Media.ColorConverter]::ConvertFromString($color)))
    $formatted = New-Object System.Windows.Media.FormattedText(
    $value,[Globalization.CultureInfo]::GetCultureInfo('en-US'),
    [System.Windows.FlowDirection]::LeftToRight, $typeface, $size, $brush, 1.0)
    $context.DrawText($formatted, (New-Object System.Windows.Point((($width - $formatted.Width) / 2), $y)))
}

function Save-StoreArtwork([string]$name, [int]$width, [int]$height, [double]$iconSize, [double]$iconY)
{
    $visual = New-Object System.Windows.Media.DrawingVisual
    $context = $visual.RenderOpen()
    try
    {
        $base = New-Object System.Windows.Media.LinearGradientBrush
        $base.StartPoint = New-Object System.Windows.Point(0, 0)
        $base.EndPoint = New-Object System.Windows.Point(1, 1)
        $base.GradientStops.Add((New-Object System.Windows.Media.GradientStop(([System.Windows.Media.ColorConverter]::ConvertFromString('#070D1D')), 0)))
        $base.GradientStops.Add((New-Object System.Windows.Media.GradientStop(([System.Windows.Media.ColorConverter]::ConvertFromString('#11244A')), 0.58)))
        $base.GradientStops.Add((New-Object System.Windows.Media.GradientStop(([System.Windows.Media.ColorConverter]::ConvertFromString('#241344')), 1)))
        $context.DrawRectangle($base, $null, (New-Object System.Windows.Rect(0, 0, $width, $height)))
        $glow = New-Object System.Windows.Media.RadialGradientBrush
        $glow.GradientStops.Add((New-Object System.Windows.Media.GradientStop(([System.Windows.Media.ColorConverter]::ConvertFromString('#6646A8FF')), 0)))
        $glow.GradientStops.Add((New-Object System.Windows.Media.GradientStop(([System.Windows.Media.ColorConverter]::ConvertFromString('#00101834')), 1)))
        $context.DrawEllipse($glow, $null, (New-Object System.Windows.Point(($width / 2), ($iconY + $iconSize / 2))), ($iconSize * 0.83), ($iconSize * 0.83))
        $context.DrawImage($icon, (New-Object System.Windows.Rect((($width - $iconSize) / 2), $iconY, $iconSize, $iconSize)))
        $titleY = $iconY + $iconSize + 45
        Draw-Text $context 'AgentMeter' 72 $titleY $width '#F5F8FF' 700
        Draw-Text $context 'AI agent usage at a glance' 28 ($titleY + 98) $width '#B4C9E4'
    }
    finally
    {
        $context.Close()
    }
    $bitmap = New-Object System.Windows.Media.Imaging.RenderTargetBitmap($width, $height, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
    $bitmap.Render($visual)
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $stream = [IO.File]::Create((Join-Path $OutputDirectory $name))
    try
    {
        $encoder.Save($stream)
    }
    finally
    {
        $stream.Dispose()
    }
}

Save-StoreArtwork 'AgentMeter-Square-1080.png' 1080 1080 530 150
Save-StoreArtwork 'AgentMeter-Poster-720x1080.png' 720 1080 480 135
