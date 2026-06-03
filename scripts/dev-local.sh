#!/bin/bash
# 本地启动服务(不用 Docker 跑 Java 应用,但可以用 Docker 跑 MySQL 等)
#
# 三种模式:
#   1. 全本地:mvn 跑 + 本地 Java 跑 + 本地连 DB(用 .env 里的 DB)
#   2. 半本地:Docker 跑 MySQL/Model-Service/UI,Java 本地 mvn 跑 + run
#   3. 全 Docker:用 dev-up.sh
#
# 用法:
#   ./scripts/dev-local.sh console     # 本地跑 console,其它容器化
#   ./scripts/dev-local.sh executor    # 本地跑 executor
#   ./scripts/dev-local.sh all        # 本地跑 console + executor
#   ./scripts/dev-local.sh --stop     # 停掉本地 Java 进程
#   ./scripts/dev-local.sh --status   # 查看本地进程状态

set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)
SERVER_DIR="$ROOT/server"
LOGS_DIR="$ROOT/.dev-logs"
PID_DIR="$ROOT/.dev-pids"

mkdir -p "$LOGS_DIR" "$PID_DIR"

ACTION=""
TARGET=""

for arg in "$@"; do
    case "$arg" in
        console|executor|all) TARGET="$arg" ;;
        --stop) ACTION="stop" ;;
        --status) ACTION="status" ;;
        --help|-h)
            echo "Usage: $0 [console|executor|all|--stop|--status]"
            exit 0
            ;;
        *) echo "Unknown: $arg"; exit 1 ;;
    esac
done

# 默认行为
[ -z "$TARGET" ] && [ -z "$ACTION" ] && TARGET="all"
[ -z "$ACTION" ] && ACTION="start"

stop_local() {
    local svc=$1
    local pidfile="$PID_DIR/$svc.pid"
    if [ -f "$pidfile" ]; then
        local pid=$(cat "$pidfile")
        echo "Stopping $svc (PID $pid)..."
        kill $pid 2>/dev/null || true
        rm -f "$pidfile"
    else
        echo "$svc not running locally"
    fi
}

start_local() {
    local svc=$1
    local module="ruleforge-$svc-app"
    local mainclass="com.ruleforge.$svc.app.RuleForge${svc^}Application"

    stop_local "$svc"

    # 加载 .env 变量
    if [ -f "$ROOT/.env" ]; then
        set -a
        . "$ROOT/.env"
        set +a
    fi

    # 启动 MySQL/Model-Service/UI(后台)
    echo ">>> Starting supporting services (mysql, model-service, console-ui)"
    docker compose up -d mysql model-service console-ui

    echo ">>> Running $module locally"
    cd "$SERVER_DIR"
    mvn -pl "$module" -am spring-boot:run \
        -Dspring-boot.run.fork=true \
        > "$LOGS_DIR/$svc.log" 2>&1 &

    local pid=$!
    echo $pid > "$PID_DIR/$svc.pid"
    echo "  PID: $pid"
    echo "  Log: $LOGS_DIR/$svc.log"
    sleep 1
    echo "  Tail: tail -f $LOGS_DIR/$svc.log"
}

case "$ACTION" in
    stop)
        [ "$TARGET" = "all" ] || [ -z "$TARGET" ] && TARGET="console executor"
        for s in $TARGET; do stop_local $s; done
        ;;
    status)
        for svc in console executor; do
            pidfile="$PID_DIR/$svc.pid"
            if [ -f "$pidfile" ] && kill -0 $(cat "$pidfile") 2>/dev/null; then
                echo "$svc: RUNNING (PID $(cat $pidfile))"
            else
                echo "$svc: not running locally"
            fi
        done
        echo ""
        echo "Docker stack:"
        docker compose ps 2>/dev/null | head -10
        ;;
    start)
        # 起 MySQL/Model-Service/UI
        echo ">>> Starting supporting services"
        docker compose up -d mysql model-service console-ui

        # 起指定 Java
        for svc in $TARGET; do
            if [ "$svc" = "all" ]; then
                start_local console
                start_local executor
            else
                start_local "$svc"
            fi
        done

        echo ""
        echo "✅ Local services starting. Check logs:"
        echo "   tail -f $LOGS_DIR/console.log"
        echo "   tail -f $LOGS_DIR/executor.log"
        echo ""
        echo "   Web: http://localhost:8080/   (vite dev server, not container)"
        echo "        http://localhost/         (nginx, container)"
        echo "   Console: http://localhost:8180/"
        echo "   Stop:   $0 --stop"
        ;;
esac
