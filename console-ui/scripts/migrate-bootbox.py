#!/usr/bin/env python3
"""
Migrate `window.bootbox.alert/confirm/prompt` calls → Antd modal helper
(imported from `utils/modal`).

对每个含 `window.bootbox.X(` 的文件:
1. 计算到 `src/utils/modal` 的相对路径
2. 在最顶的 import 区后面加 `import {alert, confirm, prompt} from '<relpath>'`
   (如果文件已有 import 块,合并;否则新增)
3. `window.bootbox.alert(`  → `alert(`
4. `window.bootbox.confirm(`  → `confirm(`
5. `window.bootbox.prompt(`   → `prompt(`

对含 `import '../bootbox.js'` (side-effect 入口) 的 index.tsx:
- 删掉那一行
"""
import re
import sys
from pathlib import Path
from collections import OrderedDict

SRC = Path("/home/fredgu/git_home/ruleforge/console-ui/src")
HELPER = "utils/modal"  # 文件实际路径 src/utils/modal.tsx

# 三种 bootbox 调用的 pattern
PATTERNS = [
    (re.compile(r"window\.bootbox\.alert\("), "alert("),
    (re.compile(r"window\.bootbox\.confirm\("), "confirm("),
    (re.compile(r"window\.bootbox\.prompt\("), "prompt("),
    (re.compile(r"window\.bootbox\.dialog\("), "dialog("),
]

# 检测一个文件用到了哪些函数
USED = re.compile(r"window\.bootbox\.(alert|confirm|prompt|dialog)\(")


def to_relative(file: Path) -> str:
    """计算 file 相对于 src/ 的相对路径,再到 src/utils/modal"""
    rel = file.relative_to(SRC)  # e.g. action/action.ts
    # file 是 foo/bar.ts, helper 在 utils/modal.tsx → ../utils/modal
    parts = list(rel.parts)
    # 文件本身深度:foo/bar.ts → 1 层上级
    depth = len(parts) - 1
    up = "../" * depth
    return f"{up}utils/modal"


def already_imports(content: str) -> bool:
    return re.search(r"from\s+['\"][^'\"]*utils/modal['\"]", content) is not None


def find_import_block_end(content: str) -> int:
    """找文件最顶 import 块结束的位置(最后一个 import 之后)"""
    # 匹配顶部的连续 import 语句
    lines = content.split("\n")
    last_import_line = -1
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("import ") or stripped.startswith("import{") or stripped.startswith("import "):
            last_import_line = i
        elif last_import_line >= 0 and stripped == "":
            # 空行,继续
            continue
        elif last_import_line >= 0:
            # 第一个非 import 非空行 → 结束
            return i
    return last_import_line + 1 if last_import_line >= 0 else 0


def add_import(content: str, functions: list[str], helper_rel: str) -> str:
    """在 import 块后面加 import {alert, confirm, prompt} from '...'"""
    fn_str = ", ".join(functions)
    new_import = f"import {{{fn_str}}} from '{helper_rel}';"

    # 如果已经导入了 utils/modal,不动
    if already_imports(content):
        return content

    lines = content.split("\n")
    end_line = find_import_block_end(content)
    lines.insert(end_line, new_import)
    return "\n".join(lines)


def remove_bootbox_sideeffect(content: str) -> tuple[str, bool]:
    """删 `import '../bootbox.js'` 这种 side-effect 行,返回 (新内容, 是否删了)"""
    new_content, n = re.subn(
        r"^import\s+['\"][^'\"]*bootbox(?:\.js)?['\"];?\s*\n",
        "",
        content,
        flags=re.MULTILINE,
    )
    return new_content, n > 0


def migrate_file(path: Path) -> bool:
    """迁移一个文件,返回是否做了改动"""
    text = path.read_text(encoding="utf-8")

    # 检测用到了哪些函数
    used = set(USED.findall(text))
    if not used and "import '..*bootbox" not in text:
        return False  # 这个文件不动

    original = text

    # 替换 window.bootbox.X( → X(
    for pat, repl in PATTERNS:
        text = pat.sub(repl, text)

    # 删除 side-effect import
    text, removed = remove_bootbox_sideeffect(text)

    # 加 import
    if used:
        helper_rel = to_relative(path)
        # 排序保证 import 一致
        functions = sorted(used)
        text = add_import(text, functions, helper_rel)

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main():
    files_changed = []
    for f in SRC.rglob("*.ts"):
        if "/node_modules/" in str(f):
            continue
        if "/__tests__/" in str(f):
            continue  # 测试文件单独处理
        if f.name.endswith(".test.ts") or f.name.endswith(".test.tsx"):
            continue
        if f.name == "bootbox.ts":
            # bootbox.ts 本身要删
            continue
        if migrate_file(f):
            files_changed.append(str(f))
    for f in SRC.rglob("*.tsx"):
        if "/node_modules/" in str(f):
            continue
        if "/__tests__/" in str(f):
            continue
        if f.name.endswith(".test.tsx"):
            continue
        if migrate_file(f):
            files_changed.append(str(f))
    print(f"Migrated {len(files_changed)} files:")
    for f in files_changed:
        print(f"  {f}")


if __name__ == "__main__":
    main()
