param(
  [string]$CompileLog = "target/compile-after-move.log",
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-Text([string]$path) {
  return [System.IO.File]::ReadAllText($path, [System.Text.UTF8Encoding]::new($false))
}

function Write-Text([string]$path, [string]$content) {
  $normalized = $content -replace "`r`n", "`n" -replace "`r", "`n"
  [System.IO.File]::WriteAllText($path, $normalized, [System.Text.UTF8Encoding]::new($false))
}

function Normalize-LogJavaPath([string]$pathValue) {
  $candidate = $pathValue.Trim()
  if ($candidate.StartsWith("/")) {
    $candidate = $candidate.Substring(1)
  }
  $candidate = $candidate -replace "\\", "/"
  if ($candidate -match "^[A-Za-z]:/") {
    return $candidate
  }
  return [System.IO.Path]::GetFullPath($candidate)
}

function Split-Package([string]$packageName) {
  if ([string]::IsNullOrWhiteSpace($packageName)) {
    return @()
  }
  return $packageName.Split(".")
}

function Common-Package-Prefix([string]$a, [string]$b) {
  $left = Split-Package $a
  $right = Split-Package $b
  $len = [Math]::Min($left.Length, $right.Length)
  $count = 0
  for ($i = 0; $i -lt $len; $i++) {
    if ($left[$i] -ne $right[$i]) {
      break
    }
    $count++
  }
  return $count
}

function Get-Java-Metadata([string]$path) {
  $text = Read-Text $path
  $pkgMatch = [regex]::Match($text, "(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*;")
  $packageName = if ($pkgMatch.Success) { $pkgMatch.Groups[1].Value } else { "" }

  $imports = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $importSimple = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $importMatches = [regex]::Matches($text, "(?m)^\s*import\s+([A-Za-z0-9_.]+)\s*;")
  foreach ($m in $importMatches) {
    $fqcn = $m.Groups[1].Value
    $imports.Add($fqcn) | Out-Null
    $parts = $fqcn.Split(".")
    if ($parts.Length -gt 0) {
      $importSimple.Add($parts[$parts.Length - 1]) | Out-Null
    }
  }

  return [PSCustomObject]@{
    package_name = $packageName
    imports = $imports
    import_simple = $importSimple
    content = $text
  }
}

function Add-Imports-To-File([string]$path, [string[]]$importsToAdd, [bool]$applyMode) {
  if ($importsToAdd.Count -eq 0) {
    return $false
  }

  $content = Read-Text $path
  $lines = [System.Collections.Generic.List[string]]::new()
  foreach ($line in ($content -split "`n", -1)) {
    $lines.Add($line) | Out-Null
  }

  $importIndexes = [System.Collections.Generic.List[int]]::new()
  $pkgIndex = -1
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($pkgIndex -lt 0 -and $lines[$i] -match "^\s*package\s+[A-Za-z0-9_.]+\s*;") {
      $pkgIndex = $i
    }
    if ($lines[$i] -match "^\s*import\s+[A-Za-z0-9_.]+\s*;") {
      $importIndexes.Add($i) | Out-Null
    }
  }

  $insertAt = -1
  if ($importIndexes.Count -gt 0) {
    $insertAt = ($importIndexes | Measure-Object -Maximum).Maximum + 1
  } elseif ($pkgIndex -ge 0) {
    $insertAt = $pkgIndex + 1
    if ($insertAt -ge $lines.Count -or $lines[$insertAt].Trim().Length -ne 0) {
      $lines.Insert($insertAt, "")
      $insertAt++
    }
  } else {
    $insertAt = 0
  }

  $ordered = $importsToAdd | Sort-Object -Unique
  foreach ($imp in $ordered) {
    $lines.Insert($insertAt, "import $imp;")
    $insertAt++
  }

  if ($insertAt -lt $lines.Count -and $lines[$insertAt].Trim().Length -ne 0) {
    $lines.Insert($insertAt, "")
  } elseif ($insertAt -ge $lines.Count) {
    $lines.Add("")
  }

  if ($applyMode) {
    $newContent = [string]::Join("`n", $lines)
    Write-Text -path $path -content $newContent
  }
  return $true
}

if (-not (Test-Path $CompileLog)) {
  throw "Compile log not found: $CompileLog"
}

$javaFiles = @()
if (Test-Path "src/main/java") {
  $javaFiles += Get-ChildItem -Path "src/main/java" -Recurse -File -Filter "*.java"
}
if (Test-Path "src/test/java") {
  $javaFiles += Get-ChildItem -Path "src/test/java" -Recurse -File -Filter "*.java"
}

$symbolIndex = @{}
foreach ($file in $javaFiles) {
  $text = Read-Text $file.FullName
  $pkgMatch = [regex]::Match($text, "(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*;")
  if (-not $pkgMatch.Success) {
    continue
  }
  $pkg = $pkgMatch.Groups[1].Value
  $typeMatches = [regex]::Matches($text, "(?m)^\s*(public\s+)?(class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)\b")
  foreach ($tm in $typeMatches) {
    $name = $tm.Groups[3].Value
    $fqcn = "$pkg.$name"
    if (-not $symbolIndex.ContainsKey($name)) {
      $symbolIndex[$name] = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    }
    $symbolIndex[$name].Add($fqcn) | Out-Null
  }
}

$missingByFile = @{}
$currentFile = ""
$lines = Get-Content -Path $CompileLog
foreach ($line in $lines) {
  if ($line -match '^\[ERROR\]\s+/(.+?\.java):\[\d+,\d+\]') {
    $currentFile = Normalize-LogJavaPath $Matches[1]
    continue
  }
  if ([string]::IsNullOrWhiteSpace($currentFile)) {
    continue
  }
  if (-not (Test-Path $currentFile)) {
    continue
  }
  if ($line -notmatch '^\[ERROR\].*?([A-Za-z_][A-Za-z0-9_]*)\s*$') {
    continue
  }
  $symbol = $Matches[1]
  if ($line.Contains(".")) {
    continue
  }
  if ($symbol.Length -lt 2) {
    continue
  }
  if (-not $symbolIndex.ContainsKey($symbol)) {
    continue
  }
  if (-not $missingByFile.ContainsKey($currentFile)) {
    $missingByFile[$currentFile] = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  }
  $missingByFile[$currentFile].Add($symbol) | Out-Null
}

$fileChangeCount = 0
$importAddCount = 0
$ambiguousCount = 0
$unresolvedCount = 0

foreach ($entry in $missingByFile.GetEnumerator()) {
  $filePath = $entry.Key
  $symbols = $entry.Value
  $meta = Get-Java-Metadata $filePath
  $importsToAdd = [System.Collections.Generic.List[string]]::new()

  foreach ($symbol in $symbols) {
    $candidates = @($symbolIndex[$symbol])
    if ($candidates.Count -eq 0) {
      $unresolvedCount++
      continue
    }

    $selected = ""
    if ($candidates.Count -eq 1) {
      $selected = $candidates[0]
    } else {
      $scored = @()
      foreach ($cand in $candidates) {
        $idx = $cand.LastIndexOf(".")
        if ($idx -le 0) {
          continue
        }
        $candPkg = $cand.Substring(0, $idx)
        $score = Common-Package-Prefix $meta.package_name $candPkg
        $scored += [PSCustomObject]@{ fqcn = $cand; score = $score }
      }
      if ($scored.Count -gt 0) {
        $bestScore = ($scored | Measure-Object score -Maximum).Maximum
        $best = @($scored | Where-Object { $_.score -eq $bestScore })
        if ($best.Count -eq 1) {
          $selected = $best[0].fqcn
        } else {
          $ambiguousCount++
          continue
        }
      } else {
        $ambiguousCount++
        continue
      }
    }

    $dot = $selected.LastIndexOf(".")
    if ($dot -le 0) {
      continue
    }
    $targetPkg = $selected.Substring(0, $dot)
    $simpleName = $selected.Substring($dot + 1)

    if ($targetPkg -eq $meta.package_name) {
      continue
    }
    if ($targetPkg -eq "java.lang") {
      continue
    }
    if ($meta.imports.Contains($selected)) {
      continue
    }
    if ($meta.import_simple.Contains($simpleName)) {
      continue
    }

    $importsToAdd.Add($selected)
    $meta.imports.Add($selected) | Out-Null
    $meta.import_simple.Add($simpleName) | Out-Null
    $importAddCount++
  }

  if ($importsToAdd.Count -gt 0) {
    $changed = Add-Imports-To-File -path $filePath -importsToAdd @($importsToAdd) -applyMode $Apply
    if ($changed) {
      $fileChangeCount++
      $rel = $filePath.Replace((Get-Location).Path + "\", "").Replace("\", "/")
      if ($Apply) {
        Write-Host ("[APPLY] imports fixed: {0} (+{1})" -f $rel, $importsToAdd.Count)
      } else {
        Write-Host ("[PLAN] imports to add: {0} (+{1})" -f $rel, $importsToAdd.Count)
      }
    }
  }
}

Write-Host ""
Write-Host "========== Summary =========="
Write-Host ("files changed: {0}" -f $fileChangeCount)
Write-Host ("imports added: {0}" -f $importAddCount)
Write-Host ("ambiguous symbols: {0}" -f $ambiguousCount)
Write-Host ("unresolved symbols: {0}" -f $unresolvedCount)
Write-Host "============================="

if (-not $Apply) {
  Write-Host "[INFO] DryRun finished. Run below to apply:"
  Write-Host "       powershell -ExecutionPolicy Bypass -File scripts/fix-missing-imports-from-compile.ps1 -Apply"
}
