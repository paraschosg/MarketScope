# Builds the portable MarketScope for a USB stick: a folder with MarketScope.exe and its own Java
# runtime, which keeps its watchlist in a "data" folder next to it.
#
#   .\tools\portable.ps1              tests, then target\portable\MarketScope and
#                                     target\MarketScope-<version>-portable-win64.zip
#   .\tools\portable.ps1 -SkipTests   without running the tests first
#
# Copy the MarketScope folder (or unzip the .zip) onto the stick and start MarketScope.exe.
# Needs JDK 25 (jpackage and jlink; the runtime it bundles is that JDK's).

param(
    [switch]$SkipTests
)

# Native tools print notices on stderr; failures are caught by their exit codes below.
$ErrorActionPreference = 'Continue'
$root = Resolve-Path (Join-Path $PSScriptRoot "..")

function Test-Jdk25([string]$jdkHome) {
    $release = Join-Path $jdkHome "release"
    (Test-Path (Join-Path $jdkHome "bin\jpackage.exe")) -and (Test-Path $release) -and
        ((Get-Content $release -Raw) -match 'JAVA_VERSION="25[."]')
}

if (-not ($env:JAVA_HOME -and (Test-Jdk25 $env:JAVA_HOME))) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Jdk25 $_.FullName } |
        Select-Object -Last 1
    if (-not $jdk) {
        throw "No JDK 25 with jpackage found. Set JAVA_HOME, or add JDK 25 in IntelliJ (File > Project Structure > SDK)."
    }
    $env:JAVA_HOME = $jdk.FullName
}
$bin = Join-Path $env:JAVA_HOME "bin"
Write-Host "JDK: $env:JAVA_HOME"

# 1. The application jar, with every library in it.
$mavenArgs = @("-q", "package")
if ($SkipTests) { $mavenArgs += "-DskipTests" }
& (Join-Path $PSScriptRoot "mvn.ps1") @mavenArgs
if ($LASTEXITCODE -ne 0) { throw "The Maven build failed." }

$jar = Get-ChildItem (Join-Path $root "target") -Filter "marketscope-*-all.jar" | Select-Object -First 1
if (-not $jar) { throw "target\marketscope-*-all.jar was not built." }
$version = $jar.Name -replace '^marketscope-(.+)-all\.jar$', '$1'

$out = Join-Path $root "target\portable"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
$staging = Join-Path $out "input"
New-Item -ItemType Directory -Force $staging | Out-Null
Copy-Item $jar.FullName $staging

# 2. The icon.
$icon = Join-Path $out "marketscope.ico"
& (Join-Path $bin "java.exe") "-Djava.awt.headless=true" -cp $jar.FullName com.marketscope.IconFile $icon
if ($LASTEXITCODE -ne 0) { throw "The icon could not be drawn." }

# 3. The Java modules the app needs: what jdeps finds, plus what it cannot see (TLS for the price
#    and news services, the Windows certificate store, locale data, screen readers).
$found = & (Join-Path $bin "jdeps.exe") --print-module-deps --ignore-missing-deps --multi-release 25 $jar.FullName
if ($LASTEXITCODE -ne 0) { throw "jdeps failed." }
$modules = (($found -split ',') + @("jdk.unsupported", "jdk.charsets", "jdk.localedata", "jdk.crypto.ec",
    "jdk.crypto.mscapi", "jdk.accessibility", "java.net.http", "java.xml") | Where-Object { $_ } |
    Sort-Object -Unique) -join ','
Write-Host "Modules: $modules"

# 4. The app image: MarketScope.exe, the app jar and a trimmed Java runtime. The watchlist,
#    temporary files and JavaFX's unpacked libraries go to ..\data next to the program.
& (Join-Path $bin "jpackage.exe") `
    --type app-image `
    --name MarketScope `
    --app-version ($version -replace '[^0-9.]', '') `
    --vendor "MarketScope" `
    --description "Chart, pattern and news analysis for crypto, stocks and forex" `
    --icon $icon `
    --input $staging `
    --main-jar $jar.Name `
    --main-class com.marketscope.Launcher `
    --add-modules $modules `
    --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-6" `
    --java-options '--enable-native-access=ALL-UNNAMED' `
    --java-options '-Dmarketscope.home=$APPDIR\..\data' `
    --java-options '-Djava.io.tmpdir=$APPDIR\..\data\temp' `
    --java-options '-Djavafx.cachedir=$APPDIR\..\data\javafx' `
    --java-options '-XX:-UsePerfData' `
    --dest $out
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

$image = Join-Path $out "MarketScope"
Copy-Item (Join-Path $PSScriptRoot "portable-README.txt") (Join-Path $image "README.txt")
Remove-Item $staging -Recurse -Force

# 5. One .zip to carry around.
$zip = Join-Path $root "target\MarketScope-$version-portable-win64.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $image -DestinationPath $zip -CompressionLevel Optimal

$size = "{0:N0} MB" -f ((Get-ChildItem $image -Recurse | Measure-Object Length -Sum).Sum / 1MB)
Write-Host ""
Write-Host "Portable MarketScope $version ($size):"
Write-Host "  folder  $image"
Write-Host "  zip     $zip"
Write-Host "Copy the MarketScope folder onto a USB stick and start MarketScope.exe."
