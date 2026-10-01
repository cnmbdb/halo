#!/usr/bin/env bash
set -euo pipefail

shop_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
port="${HALO_PORT:-8090}"

docker compose -f "$shop_dir/dev/compose.yaml" up -d

ready=false
for _ in {1..45}; do
  if curl --noproxy '*' --silent --show-error --max-time 2 \
    --output /dev/null --fail "http://127.0.0.1:$port/actuator/health" 2>/dev/null; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  docker compose -f "$shop_dir/dev/compose.yaml" ps
  docker compose -f "$shop_dir/dev/compose.yaml" logs --tail 40 halo
  printf 'Halo 未能在本机端口 %s 启动。若端口被占用，使用 HALO_PORT=8091 重试。\n' "$port" >&2
  exit 1
fi

printf 'Halo 已启动：http://localhost:%s/system/setup\n' "$port"
printf '正在构建商城插件；此时可以先打开 Halo 初始化页面。\n'

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/tmp/gradle-cache \
  -v "$shop_dir:/project" \
  -w /project \
  eclipse-temurin:21-jdk \
  ./gradlew build

printf '插件包: %s/build/libs/plugin-shop-1.0.0-SNAPSHOT.jar\n' "$shop_dir"
printf '完成 Halo 初始化后，在后台安装插件包并启用 Shop Starter 主题。\n'
