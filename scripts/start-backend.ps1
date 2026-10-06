param([switch]$EnableRunner)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        $parts = $line.Split('=', 2)
        if ($parts.Length -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
        }
    }
}
if ($EnableRunner) { $env:RESEARCH_RUNNER_ENABLED = 'true' }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $portableRoot = Join-Path (Split-Path $projectRoot -Parent) '.tools/jdk21'
    $portableJava = Get-ChildItem -Path "$portableRoot/*/bin/java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $portableJava) { throw '请安装 JDK 21，或设置 JAVA_HOME 与 PATH。' }
    $env:JAVA_HOME = Split-Path (Split-Path $portableJava.FullName -Parent) -Parent
    $env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
}
Set-Location $projectRoot
& "$projectRoot/mvnw.cmd" -B -ntp spring-boot:run
exit $LASTEXITCODE
