#!/usr/bin/env bash
#
# Pirate Android Screenshot Tool
# Automated Play Store screenshot capture for phone and tablet
#
# Based on: docs/android-tablet-screenshots.md
# This automates the manual adb tap approach with repeatable coordinates

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Default paths
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/t42/Android/Sdk}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-/home/t42/.android/avd}"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_DIR}/fastlane/metadata/android/en-US/images}"
APK_PATH="${APK_PATH:-${PROJECT_DIR}/app/build/outputs/apk/debug/app-debug.apk}"

# Device types
DEVICE_TYPE="${DEVICE_TYPE:-tablet}"  # 'tablet' or 'phone'
AVD_NAME="${AVD_NAME:-}"

# Screenshot configuration
SCREENSHOT_DELAY="${SCREENSHOT_DELAY:-2}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
  cat << 'EOF'
Pirate Android Screenshot Tool

Usage: ./capture-screenshots.sh [OPTIONS]

Options:
  -t, --type TYPE        Device type: 'phone' or 'tablet' (default: tablet)
  -a, --avd NAME         AVD name to use (auto-detected if not specified)
  -o, --output DIR       Output directory for screenshots
  -d, --delay SECONDS    Delay between screenshots (default: 2)
  -k, --keep-emulator    Keep emulator running after capture
  -b, --build            Build debug APK before capturing
  -h, --help             Show this help message

Examples:
  # Capture tablet screenshots
  ./capture-screenshots.sh --type tablet

  # Capture phone screenshots with custom AVD
  ./capture-screenshots.sh --type phone --avd Pixel_6_API_35

  # Build and capture
  ./capture-screenshots.sh --build --type tablet

Environment Variables:
  ANDROID_SDK_ROOT    Path to Android SDK (default: /home/t42/Android/Sdk)
  ANDROID_AVD_HOME    Path to AVD storage (default: /home/t42/.android/avd)
  APK_PATH            Path to APK file (default: auto-detected)

EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      -t|--type)
        DEVICE_TYPE="$2"
        shift 2
        ;;
      -a|--avd)
        AVD_NAME="$2"
        shift 2
        ;;
      -o|--output)
        OUTPUT_DIR="$2"
        shift 2
        ;;
      -d|--delay)
        SCREENSHOT_DELAY="$2"
        shift 2
        ;;
      -k|--keep-emulator)
        KEEP_EMULATOR=1
        shift
        ;;
      -b|--build)
        BUILD_APK=1
        shift
        ;;
      -h|--help)
        show_help
        exit 0
        ;;
      *)
        log_error "Unknown option: $1"
        show_help
        exit 1
        ;;
    esac
  done
}

# Validate environment
check_prerequisites() {
  log_info "Checking prerequisites..."

  if [[ ! -d "$ANDROID_SDK_ROOT" ]]; then
    log_error "Android SDK not found at: $ANDROID_SDK_ROOT"
    log_error "Set ANDROID_SDK_ROOT environment variable"
    exit 1
  fi

  if [[ ! -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
    log_error "adb not found in: $ANDROID_SDK_ROOT/platform-tools/"
    exit 1
  fi

  if [[ ! -x "$ANDROID_SDK_ROOT/emulator/emulator" ]]; then
    log_error "emulator not found in: $ANDROID_SDK_ROOT/emulator/"
    exit 1
  fi

  # Check KVM access for performance
  if [[ ! -r /dev/kvm ]]; then
    log_warn "KVM not accessible. Emulator will be slow."
    log_warn "Run: sudo usermod -aG kvm \$USER && newgrp kvm"
  fi

  log_success "Prerequisites check passed"
}

# Determine AVD name based on device type
get_avd_name() {
  if [[ -n "$AVD_NAME" ]]; then
    echo "$AVD_NAME"
    return
  fi

  case "$DEVICE_TYPE" in
    tablet)
      echo "Pixel_Tablet_API_35"
      ;;
    phone)
      echo "Pixel_6_API_35"
      ;;
    *)
      log_error "Unknown device type: $DEVICE_TYPE"
      exit 1
      ;;
  esac
}

# Create AVD if it doesn't exist
ensure_avd_exists() {
  local avd_name=$1
  local avd_path="$ANDROID_AVD_HOME/${avd_name}.avd"

  if [[ -d "$avd_path" ]]; then
    log_info "AVD already exists: $avd_name"
    return
  fi

  log_info "Creating AVD: $avd_name"

  local system_image="system-images;android-35;google_apis;x86_64"
  local device_profile

  case "$DEVICE_TYPE" in
    tablet)
      device_profile="pixel_tablet"
      ;;
    phone)
      device_profile="pixel_6"
      ;;
  esac

  # Install system image if needed
  if [[ ! -d "$ANDROID_SDK_ROOT/system-images/android-35/google_apis/x86_64" ]]; then
    log_info "Installing system image..."
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
      --sdk_root="$ANDROID_SDK_ROOT" \
      "$system_image"
  fi

  # Create AVD
  printf 'no\n' | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \
    -n "$avd_name" \
    -k "$system_image" \
    -d "$device_profile" \
    2>/dev/null || true

  log_success "AVD created: $avd_name"
}

# Start emulator
start_emulator() {
  local avd_name=$1

  log_info "Starting emulator: $avd_name"

  # Check if emulator is already running
  local running_devices
  running_devices=$($ANDROID_SDK_ROOT/platform-tools/adb devices -l | grep "emulator-" | awk '{print $1}' || true)

  if [[ -n "$running_devices" ]]; then
    log_info "Emulator already running: $running_devices"
    EMULATOR_DEVICE="$running_devices"
    return
  fi

  # Start emulator headless
  "$ANDROID_SDK_ROOT/emulator/emulator" \
    -avd "$avd_name" \
    -cores 2 \
    -memory 2048 \
    -no-snapshot-load \
    -no-snapshot-save \
    -noaudio \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -no-window \
    -netfast &

  EMULATOR_PID=$!
  log_info "Emulator started with PID: $EMULATOR_PID"

  # Wait for device
  log_info "Waiting for device to boot..."
  local attempts=0
  local max_attempts=60

  while [[ $attempts -lt $max_attempts ]]; do
    sleep 2
    if $ANDROID_SDK_ROOT/platform-tools/adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
      log_success "Device booted"
      break
    fi
    attempts=$((attempts + 1))
    if [[ $((attempts % 10)) -eq 0 ]]; then
      log_info "Still waiting for boot... ($attempts/$max_attempts)"
    fi
  done

  if [[ $attempts -ge $max_attempts ]]; then
    log_error "Emulator failed to boot"
    exit 1
  fi

  # Get device serial
  EMULATOR_DEVICE=$($ANDROID_SDK_ROOT/platform-tools/adb devices -l | grep "emulator-" | head -1 | awk '{print $1}')
  log_info "Using device: $EMULATOR_DEVICE"
}

# Configure emulator for screenshots
configure_emulator() {
  log_info "Configuring emulator for screenshots..."

  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell settings put global window_animation_scale 0
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell settings put global transition_animation_scale 0
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell settings put global animator_duration_scale 0
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell settings put system screen_off_timeout 1800000

  log_success "Emulator configured"
}

# Build APK if requested
build_apk() {
  if [[ "${BUILD_APK:-0}" -ne 1 ]]; then
    return
  fi

  log_info "Building debug APK..."

  cd "$PROJECT_DIR"
  JAVA_HOME=/home/t42/.local/share/jdks/jdk-17.0.18+8 \
    GRADLE_USER_HOME=/tmp/gradle-t42 \
    ./scripts/androidw.sh :app:assembleDebug

  log_success "APK built"
}

# Install APK
install_apk() {
  if [[ ! -f "$APK_PATH" ]]; then
    log_error "APK not found: $APK_PATH"
    log_error "Build first with: ./scripts/androidw.sh :app:assembleDebug"
    exit 1
  fi

  log_info "Installing APK..."
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" install -r "$APK_PATH"
  log_success "APK installed"
}

# Launch app
launch_app() {
  log_info "Launching app..."
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell am start \
    -n sc.pirate.app/.MainActivity \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER
  sleep 3
  log_success "App launched"
}

# Define coordinates based on device type
get_coordinates() {
  case "$DEVICE_TYPE" in
    tablet)
      # 2560x1600 Pixel Tablet
      cat << 'EOF'
HOME_TAB:250 1520
MUSIC_TAB:760 1520
LEARN_TAB:1280 1520
SCHEDULE_TAB:1795 1520
CHAT_TAB:2310 1520
MENU_BUTTON:80 120
SCHEDULE_EDIT:2480 120
PROFILE_TAB:2310 1520
EOF
      ;;
    phone)
      # 1080x2400 Pixel 6 (approximate, needs adjustment)
      cat << 'EOF'
HOME_TAB:130 2200
MUSIC_TAB:400 2200
LEARN_TAB:650 2200
SCHEDULE_TAB:900 2200
CHAT_TAB:1150 2200
MENU_BUTTON:80 180
BACK_BUTTON:80 180
EOF
      ;;
  esac
}

# Capture screenshot
capture_screenshot() {
  local name=$1
  local output_subdir

  case "$DEVICE_TYPE" in
    tablet)
      output_subdir="tabletScreenshots"
      ;;
    phone)
      output_subdir="phoneScreenshots"
      ;;
  esac

  local output_path="$OUTPUT_DIR/$output_subdir/$name.png"

  log_info "Capturing: $name"

  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" exec-out screencap -p > "$output_path"

  log_success "Saved: $output_path"
}

# Tap at coordinates
tap() {
  local x=$1
  local y=$2
  $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" shell input tap "$x" "$y"
}

# Navigate to screen
navigate_to() {
  local screen=$1

  log_info "Navigating to: $screen"

  case "$screen" in
    home)
      case "$DEVICE_TYPE" in
        tablet) tap 250 1520 ;;
        phone) tap 130 2200 ;;
      esac
      ;;
    music)
      case "$DEVICE_TYPE" in
        tablet) tap 760 1520 ;;
        phone) tap 400 2200 ;;
      esac
      ;;
    learn)
      case "$DEVICE_TYPE" in
        tablet) tap 1280 1520 ;;
        phone) tap 650 2200 ;;
      esac
      ;;
    schedule)
      case "$DEVICE_TYPE" in
        tablet) tap 1795 1520 ;;
        phone) tap 900 2200 ;;
      esac
      ;;
    chat)
      case "$DEVICE_TYPE" in
        tablet) tap 2310 1520 ;;
        phone) tap 1150 2200 ;;
      esac
      ;;
  esac

  sleep "$SCREENSHOT_DELAY"
}

# Capture all screenshots
capture_all_screenshots() {
  log_info "Starting screenshot capture for $DEVICE_TYPE..."

  # Create output directories
  mkdir -p "$OUTPUT_DIR/phoneScreenshots"
  mkdir -p "$OUTPUT_DIR/tabletScreenshots"

  # Navigate and capture each screen
  local screens=("music" "schedule" "chat" "learn")

  for screen in "${screens[@]}"; do
    navigate_to "$screen"
    capture_screenshot "$screen"
  done

  # Home last (in case feed content is risky)
  navigate_to "home"
  capture_screenshot "home"

  log_success "All screenshots captured!"
}

# Stop emulator
stop_emulator() {
  if [[ "${KEEP_EMULATOR:-0}" -eq 1 ]]; then
    log_info "Keeping emulator running (device: $EMULATOR_DEVICE)"
    return
  fi

  log_info "Stopping emulator..."

  if [[ -n "${EMULATOR_DEVICE:-}" ]]; then
    $ANDROID_SDK_ROOT/platform-tools/adb -s "$EMULATOR_DEVICE" emu kill 2>/dev/null || true
  fi

  if [[ -n "${EMULATOR_PID:-}" ]]; then
    kill "$EMULATOR_PID" 2>/dev/null || true
    wait "$EMULATOR_PID" 2>/dev/null || true
  fi

  log_success "Emulator stopped"
}

# Cleanup on exit
cleanup() {
  stop_emulator
}

trap cleanup EXIT

# Main execution
main() {
  parse_args "$@"

  log_info "Pirate Android Screenshot Tool"
  log_info "Device type: $DEVICE_TYPE"
  log_info "Output: $OUTPUT_DIR"

  check_prerequisites

  local avd_name
  avd_name=$(get_avd_name)

  ensure_avd_exists "$avd_name"
  build_apk
  start_emulator "$avd_name"
  configure_emulator
  install_apk
  launch_app
  capture_all_screenshots

  log_success "Screenshot capture complete!"
  log_info "Screenshots saved to: $OUTPUT_DIR"
}

main "$@"
