#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: scripts/release.sh <version> [changelog text]"
  echo "Example: scripts/release.sh 1.1.0 \"Add presets and expanded effects\""
  exit 1
fi

VERSION="$1"
CHANGELOG_TEXT="${2:-Release $VERSION}"
TAG="v$VERSION"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes before releasing."
  exit 1
fi

CURRENT_CODE="$(grep '^VERSION_CODE=' gradle.properties | cut -d '=' -f 2)"
NEXT_CODE="$((CURRENT_CODE + 1))"

if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties
  sed -i '' "s/^VERSION_CODE=.*/VERSION_CODE=$NEXT_CODE/" gradle.properties
else
  sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties
  sed -i "s/^VERSION_CODE=.*/VERSION_CODE=$NEXT_CODE/" gradle.properties
fi

TMP_CHANGELOG="$(mktemp)"
{
  echo "# Changelog"
  echo
  echo "All notable changes to ReverbRoom are documented here."
  echo
  echo "## $VERSION - $(date +%Y-%m-%d)"
  echo
  echo "- $CHANGELOG_TEXT"
  echo
  tail -n +4 CHANGELOG.md
} > "$TMP_CHANGELOG"
mv "$TMP_CHANGELOG" CHANGELOG.md

./gradlew :app:testDebugUnitTest :app:assembleDebug

git add gradle.properties CHANGELOG.md
git commit -m "Release $TAG"
git tag "$TAG"

if command -v gh >/dev/null 2>&1; then
  gh release create "$TAG" app/build/outputs/apk/debug/app-debug.apk \
    --title "ReverbRoom $VERSION" \
    --notes "$CHANGELOG_TEXT"
else
  BRANCH="$(git branch --show-current)"
  echo "Git tag $TAG created. Push it to trigger GitHub Actions:"
  echo "  git push origin $BRANCH --tags"
fi
