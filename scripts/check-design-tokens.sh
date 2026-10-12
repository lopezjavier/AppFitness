#!/usr/bin/env bash
# Design tokens guard (T-020): hardcoded colors, dp/sp/em values, fonts, font weights and corner
# radii may only live in designsystem/ (planning/05-design-system.md). Everything else uses the
# AppTheme / Spacing / AppRadius / AppShapes tokens. detekt's MagicNumber ignores 0, 1 and 2, so
# this also catches values such as 1.dp.
#
# Scanned: production Kotlin in composeApp/src/*Main/ and androidApp/src/<sourceSet>/kotlin/.
# Not scanned: test source sets (androidApp/src/test*/, androidTest*/, composeApp/src/*Test/) and
# designsystem/. Exit 1 and print the offending lines on any match.
set -euo pipefail

cd "$(dirname "$0")/.."

pattern='[0-9]+(\.[0-9]+)?f?\)?\.(dp|sp|em)\b'
pattern+='|Color\(0x|Color\([0-9]|Color\((red|alpha) *='
pattern+='|\bColor\.(White|Black|Red|Green|Blue|Gray|DarkGray|LightGray|Yellow|Cyan|Magenta)\b'
pattern+='|\bDp\(|TextUnit\('
pattern+='|\bFontFamily\.(Default|SansSerif|Serif|Monospace|Cursive)\b'
pattern+='|\bFontWeight\.'
pattern+='|\bRoundedCornerShape\('

files=()
while IFS= read -r file; do
  files+=("$file")
done < <(
  find composeApp/src androidApp/src -type f -name '*.kt' 2>/dev/null \
    | grep -E '^(composeApp/src/[^/]+Main/|androidApp/src/[^/]+/kotlin/)' \
    | grep -Ev '^androidApp/src/(test|androidTest)[^/]*/' \
    | grep -v '/designsystem/' \
    | sort || true
)

if [ "${#files[@]}" -eq 0 ]; then
  echo "Design tokens guard: no files to scan."
  exit 0
fi

if violations=$(grep -nE "$pattern" "${files[@]}"); then
  echo "Design tokens guard FAILED: hardcoded design values outside designsystem/." >&2
  echo "Use the AppTheme, Spacing, AppRadius or AppShapes tokens instead:" >&2
  echo "$violations" >&2
  exit 1
fi

echo "Design tokens guard passed (${#files[@]} files scanned)."
