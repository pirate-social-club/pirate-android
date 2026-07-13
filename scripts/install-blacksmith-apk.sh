#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="android-ci.yml"
ARTIFACT_NAME="debug-apk"
PACKAGE_NAME="sc.pirate.mobile.blacksmith"
RUN_ID=""
REF=""
DEVICE_SERIAL=""
WORK_DIR=""
LAUNCH_APP=0
SKIP_PUSH_CHECK=0
UNINSTALL_ON_SIGNATURE_MISMATCH=1

usage() {
  cat <<'USAGE'
Usage:
  scripts/install-blacksmith-apk.sh [--ref <branch-or-sha>] [--launch]
  scripts/install-blacksmith-apk.sh --run-id <github-run-id> [--launch]
  scripts/install-blacksmith-apk.sh --production [--ref <branch-or-sha>] [--launch]

Builds the Android debug APK on Blacksmith, downloads the selected environment's
artifact, installs it on an attached Android device, and verifies the installed
package. The default artifact uses staging APIs; pass --production when testing
production post/community IDs.

Options:
  --ref <ref>             Git ref for Blacksmith. Defaults to current branch.
  --run-id <id>           Install from an existing successful selected-workflow run.
  --production            Use android-compile.yml and its production-API debug APK.
  --device <serial>       adb device serial. Defaults to the single attached device.
  --launch                Launch Pirate Blacksmith after install.
  --work-dir <dir>        Download/unzip directory. Defaults to /tmp/pirate-android-blacksmith-<run-id>.
  --skip-push-check       Do not verify that the selected ref is pushed.
  --keep-existing         Do not uninstall on signature mismatch; fail instead.
  -h, --help              Show this help.

Notes:
  The default android-ci artifact targets https://api-staging.pirate.sc.
  --production targets https://api.pirate.sc and keeps the Blacksmith package ID.
  Blacksmith debug APKs may be signed with a different CI debug key than the
  package already on the phone. By default, this script handles that by
  uninstalling only sc.pirate.mobile.blacksmith and then reinstalling. The release
  package sc.pirate.mobile is not touched.
USAGE
}

log() {
  printf '[blacksmith-install] %s\n' "$*" >&2
}

die() {
  printf '[blacksmith-install] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

current_branch() {
  git -C "$ROOT_DIR" branch --show-current
}

current_head_sha() {
  git -C "$ROOT_DIR" rev-parse HEAD
}

resolve_adb() {
  if [ -n "${ADB:-}" ] && [ -x "${ADB}" ]; then
    printf '%s\n' "$ADB"
    return
  fi
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}/platform-tools/adb"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME}/platform-tools/adb" ]; then
    printf '%s\n' "${ANDROID_HOME}/platform-tools/adb"
    return
  fi
  if [ -x "${HOME}/Android/Sdk/platform-tools/adb" ]; then
    printf '%s\n' "${HOME}/Android/Sdk/platform-tools/adb"
    return
  fi
  command -v adb 2>/dev/null || true
}

check_pushed_ref() {
  local ref="$1"
  local head_sha="$2"

  if [ "$SKIP_PUSH_CHECK" = "1" ] || [ -n "$RUN_ID" ]; then
    return
  fi

  if ! git -C "$ROOT_DIR" ls-remote --exit-code origin "$ref" >/dev/null 2>&1; then
    die "Ref '$ref' is not on origin. Push it first or pass --skip-push-check."
  fi

  local remote_sha
  remote_sha="$(git -C "$ROOT_DIR" ls-remote origin "$ref" | awk '{print $1}' | head -n 1)"
  if [ -n "$remote_sha" ] && [ "$remote_sha" != "$head_sha" ]; then
    die "Origin ref '$ref' is at $remote_sha, but local HEAD is $head_sha. Push before building."
  fi
}

select_device() {
  local adb_bin="$1"
  local requested="$2"

  if [ -n "$requested" ]; then
    "$adb_bin" -s "$requested" get-state >/dev/null 2>&1 || die "ADB device '$requested' is not available."
    printf '%s\n' "$requested"
    return
  fi

  local devices
  devices="$("$adb_bin" devices | awk 'NR > 1 && $2 == "device" {print $1}')"
  local count
  count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"

  if [ "$count" = "0" ]; then
    die "No attached ADB device is authorized. Connect/unlock the phone and enable USB debugging."
  fi
  if [ "$count" != "1" ]; then
    printf '%s\n' "$devices" >&2
    die "Multiple ADB devices found. Pass --device <serial>."
  fi

  printf '%s\n' "$devices"
}

trigger_workflow() {
  local ref="$1"
  local head_sha="$2"
  local started_at
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  log "Triggering $WORKFLOW on ref '$ref'."
  gh workflow run "$WORKFLOW" --ref "$ref" >/tmp/pirate-android-gh-workflow-run.out 2>&1 || {
    cat /tmp/pirate-android-gh-workflow-run.out >&2
    die "Could not trigger $WORKFLOW."
  }

  local run_id=""
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 3
    run_id="$(gh run list \
      --workflow "$WORKFLOW" \
      --branch "$ref" \
      --event workflow_dispatch \
      --limit 10 \
      --json databaseId,headSha,createdAt \
      --jq ".[] | select(.headSha == \"$head_sha\" and .createdAt >= \"$started_at\") | .databaseId" \
      | head -n 1)"
    if [ -n "$run_id" ]; then
      printf '%s\n' "$run_id"
      return
    fi
  done

  die "Triggered workflow but could not find its run id. Check GitHub Actions for $WORKFLOW on $ref."
}

watch_run() {
  local run_id="$1"
  log "Watching Blacksmith run $run_id."
  gh run watch "$run_id" --exit-status
}

artifact_id_for_run() {
  local run_id="$1"
  gh api "repos/{owner}/{repo}/actions/runs/${run_id}/artifacts" \
    --jq ".artifacts[] | select(.name == \"$ARTIFACT_NAME\" and .expired == false) | .id" \
    | head -n 1
}

download_artifact() {
  local run_id="$1"
  local artifact_id="$2"
  local target_dir="$3"
  local repo
  repo="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"

  rm -rf "$target_dir"
  mkdir -p "$target_dir"

  log "Downloading artifact '$ARTIFACT_NAME' from run $run_id."
  if command -v timeout >/dev/null 2>&1; then
    if timeout 180 gh run download "$run_id" -n "$ARTIFACT_NAME" -D "$target_dir"; then
      return
    fi
    log "gh run download did not complete cleanly; falling back to direct artifact download."
  elif gh run download "$run_id" -n "$ARTIFACT_NAME" -D "$target_dir"; then
    return
  else
    log "gh run download failed; falling back to direct artifact download."
  fi

  local zip_path="${target_dir}/${ARTIFACT_NAME}.zip"
  local token
  token="$(gh auth token)"
  curl -fL --retry 3 \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${repo}/actions/artifacts/${artifact_id}/zip" \
    -o "$zip_path"
}

unpack_artifact() {
  local target_dir="$1"
  local zip_path="${target_dir}/${ARTIFACT_NAME}.zip"

  if [ -f "$zip_path" ]; then
    log "Unpacking $zip_path."
    unzip -o "$zip_path" -d "$target_dir" >/dev/null
  fi

  local apk_path
  apk_path="$(find "$target_dir" -name '*.apk' -type f | sort | head -n 1)"
  if [ -z "$apk_path" ]; then
    die "No APK found in $target_dir."
  fi
  printf '%s\n' "$apk_path"
}

install_apk() {
  local adb_bin="$1"
  local serial="$2"
  local apk_path="$3"

  log "Installing $(basename "$apk_path") on device $serial."
  local install_output
  set +e
  install_output="$("$adb_bin" -s "$serial" install -r "$apk_path" 2>&1)"
  local install_status=$?
  set -e

  if [ "$install_status" = "0" ]; then
    printf '%s\n' "$install_output"
    return
  fi

  if printf '%s\n' "$install_output" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' \
    && [ "$UNINSTALL_ON_SIGNATURE_MISMATCH" = "1" ]; then
    log "Existing $PACKAGE_NAME has a different debug signature; uninstalling only that package."
    "$adb_bin" -s "$serial" uninstall "$PACKAGE_NAME" >/dev/null || true
    "$adb_bin" -s "$serial" install "$apk_path"
    return
  fi

  printf '%s\n' "$install_output" >&2
  die "ADB install failed."
}

verify_install() {
  local adb_bin="$1"
  local serial="$2"

  local package_info
  package_info="$("$adb_bin" -s "$serial" shell dumpsys package "$PACKAGE_NAME")"
  printf '%s\n' "$package_info" | grep -q "Package \[$PACKAGE_NAME\]" || die "$PACKAGE_NAME is not installed."

  local version_name version_code update_time
  version_name="$(printf '%s\n' "$package_info" | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"
  version_code="$(printf '%s\n' "$package_info" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1 | tr -d '\r')"
  update_time="$(printf '%s\n' "$package_info" | sed -n 's/.*lastUpdateTime=//p' | head -n 1 | tr -d '\r')"

  log "Installed $PACKAGE_NAME versionCode=${version_code:-unknown} versionName=${version_name:-unknown}."
  if [ -n "$update_time" ]; then
    log "Device lastUpdateTime=$update_time."
  fi
}

launch_app() {
  local adb_bin="$1"
  local serial="$2"
  log "Launching Pirate Blacksmith."
  "$adb_bin" -s "$serial" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --ref)
      REF="${2:-}"
      [ -n "$REF" ] || die "--ref requires a value."
      shift 2
      ;;
    --run-id)
      RUN_ID="${2:-}"
      [ -n "$RUN_ID" ] || die "--run-id requires a value."
      shift 2
      ;;
    --production)
      WORKFLOW="android-compile.yml"
      ARTIFACT_NAME="production-debug-apk"
      shift
      ;;
    --device)
      DEVICE_SERIAL="${2:-}"
      [ -n "$DEVICE_SERIAL" ] || die "--device requires a value."
      shift 2
      ;;
    --work-dir)
      WORK_DIR="${2:-}"
      [ -n "$WORK_DIR" ] || die "--work-dir requires a value."
      shift 2
      ;;
    --launch)
      LAUNCH_APP=1
      shift
      ;;
    --skip-push-check)
      SKIP_PUSH_CHECK=1
      shift
      ;;
    --keep-existing)
      UNINSTALL_ON_SIGNATURE_MISMATCH=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

cd "$ROOT_DIR"
require_cmd git
require_cmd gh
require_cmd curl
require_cmd unzip
require_cmd find

ADB_BIN="$(resolve_adb)"
[ -n "$ADB_BIN" ] || die "adb was not found. Set ADB or ANDROID_SDK_ROOT."

if [ -z "$RUN_ID" ]; then
  REF="${REF:-$(current_branch)}"
  [ -n "$REF" ] || die "Could not resolve current branch. Pass --ref <branch-or-sha>."
  HEAD_SHA="$(current_head_sha)"
  check_pushed_ref "$REF" "$HEAD_SHA"
  RUN_ID="$(trigger_workflow "$REF" "$HEAD_SHA")"
  watch_run "$RUN_ID"
else
  log "Using existing Blacksmith run $RUN_ID."
  conclusion="$(gh run view "$RUN_ID" --json conclusion,status --jq '.conclusion // .status')"
  [ "$conclusion" = "success" ] || die "Run $RUN_ID is not successful; current state: $conclusion."
fi

ARTIFACT_ID="$(artifact_id_for_run "$RUN_ID")"
[ -n "$ARTIFACT_ID" ] || die "No non-expired '$ARTIFACT_NAME' artifact found for run $RUN_ID."

WORK_DIR="${WORK_DIR:-/tmp/pirate-android-blacksmith-${RUN_ID}}"
DEVICE_SERIAL="$(select_device "$ADB_BIN" "$DEVICE_SERIAL")"

download_artifact "$RUN_ID" "$ARTIFACT_ID" "$WORK_DIR"
APK_PATH="$(unpack_artifact "$WORK_DIR")"
install_apk "$ADB_BIN" "$DEVICE_SERIAL" "$APK_PATH"
verify_install "$ADB_BIN" "$DEVICE_SERIAL"

if [ "$LAUNCH_APP" = "1" ]; then
  launch_app "$ADB_BIN" "$DEVICE_SERIAL"
fi

log "Done. APK: $APK_PATH"
