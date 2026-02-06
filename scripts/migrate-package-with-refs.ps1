<#
.SYNOPSIS
  Migrate Java files across packages and update references (default is DryRun).

.DESCRIPTION
  Reads a CSV (current_path,target_path) and performs:
  1) Move Java files (DryRun by default).
  2) Update package declaration for moved files based on the target path.
  3) Replace explicit fully-qualified class name references (imports and code).

  Notes:
  - This script only replaces explicit FQNs; it does not auto-add imports for same-package simple-name refs.
  - Writes with UTF-8 (no BOM) and LF to avoid encoding/line-ending drift.

.PARAMETER CsvPath
  CSV path. Required columns: current_path,target_path.

.PARAMETER Apply
  Apply changes to disk. If omitted, runs in DryRun mode (no writes).

.PARAMETER ScanRoots
  Source roots to scan for FQN replacements. Defaults to src/main/java and src/test/java.

.EXAMPLE
  # DryRun (default)
  .\scripts\migrate-package-with-refs.ps1 -CsvPath doc/runtime-package-migration-202602060908.csv

.EXAMPLE
  # Apply changes
  .\scripts\migrate-package-with-refs.ps1 -CsvPath doc/runtime-package-migration-202602060908.csv -Apply
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$CsvPath = 'doc/runtime-package-migration-202602060908.csv',

    [Parameter(Mandatory = $false)]
    [switch]$Apply,

    [Parameter(Mandatory = $false)]
    [string]$RepoRoot,

    [Parameter(Mandatory = $false)]
    [string[]]$ScanRoots = @('src/main/java', 'src/test/java')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Normalize-PosixPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        return $null
    }
    $p = $path.Trim()
    $p = $p -replace '\\', '/'
    while ($p.Contains('//')) {
        $p = $p -replace '//', '/'
    }
    return $p
}

function Resolve-RepoRoot() {
    $scriptRoot = $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
        return (Resolve-Path '.').Path
    }
    return (Resolve-Path (Join-Path $scriptRoot '..')).Path
}

function To-FullPath([string]$repoRoot, [string]$relativePosix) {
    $rel = Normalize-PosixPath $relativePosix
    if (-not $rel) {
        return $null
    }
    return (Join-Path $repoRoot ($rel -replace '/', '\'))
}

function Get-FqnFromJavaPath([string]$relativePosix) {
    $p = Normalize-PosixPath $relativePosix
    if (-not $p -or -not $p.EndsWith('.java')) {
        return $null
    }
    $prefixes = @('src/main/java/', 'src/test/java/')
    $matchedPrefix = $null
    foreach ($prefix in $prefixes) {
        if ($p.StartsWith($prefix)) {
            $matchedPrefix = $prefix
            break
        }
    }
    if (-not $matchedPrefix) {
        return $null
    }
    $suffix = $p.Substring($matchedPrefix.Length)
    $suffix = $suffix.Substring(0, $suffix.Length - 5) # strip .java
    return ($suffix -replace '/', '.')
}

function Get-PackageFromJavaPath([string]$relativePosix) {
    $fqn = Get-FqnFromJavaPath $relativePosix
    if (-not $fqn) {
        return $null
    }
    $lastDot = $fqn.LastIndexOf('.')
    if ($lastDot -lt 0) {
        return $null
    }
    return $fqn.Substring(0, $lastDot)
}

function Read-TextUtf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Normalize-ToLf([string]$text) {
    if ($null -eq $text) {
        return $null
    }
    $t = $text.Replace("`r`n", "`n")
    $t = $t.Replace("`r", "`n")
    return $t
}

function Write-TextUtf8NoBomLf([string]$path, [string]$content) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $normalized = Normalize-ToLf $content
    if (-not $normalized.EndsWith("`n")) {
        $normalized = $normalized + "`n"
    }
    [System.IO.File]::WriteAllText($path, $normalized, $utf8NoBom)
}

function Update-PackageDeclaration([string]$content, [string]$newPackage) {
    if ([string]::IsNullOrWhiteSpace($newPackage)) {
        return $content
    }
    if ($null -eq $content) {
        return $content
    }

    # Strip UTF-8 BOM if present (ReadAllText with Encoding.UTF8 keeps it as U+FEFF).
    $content = $content.Replace([char]0xFEFF, '')
    $content = Normalize-ToLf $content

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.AddRange(($content -split "`n", -1))

    $packageLineIndexes = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*package\s+[^;]+;\s*$') {
            $packageLineIndexes.Add($i)
        }
    }

    if ($packageLineIndexes.Count -eq 0) {
        # No package statement found: insert at the beginning.
        return "package $newPackage;`n`n" + $content
    }

    # Replace the first package statement, remove any duplicates.
    $lines[$packageLineIndexes[0]] = "package $newPackage;"
    for ($j = $packageLineIndexes.Count - 1; $j -ge 1; $j--) {
        $lines.RemoveAt($packageLineIndexes[$j])
    }
    return ($lines -join "`n")
}

$repoRoot = $RepoRoot
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    $repoRoot = Resolve-RepoRoot
} else {
    $repoRoot = (Resolve-Path $repoRoot).Path
}
$csvFullPath = To-FullPath $repoRoot $CsvPath
if (-not $csvFullPath -or -not (Test-Path -LiteralPath $csvFullPath)) {
    throw "CSV not found: $CsvPath"
}

$rows = Import-Csv -LiteralPath $csvFullPath
if (-not $rows -or $rows.Count -eq 0) {
    throw "CSV is empty: $CsvPath"
}

foreach ($row in $rows) {
    if (-not $row.PSObject.Properties.Name -contains 'current_path' -or -not $row.PSObject.Properties.Name -contains 'target_path') {
        throw "CSV columns missing. Required: current_path,target_path"
    }
    break
}

$mappings = @()
foreach ($row in $rows) {
    $current = Normalize-PosixPath $row.current_path
    $target = Normalize-PosixPath $row.target_path
    if (-not $current -or -not $target) {
        continue
    }
    if (-not $current.EndsWith('.java') -or -not $target.EndsWith('.java')) {
        continue
    }
    $oldFqn = Get-FqnFromJavaPath $current
    $newFqn = Get-FqnFromJavaPath $target
    if (-not $oldFqn -or -not $newFqn) {
        continue
    }
    $oldPkg = Get-PackageFromJavaPath $current
    $newPkg = Get-PackageFromJavaPath $target
    $simple = $oldFqn.Substring($oldFqn.LastIndexOf('.') + 1)
    $needsMove = ($current -ne $target)
    $needsReplace = ($oldFqn -ne $newFqn)

    $mappings += [pscustomobject]@{
        current_path = $current
        target_path  = $target
        old_fqn       = $oldFqn
        new_fqn       = $newFqn
        old_package   = $oldPkg
        new_package   = $newPkg
        simple_name   = $simple
        needs_move    = $needsMove
        needs_replace = $needsReplace
    }
}

$moves = @($mappings | Where-Object { $_.needs_move })
$replacements = @($mappings | Where-Object { $_.needs_replace })

$mode = if ($Apply) { 'Apply' } else { 'DryRun' }
Write-Host "Mode: $mode"
Write-Host "CSV: $CsvPath"
Write-Host ("Planned moves: {0}" -f $moves.Count)
Write-Host ("Planned FQN rewrites: {0}" -f $replacements.Count)

if ($moves.Count -gt 0) {
    Write-Host '---'
    foreach ($m in $moves) {
        Write-Host ("Move: {0} -> {1}" -f $m.current_path, $m.target_path)
    }
}

function Ensure-Directory([string]$path) {
    $dir = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
}

# 1) Move files and update package declaration
foreach ($m in $moves) {
    $srcFull = To-FullPath $repoRoot $m.current_path
    $dstFull = To-FullPath $repoRoot $m.target_path

    if (-not (Test-Path -LiteralPath $srcFull)) {
        Write-Warning ("Source file not found, skip: {0}" -f $m.current_path)
        continue
    }
    if (Test-Path -LiteralPath $dstFull) {
        throw ("Target file already exists, refuse to overwrite: {0}" -f $m.target_path)
    }

    if (-not $Apply) {
        continue
    }

    Ensure-Directory $dstFull
    Move-Item -LiteralPath $srcFull -Destination $dstFull

    $content = Read-TextUtf8 $dstFull
    $updated = Update-PackageDeclaration $content $m.new_package
    Write-TextUtf8NoBomLf $dstFull $updated
}

# 2) Scan roots and rewrite explicit FQNs
$scanFiles = @()
foreach ($root in $ScanRoots) {
    $fullRoot = To-FullPath $repoRoot $root
    if (-not $fullRoot -or -not (Test-Path -LiteralPath $fullRoot)) {
        continue
    }
    $scanFiles += Get-ChildItem -LiteralPath $fullRoot -Recurse -File -Filter *.java
}

$updatedFileCount = 0
$totalReplaceCount = 0

foreach ($file in $scanFiles) {
    $path = $file.FullName
    $content = Read-TextUtf8 $path
    $newContent = $content

    foreach ($r in $replacements) {
        $old = $r.old_fqn
        $new = $r.new_fqn
        if ([string]::IsNullOrEmpty($old) -or [string]::IsNullOrEmpty($new)) {
            continue
        }
        if ($newContent.Contains($old)) {
            $count = ([regex]::Matches($newContent, [regex]::Escape($old))).Count
            $totalReplaceCount += $count
            $newContent = $newContent.Replace($old, $new)
        }
    }

    if ($newContent -ne $content) {
        $updatedFileCount++
        if ($Apply) {
            Write-TextUtf8NoBomLf $path $newContent
        }
    }
}

Write-Host '---'
Write-Host ("Scanned Java files: {0}" -f $scanFiles.Count)
Write-Host ("Files changed by FQN rewrite: {0}" -f $updatedFileCount)
Write-Host ("Total FQN rewrite occurrences: {0}" -f $totalReplaceCount)

if (-not $Apply) {
    Write-Host 'DryRun: no files were written. Add -Apply to persist changes.'
}
