"""PreToolUse hook: edits to workflow rule files need the user's explicit approval."""
import json
import re
import sys

PROTECTED = re.compile(r"(^|/)(CLAUDE\.md|\.claude/(agents|rules)/[^/]+)$")

path = json.load(sys.stdin).get("tool_input", {}).get("file_path", "").replace("\\", "/")
if PROTECTED.search(path):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "ask",
        "permissionDecisionReason": "Rule/agent file: show the exact wording in chat and get an explicit OK first.",
    }}))
