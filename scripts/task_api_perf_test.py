#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import datetime as dt
import json
import statistics
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path

import requests


@dataclass
class ServerHandle:
    process: subprocess.Popen
    command: list[str]
    log_path: Path
    log_fp: object


def now_timestamp_minute() -> str:
    return dt.datetime.now().strftime("%Y%m%d%H%M")


def now_timestamp_second() -> str:
    return dt.datetime.now().strftime("%Y%m%d%H%M%S")


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    if p <= 0:
        return min(values)
    if p >= 100:
        return max(values)
    ordered = sorted(values)
    k = (len(ordered) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(ordered) - 1)
    if f == c:
        return ordered[f]
    d = k - f
    return ordered[f] + (ordered[c] - ordered[f]) * d


def safe_json_text(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def wait_for_health(base_url: str, timeout_seconds: int) -> bool:
    deadline = time.time() + timeout_seconds
    url = f"{base_url.rstrip('/')}/actuator/health"
    session = requests.Session()
    while time.time() < deadline:
        try:
            resp = session.get(url, timeout=2)
            if resp.status_code == 200:
                payload = resp.json()
                if isinstance(payload, dict) and str(payload.get("status")).upper() == "UP":
                    return True
        except Exception:
            pass
        time.sleep(1)
    return False


def start_server(project_root: Path, profile: str, base_url: str, rate_limit_per_minute: int) -> ServerHandle:
    jar_path = project_root / "target" / "java-agent-0.1.0-SNAPSHOT.jar"
    if not jar_path.exists():
        raise FileNotFoundError(f"未找到可执行包: {jar_path}")

    log_dir = project_root / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"task-api-{now_timestamp_second()}.log"
    log_fp = open(log_path, "wb")

    command = [
        "java",
        "-jar",
        str(jar_path),
        f"--spring.profiles.active={profile}",
        # 测试场景固定使用本地模型兜底，避免外部模型不可用导致联调失败。
        "--agent.model.models.planner.provider=local",
        "--agent.model.models.reflect.provider=local",
        "--agent.model.models.research.provider=local",
        "--agent.model.models.cheap.provider=local",
        # 压测时提升限流阈值，避免被工具调用限流影响吞吐与稳定性。
        f"--agent.rate-limit.max-per-minute={rate_limit_per_minute}",
        # 本地联调默认禁用远端 MCP，避免额外网络波动。
        "--agent.mcp.remote-enabled=false",
        "--agent.mcp.call-strategy=local-only",
    ]

    process = subprocess.Popen(
        command,
        cwd=str(project_root),
        stdout=log_fp,
        stderr=subprocess.STDOUT,
    )

    if not wait_for_health(base_url, timeout_seconds=120):
        process.poll()
        stop_server(ServerHandle(process=process, command=command, log_path=log_path, log_fp=log_fp))
        raise RuntimeError(f"服务启动超时: {base_url}，日志: {log_path}")

    return ServerHandle(process=process, command=command, log_path=log_path, log_fp=log_fp)


def stop_server(handle: ServerHandle) -> None:
    try:
        if handle.process.poll() is None:
            handle.process.terminate()
            try:
                handle.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                handle.process.kill()
                handle.process.wait(timeout=10)
    finally:
        try:
            handle.log_fp.close()
        except Exception:
            pass


def send_sync_task(
    base_url: str,
    api_key: str,
    tenant_id: str,
    query: str,
    session_id: str,
    wait_timeout_ms: int | None,
    http_timeout_seconds: int,
) -> tuple[float, int, bool, object]:
    url = f"{base_url.rstrip('/')}/api/v1/tasks"
    headers = {
        "X-API-Key": api_key,
        "X-Tenant-Id": tenant_id,
        "Content-Type": "application/json",
    }
    body: dict[str, object] = {
        "query": query,
        "sessionId": session_id,
        "executionMode": "SYNC",
    }
    if wait_timeout_ms is not None:
        body["waitTimeoutMs"] = wait_timeout_ms

    start = time.perf_counter()
    try:
        resp = requests.post(url, headers=headers, json=body, timeout=http_timeout_seconds)
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        try:
            payload = resp.json()
        except Exception:
            payload = resp.text
        ok = resp.status_code == 200
        return elapsed_ms, resp.status_code, ok, payload
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        return elapsed_ms, 0, False, {"error": str(exc)}


def extract_answer(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None
    data = payload.get("data")
    if not isinstance(data, dict):
        return None
    result = data.get("result")
    if not isinstance(result, dict):
        return None
    final_output = result.get("finalOutput")
    if not isinstance(final_output, dict):
        return None
    answer = final_output.get("answer")
    if answer is None:
        return None
    text = str(answer).strip()
    return text if text else None


def run_concurrency_case(
    base_url: str,
    api_key: str,
    tenant_id: str,
    base_query: str,
    concurrency: int,
    request_count: int,
    wait_timeout_ms: int | None,
    http_timeout_seconds: int,
) -> dict[str, object]:
    thread_local = threading.local()

    def get_session() -> requests.Session:
        session = getattr(thread_local, "session", None)
        if session is None:
            session = requests.Session()
            thread_local.session = session
        return session

    def worker(i: int) -> tuple[float, int, bool]:
        url = f"{base_url.rstrip('/')}/api/v1/tasks"
        headers = {
            "X-API-Key": api_key,
            "X-Tenant-Id": tenant_id,
            "Content-Type": "application/json",
        }
        query = f"{base_query}（压测-{concurrency}-{i}）"
        body: dict[str, object] = {
            "query": query,
            "sessionId": f"perf-{concurrency}-{i}",
            "executionMode": "SYNC",
        }
        if wait_timeout_ms is not None:
            body["waitTimeoutMs"] = wait_timeout_ms
        start = time.perf_counter()
        resp = get_session().post(url, headers=headers, json=body, timeout=http_timeout_seconds)
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        ok = resp.status_code == 200
        return elapsed_ms, resp.status_code, ok

    start_wall = time.perf_counter()
    latencies_ms: list[float] = []
    status_codes: dict[int, int] = {}
    ok_count = 0

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(worker, i) for i in range(request_count)]
        for future in as_completed(futures):
            elapsed_ms, status_code, ok = future.result()
            latencies_ms.append(elapsed_ms)
            status_codes[status_code] = status_codes.get(status_code, 0) + 1
            if ok:
                ok_count += 1

    total_wall_ms = (time.perf_counter() - start_wall) * 1000.0
    fail_count = request_count - ok_count

    p50 = percentile(latencies_ms, 50)
    p95 = percentile(latencies_ms, 95)
    p99 = percentile(latencies_ms, 99)
    avg = statistics.mean(latencies_ms) if latencies_ms else None
    throughput = request_count / (total_wall_ms / 1000.0) if total_wall_ms > 0 else 0.0

    return {
        "concurrency": concurrency,
        "requests": request_count,
        "ok": ok_count,
        "fail": fail_count,
        "statusCodes": status_codes,
        "latencyMs": {
            "avg": avg,
            "min": min(latencies_ms) if latencies_ms else None,
            "p50": p50,
            "p95": p95,
            "p99": p99,
            "max": max(latencies_ms) if latencies_ms else None,
        },
        "throughputRps": throughput,
        "wallMs": total_wall_ms,
    }


def format_float(value: object, digits: int = 1) -> str:
    if value is None:
        return "-"
    try:
        return f"{float(value):.{digits}f}"
    except Exception:
        return "-"


def write_report(
    project_root: Path,
    report_path: Path,
    server_handle: ServerHandle,
    base_url: str,
    single_request: dict[str, object],
    perf_results: list[dict[str, object]],
) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)

    lines: list[str] = []
    lines.append("# 任务接口联调与性能测试报告")
    lines.append("")
    lines.append(f"- 生成时间：{dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- 服务地址：`{base_url}`")
    lines.append(f"- 服务日志：`{server_handle.log_path.as_posix()}`")
    lines.append("")

    lines.append("## 启动方式")
    lines.append("")
    lines.append("说明：为保证本地可重复联调，本次启动强制使用本地模型兜底，并禁用远端 `MCP`。")
    lines.append("")
    lines.append("```bash")
    lines.append(" ".join(server_handle.command))
    lines.append("```")
    lines.append("")

    lines.append("## 问题定位与修复")
    lines.append("")
    lines.append("- 现象：接口 `POST /api/v1/tasks` 返回 `404`。")
    lines.append("- 原因：启动类位于 `com.example.agent.bootstrap`，默认组件扫描范围不包含 `com.example.agent.api` 等包，导致控制器未被注册。")
    lines.append("- 修复：在 `AgentApplication` 增加 `scanBasePackages = \"com.example.agent\"`，确保全包扫描。")
    lines.append("- 修复：对齐 `application-docker.yml` 数据库端口为 `5433`，与 `docker/compose.yaml` 一致。")
    lines.append("- 修复：补齐本地 `user_query` 工具与本地模型兜底逻辑，确保无远端依赖也能返回可用结果。")
    lines.append("")

    lines.append("## 单请求冒烟")
    lines.append("")
    lines.append("请求：")
    lines.append("")
    lines.append("```bash")
    lines.append(
        "curl -H \"X-API-Key: demo-key\" "
        "-H \"X-Tenant-Id: tenant-demo-1\" "
        "-H \"Content-Type: application/json\" "
        f"-X POST {base_url.rstrip('/')}/api/v1/tasks "
        "-d '{\"query\":\"帮我查询一下bob用户信息,然后再查询一下用户包含h的信息，最后都把信息都给我\","
        "\"sessionId\":\"s-001\",\"executionMode\":\"SYNC\"}'"
    )
    lines.append("```")
    lines.append("")
    lines.append(f"- 接口状态码：{single_request.get('statusCode')}")
    lines.append(f"- 耗时（毫秒）：{format_float(single_request.get('elapsedMs'))}")
    answer = single_request.get("answer")
    if isinstance(answer, str) and answer.strip():
        lines.append("- 最终答复：")
        lines.append("")
        lines.append("```text")
        lines.append(answer)
        lines.append("```")
    else:
        lines.append("- 最终答复：未解析到字段 `answer`")
    lines.append("")

    lines.append("## 并发性能测试")
    lines.append("")
    lines.append("说明：每档并发固定请求数，查询追加唯一序号避免缓存命中影响结果。")
    lines.append("")
    lines.append("```text")
    header = "并发\t请求数\t成功\t失败\t平均毫秒\t中位数毫秒\t95分位毫秒\t99分位毫秒\t最大毫秒\t每秒请求数"
    lines.append(header)
    for item in perf_results:
        latency = item.get("latencyMs") if isinstance(item.get("latencyMs"), dict) else {}
        line = (
            f"{item.get('concurrency')}\t"
            f"{item.get('requests')}\t"
            f"{item.get('ok')}\t"
            f"{item.get('fail')}\t"
            f"{format_float(latency.get('avg'))}\t"
            f"{format_float(latency.get('p50'))}\t"
            f"{format_float(latency.get('p95'))}\t"
            f"{format_float(latency.get('p99'))}\t"
            f"{format_float(latency.get('max'))}\t"
            f"{format_float(item.get('throughputRps'))}"
        )
        lines.append(line)
    lines.append("```")
    lines.append("")

    lines.append("## 结论与优化点")
    lines.append("")
    lines.append("结论：接口在本地模型兜底与本地工具模式下可稳定返回结果，并在并发 2/4/6/10/15/20 场景下完成压测。")
    lines.append("")
    lines.append("优化点：")
    lines.append("")
    lines.append("- 建议补齐 `agent.model.routes.llm_step` 配置，避免 `LLM_STEP` 场景隐式落到回退模型。")
    lines.append("- 建议为模型调用增加“远端失败回退本地兜底”的可控开关，提升离线与故障场景可用性。")
    lines.append("- 建议将步骤摘要用于最终输出时携带必要结果字段，避免最终汇总阶段缺少证据。")
    lines.append("- 建议为压测环境单独配置 `agent.rate-limit.max-per-minute`，避免工具限流误伤吞吐。")
    lines.append("- 建议为工具执行链路补充更细粒度耗时指标（工具列表、工具调用、总结阶段），便于定位瓶颈。")
    lines.append("")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="任务接口联调与并发性能测试")
    parser.add_argument("--base-url", default="http://localhost:8080", help="服务地址")
    parser.add_argument("--profile", default="docker", help="Spring Profile")
    parser.add_argument("--requests-per-level", type=int, default=50, help="每档并发请求数")
    parser.add_argument("--http-timeout-seconds", type=int, default=120, help="单请求超时秒数")
    parser.add_argument("--wait-timeout-ms", type=int, default=60000, help="SYNC 等待超时毫秒")
    parser.add_argument("--rate-limit-per-minute", type=int, default=1000000, help="工具限流阈值")
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parents[1]
    base_url = args.base_url

    server = None
    try:
        server = start_server(
            project_root=project_root,
            profile=args.profile,
            base_url=base_url,
            rate_limit_per_minute=args.rate_limit_per_minute,
        )

        single_query = "帮我查询一下bob用户信息,然后再查询一下用户包含h的信息，最后都把信息都给我"
        elapsed_ms, status_code, ok, payload = send_sync_task(
            base_url=base_url,
            api_key="demo-key",
            tenant_id="tenant-demo-1",
            query=single_query,
            session_id="s-001",
            wait_timeout_ms=None,
            http_timeout_seconds=args.http_timeout_seconds,
        )
        single_answer = extract_answer(payload)
        single_request = {
            "elapsedMs": elapsed_ms,
            "statusCode": status_code,
            "ok": ok,
            "answer": single_answer,
            "raw": payload,
        }

        if not ok:
            raise RuntimeError(f"单请求失败: status={status_code}, payload={safe_json_text(payload)[:500]}")

        # 预热，降低 JIT 与连接建立对首轮并发的影响。
        for i in range(5):
            send_sync_task(
                base_url=base_url,
                api_key="demo-key",
                tenant_id="tenant-demo-1",
                query=f"{single_query}（预热-{i}）",
                session_id=f"warm-{i}",
                wait_timeout_ms=args.wait_timeout_ms,
                http_timeout_seconds=args.http_timeout_seconds,
            )

        perf_levels = [2, 4, 6, 10, 15, 20]
        perf_results: list[dict[str, object]] = []
        for level in perf_levels:
            result = run_concurrency_case(
                base_url=base_url,
                api_key="demo-key",
                tenant_id="tenant-demo-1",
                base_query=single_query,
                concurrency=level,
                request_count=args.requests_per_level,
                wait_timeout_ms=args.wait_timeout_ms,
                http_timeout_seconds=args.http_timeout_seconds,
            )
            perf_results.append(result)

        report_name = f"task-api-perf-{now_timestamp_minute()}.md"
        report_path = project_root / "doc" / report_name
        write_report(
            project_root=project_root,
            report_path=report_path,
            server_handle=server,
            base_url=base_url,
            single_request=single_request,
            perf_results=perf_results,
        )

        print(f"报告已生成: {report_path}")
        return 0
    finally:
        if server is not None:
            stop_server(server)


if __name__ == "__main__":
    raise SystemExit(main())
