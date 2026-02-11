Param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiKey = "",
    [string]$TenantId = "tenant-a",
    [string]$WorkflowId = "wf-drill",
    [string]$TaskId = "task-drill"
)

$headers = @{
    "Content-Type" = "application/json"
}
if ($ApiKey -ne "") {
    $headers["X-API-Key"] = $ApiKey
}

Write-Host "[Drill] 1) 触发回放失败场景（任务不存在）"
$replayBody = @{
    taskId = "not-found-task"
    mode = "full"
} | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/replay" -Headers $headers -Body $replayBody | Out-Null
    Write-Host "[Drill] 回放请求返回成功（请检查业务是否允许）"
} catch {
    Write-Host "[Drill] 预期中的回放失败已触发: $($_.Exception.Message)"
}

Write-Host "[Drill] 2) 提交一条高风险任务以触发审批（需服务侧开启能力评估与审批）"
$taskBody = @{
    query = "请深入调研并对比高风险方案，输出完整证据链"
    context = @{
        tool = "demo_tool"
    }
} | ConvertTo-Json -Depth 10
try {
    $taskResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/tasks" -Headers $headers -Body $taskBody
    Write-Host "[Drill] 任务提交返回: $($taskResp | ConvertTo-Json -Depth 10)"
} catch {
    Write-Host "[Drill] 提交任务失败: $($_.Exception.Message)"
}

Write-Host "[Drill] 3) 模拟审批超时：不提交审批决策，等待超时窗口后观察指标"
Write-Host "[Drill] 提示: 观察 governance.approval.decision.total{result=timeout}"

Write-Host "[Drill] 4) 限流/熔断压力提示"
Write-Host "[Drill] 可重复快速调用 /api/v1/tasks 触发治理限流与熔断保护"

Write-Host "[Drill] 演练结束，请在日志中搜索 domain/action/result 字段组合。"

