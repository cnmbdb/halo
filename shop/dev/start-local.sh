#!/usr/bin/env bash
set -euo pipefail

shop_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/tmp/gradle-cache \
  -v "$shop_dir:/project" \
  -w /project \
  eclipse-temurin:21-jdk \
  ./gradlew build

docker compose -f "$shop_dir/dev/compose.yaml" up -d

printf '\nHalo: http://localhost:%s/system/setup\n' "${HALO_PORT:-8090}"
printf '插件包: %s/build/libs/plugin-shop-1.0.0-SNAPSHOT.jar\n' "$shop_dir"
printf '完成 Halo 初始化后，在后台安装插件包并启用 Shop Starter 主题。\n'
