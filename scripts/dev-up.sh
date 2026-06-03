#!/bin/bash
# Docker 全栈启动脚本 — 推荐日常 dev 用法
#
# 工作流:
#   1. 改 Java 代码
#   2. ./scripts/build-images.sh      # 本地 mvn + docker build(增量,几秒)
#   3. ./scripts/dev-up.sh           # 启动/重启容器
#
# 选项:
#   --rebuild         强制 rebuild 镜像(代码改了 pom 但 mvn 没重新 package 时)
#   --clean           删数据卷清状态(慎用,会丢 MySQL 数据)
#   --logs            启动后 tail 关键日志
#   --stop            只 stop,不起

set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)

REBUILD=""
CLEAN=""
LOGS=""
STOP_ONLY=""

for arg in "$@"; do
    case "$arg" in
        --rebuild) REBUILD="1" ;;
        --clean)   CLEAN="1" ;;
        --logs)    LOGS="1" ;;
        --stop)    STOP_ONLY="1" ;;
        --help|-h)
            echo "Usage: $0 [--rebuild] [--clean] [--logs] [--stop]"
            exit 0
            ;;
    esac
done

# 启动前检查 jar 是否存在
if [ ! -f "$ROOT/server/ruleforge-console-app/target/ruleforge-console-app.jar" ] || \
   [ ! -f "$ROOT/server/ruleforge-executor-app/target/ruleforge-executor-app.jar" ]; then
    echo "❌ 找不到预编译 jar,先跑 ./scripts/build-images.sh"
    exit 1
fi

if [ -n "$STOP_ONLY" ]; then
    echo ">>> Stopping stack"
    docker compose down
    exit 0
fi

if [ -n "$CLEAN" ]; then
    echo ">>> Clean state (deletes MySQL data!)"
    docker compose down -v
    # init SQL 会自动重新跑,创建 3 个数据库
else
    echo ">>> Stopping existing containers (keeping volumes)"
    docker compose down
fi

echo ""
echo ">>> Building images (fast: incremental)"
if [ -n "$REBUILD" ]; then
    docker compose build --no-cache
else
    docker compose build
fi

echo ""
echo ">>> Starting stack"
docker compose up -d

if [ -n "$LOGS" ]; then
    echo ""
    echo ">>> Tailing logs (Ctrl-C to stop, containers keep running)"
    sleep 5
    docker compose logs -f --tail=100 console-app executor-app mysql
fi

echo ""
echo "✅ Stack started. View status: docker compose ps"
echo "   Tail logs:  docker compose logs -f"
echo "   Stop:       $0 --stop"
echo "   UI:         http://localhost/"
echo "   Console:    http://localhost:8180/"
echo "   Executor:   http://localhost:8280/"
