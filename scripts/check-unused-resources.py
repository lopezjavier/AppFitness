"""Fail when a Compose resource (string, plurals, string-array, drawable, font, file) is never used.

Usage: python -I scripts/check-unused-resources.py [repo_root]

Resources: ids in `composeResources/values/*.xml`, basenames of `drawable*` and `font` files and the
file names under `files/` (except `files/exercises/images/` and `files/licenses/`).
A use is a word-boundary hit in any `*.kt` under `shared/src`, `composeApp/src` or `androidApp/src`, or in
`scripts/ux-inline-text-allowlist.txt` (a `*` wildcard there counts as a use of every id it matches).
Dynamic lookups (`allStringResources`, `Res.getString`, `StringResource("`) also fail: they hide uses.
"""

import fnmatch
import os
import re
import sys

SOURCE_ROOTS = ("shared/src", "composeApp/src", "androidApp/src")
DYNAMIC = ("allStringResources", "Res.getString", 'StringResource("')
SKIPPED_FILE_DIRS = ("files/exercises/images/", "files/licenses/")
VALUE_TAG = re.compile(r"<(?:string|plurals|string-array)\s+[^>]*?name=\"([^\"]+)\"")


def walk(base):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in ("build", ".gradle")]
        for name in filenames:
            yield os.path.join(dirpath, name)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def resource_ids(root):
    """Return {id: description} for every declared resource."""
    ids = {}
    for source in SOURCE_ROOTS:
        for path in walk(os.path.join(root, source)):
            rel = path.replace(os.sep, "/")
            marker = "/composeResources/"
            if marker not in rel:
                continue
            inner = rel.split(marker, 1)[1]
            kind, _, rest = inner.partition("/")
            if kind == "values" and rest.endswith(".xml"):
                for name in VALUE_TAG.findall(read(path)):
                    ids[name] = rel
            elif kind.startswith("drawable") or kind == "font":
                ids[os.path.splitext(os.path.basename(rel))[0]] = rel
            elif kind == "files" and not any(("files/" + rest).startswith(s) for s in SKIPPED_FILE_DIRS):
                ids[os.path.basename(rel)] = rel
    return ids


def allowlist_patterns(root):
    path = os.path.join(root, "scripts", "ux-inline-text-allowlist.txt")
    if not os.path.isfile(path):
        return []
    patterns = []
    for line in read(path).splitlines():
        entry = line.split("#", 1)[0].strip()
        if entry:
            patterns.append(entry.split()[0])
    return patterns


def kotlin_text(root):
    chunks = []
    for source in SOURCE_ROOTS:
        for path in walk(os.path.join(root, source)):
            if path.endswith(".kt"):
                chunks.append((path, read(path)))
    return chunks


def check(root):
    """Return (unused ids, dynamic-use findings)."""
    ids = resource_ids(root)
    sources = kotlin_text(root)
    haystack = "\n".join(text for _, text in sources)
    patterns = allowlist_patterns(root)
    unused = []
    words = set(re.findall(r"[A-Za-z0-9_]+", haystack))
    for name in sorted(ids):
        if name in words or ("." in name and name in haystack):
            continue
        if any(fnmatch.fnmatchcase(name, pattern) for pattern in patterns):
            continue
        unused.append(name)
    dynamic = [f"{os.path.relpath(p, root)}: {token}" for p, text in sources for token in DYNAMIC if token in text]
    return unused, dynamic


def main(argv):
    root = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    unused, dynamic = check(os.path.abspath(root))
    for name in unused:
        print(f"unused resource: {name}")
    for finding in dynamic:
        print(f"dynamic resource lookup (hides uses): {finding}")
    if unused or dynamic:
        return 1
    print("check-unused-resources: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
