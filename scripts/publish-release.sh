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

# A manual dispatch can overlap the automatic release-triggered workflow.
# Serialize via the release-job concurrency group; a repeated run should be
# idempotent when the existing stable release is exactly this build.
if gh release view "$tag" --repo "$repo" --json isDraft >/dev/null 2>&1; then
  existing_target="$(gh api "repos/$repo/git/ref/tags/$tag" --jq .object.sha 2>/dev/null || true)"
  existing_asset="$(gh release view "$tag" --repo "$repo" --json assets --jq '.assets[].name' | grep -Fx "$apk" || true)"
  is_draft="$(gh release view "$tag" --repo "$repo" --json isDraft --jq .isDraft)"
  if [[ "$existing_target" == "$GITHUB_SHA" && "$existing_asset" == "$apk" && "$is_draft" == "false" ]]; then
    echo "Release $tag is already published for this exact commit and APK. Nothing to do."
    exit 0
  fi
  echo "::error::Release $tag exists but is not a verified stable release for this commit. Refusing to overwrite it."
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
# Draft releases created by gh can have an "untagged-..." URL, and
# gh release download/edit by tag may fail with HTTP 404 before publishing.
# Create and verify the exact tag before creating the release.
if ! tag_ref="$(gh api "repos/$repo/git/ref/tags/$tag" --jq .object.sha 2>/dev/null)"; then
  gh api --method POST "repos/$repo/git/refs" -f "ref=refs/tags/$tag" -f "sha=$GITHUB_SHA" --silent
  tag_ref="$(gh api "repos/$repo/git/ref/tags/$tag" --jq .object.sha)"
fi
if [[ "$tag_ref" != "$GITHUB_SHA" ]]; then
  echo "::error::The existing tag does not point directly to this build commit."
  exit 1
fi

# The draft created by a previous failed attempt may still be present with a
# different target SHA. Delete only that unpublished draft for this version,
# and never modify a published release.
draft_id="$(gh api "repos/$repo/releases?per_page=100" --jq ".[] | select(.tag_name == \"$tag\") | .id" | head -n 1)"
if [[ -n "$draft_id" ]]; then
  draft_target="$(gh api "repos/$repo/releases/$draft_id" --jq .target_commitish)"
  draft_state="$(gh api "repos/$repo/releases/$draft_id" --jq .draft)"
  if [[ "$draft_state" != "true" ]]; then
    echo "::error::A published release for $tag already exists. Refusing to modify."
    exit 1
  fi
  if [[ "$draft_target" != "$GITHUB_SHA" ]]; then
    echo "Removing unpublished stale draft for $tag from prior failed build"
    gh api --method DELETE "repos/$repo/releases/$draft_id" --silent
    draft_id=""
  fi
fi

if [[ -z "$draft_id" ]]; then
  gh release create "$tag" "$apk" --repo "$repo" --target "$GITHUB_SHA" \
    --verify-tag --draft --title "$tag" --notes "Android Image Editor $tag"
  draft_id="$(gh api "repos/$repo/releases?per_page=100" --jq ".[] | select(.tag_name == \"$tag\") | .id" | head -n 1)"
fi
if [[ -z "$draft_id" ]]; then
  echo "::error::Could not locate draft release $tag by API"
  exit 1
fi
draft_state="$(gh api "repos/$repo/releases/$draft_id" --jq .draft)"
if [[ "$draft_state" != "true" ]]; then
  echo "::error::Expected unpublished draft before verifying APK"
  exit 1
fi

# The numeric asset ID stays available even if the draft has an untagged URL.
asset_id="$(gh api "repos/$repo/releases/$draft_id/assets?per_page=100" --jq ".[] | select(.name == \"$apk\") | .id" | head -n 1)"
if [[ -z "$asset_id" ]]; then
  echo "::error::Draft release is missing APK asset $apk"
  exit 1
fi
gh api -H "Accept: application/octet-stream" \
  "repos/$repo/releases/assets/$asset_id" > "$RUNNER_TEMP/$apk"
cmp "$apk" "$RUNNER_TEMP/$apk"

# Publish by release ID, not by the temporary draft URL or tag lookup.
gh api --method PATCH "repos/$repo/releases/$draft_id" \
  -F draft=false -F make_latest=true --silent
test "$(gh api "repos/$repo/commits/$tag" --jq .sha)" = "$GITHUB_SHA"
test "$(gh api "repos/$repo/releases/latest" --jq .tag_name)" = "$tag"
rm -f "$apk" "$apk.idsig" "$RUNNER_TEMP/$apk"
