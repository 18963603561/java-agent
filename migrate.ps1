param(
  [string]$MappingFile = "mapping.csv"
)

if (-not (Test-Path $MappingFile)) {
  Write-Error "mapping file not found: $MappingFile"
  exit 1
}

function PathToPackage([string]$path) {
  $rel = $path -replace '^src/main/java/', ''
  $rel = $rel -replace '\.java$', ''
  $rel = $rel -replace '/', '.'
  # remove class name
  return ($rel.Substring(0, $rel.LastIndexOf('.')))
}

function UpdatePackageDecl([string]$file, [string]$pkg) {
  $content = Get-Content $file -Raw
  if ($content -match '^\s*package\s+[^;]+;') {
    $content = [regex]::Replace($content, '^\s*package\s+[^;]+;', "package $pkg;", "Multiline")
  } else {
    $content = "package $pkg;`r`n`r`n" + $content
  }
  Set-Content -Path $file -Value $content -NoNewline
}

$lines = Get-Content $MappingFile
foreach ($line in $lines) {
  if ($line.Trim().Length -eq 0) { continue }
  if ($line.StartsWith("old_path")) { continue }

  $parts = $line.Split(",", 2)
  $old = $parts[0].Trim()
  $new = $parts[1].Trim()

  if (-not (Test-Path $old)) {
    Write-Host "skip (missing): $old"
    continue
  }

  $newDir = Split-Path $new -Parent
  if (-not (Test-Path $newDir)) { New-Item -ItemType Directory -Force -Path $newDir | Out-Null }

  git mv $old $new | Out-Null

  $pkg = PathToPackage $new
  UpdatePackageDecl $new $pkg

  Write-Host "moved: $old -> $new (package $pkg)"
}

Write-Host "Done. Next: mvn -DskipTests compile (then fix imports via IDE/OpenRewrite)"
