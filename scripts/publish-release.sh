#!/usr/bin/env bash
set -euo pipefail

version="$(sed -n 's/^versionName=//p' version.properties)"
tag="v$version"
apk="android-image-editor-$tag.apk"
repo="$GITHUB_REPOSITORY"
head="$(gh api "repos/$repo/branches/main" --jq .commit.sha)"
if [[ "$head" != "$GITHUB_SHA" ]]; then
  echo "::error::main changed since this workflow started. Dispatch the current main version."
  exit 1
fi

if gh release view "$tag" --repo "$repo" --json isDraft >/dev/null 2>&1; then
  echo "::error::Release $tag already exists. Inspect it before attempting publication."
  exit 1
fi
latest="$(gh release list --repo "$repo" --exclude-drafts --exclude-pre-releases --json tagName,isLatest --jq '.[] | select(.isLatest) | .tagName')"
if [[ -n "$latest" ]]; then
  if [[ ! "$latest" =~ ^v(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]]; then
    echo "::error::Latest release has an unexpected tag."
    exit 1
  fi
  newest=$((BASH_REMATCH[1] * 1000000 + BASH_REMATCH[2] * 1000 + BASH_REMATCH[3]))
  IFS=. read -r major minor patch <<< "$version"
  if (( major * 1000000 + minor * 1000 + patch <= newest )); then
    echo "::error::Release version must be newer than the current stable release."
    exit 1
  fi
fi
# Draft first so clients never discover a published release with a missing asset.
gh release create "$tag" "$apk" --repo "$repo" --target "$GITHUB_SHA" \
  --draft --title "$tag" --notes "Android Image Editor $tag"
gh release download "$tag" --repo "$repo" --pattern "$apk" --output "$RUNNER_TEMP/$apk"
cmp "$apk" "$RUNNER_TEMP/$apk"
# Use the exact tag ref. The commits endpoint can resolve version-like names loosely.
if ! tag_ref="$(gh api "repos/$repo/git/ref/tags/$tag" --jq .object.sha 2>/dev/null)"; then
  gh api --method POST "repos/$repo/git/refs" -f "ref=refs/tags/$tag" -f "sha=$GITHUB_SHA" --silent
  tag_ref="$(gh api "repos/$repo/git/ref/tags/$tag" --jq .object.sha)"
fi
if [[ "$tag_ref" != "$GITHUB_SHA" ]]; then
  echo "::error::The existing tag does not point directly to this build commit."
  exit 1
fi
gh release edit "$tag" --repo "$repo" --verify-tag --draft=false --latest
test "$(gh api "repos/$repo/commits/$tag" --jq .sha)" = "$GITHUB_SHA"
test "$(gh api "repos/$repo/releases/latest" --jq .tag_name)" = "$tag"
rm -f "$apk" "$apk.idsig" "$RUNNER_TEMP/$apk"
