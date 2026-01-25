[CmdletBinding()]
param(
  [string]$FromTaskId,
  [string]$ToTaskId,
  [string]$OnlyTaskId,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Convert-FromBase64Utf8 {
  param([string]$Value)
  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

$TextTaskListEmpty = Convert-FromBase64Utf8 '5Lu75Yqh5riF5Y2V5Li656m6'
$TextTaskNotFound = Convert-FromBase64Utf8 '5pyq5om+5Yiw5Lu75YqhIHswfQ=='
$TextTaskRangeInvalid = Convert-FromBase64Utf8 '5Lu75Yqh6IyD5Zu05LiN5ZCI5rOV'
$TextTaskNextDisabled = Convert-FromBase64Utf8 '5LiL5LiA5Liq5pyq5a6e546w5Lu75Yqh5pivIHswfQ=='
$TextDepsMissing = Convert-FromBase64Utf8 '5L6d6LWW5pyq5ruh6Laz77yaezB9'
$TextTaskWillRun = Convert-FromBase64Utf8 '5bCG5omn6KGM5Lu75YqhIHswfQ=='
$TextTaskFailed = Convert-FromBase64Utf8 '5Lu75Yqh5aSx6LSlIHswfe+8jOmAgOWHuueggSB7MX0='
$TextTaskStart = Convert-FromBase64Utf8 '5byA5aeL5omn6KGMIHswfSAtIHsxfe+8jOWRveS7pO+8mnsyfe+8jOaXpeW/l++8mnszfQ=='
$TextTaskDone = Convert-FromBase64Utf8 '5a6M5oiQIHswfe+8jHN0YXR1cz17MX3vvIxleGl0Q29kZT17Mn3vvIzml6Xlv5fvvJp7M30='


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

function Save-StatusMap {
  param(
    [string]$Path,
    [hashtable]$Map
  )
  $json = $Map | ConvertTo-Json -Depth 6
  $json = $json -replace "`r`n", "`n"
  [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$tasksPath = Join-Path $PSScriptRoot 'm1-tasks.ps1'
$tasks = & $tasksPath
if ($null -eq $tasks -or $tasks.Count -eq 0) {
  Write-Host $TextTaskListEmpty
  exit 1
}

$statusPath = Join-Path $repoRoot 'run/m1-status.json'
$runDir = Join-Path $repoRoot 'run'
$logDir = Join-Path $runDir 'logs'
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

$statusMap = Load-StatusMap -Path $statusPath

if ($OnlyTaskId) {
  $index = Get-TaskIndex -Tasks $tasks -TaskId $OnlyTaskId
  if ($index -lt 0) {
    Write-Host ($TextTaskNotFound -f $OnlyTaskId)
    exit 1
  }
  $selectedTasks = @($tasks[$index])
}
else {
  $startIndex = 0
  $endIndex = $tasks.Count - 1

  if ($FromTaskId) {
    $startIndex = Get-TaskIndex -Tasks $tasks -TaskId $FromTaskId
    if ($startIndex -lt 0) {
      Write-Host ($TextTaskNotFound -f $FromTaskId)
      exit 1
    }
  }

  if ($ToTaskId) {
    $endIndex = Get-TaskIndex -Tasks $tasks -TaskId $ToTaskId
    if ($endIndex -lt 0) {
      Write-Host ($TextTaskNotFound -f $ToTaskId)
      exit 1
    }
  }

  if ($startIndex -gt $endIndex) {
    Write-Host $TextTaskRangeInvalid
    exit 1
  }

  $selectedTasks = @($tasks[$startIndex..$endIndex])
}

$exitCode = 0
Push-Location $repoRoot
try {
  foreach ($task in $selectedTasks) {
    if (-not $task.enabled) {
      Write-Host ($TextTaskNextDisabled -f $task.taskId)
      break
    }

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
      $exitCode = 1
      break
    }

    if ($DryRun) {
      Write-Host ($TextTaskWillRun -f $task.taskId)
      if (-not $statusMap.ContainsKey($task.taskId)) {
        $statusMap[$task.taskId] = [PSCustomObject]@{
          exitCode = 0
        }
      }
      continue
    }

    $logPath = Join-Path $logDir "$($task.taskId).log"
    Write-Host ($TextTaskStart -f $task.taskId, $task.name, $task.testCmd, $logPath)
    $prevErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & cmd.exe /c $task.testCmd *> $logPath
    $ErrorActionPreference = $prevErrorActionPreference
    $taskExitCode = $LASTEXITCODE
    $taskStatus = if ($taskExitCode -eq 0) { 'success' } else { 'failed' }

    $statusMap[$task.taskId] = [PSCustomObject]@{
      lastRun = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')
      status = $taskStatus
      exitCode = $taskExitCode
      logPath = "run/logs/$($task.taskId).log"
    }
    Save-StatusMap -Path $statusPath -Map $statusMap
    Write-Host ($TextTaskDone -f $task.taskId, $taskStatus, $taskExitCode, $logPath)

    if ($taskExitCode -ne 0) {
      Write-Host ($TextTaskFailed -f $task.taskId, $taskExitCode)
      $exitCode = $taskExitCode
      break
    }
  }
}
finally {
  Pop-Location
}

exit $exitCode
