param(
  [string]$MappingFile = "",
  [switch]$Apply,
  [bool]$UseGitMove = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-Path([string]$path) {
  if ([string]::IsNullOrWhiteSpace($path)) {
    return $path
  }
  return ($path.Replace("\", "/").Trim())
}

function Ensure-Directory([string]$filePath) {
  $dir = Split-Path -Path $filePath -Parent
  if (-not [string]::IsNullOrWhiteSpace($dir) -and -not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
}

function Read-Text([string]$path) {
  return [System.IO.File]::ReadAllText($path, [System.Text.UTF8Encoding]::new($false))
}

function Write-Text([string]$path, [string]$content) {
  $normalized = $content -replace "`r`n", "`n" -replace "`r", "`n"
  [System.IO.File]::WriteAllText($path, $normalized, [System.Text.UTF8Encoding]::new($false))
}

function Path-To-Fqcn([string]$javaPath) {
  $rel = Normalize-Path $javaPath
  if ($rel.StartsWith("src/main/java/")) {
    $rel = $rel.Substring(14)
  } elseif ($rel.StartsWith("src/test/java/")) {
    $rel = $rel.Substring(14)
  } else {
    throw "Java path must be under src/main/java or src/test/java: $javaPath"
  }

  if (-not $rel.EndsWith(".java")) {
    throw "Only .java file is supported in mapping: $javaPath"
  }

  $rel = $rel.Substring(0, $rel.Length - 5)
  return $rel.Replace("/", ".")
}

function Fqcn-To-Package([string]$fqcn) {
  $idx = $fqcn.LastIndexOf(".")
  if ($idx -le 0) {
    throw "Cannot parse package from FQCN: $fqcn"
  }
  return $fqcn.Substring(0, $idx)
}

function Update-Package-Declaration([string]$filePath, [string]$targetPackage, [bool]$applyMode) {
  $content = Read-Text $filePath
  $updated = $content
  $packagePattern = "(?m)^\s*package\s+[^;]+;"

  if ([regex]::IsMatch($updated, $packagePattern)) {
    $updated = [regex]::Replace($updated, $packagePattern, "package $targetPackage;", 1)
  } else {
    $updated = "package $targetPackage;`n`n$updated"
  }

  if ($updated -ne $content -and $applyMode) {
    Write-Text -path $filePath -content $updated
  }

  return ($updated -ne $content)
}

function Test-Git-Repo {
  if (-not (Test-Path ".git")) {
    return $false
  }
  git rev-parse --is-inside-work-tree *> $null
  return ($LASTEXITCODE -eq 0)
}

if ([string]::IsNullOrWhiteSpace($MappingFile)) {
  if (Test-Path "doc/package/mapping.csv") {
    $MappingFile = "doc/package/mapping.csv"
  } elseif (Test-Path "doc/package") {
    $candidate = Get-ChildItem -Path "doc/package" -File -Filter "*.csv" | Select-Object -First 1
    if ($null -ne $candidate) {
      $MappingFile = $candidate.FullName
    }
  }
}

if ([string]::IsNullOrWhiteSpace($MappingFile) -or -not (Test-Path $MappingFile)) {
  throw "Mapping file not found. Please pass -MappingFile explicitly."
}

$rawMappings = @(Import-Csv -Path $MappingFile)
if ($rawMappings.Count -eq 0) {
  throw "Mapping file is empty: $MappingFile"
}

$mappings = @()
foreach ($row in $rawMappings) {
  $oldPath = Normalize-Path $row.old_path
  $newPath = Normalize-Path $row.new_path

  if ([string]::IsNullOrWhiteSpace($oldPath) -or [string]::IsNullOrWhiteSpace($newPath)) {
    continue
  }

  $oldFqcn = Path-To-Fqcn $oldPath
  $newFqcn = Path-To-Fqcn $newPath

  $mappings += [PSCustomObject]@{
    old_path = $oldPath
    new_path = $newPath
    old_fqcn = $oldFqcn
    new_fqcn = $newFqcn
    new_pkg  = Fqcn-To-Package $newFqcn
    changed  = ($oldPath -ne $newPath)
  }
}

$dupOld = @($mappings | Group-Object old_path | Where-Object { $_.Count -gt 1 })
$dupNew = @($mappings | Group-Object new_path | Where-Object { $_.Count -gt 1 })
if ($dupOld.Count -gt 0 -or $dupNew.Count -gt 0) {
  throw "Duplicate path is found in mapping file."
}

$changedMappings = @($mappings | Where-Object { $_.changed })
$sameMappings = @($mappings | Where-Object { -not $_.changed })

Write-Host ("[INFO] mapping total: {0}" -f $mappings.Count)
Write-Host ("[INFO] changed mappings: {0}" -f $changedMappings.Count)
Write-Host ("[INFO] unchanged mappings: {0}" -f $sameMappings.Count)
Write-Host ("[INFO] mode: {0}" -f ($(if ($Apply) { "Apply" } else { "DryRun" })))

$isGitRepo = Test-Git-Repo
if ($UseGitMove -and -not $isGitRepo) {
  Write-Host "[WARN] not a git repository, fallback to Move-Item."
  $UseGitMove = $false
}

$moveCount = 0
$alreadyMovedCount = 0
$missingCount = 0
$packageUpdateCount = 0

foreach ($item in $changedMappings) {
  $oldPath = $item.old_path
  $newPath = $item.new_path

  $oldExists = Test-Path $oldPath
  $newExists = Test-Path $newPath

  if ($oldExists) {
    Ensure-Directory $newPath
    if ($Apply) {
      if ($UseGitMove) {
        git ls-files --error-unmatch -- $oldPath *> $null
        if ($LASTEXITCODE -eq 0) {
          git mv -- $oldPath $newPath
        } else {
          Move-Item -Path $oldPath -Destination $newPath -Force
        }
      } else {
        Move-Item -Path $oldPath -Destination $newPath -Force
      }
    }
    $moveCount++
  } elseif ($newExists) {
    $alreadyMovedCount++
  } else {
    $missingCount++
    Write-Host ("[WARN] both paths are missing, skipped: {0} -> {1}" -f $oldPath, $newPath)
    continue
  }

  if ((Test-Path $newPath) -or (-not $Apply -and (Test-Path $oldPath))) {
    $targetFile = if (Test-Path $newPath) { $newPath } else { $oldPath }
    $packageUpdated = Update-Package-Declaration -filePath $targetFile -targetPackage $item.new_pkg -applyMode $Apply
    if ($packageUpdated) {
      $packageUpdateCount++
      if (-not $Apply) {
        Write-Host ("[PLAN] package declaration will be updated: {0} -> {1}" -f $targetFile, $item.new_pkg)
      }
    }
  }
}

$refPairs = $changedMappings | Sort-Object { $_.old_fqcn.Length } -Descending

$javaFiles = @()
if (Test-Path "src/main/java") {
  $javaFiles += Get-ChildItem -Path "src/main/java" -Recurse -File -Filter "*.java"
}
if (Test-Path "src/test/java") {
  $javaFiles += Get-ChildItem -Path "src/test/java" -Recurse -File -Filter "*.java"
}

$refFileChangedCount = 0
$refHitCount = 0

foreach ($file in $javaFiles) {
  $path = $file.FullName
  $content = Read-Text $path
  $updated = $content
  $fileHit = 0

  foreach ($pair in $refPairs) {
    $pattern = "(?<![A-Za-z0-9_$.])" + [regex]::Escape($pair.old_fqcn) + "(?![A-Za-z0-9_$])"
    $hits = [regex]::Matches($updated, $pattern).Count
    if ($hits -gt 0) {
      $fileHit += $hits
      $updated = [regex]::Replace($updated, $pattern, $pair.new_fqcn)
    }
  }

  if ($fileHit -gt 0) {
    $refHitCount += $fileHit
    $refFileChangedCount++
    if ($Apply) {
      Write-Text -path $path -content $updated
    } else {
      $rootPrefix = (Get-Location).Path + [System.IO.Path]::DirectorySeparatorChar
      $rel = $path
      if ($path.StartsWith($rootPrefix)) {
        $rel = $path.Substring($rootPrefix.Length)
      }
      $rel = $rel.Replace("\", "/")
      Write-Host ("[PLAN] references will be updated: {0} (hits: {1})" -f $rel, $fileHit)
    }
  }
}

Write-Host ""
Write-Host "========== Summary =========="
Write-Host ("moved files: {0}" -f $moveCount)
Write-Host ("already at new path: {0}" -f $alreadyMovedCount)
Write-Host ("missing mappings: {0}" -f $missingCount)
Write-Host ("package declarations updated: {0}" -f $packageUpdateCount)
Write-Host ("files with reference updates: {0}" -f $refFileChangedCount)
Write-Host ("total reference replacement hits: {0}" -f $refHitCount)
Write-Host "============================="

if (-not $Apply) {
  Write-Host "[INFO] DryRun finished. Run below to apply:"
  Write-Host "       powershell -ExecutionPolicy Bypass -File scripts/migrate-package-with-refs.ps1 -Apply"
}
