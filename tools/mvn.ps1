# Runs Maven with JDK 25 when neither is on PATH.
# Usage: .\tools\mvn.ps1 javafx:run

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '25' -and (Test-Path "$($_.FullName)\bin\java.exe") } |
        Select-Object -Last 1
    if (-not $jdk) {
        Write-Error "No JDK 25 found. Set JAVA_HOME."
        exit 1
    }
    $env:JAVA_HOME = $jdk.FullName
}

$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    # IntelliJ's bundled Maven: machine-wide, per-user, or under JetBrains Toolbox.
    $ides = @(
        Get-ChildItem "C:\Program Files\JetBrains" -Directory -ErrorAction SilentlyContinue
        Get-ChildItem "$env:LOCALAPPDATA\Programs" -Directory -Filter "IntelliJ*" -ErrorAction SilentlyContinue
        Get-ChildItem "$env:LOCALAPPDATA\JetBrains\Toolbox\apps" -Directory -Recurse -Depth 3 -Filter "IntelliJ*" -ErrorAction SilentlyContinue
    )
    $mvn = $ides |
        ForEach-Object { Join-Path $_.FullName "plugins\maven-plugin\lib\maven3\bin\mvn.cmd" } |
        Where-Object { Test-Path $_ } |
        Select-Object -Last 1
}
if (-not $mvn) {
    Write-Error "No Maven found. Install Maven or IntelliJ IDEA."
    exit 1
}

Push-Location (Join-Path $PSScriptRoot "..")
try {
    & $mvn @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
