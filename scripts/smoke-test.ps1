Param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiKey = "demo-key",
    [string]$TenantId = "tenant-demo-1",
    [string]$SessionId = "s-001",
    [ValidateSet("SYNC", "ASYNC")]
    [string]$ExecutionMode = "SYNC",
    [string]$Query = "query bob user info, then users contains h, then return all",
    [int]$StartupTimeoutSec = 180,
    [switch]$SkipStart
)

$ErrorActionPreference = "Stop"

function Write-Step {
    Param(
        [string]$Message
    )
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message)
}

function Test-Health {
    Param(
        [string]$HealthUrl
    )
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -Method Get -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$healthUrl = "$BaseUrl/actuator/health"
$taskUrl = "$BaseUrl/api/v1/tasks"

$logsDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$startupLog = Join-Path $logsDir ("smoke-test-startup-{0}.log" -f $timestamp)
$responseFile = Join-Path $logsDir ("smoke-test-response-{0}.json" -f $timestamp)

Push-Location $projectRoot
try {
    if (-not (Test-Health -HealthUrl $healthUrl)) {
        if ($SkipStart) {
            throw "service is not running and SkipStart was specified"
        }

        Write-Step "service not detected, starting spring boot"
        $process = Start-Process -FilePath "mvn.cmd" -ArgumentList "spring-boot:run" -PassThru -RedirectStandardOutput $startupLog -RedirectStandardError $startupLog
        Write-Step ("startup pid={0}, waiting for health" -f $process.Id)

        $deadline = (Get-Date).AddSeconds($StartupTimeoutSec)
        $healthy = $false
        while ((Get-Date) -lt $deadline) {
            if (Test-Health -HealthUrl $healthUrl) {
                $healthy = $true
                break
            }
            if ($process.HasExited) {
                throw ("startup process exited, check log: {0}" -f $startupLog)
            }
            Start-Sleep -Seconds 2
        }

        if (-not $healthy) {
            throw ("startup timeout after {0}s, check log: {1}" -f $StartupTimeoutSec, $startupLog)
        }
        Write-Step "service ready, sending request"
    } else {
        Write-Step "service already running, sending request"
    }

    $headers = @{
        "X-API-Key" = $ApiKey
        "X-Tenant-Id" = $TenantId
    }
    $payload = @{
        query = $Query
        sessionId = $SessionId
        executionMode = $ExecutionMode
    } | ConvertTo-Json -Compress

    $response = Invoke-WebRequest -Method Post -Uri $taskUrl -Headers $headers -ContentType "application/json; charset=utf-8" -Body $payload -TimeoutSec 180
    $response.Content | Set-Content -Path $responseFile -Encoding utf8

    $json = $response.Content | ConvertFrom-Json
    Write-Step ("request done: http={0}, code={1}, taskStatus={2}" -f $response.StatusCode, $json.code, $json.data.status)
    Write-Step ("response file: {0}" -f $responseFile)

    [PSCustomObject]@{
        httpStatus = $response.StatusCode
        code = $json.code
        message = $json.message
        taskId = $json.data.taskId
        workflowId = $json.data.workflowId
        status = $json.data.status
        responseFile = $responseFile
    } | ConvertTo-Json -Compress
} catch {
    Write-Error ("smoke test failed: {0}" -f $_.Exception.Message)
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        Write-Host "api error detail:"
        Write-Host $_.ErrorDetails.Message
    }
    Write-Host ("startup troubleshoot log: {0}" -f $startupLog)
    exit 1
} finally {
    Pop-Location
}
