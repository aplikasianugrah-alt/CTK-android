param(
    [string]$Source = (Join-Path $PSScriptRoot '..\branding\ctk-icon-master.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePath = [System.IO.Path]::GetFullPath($Source)
$navy = [System.Drawing.Color]::FromArgb(255, 7, 29, 53)

function New-Canvas([int]$Size) {
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bitmap.SetResolution(96, 96)
    return $bitmap
}

function New-Graphics([System.Drawing.Bitmap]$Bitmap) {
    $graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    return $graphics
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    $directory = Split-Path -Parent $Path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path
    }
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Write-TransparentIcon([System.Drawing.Image]$Logo, [int]$Size, [string]$Path) {
    $bitmap = New-Canvas $Size
    $graphics = New-Graphics $bitmap
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($Logo, 0, 0, $Size, $Size)
    $graphics.Dispose()
    Save-Png $bitmap $Path
    $bitmap.Dispose()
}

function Write-SquareIcon([System.Drawing.Image]$Logo, [int]$Size, [string]$Path) {
    $bitmap = New-Canvas $Size
    $graphics = New-Graphics $bitmap
    $graphics.Clear($navy)
    $graphics.DrawImage($Logo, 0, 0, $Size, $Size)
    $graphics.Dispose()
    Save-Png $bitmap $Path
    $bitmap.Dispose()
}

function Write-LegacyAndroidIcon([System.Drawing.Image]$Logo, [int]$Size, [string]$Path) {
    $bitmap = New-Canvas $Size
    $graphics = New-Graphics $bitmap
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $margin = [math]::Max(1, [math]::Round($Size * 0.04))
    $diameter = [math]::Max(4, [math]::Round($Size * 0.22))
    $right = $Size - $margin
    $bottom = $Size - $margin
    $shape = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $shape.AddArc($margin, $margin, $diameter, $diameter, 180, 90)
    $shape.AddArc($right - $diameter, $margin, $diameter, $diameter, 270, 90)
    $shape.AddArc($right - $diameter, $bottom - $diameter, $diameter, $diameter, 0, 90)
    $shape.AddArc($margin, $bottom - $diameter, $diameter, $diameter, 90, 90)
    $shape.CloseFigure()
    $brush = [System.Drawing.SolidBrush]::new($navy)
    $graphics.FillPath($brush, $shape)
    $brush.Dispose()
    $shape.Dispose()

    $graphics.DrawImage($Logo, 0, 0, $Size, $Size)
    $graphics.Dispose()
    Save-Png $bitmap $Path
    $bitmap.Dispose()
}

function Write-RoundIcon([System.Drawing.Image]$Logo, [int]$Size, [string]$Path) {
    $bitmap = New-Canvas $Size
    $graphics = New-Graphics $bitmap
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $brush = [System.Drawing.SolidBrush]::new($navy)
    $graphics.FillEllipse($brush, 0, 0, $Size, $Size)
    $brush.Dispose()
    $graphics.DrawImage($Logo, 0, 0, $Size, $Size)
    $graphics.Dispose()
    Save-Png $bitmap $Path
    $bitmap.Dispose()
}

function Write-MonochromeIcon([System.Drawing.Bitmap]$Logo, [int]$Size, [string]$Path) {
    $resized = New-Canvas $Size
    $graphics = New-Graphics $resized
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($Logo, 0, 0, $Size, $Size)
    $graphics.Dispose()

    for ($y = 0; $y -lt $Size; $y++) {
        for ($x = 0; $x -lt $Size; $x++) {
            $alpha = $resized.GetPixel($x, $y).A
            $resized.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255))
        }
    }

    Save-Png $resized $Path
    $resized.Dispose()
}

$logo = [System.Drawing.Bitmap]::FromFile($sourcePath)
try {
    # Canonical transparent master used by Android and PWA derivatives.
    Write-TransparentIcon $logo 1024 (Join-Path $projectRoot 'branding\ctk-icon-master-1024.png')

    # Android adaptive foreground (the source already contains mask-safe transparent padding).
    Write-TransparentIcon $logo 432 (Join-Path $projectRoot 'app\src\main\res\drawable-nodpi\ctk_icon_foreground.png')
    Write-MonochromeIcon $logo 432 (Join-Path $projectRoot 'app\src\main\res\drawable-nodpi\ctk_icon_monochrome.png')

    $androidDensities = [ordered]@{
        'mdpi' = 48
        'hdpi' = 72
        'xhdpi' = 96
        'xxhdpi' = 144
        'xxxhdpi' = 192
    }
    foreach ($entry in $androidDensities.GetEnumerator()) {
        $directory = Join-Path $projectRoot ("app\src\main\res\mipmap-{0}" -f $entry.Key)
        Write-LegacyAndroidIcon $logo $entry.Value (Join-Path $directory 'ic_launcher.png')
        Write-RoundIcon $logo $entry.Value (Join-Path $directory 'ic_launcher_round.png')
    }

    # PWA standard and maskable icons. Opaque navy is required for maskable icons.
    $pwaIconDir = Join-Path $projectRoot 'pwa\icons'
    foreach ($size in @(48, 72, 96, 128, 144, 152, 180, 192, 384, 512)) {
        Write-SquareIcon $logo $size (Join-Path $pwaIconDir ("icon-{0}-v3.png" -f $size))
    }
    foreach ($size in @(192, 512)) {
        Write-SquareIcon $logo $size (Join-Path $pwaIconDir ("icon-maskable-{0}-v3.png" -f $size))
    }
    Write-SquareIcon $logo 32 (Join-Path $pwaIconDir 'favicon-32-v3.png')
}
finally {
    $logo.Dispose()
}

Write-Host 'Android and PWA icon assets generated successfully.'
