#!/bin/bash
# 本地预编译 Spring Boot jars,然后构建 Docker 镜像。
#
# 工作流:
#   1. 改 Java 代码
#   2. ./scripts/build-images.sh       ← 这一步
#   3. docker compose up -d           ← 用 build 好的 image 启动
#
# 优化点:
# - 增量编译:只重 build 改过的 module (mvn -am 自动拉依赖模块)
# - Docker layer 缓存:Dockerfile 只 COPY 预编译 jar,源码/pom 变化不影响 base layer
# - 离线友好:Maven 本地仓库 .m2 共享,避免每次重下
# - 速度:首次 60-90s(包含依赖下载),后续 10-20s(增量编译 + 镜像 layer 缓存)
#
# 用法:
#   ./scripts/build-images.sh                  # 构建两个 app 镜像
#   ./scripts/build-images.sh console          # 只构建 console
#   ./scripts/build-images.sh --no-tests      # 跳过测试
#   ./scripts/build-images.sh --clean         # mvn clean 后再 build

set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)
SERVER_DIR="$ROOT/server"
TARGETS="ruleforge-console-app ruleforge-executor-app"
SKIP_TESTS=true
DO_CLEAN=false

# 解析参数
for arg in "$@"; do
    case "$arg" in
        console)
            TARGETS="ruleforge-console-app"
            ;;
        executor)
            TARGETS="ruleforge-executor-app"
            ;;
        --no-tests)
            SKIP_TESTS=true
            ;;
        --with-tests)
            SKIP_TESTS=false
            ;;
        --clean)
            DO_CLEAN=true
            ;;
        *)
            echo "Unknown arg: $arg"
            exit 1
            ;;
    esac
done

MVN_OPTS=(-B -pl "$(echo $TARGETS | tr ' ' ',')" -am)
if $SKIP_TESTS; then
    MVN_OPTS+=(-DskipTests)
fi

echo "=========================================="
echo "Step 1/2: Local Maven build"
echo "  Targets: $TARGETS"
echo "  Skip tests: $SKIP_TESTS"
echo "  Clean: $DO_CLEAN"
echo "=========================================="

cd "$SERVER_DIR"

if $DO_CLEAN; then
    mvn "${MVN_OPTS[@]}" clean package
else
    mvn "${MVN_OPTS[@]}" package
fi

echo ""
echo "=========================================="
echo "Step 2/2: Docker build"
echo "=========================================="
cd "$ROOT"

# 把 TARGETS (ruleforge-console-app/ruleforge-executor-app) 转成对应的 docker service
DOCKER_TARGETS=""
for tgt in $TARGETS; do
    case "$tgt" in
        ruleforge-console-app) DOCKER_TARGETS="$DOCKER_TARGETS console-app" ;;
        ruleforge-executor-app) DOCKER_TARGETS="$DOCKER_TARGETS executor-app" ;;
    esac
done

docker compose build $DOCKER_TARGETS

echo ""
echo "✅ Build complete. Run: docker compose up -d"
echo "   Images: $DOCKER_TARGETS"

