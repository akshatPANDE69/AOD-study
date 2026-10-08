#!/usr/bin/env bash
# termux_runner.sh - Helper script for building and running aod_device_runner in Termux

set -euo pipefail

error() {
    echo -e "\033[31mError:\033[0m $*" >&2
}
info() {
    echo -e "\033[32mInfo:\033[0m $*"
}
suggest_install() {
    echo -e "\033[33mPlease install the missing packages with:\033[0m"
    echo "    pkg update && pkg install -y clang make git"
}

if [[ ! -f "Makefile" ]]; then
    error "Makefile not found. Please run this script from the repository root."
    exit 1
fi

missing=()
for cmd in clang make; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        missing+=("$cmd")
    fi
done

if (( ${#missing[@]} )); then
    error "The following required tools are missing: ${missing[*]}"
    suggest_install
    exit 1
fi

info "Compiling the project with 'make all'..."
make all

default_duration=30
read -rp "Enter test duration in seconds (e.g. 10, 30, 60) [default: ${default_duration}]: " duration_input
duration="${duration_input:-$default_duration}"

if ! [[ "$duration" =~ ^[0-9]+$ ]] || (( duration <= 0 )); then
    error "Invalid duration: $duration_input"
    exit 1
fi

if [[ ! -x "./aod_device_runner" ]]; then
    error "./aod_device_runner not found or not executable."
    exit 1
fi

info "Running ./aod_device_runner for ${duration}s..."
./aod_device_runner "$duration"

cat <<'EOF'

=============================================================
You can now copy the Markdown table output above and
paste it into a GitHub Issue, Pull Request, or the
RESEARCH_LEDGER.md file in the repository.
=============================================================
EOF
