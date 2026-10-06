param([int]$Port = 18123, [switch]$EnableRunner, [switch]$SyncPapers)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jar = (Resolve-Path (Join-Path $root 'target/research_agent-0.1.0-SNAPSHOT.jar')).Path
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$base = "http://127.0.0.1:$Port/api"
$data = Join-Path $root 'data'
New-Item -ItemType Directory -Force -Path $data | Out-Null
$database = 'jdbc:h2:file:./data/research_agent_smoke_v2;MODE=MySQL;DATABASE_TO_LOWER=TRUE'
$oldRunner = $env:RESEARCH_RUNNER_ENABLED
if ($EnableRunner) { $env:RESEARCH_RUNNER_ENABLED = 'true' }
$process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar, "--server.port=$Port", "--spring.datasource.url=$database") -WorkingDirectory $root -RedirectStandardOutput (Join-Path $data 'smoke-v2.log') -RedirectStandardError (Join-Path $data 'smoke-v2.err.log') -WindowStyle Hidden -PassThru
if ($EnableRunner) { $env:RESEARCH_RUNNER_ENABLED = $oldRunner }

try {
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $null = Invoke-RestMethod "$base/research/health"
            $ready = $true
            break
        } catch {}
    }
    if (-not $ready) { throw 'Backend did not become ready in 30 seconds. See data/smoke-v2.err.log.' }

    $project = Invoke-RestMethod -Method Post -Uri "$base/research-projects" -ContentType 'application/json' -Body (@{ name='smoke-v2'; description='API smoke'; defaultLanguage='Python' } | ConvertTo-Json)
    $projectId = $project.data.id
    $null = Invoke-RestMethod -Method Put -Uri "$base/research-projects/$projectId/memory" -ContentType 'application/json' -Body (@{ content='Compare baseline accuracy with a fixed validation split.' } | ConvertTo-Json)
    $null = Invoke-RestMethod -Method Post -Uri "$base/research-projects/$projectId/knowledge" -ContentType 'application/json' -Body (@{ source='demo-paper'; content='Early stopping monitors validation loss to reduce overfitting.' } | ConvertTo-Json)
    $hits = Invoke-RestMethod "$base/research-projects/$projectId/knowledge/search?query=early%20stopping"
    $task = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/tasks" -ContentType 'application/json' -Body (@{ prompt='Generate a Python early stopping example using the note.' } | ConvertTo-Json)
    $taskId = $task.data.id
    $null = Invoke-RestMethod -Method Post -Uri "$base/tasks/$taskId/approve"

    $final = $null
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        $final = Invoke-RestMethod "$base/tasks/$taskId"
        if ($final.data.status -in @('SUCCEEDED','FAILED','CANCELED')) { break }
    }
    $events = Invoke-RestMethod "$base/tasks/$taskId/events"
    $files = Invoke-RestMethod "$base/tasks/$taskId/files"
    $paperCount = 0
    if ($SyncPapers) {
        $null = Invoke-RestMethod -Method Put -Uri "$base/research-projects/$projectId/papers/subscription" -ContentType 'application/json' -Body (@{ topic='machine learning'; enabled=$false } | ConvertTo-Json)
        $null = Invoke-RestMethod -Method Post -Uri "$base/research-projects/$projectId/papers/sync"
        $paperList = Invoke-RestMethod "$base/research-projects/$projectId/papers"
        $paperCount = @($paperList.data).Count
        if ($paperCount -lt 1) { throw 'Paper sync returned no papers.' }
    }
    $runStatus = 'disabled'
    if ($EnableRunner) {
        $run = Invoke-RestMethod -Method Post -Uri "$base/tasks/$taskId/runs" -ContentType 'application/json' -Body (@{ scriptPath='main.py' } | ConvertTo-Json)
        if ($run.data.status -ne 'WAITING_APPROVAL') { throw 'Run was not awaiting approval' }
        $runId = $run.data.id
        $null = Invoke-RestMethod -Method Post -Uri "$base/runs/$runId/approve"
        for ($i = 0; $i -lt 20; $i++) {
            Start-Sleep -Seconds 1
            $run = Invoke-RestMethod "$base/runs/$runId"
            if ($run.data.status -in @('SUCCEEDED','FAILED','CANCELED')) { break }
        }
        $runStatus = $run.data.status
        $runLogs = Invoke-RestMethod "$base/runs/$runId/logs"
        if ($runStatus -ne 'SUCCEEDED' -or $runLogs.data.stdout -notmatch 'Demo metrics written') {
            throw "Runner smoke failed. Status: $runStatus; stderr: $($runLogs.data.stderr)"
        }
    }
    [pscustomobject]@{
        ProjectId = $projectId
        KnowledgeHits = @($hits.data).Count
        TaskStatus = $final.data.status
        EventTypes = (@($events.data | ForEach-Object { $_.type }) -join ',')
        FileCount = @($files.data).Count
        RunStatus = $runStatus
        PaperCount = $paperCount
    } | Format-List
    if ($final.data.status -ne 'SUCCEEDED' -or @($hits.data).Count -lt 1 -or @($files.data).Count -lt 1) {
        throw 'Demo smoke failed: expected a matching note and generated files.'
    }
} finally {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
}
