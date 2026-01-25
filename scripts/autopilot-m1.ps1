[CmdletBinding()]
param(
  [int]$MaxTasks = 0,
  [string]$StartTaskId,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Convert-FromBase64Utf8 {
  param([string]$Value)
  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

$TextTaskListEmpty = Convert-FromBase64Utf8 '5Lu75Yqh5riF5Y2V5Li656m6'
$TextTaskNotFound = Convert-FromBase64Utf8 '5pyq5om+5Yiw5Lu75YqhIHswfQ=='
$TextNoPendingTask = Convert-FromBase64Utf8 '5pyq5om+5Yiw5pyq5a6e546w5Lu75Yqh'
$TextDepsMissing = Convert-FromBase64Utf8 '5L6d6LWW5pyq5ruh6Laz77yaezB9'
$TextTaskWillRun = Convert-FromBase64Utf8 '5bCG5omn6KGM5Lu75YqhIHswfQ=='
$TextTaskStart = Convert-FromBase64Utf8 '5byA5aeL5aSE55CGIHswfSAtIHsxfQ=='
$TextCodexRun = Convert-FromBase64Utf8 '5omn6KGMIENvZGV4IOWunueOsCB7MH3vvIzml6Xlv5fvvJp7MX0='
$TextCodexFailed = Convert-FromBase64Utf8 'Q29kZXgg5omn6KGM5aSx6LSlIHswfe+8jGV4aXRDb2RlPXsxfe+8jOaXpeW/l++8mnsyfQ=='
$TextTaskNotReady = Convert-FromBase64Utf8 '5Lu75Yqh5pyq5a6e546w77yaezB9IOacquWQr+eUqOaIliB0ZXN0Q21kIOacquabtOaWsA=='
$TextValidateRun = Convert-FromBase64Utf8 '5omn6KGM6aqM5pS2IHswfe+8jOWRveS7pO+8mnsxfQ=='
$TextValidateOk = Convert-FromBase64Utf8 '6aqM5pS25a6M5oiQIHswfe+8jGV4aXRDb2RlPXsxfQ=='
$TextValidateFailed = Convert-FromBase64Utf8 '6aqM5pS25aSx6LSlIHswfe+8jOivt+afpeeciyBydW4vbTEtc3RhdHVzLmpzb27vvIzlubbkvb/nlKggLlxzY3JpcHRzXHJ1bi1tMS5wczEgLU9ubHlUYXNrSWQgezB9IOaIliAuXHNjcmlwdHNccnVuLW0xLnBzMSAtRnJvbVRhc2tJZCB7MH0g57un57ut'

function Get-TaskIndex {
  param(
    [object[]]$Tasks,
    [string]$TaskId
  )
  for ($i = 0; $i -lt $Tasks.Count; $i++) {
    if ($Tasks[$i].taskId -eq $TaskId) {
      return $i
    }
  }
  return -1
}

function Get-TaskById {
  param(
    [object[]]$Tasks,
    [string]$TaskId
  )
  foreach ($item in $Tasks) {
    if ($item.taskId -eq $TaskId) {
      return $item
    }
  }
  return $null
}

function Load-StatusMap {
  param([string]$Path)
  $map = @{}
  if (Test-Path $Path) {
    $raw = Get-Content $Path -Raw
    if ($raw.Trim()) {
      $obj = $raw | ConvertFrom-Json
      if ($null -ne $obj) {
        foreach ($prop in $obj.PSObject.Properties) {
          $map[$prop.Name] = $prop.Value
        }
      }
    }
  }
  return $map
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$tasksPath = Join-Path $PSScriptRoot 'm1-tasks.ps1'
$promptPath = Join-Path $PSScriptRoot 'prompts/implement-task.txt'
$runScript = Join-Path $PSScriptRoot 'run-m1.ps1'
$statusPath = Join-Path $repoRoot 'run/m1-status.json'
$runDir = Join-Path $repoRoot 'run'
$logDir = Join-Path $runDir 'logs'

$tasks = & $tasksPath
if ($null -eq $tasks -or $tasks.Count -eq 0) {
  Write-Host $TextTaskListEmpty
  exit 1
}

$startIndex = -1
if ($StartTaskId) {
  $startIndex = Get-TaskIndex -Tasks $tasks -TaskId $StartTaskId
  if ($startIndex -lt 0) {
    Write-Host ($TextTaskNotFound -f $StartTaskId)
    exit 1
  }
}
else {
  for ($i = 0; $i -lt $tasks.Count; $i++) {
    if (-not $tasks[$i].enabled) {
      $startIndex = $i
      break
    }
  }
  if ($startIndex -lt 0) {
    Write-Host $TextNoPendingTask
    exit 0
  }
}

if (-not $DryRun) {
  New-Item -ItemType Directory -Path $runDir -Force | Out-Null
  New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$maxCount = if ($MaxTasks -gt 0) { $MaxTasks } else { [int]::MaxValue }
$processed = 0
$promptTemplate = if (-not $DryRun) { Get-Content $promptPath -Raw -Encoding UTF8 } else { '' }

$selectedTasks = @($tasks[$startIndex..($tasks.Count - 1)])
foreach ($task in $selectedTasks) {
  if ($processed -ge $maxCount) {
    break
  }
  $processed++

  $statusMap = Load-StatusMap -Path $statusPath
  $missing = @()
  foreach ($dep in $task.deps) {
    if (-not $statusMap.ContainsKey($dep)) {
      $missing += $dep
      continue
    }
    $depExit = $statusMap[$dep].exitCode
    if ($depExit -ne 0) {
      $missing += $dep
    }
  }

  if ($missing.Count -gt 0) {
    Write-Host ($TextDepsMissing -f ($missing -join ", "))
    exit 1
  }

  $codexLog = Join-Path $logDir "$($task.taskId).codex.log"
  $validateCmd = ".\\scripts\\run-m1.ps1 -OnlyTaskId $($task.taskId)"

  if ($DryRun) {
    Write-Host ($TextTaskWillRun -f $task.taskId)
    Write-Host ($TextCodexRun -f $task.taskId, $codexLog)
    Write-Host ($TextValidateRun -f $task.taskId, $validateCmd)
    continue
  }

  Write-Host ($TextTaskStart -f $task.taskId, $task.name)
  Write-Host ($TextCodexRun -f $task.taskId, $codexLog)
  $prompt = $promptTemplate.Replace('{TASK_ID}', $task.taskId)

  $prevErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & codex exec --full-auto -C $repoRoot $prompt *> $codexLog
  $ErrorActionPreference = $prevErrorActionPreference
  $codexExitCode = $LASTEXITCODE
  if ($codexExitCode -ne 0) {
    Write-Host ($TextCodexFailed -f $task.taskId, $codexExitCode, $codexLog)
    exit $codexExitCode
  }

  $tasksAfter = & $tasksPath
  $currentTask = Get-TaskById -Tasks $tasksAfter -TaskId $task.taskId
  if ($null -eq $currentTask) {
    Write-Host ($TextTaskNotFound -f $task.taskId)
    exit 1
  }

  $currentCmd = if ($null -ne $currentTask.testCmd) { $currentTask.testCmd.Trim() } else { '' }
  $cmdIsTodo = ($currentCmd -eq '' -or $currentCmd -ieq 'echo TODO')
  if (-not $currentTask.enabled -or $cmdIsTodo) {
    Write-Host ($TextTaskNotReady -f $task.taskId)
    exit 1
  }

  Write-Host ($TextValidateRun -f $task.taskId, $validateCmd)
  & $runScript -OnlyTaskId $task.taskId
  $validateExitCode = $LASTEXITCODE
  if ($validateExitCode -ne 0) {
    Write-Host ($TextValidateFailed -f $task.taskId)
    exit $validateExitCode
  }
  Write-Host ($TextValidateOk -f $task.taskId, $validateExitCode)
}

exit 0
