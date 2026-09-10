#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
guide="$repo_root/AGENTS.md"

grep -Fq '`api/inventory/` contains GUI/editor behavior' "$guide"
grep -Fq '`api/item/` owns item construction and compatibility-sensitive item serialization' "$guide"

echo "architecture map guidance: ok"
