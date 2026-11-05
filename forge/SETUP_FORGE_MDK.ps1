$ErrorActionPreference = 'Stop'
$version = '1.20.1-47.4.28'
$mdkUri = "https://maven.minecraftforge.net/net/minecraftforge/forge/$version/forge-$version-mdk.zip"
$expectedSHA1 = 'eee9145e892bb5ca5fe72323bd81c8522c53417f'
$zip = Join-Path $PSScriptRoot "_forge_mdk-$version.zip"
$dir = Join-Path $PSScriptRoot '_forge_mdk_extracted'
Write-Host "Downloading official Forge MDK $version ..."
if (!(Test-Path $zip)) {
    Invoke-WebRequest -Uri $mdkUri -OutFile $zip -UseBasicParsing
}
$actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA1).Hash.ToLowerInvariant()
if ($actual -ne $expectedSHA1) {
    throw "Forge MDK hash mismatch. Expected $expectedSHA1 but got $actual. Delete the downloaded file and check the official Forge download page."
}
Write-Host 'Verified MDK SHA1 against Forge published release checksum.'
if (!(Test-Path $dir)) { Expand-Archive -LiteralPath $zip -DestinationPath $dir }
foreach($name in @('gradlew.bat', 'gradlew')) {
    $source = Join-Path $dir $name
    if (!(Test-Path $source)) { throw "MDK missing $name" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $PSScriptRoot $name) -Force
}
$wrapper = Join-Path $dir 'gradle/wrapper'
if (!(Test-Path $wrapper)) { throw 'MDK does not include the Gradle wrapper files.' }
$target = Join-Path $PSScriptRoot 'gradle/wrapper'
New-Item -ItemType Directory -Force -Path $target | Out-Null
Copy-Item -Path (Join-Path $wrapper '*') -Destination $target -Force
Write-Host 'Forge Gradle wrapper installed from verified official MDK.'
