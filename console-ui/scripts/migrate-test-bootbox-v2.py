#!/usr/bin/env python3
"""
Carefully migrate test files from mockBootbox to vi.hoisted modal mock pattern.

For each test file with bootbox references:
  1. Replace `import { setupMockBootbox, teardownMockBootbox } from '...';` with NOTHING.
  2. Add a vi.hoisted block + vi.mock for utils/modal AFTER the vitest import.
  3. Replace `setupMockBootbox()` with `clearModalMockState()`.
  4. Remove `teardownMockBootbox();` calls (or replace with empty).
  5. Replace `let X: ..., mockBootbox: ...;` (composite declarations) by removing
     the mockBootbox part.
  6. Replace `mockBootbox = ...;` with nothing.
  7. Replace `(window as any).bootbox.alert` with `mocks.alert`.
  8. Replace `(window as any).bootbox.prompt` with `mocks.prompt`.
  9. Replace `(window as any).bootbox.confirm` with `mocks.confirm`.
  10. Replace `(window as any).bootbox.dialog` with `mocks.dialog`.
  11. Replace `window.bootbox.prompt = vi.fn((m, cb) => {...})` with
      `mocks.prompt.mockImplementation((m, cb) => {...})`.
  12. Replace `mockBootbox.getLastAlertMessage()` with `getLastAlertMessage()`.
  13. Replace `mockBootbox.confirmLast(...)` with `confirmLast(...)`.
  14. Replace `mockBootbox.getLastConfirm()` with `getLastConfirm()`.
  15. Replace `const bootboxAlert = vi.fn();` with `const bootboxAlert = mocks.alert;`.
  16. Replace `(window as any).bootbox = { alert: bootboxAlert };` with a comment.
"""
import re
from pathlib import Path

ROOT = Path("/home/fredgu/git_home/ruleforge/console-ui")

TEST_FILES = [
    "src/Utils.test.ts",
    "src/action/__tests__/action.test.ts",
    "src/action/__tests__/reducer.test.ts",
    "src/api/client.test.ts",
    "src/constant/action.test.ts",
    "src/frame/action.test.ts",
    "src/login/LoginPage.test.tsx",
    "src/package/__tests__/action.test.ts",
    "src/package/__tests__/reducer.test.ts",
    "src/parameter/__tests__/action.test.ts",
    "src/permission/action.test.ts",
    "src/resource/action.test.ts",
    "src/variable/action.test.ts",
    "src/variable/reducer.test.ts",
]


def get_modal_path(test_file: Path) -> str:
    """Relative path from test file to src/utils/modal"""
    test_dir = test_file.parent
    rel = ""
    while test_dir != ROOT / "src":
        rel = "../" + rel
        test_dir = test_dir.parent
    return rel + "utils/modal"


HOISTED_BLOCK = """const { mocks, clearModalMockState, getLastAlertMessage, getLastConfirm, confirmLast } = vi.hoisted(() => {{
    const alerts: {{ message: unknown; cb?: () => void }}[] = [];
    const confirms: {{ message: string; callback: (ok: boolean) => void }}[] = [];
    const alert = vi.fn((message: unknown, cb?: () => void) => {{
        alerts.push({{ message, cb }});
        if (typeof cb === 'function') cb();
    }});
    const confirm = vi.fn((message: string, callback: (ok: boolean) => void) => {{
        confirms.push({{ message, callback }});
    }});
    const prompt = vi.fn();
    const dialog = vi.fn();
    return {{
        mocks: {{ alert, confirm, prompt, dialog }},
        clearModalMockState: () => {{
            alerts.length = 0;
            confirms.length = 0;
            alert.mockReset();
            confirm.mockReset();
            prompt.mockReset();
            dialog.mockReset();
        }},
        getLastAlertMessage: () => {{
            const last = alerts[alerts.length - 1];
            if (!last) return null;
            return typeof last.message === 'string' ? last.message : String(last.message);
        }},
        getLastConfirm: () => confirms[confirms.length - 1] ?? null,
        confirmLast: (accept = true) => {{
            const last = confirms[confirms.length - 1];
            if (last) last.callback(accept);
        }},
    }};
}});
"""


def migrate(p: Path) -> bool:
    text = p.read_text(encoding="utf-8")
    if "mockBootbox" not in text and "bootbox" not in text:
        return False
    original = text

    # 1. Remove the import block for setupMockBootbox/teardownMockBootbox
    #    (match the entire multi-line import statement)
    text = re.sub(
        r"import\s*\{\s*setupMockBootbox\s*,\s*teardownMockBootbox\s*\}\s*from\s*['\"][^'\"]*mockBootbox(?:\.js)?['\"];?\s*\n",
        "",
        text,
    )

    # Remove `vi.mock('../bootbox.js', () => ({}))` lines (no longer needed)
    text = re.sub(
        r"^[ \t]*vi\.mock\(['\"][^'\"]*bootbox(?:\.js)?['\"],\s*\(\)\s*=>\s*\(\{[^}]*\}\)\);?\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )

    # 5. Remove `mockBootbox: any` or `mockBootbox: ReturnType<...>` from composite type annotations
    text = re.sub(r",\s*mockBootbox:\s*[^,)]*", "", text)
    text = re.sub(r"mockBootbox:\s*ReturnType<typeof\s+setupMockBootbox>,\s*", "", text)
    text = re.sub(r"\bmockBootbox:\s*any\b\s*,?\s*", "", text)

    # 6. Remove `mockBootbox = setupMockBootbox();` (standalone)
    text = re.sub(r"^\s*mockBootbox\s*=\s*[^;]+;\s*\n", "", text, flags=re.MULTILINE)

    # 3. setupMockBootbox() → clearModalMockState()
    text = re.sub(r"\bsetupMockBootbox\(\)", "clearModalMockState()", text)

    # 4. teardownMockBootbox() → empty
    text = re.sub(r"\s*teardownMockBootbox\(\);?", "", text)

    # 7-10. window.bootbox.X → mocks.X
    text = re.sub(r"\(window as any\)\.bootbox\.alert", "mocks.alert", text)
    text = re.sub(r"\(window as any\)\.bootbox\.confirm", "mocks.confirm", text)
    text = re.sub(r"\(window as any\)\.bootbox\.prompt", "mocks.prompt", text)
    text = re.sub(r"\(window as any\)\.bootbox\.dialog", "mocks.dialog", text)

    # 11. window.bootbox.prompt = vi.fn((...) => { ... })
    # Convert to mocks.prompt.mockImplementation((...) => { ... })
    text = re.sub(
        r"window\.bootbox\.prompt\s*=\s*vi\.fn\(\s*\(([^)]*?)\)\s*=>\s*\{",
        r"mocks.prompt.mockImplementation(\1 => {",
        text,
    )
    text = re.sub(
        r"window\.bootbox\.confirm\s*=\s*vi\.fn\(\s*\(([^)]*?)\)\s*=>\s*\{",
        r"mocks.confirm.mockImplementation(\1 => {",
        text,
    )

    # 12-14. mockBootbox.getLastAlertMessage() etc. → bare helpers
    text = re.sub(r"mockBootbox\.getLastAlertMessage\(\)", "getLastAlertMessage()", text)
    text = re.sub(r"mockBootbox\.getLastConfirm\(\)", "getLastConfirm()", text)
    text = re.sub(r"mockBootbox\.confirmLast\(([^)]*)\)", r"confirmLast(\1)", text)

    # 15. const bootboxAlert = vi.fn();
    text = re.sub(r"const\s+bootboxAlert\s*=\s*vi\.fn\(\);", r"const bootboxAlert = mocks.alert;", text)

    # 16. (window as any).bootbox = { alert: bootboxAlert };
    text = re.sub(
        r"\(window as any\)\.bootbox\s*=\s*\{\s*alert:\s*bootboxAlert\s*\};",
        "// window.bootbox mock removed — utils/modal is mocked via vi.mock",
        text,
    )
    text = re.sub(
        r"delete\s+\(window as any\)\.bootbox;",
        "// window.bootbox cleanup removed",
        text,
    )

    # 17. Remove `// setupMockBootbox ...` comments
    text = re.sub(
        r"//\s*setupMockBootbox[^\n]*\n",
        "",
        text,
    )

    # 2. Add the hoisted block + vi.mock at the top (right after vitest import)
    if "const { mocks, clearModalMockState" not in text:
        modal = get_modal_path(p)
        block = HOISTED_BLOCK.replace("{{", "{").replace("}}", "}") + f"vi.mock('{modal}', () => mocks);\n\n"
        # Insert right after the vitest import statement
        text = re.sub(
            r"(import\s*\{[^}]*\}\s*from\s*['\"]vitest['\"];?\s*\n)",
            r"\1" + block,
            text,
            count=1,
        )

    if text != original:
        p.write_text(text, encoding="utf-8")
        return True
    return False


def main():
    n = 0
    for f in TEST_FILES:
        p = ROOT / f
        if not p.exists():
            print(f"  SKIP missing: {f}")
            continue
        if migrate(p):
            print(f"  OK: {f}")
            n += 1
        else:
            print(f"  no-op: {f}")
    print(f"\n{n} files migrated.")


if __name__ == "__main__":
    main()
