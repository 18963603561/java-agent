function Convert-FromBase64Utf8 {
  param([string]$Value)
  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

$M1Tasks = @(
  [PSCustomObject]@{
    taskId = 'M1-TASK-001'
    name = (Convert-FromBase64Utf8 '5pCt5bu65Z+656GA5qih5Z2X6aqo5p625LiO6YWN572u')
    deps = @()
    testCmd = 'mvn -q -DskipTests package'
    enabled = $true
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-011'
    name = (Convert-FromBase64Utf8 '6JC95bqT5Lu75Yqh5LiO5LqL5Lu25qC45b+D6KGo')
    deps = @('M1-TASK-001')
    testCmd = 'mvn -q -pl persistence test'
    enabled = $true
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-012'
    name = (Convert-FromBase64Utf8 '6JC95bqT5bel5YW35LiO5oiQ5pys5rK755CG6KGo')
    deps = @('M1-TASK-001')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-004'
    name = (Convert-FromBase64Utf8 '6JC95Zyw5Lu75Yqh54q25oCB5py65LiO5bmC562J6KeE5YiZ')
    deps = @('M1-TASK-001', 'M1-TASK-011')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-013'
    name = (Convert-FromBase64Utf8 '5a6e546w5pyA5bCP6aKE566X5LiO6ZmQ5rWB5rK755CG')
    deps = @('M1-TASK-012', 'M1-TASK-002')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-002'
    name = (Convert-FromBase64Utf8 '5bu656uL5Lu75Yqh55Sf5ZG95ZGo5pyf5o6l5Y+j')
    deps = @('M1-TASK-001', 'M1-TASK-011', 'M1-TASK-017')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-003'
    name = (Convert-FromBase64Utf8 '5bu656uL5a6e5pe25LqL5Lu25rWB6K6i6ZiF5LiO5pat57q/57ut5Lyg')
    deps = @('M1-TASK-001', 'M1-TASK-011')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-005'
    name = (Convert-FromBase64Utf8 '5bu656uL5oiY55Wl5bGC55uu5qCH5LiO57qm5p2f5pyA5bCP5aSE55CG')
    deps = @('M1-TASK-002', 'M1-TASK-011', 'M1-TASK-013')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-006'
    name = (Convert-FromBase64Utf8 '5bu656uL5oiY5pyv5bGC5Lu75Yqh5ouG6Kej5LiO5L6d6LWW5Y2g5L2N')
    deps = @('M1-TASK-004', 'M1-TASK-011')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-007'
    name = (Convert-FromBase64Utf8 '5bu656uL5o6o55CG5bGC5bel5YW36YCJ5oup5Y2g5L2N')
    deps = @('M1-TASK-006', 'M1-TASK-012')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-008'
    name = (Convert-FromBase64Utf8 '5bu656uL6KGM5Yqo6K6h5YiS55Sf5oiQ5LiO5qCh6aqM5Y2g5L2N')
    deps = @('M1-TASK-005', 'M1-TASK-006', 'M1-TASK-013')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-009'
    name = (Convert-FromBase64Utf8 '5bu656uL5bel5YW35rOo5YaM5LiO5omn6KGM5pyA5bCP6Zet546v')
    deps = @('M1-TASK-012', 'M1-TASK-013')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-010'
    name = (Convert-FromBase64Utf8 '5o6l5YWl6ZW/5Lu75Yqh57yW5o6S5pyA5bCP6Zet546v')
    deps = @('M1-TASK-004', 'M1-TASK-009', 'M1-TASK-011')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-014'
    name = (Convert-FromBase64Utf8 '5bu656uL5Z+656GA5Y+v6KeC5rWL5LiO5pel5b+X6KeE6IyD')
    deps = @('M1-TASK-011', 'M1-TASK-003', 'M1-TASK-009')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-015'
    name = (Convert-FromBase64Utf8 '5bu656uL5Lya6K+d5LiK5LiL5paH5b+r54Wn5LiO5Y6L57yp5Y2g5L2N')
    deps = @('M1-TASK-012', 'M1-TASK-002')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-016'
    name = (Convert-FromBase64Utf8 '5bu656uL5pyA5bCP5Y+N6aaI6Zet546v')
    deps = @('M1-TASK-004', 'M1-TASK-014')
    testCmd = 'echo TODO'
    enabled = $false
  }
  [PSCustomObject]@{
    taskId = 'M1-TASK-017'
    name = (Convert-FromBase64Utf8 '5bu656uL5pyA5bCP6Lqr5Lu95LiO5aSa56ef5oi36ZqU56a7')
    deps = @('M1-TASK-001', 'M1-TASK-011')
    testCmd = 'echo TODO'
    enabled = $false
  }
)

$M1Tasks
