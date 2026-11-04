#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
api="$repo_root/AdvancedCore/src/main/java/com/bencodez/advancedcore/api"

test -f "$api/inventory/BInventory.java"
test -d "$api/inventory/editgui"
test -f "$api/item/ItemBuilder.java"
test -f "$api/item/FullInventoryHandler.java"

echo "inventory and item architecture: ok"
