[CmdletBinding()]
param(
  [string]$TaskId
)

$ErrorActionPreference = 'Stop'

function Convert-FromBase64Utf8 {
  param([string]$Value)
  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
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

function Test-Utf8NoBom {
  param([string]$Path)
  if (-not (Test-Path $Path)) {
    return @{ ok = $false; reason = 'missing' }
  }
  $bytes = [System.IO.File]::ReadAllBytes($Path)
  if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    return @{ ok = $false; reason = 'bom' }
  }
  try {
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $null = $utf8.GetString($bytes)
    return @{ ok = $true }
  }
  catch {
    return @{ ok = $false; reason = 'invalid' }
  }
}

$TextPreflightStart = Convert-FromBase64Utf8 '6aKE5qOA5p+l5byA5aeL'
$TextPreflightOk = Convert-FromBase64Utf8 '6aKE5qOA5p+l6YCa6L+H'
$TextPreflightFailed = Convert-FromBase64Utf8 '6aKE5qOA5p+l5aSx6LSl'
$TextCommandMissing = Convert-FromBase64Utf8 '57y65bCR5ZG95Luk77yaezB9'
$TextPromptMissing = Convert-FromBase64Utf8 '5o+Q56S65qih5p2/5LiN5a2Y5Zyo77yaezB9'
$TextPromptHasBom = Convert-FromBase64Utf8 '5o+Q56S65qih5p2/5YyF5ZCrIEJPTe+8mnswfQ=='
$TextPromptEncodingInvalid = Convert-FromBase64Utf8 '5o+Q56S65qih5p2/57yW56CB5byC5bi477yaezB9'
$TextStatusMissing = Convert-FromBase64Utf8 '54q25oCB5paH5Lu25LiN5a2Y5Zyo77yaezB9'
$TextDepsMissing = Convert-FromBase64Utf8 '5L6d6LWW5pyq5ruh6Laz77yaezB9'
$TextTaskListEmpty = Convert-FromBase64Utf8 '5Lu75Yqh5riF5Y2V5Li656m6'
$TextTaskNotFound = Convert-FromBase64Utf8 '5pyq5om+5Yiw5Lu75YqhIHswfQ=='

$repoRoot = Split-Path -Parent $PSScriptRoot
$tasksPath = Join-Path $PSScriptRoot 'm1-tasks.ps1'
$promptPath = Join-Path $PSScriptRoot 'prompts/implement-task.txt'
$statusPath = Join-Path $repoRoot 'run/m1-status.json'

$exitCode = 0
Write-Host $TextPreflightStart

if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
  Write-Host ($TextCommandMissing -f 'codex')
  $exitCode = 1
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Host ($TextCommandMissing -f 'mvn')
  $exitCode = 1
}

$promptCheck = Test-Utf8NoBom -Path $promptPath
if (-not $promptCheck.ok) {
  switch ($promptCheck.reason) {
    'missing' {
      Write-Host ($TextPromptMissing -f $promptPath)
      $exitCode = 1
    }
    'bom' {
      Write-Host ($TextPromptHasBom -f $promptPath)
      $exitCode = 1
    }
    default {
      Write-Host ($TextPromptEncodingInvalid -f $promptPath)
      $exitCode = 1
    }
  }
}

if ($TaskId) {
  $tasks = & $tasksPath
  if ($null -eq $tasks -or $tasks.Count -eq 0) {
    Write-Host $TextTaskListEmpty
    exit 1
  }

  $task = $null
  foreach ($item in $tasks) {
    if ($item.taskId -eq $TaskId) {
      $task = $item
      break
    }
  }
  if ($null -eq $task) {
    Write-Host ($TextTaskNotFound -f $TaskId)
    exit 1
  }

  if (-not (Test-Path $statusPath)) {
    Write-Host ($TextStatusMissing -f $statusPath)
    $exitCode = 1
  }

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
    $exitCode = 1
  }
}

if ($exitCode -eq 0) {
  Write-Host $TextPreflightOk
}
else {
  Write-Host $TextPreflightFailed
}

exit $exitCode
