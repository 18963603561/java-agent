import argparse
import datetime
import os
import re
from pathlib import Path


def iter_java_files(root: Path):
    if not root.exists():
        return []
    return sorted([p for p in root.rglob('*.java') if p.is_file()])


def strip_comments(line: str, in_block: bool):
    result = []
    i = 0
    n = len(line)
    while i < n:
        if in_block:
            end = line.find('*/', i)
            if end == -1:
                return ''.join(result), True
            i = end + 2
            in_block = False
            continue
        if line.startswith('/*', i):
            in_block = True
            i += 2
            continue
        if line.startswith('//', i):
            break
        ch = line[i]
        if ch in ('"', "'"):
            quote = ch
            result.append('""')
            i += 1
            while i < n:
                if line[i] == '\\':
                    i += 2
                    continue
                if line[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        result.append(ch)
        i += 1
    return ''.join(result), in_block


def compute_comment_flags(lines):
    flags = [False] * len(lines)
    in_block = False
    for idx, line in enumerate(lines):
        s = line.strip()
        if in_block:
            flags[idx] = True
            if '*/' in s:
                in_block = False
            continue
        if s.startswith('//'):
            flags[idx] = True
            continue
        if '/*' in s:
            flags[idx] = True
            if '*/' not in s:
                in_block = True
    return flags


def has_leading_comment(lines, comment_flags, idx):
    j = idx - 1
    while j >= 0:
        if lines[j].strip() == '':
            j -= 1
            continue
        return comment_flags[j]
    return False


def has_nearby_comment(lines, comment_flags, idx):
    for j in range(idx, max(-1, idx - 3), -1):
        if j < 0:
            break
        if comment_flags[j]:
            return True
        line = lines[j]
        if '//' in line or '/*' in line:
            return True
        if line.strip().startswith('*'):
            return True
        if line.strip() == '':
            continue
        if j != idx:
            break
    return False


def parse_package(lines):
    for line in lines:
        m = re.match(r'\s*package\s+([a-zA-Z0-9_.]+)\s*;', line)
        if m:
            return m.group(1)
    return '(default)'


def is_getter_setter(name: str, params: str):
    params = params.strip()
    if name.startswith('get') or name.startswith('is'):
        return params == ''
    if name.startswith('set'):
        if params == '':
            return False
        return len([p for p in params.split(',') if p.strip()]) == 1
    return False


def scan_file(path: Path):
    text = path.read_text(encoding='utf-8', errors='replace')
    lines = text.splitlines()
    total_lines = len(lines)
    comment_flags = compute_comment_flags(lines)
    comment_lines = sum(1 for f in comment_flags if f)
    density = (comment_lines / total_lines) if total_lines > 0 else 0.0
    package = parse_package(lines)

    missing_type_doc = []
    missing_field_doc = []
    missing_method_doc = []
    missing_block_comment = []

    types = []

    depth = 0
    in_block = False
    class_stack = []
    pending_type = None
    method_depth = None
    current_method_name = None

    method_pattern = re.compile(
        r'^\s*(public|protected|private)?\s*'
        r'(static\s+)?(abstract\s+)?'
        r'([\w<>\[\], ?]+)\s+'
        r'([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*(throws [^{;]+)?\s*([;{])'
    )

    type_pattern = re.compile(
        r'^\s*(public|protected|private)?\s*(abstract\s+)?(class|interface|enum)\s+([A-Za-z_][A-Za-z0-9_]*)'
    )

    for idx, line in enumerate(lines):
        code_line, in_block = strip_comments(line, in_block)
        base_depth = depth
        code = code_line

        m_type = type_pattern.match(code)
        if m_type and base_depth == 0:
            kind = m_type.group(3)
            name = m_type.group(4)
            is_abstract = m_type.group(2) is not None or kind in ('interface',)
            types.append({"name": name, "kind": kind, "line": idx + 1})
            if not has_leading_comment(lines, comment_flags, idx):
                missing_type_doc.append(idx + 1)
            pending_type = {"name": name, "kind": kind, "line": idx + 1, "is_abstract": is_abstract}

        if class_stack:
            current_class = class_stack[-1]
            if base_depth == current_class['body_depth'] and method_depth is None:
                m_method = method_pattern.match(code)
                if m_method:
                    name = m_method.group(5)
                    params = m_method.group(6) or ''
                    modifiers = code
                    is_public = 'public' in modifiers
                    is_protected = 'protected' in modifiers
                    is_abstract = 'abstract' in modifiers
                    is_interface = current_class['kind'] == 'interface'
                    require_doc = (is_public or is_protected or is_abstract or is_interface)
                    if require_doc and not is_getter_setter(name, params):
                        if not has_leading_comment(lines, comment_flags, idx):
                            missing_method_doc.append(idx + 1)
                    if m_method.group(8) == '{':
                        method_depth = base_depth + 1
                        current_method_name = name
                else:
                    if ';' in code and '(' not in code and 'class ' not in code and 'interface ' not in code and 'enum ' not in code:
                        if any(k in code for k in ['public', 'protected', 'static', 'final']):
                            if not has_leading_comment(lines, comment_flags, idx):
                                missing_field_doc.append(idx + 1)

        if method_depth is not None:
            if re.search(r'\b(if|for|while|switch|try|catch)\b', code):
                if not has_nearby_comment(lines, comment_flags, idx):
                    missing_block_comment.append(idx + 1)

        open_braces = code.count('{')
        close_braces = code.count('}')
        depth += open_braces - close_braces

        if pending_type and base_depth == 0 and open_braces > 0:
            class_stack.append({
                "name": pending_type['name'],
                "kind": pending_type['kind'],
                "body_depth": base_depth + 1,
            })
            pending_type = None

        if method_depth is not None and depth < method_depth:
            method_depth = None
            current_method_name = None

        if class_stack and depth < class_stack[-1]['body_depth']:
            class_stack.pop()

    return {
        "path": str(path).replace('\\', '/'),
        "package": package,
        "total_lines": total_lines,
        "comment_lines": comment_lines,
        "density": density,
        "missing_type_doc": missing_type_doc,
        "missing_field_doc": missing_field_doc,
        "missing_method_doc": missing_method_doc,
        "missing_block_comment": missing_block_comment,
        "types": types,
    }


def summarize(files):
    by_package = {}
    for info in files:
        pkg = info['package']
        stats = by_package.setdefault(pkg, {
            'files': 0,
            'noncompliant': 0,
            'low_density': 0,
            'missing_type_doc': 0,
            'missing_field_doc': 0,
            'missing_method_doc': 0,
            'missing_block_comment': 0,
            'compliant': 0,
        })
        stats['files'] += 1
        has_type = bool(info['missing_type_doc'])
        has_field = bool(info['missing_field_doc'])
        has_method = bool(info['missing_method_doc'])
        has_block = bool(info['missing_block_comment'])
        low_density = info['density'] < 0.25
        if low_density:
            stats['low_density'] += 1
        if has_type:
            stats['missing_type_doc'] += 1
        if has_field:
            stats['missing_field_doc'] += 1
        if has_method:
            stats['missing_method_doc'] += 1
        if has_block:
            stats['missing_block_comment'] += 1
        if has_type or has_field or has_method or has_block:
            stats['noncompliant'] += 1
        else:
            stats['compliant'] += 1
    return by_package


def format_density(density):
    return f"{density:.2%}"


def write_report(out_path: Path, main_files, test_files):
    now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M')
    with out_path.open('w', encoding='utf-8') as f:
        f.write('# Comment Audit Report\n\n')
        f.write(f"生成时间: {now}\n")
        f.write('扫描范围:\n')
        f.write('- src/main/java\n')
        if test_files is not None:
            f.write('- src/test/java（单独统计）\n')
        f.write('\n规则摘要:\n')
        f.write('- 类/接口/枚举/抽象类必须有类型注释\n')
        f.write('- public/protected 方法必须有方法注释（get/set 例外）\n')
        f.write('- 抽象方法/接口方法必须有注释\n')
        f.write('- 全局字段（static/public/protected/final）必须有注释\n')
        f.write('- 方法体关键代码块需有块注释说明\n\n')
        f.write('注释密度口径说明:\n')
        f.write('- comment_lines / total_lines\n')
        f.write('- comment_lines 统计 // 与 /* */ 所在行\n\n')

        def write_summary(title, files):
            f.write(f'## Summary by Package（{title}）\n')
            by_pkg = summarize(files)
            f.write('| package | files | noncompliant | low_density(<25%) | missing_type_doc | missing_field_doc | missing_method_doc | missing_block_comment |\n')
            f.write('| --- | --- | --- | --- | --- | --- | --- | --- |\n')
            for pkg in sorted(by_pkg.keys()):
                s = by_pkg[pkg]
                f.write(f"| {pkg} | {s['files']} | {s['noncompliant']} | {s['low_density']} | {s['missing_type_doc']} | {s['missing_field_doc']} | {s['missing_method_doc']} | {s['missing_block_comment']} |\n")
            f.write('\n')
            f.write('包级统计补充（合规/问题占比，按文件口径）：\n')
            for pkg in sorted(by_pkg.keys()):
                s = by_pkg[pkg]
                non = s['noncompliant'] if s['noncompliant'] > 0 else 1
                f.write(f"- {pkg}: 合规 {s['compliant']}，不合规 {s['noncompliant']}；类型缺失 {s['missing_type_doc']/non:.0%}，字段缺失 {s['missing_field_doc']/non:.0%}，方法缺失 {s['missing_method_doc']/non:.0%}，块注释缺失 {s['missing_block_comment']/non:.0%}\n")
            f.write('\n')

        write_summary('main', main_files)
        if test_files is not None:
            write_summary('test', test_files)

        def write_details(title, files):
            f.write(f'## Noncompliant Details（{title}）\n')
            by_pkg = {}
            for info in files:
                if not (info['missing_type_doc'] or info['missing_field_doc'] or info['missing_method_doc'] or info['missing_block_comment']):
                    continue
                by_pkg.setdefault(info['package'], []).append(info)
            for pkg in sorted(by_pkg.keys()):
                f.write(f'### {pkg}\n')
                for info in by_pkg[pkg]:
                    types = info['types']
                    type_desc = ', '.join([f"{t['kind']} {t['name']}" for t in types]) if types else 'unknown'
                    f.write(f"#### {info['path']}\n")
                    f.write(f"- 类型: {type_desc}\n")
                    f.write(f"- 注释密度: {format_density(info['density'])}\n")
                    if info['density'] < 0.25:
                        f.write('- 低密度文件: 是\n')
                    missing_items = []
                    if info['missing_type_doc']:
                        missing_items.append(f"类型注释缺失: {info['missing_type_doc']}")
                    if info['missing_field_doc']:
                        missing_items.append(f"字段注释缺失: {info['missing_field_doc']}")
                    if info['missing_method_doc']:
                        missing_items.append(f"方法注释缺失: {info['missing_method_doc']}")
                    if info['missing_block_comment']:
                        missing_items.append(f"块注释缺失: {info['missing_block_comment']}")
                    f.write('- 缺失项:\n')
                    for item in missing_items:
                        f.write(f"  - {item}\n")
                    f.write('- 建议修复点: 根据行号补充说明性注释，说明原因与意图\n\n')
            if not by_pkg:
                f.write('无不合规项。\n\n')

        write_details('main', main_files)
        if test_files is not None:
            write_details('test', test_files)

        f.write('## How to Run\n')
        f.write('```powershell\n')
        f.write('python scripts/comment_audit.py\n')
        f.write('```\n')
        f.write('输出路径: doc/comment-audit-YYYYMMDD.md\n\n')

        f.write('## Next Steps (No code changes in this phase)\n')
        f.write('- 第 1 阶段: 为缺失类型注释与 public/protected 方法注释补齐基础说明\n')
        f.write('- 第 2 阶段: 对关键控制流程补充块注释，说明设计意图与风险\n')
        f.write('- 第 3 阶段: 针对全局常量与配置字段补充来源/约束说明\n')



def main():
    parser = argparse.ArgumentParser(description='Java 注释合规性审计')
    parser.add_argument('--main-root', default='src/main/java')
    parser.add_argument('--test-root', default='src/test/java')
    parser.add_argument('--out', default='')
    args = parser.parse_args()

    main_root = Path(args.main_root)
    test_root = Path(args.test_root)
    main_files = [scan_file(p) for p in iter_java_files(main_root)]
    test_files = None
    if test_root.exists():
        test_files = [scan_file(p) for p in iter_java_files(test_root)]

    if args.out:
        out_path = Path(args.out)
    else:
        date = datetime.datetime.now().strftime('%Y%m%d')
        out_path = Path('doc') / f'comment-audit-{date}.md'
    out_path.parent.mkdir(parents=True, exist_ok=True)
    write_report(out_path, main_files, test_files)
    print(str(out_path))


if __name__ == '__main__':
    main()