#!/usr/bin/env python3
"""
Migrate all *.test.ts(x) files that still use mockBootbox / window.bootbox
to the new mockModal pattern (vi.mock('../utils/modal', ...) + mockAlert
assertions).

Steps per file:
  1. Replace import of setupMockBootbox/teardownMockBootbox with imports
     from mockModal, plus a vi.mock call at top.
  2. Replace `setupMockBootbox()` with `clearModalMockState()`.
  3. Replace `teardownMockBootbox()` with comment.
  4. Replace `(window as any).bootbox.alert` assertions with `mockAlert`.
  5. Replace `mockBootbox.alert` / `mockBootbox.prompt` patterns.
  6. Replace `mockBootbox.getLastAlertMessage()` with `getLastAlertMessage()`.
  7. Replace `mockBootbox.confirmLast(accept)` with `confirmLast(accept)`.
  8. Replace `window.bootbox.prompt = vi.fn(...)` with
     `mockPrompt.mockImplementation(...)`.
"""
import re
from pathlib import Path

SRC = Path("/home/fredgu/git_home/ruleforge/console-ui/src")
TEST_FILES = [
    "action/__tests__/action.test.ts",
    "action/__tests__/reducer.test.ts",
    "api/client.test.ts",
    "constant/action.test.ts",
    "frame/action.test.ts",
    "login/LoginPage.test.tsx",
    "package/__tests__/action.test.ts",
    "package/__tests__/reducer.test.ts",
    "parameter/__tests__/action.test.ts",
    "permission/action.test.ts",
    "resource/action.test.ts",
    "Utils.test.ts",
    "variable/action.test.ts",
    "variable/reducer.test.ts",
]


def get_rel_modal_path(test_file: Path) -> str:
    """Compute relative path from test file to src/utils/modal"""
    test_dir = test_file.parent
    rel = Path("../utils/modal")
    while test_dir != SRC:
        rel = Path("..") / rel
        test_dir = test_dir.parent
    return str(rel)


def migrate(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "mockBootbox" not in text and "window.bootbox" not in text:
        return False
    if "bootbox" not in text and "mockBootbox" not in text:
        return False
    original = text

    rel_path = get_rel_modal_path(path)

    # 1. Replace import line for setupMockBootbox/teardownMockBootbox
    text = re.sub(
        r"import\s*\{([^}]*?(?:setupMockBootbox|teardownMockBootbox)[^}]*?)\}\s*from\s*['\"][^'\"]*mockBootbox(?:\.js)?['\"];?",
        lambda m: "",  # we'll re-emit
        text,
    )

    # Build the new mockModal import
    imports = [
        "import {",
        "    mockAlert,",
        "    mockConfirm,",
        "    mockPrompt,",
        "    mockDialog,",
        "    clearModalMockState,",
        "    getLastAlertMessage,",
        "    getLastConfirm,",
        "    confirmLast,",
        f"}} from '{path.parent.relative_to(SRC).parent if path.parent.name == '__tests__' else path.parent.relative_to(SRC.parent)}/__test_utils__/mockModal';",
    ]
    # Reformat to a single import block (some files use named imports; we just write the standard form)
    rel_dir = path.parent
    if rel_dir.name == "__tests__":
        modal_helper = "../" * 2 + "__test_utils__/mockModal"
    else:
        modal_helper = "../__test_utils__/mockModal"

    modal_import = (
        f"import {{\n"
        f"    mockAlert,\n"
        f"    mockConfirm,\n"
        f"    mockPrompt,\n"
        f"    mockDialog,\n"
        f"    clearModalMockState,\n"
        f"    getLastAlertMessage,\n"
        f"    getLastConfirm,\n"
        f"    confirmLast,\n"
        f"}} from '{modal_helper}';\n\n"
    )
    vi_mock = (
        f"vi.mock('{rel_path}', () => ({{\n"
        f"    alert: mockAlert,\n"
        f"    confirm: mockConfirm,\n"
        f"    prompt: mockPrompt,\n"
        f"    dialog: mockDialog,\n"
        f"}}));\n\n"
    )

    # Insert the new imports + vi.mock at the top, after the vitest import
    text = re.sub(
        r"(import\s*\{[^}]*\}\s*from\s*['\"]vitest['\"];?)\n",
        r"\1\n" + modal_import + vi_mock,
        text,
        count=1,
    )

    # 2. Replace setupMockBootbox() calls with clearModalMockState()
    text = re.sub(
        r"setupMockBootbox\(\)",
        "clearModalMockState()",
        text,
    )

    # 3. Replace teardownMockBootbox() with empty
    text = re.sub(
        r"\s*teardownMockBootbox\(\);",
        "",
        text,
    )

    # 4. Replace (window as any).bootbox.alert with mockAlert (and remove the type cast)
    text = re.sub(
        r"\(window as any\)\.bootbox\.alert",
        "mockAlert",
        text,
    )
    text = re.sub(
        r"window\.bootbox\.alert",
        "mockAlert",
        text,
    )

    # 5. Replace window.bootbox.prompt assignments
    text = re.sub(
        r"window\.bootbox\.prompt\s*=\s*vi\.fn\(\(([^)]*?)\)\s*=>\s*\{",
        r"mockPrompt.mockImplementation((\1) => {",
        text,
    )
    text = re.sub(
        r"window\.bootbox\.confirm\s*=\s*vi\.fn",
        r"mockConfirm.mockImplementation",
        text,
    )

    # 6. Replace remaining window.bootbox.{X} references
    text = re.sub(
        r"window\.bootbox\.prompt",
        "mockPrompt",
        text,
    )
    text = re.sub(
        r"window\.bootbox\.confirm",
        "mockConfirm",
        text,
    )
    text = re.sub(
        r"window\.bootbox\.dialog",
        "mockDialog",
        text,
    )

    # 7. Replace mockBootbox.getLastAlertMessage()
    text = re.sub(
        r"mockBootbox\.getLastAlertMessage\(\)",
        "getLastAlertMessage()",
        text,
    )
    text = re.sub(
        r"mockBootbox\.getLastConfirm\(\)",
        "getLastConfirm()",
        text,
    )
    text = re.sub(
        r"mockBootbox\.confirmLast\(([^)]*)\)",
        r"confirmLast(\1)",
        text,
    )

    # 8. Remove 'let mockBootbox: any;' / 'let mockBootbox: ReturnType<...>;' declarations
    text = re.sub(
        r"^\s*let\s+mockBootbox:.*?;\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main():
    changed = []
    for tf in TEST_FILES:
        p = SRC / tf
        if not p.exists():
            print(f"  SKIP missing: {tf}")
            continue
        if migrate(p):
            changed.append(tf)
            print(f"  OK: {tf}")
        else:
            print(f"  no-op: {tf}")
    print(f"\n{len(changed)} files migrated.")


if __name__ == "__main__":
    main()
