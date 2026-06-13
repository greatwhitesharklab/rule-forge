#!/bin/bash
# V5.53 — LAN 启动脚本(从 dev-local.sh 简化 + 改写)
#
# 区别于 dev-local.sh:
#   - 不起本机 MySQL / Model-Service / console-ui(走 .env 的 192.168.3.36 dev DB)
#   - 端口改 8081/8082(原 8180/8280),Vite dev 走 5173
#   - 绑 0.0.0.0(LAN 客户端可访问)
#   - 跑 java -jar(预先 mvn package -DskipTests),不用 mvn spring-boot:run
#
# 用法:
#   ./scripts/dev-lan.sh build    # 一次性打 jar(可选,本会话已打)
#   ./scripts/dev-lan.sh console  # 起 console-app(8081)
#   ./scripts/dev-lan.sh executor # 起 executor-app(8082)
#   ./scripts/dev-lan.sh ui       # 起 console-ui vite dev(5173)
#   ./scripts/dev-lan.sh all      # console + executor + ui 全起
#   ./scripts/dev-lan.sh stop     # 停掉
#   ./scripts/dev-lan.sh status   # 看进程 + 端口 + DB

set -e

cd "$(dirname "$0")/.."
ROOT=$(pwd)
SERVER_DIR="$ROOT/server"
UI_DIR="$ROOT/console-ui"
LOGS_DIR="$ROOT/.dev-logs"
PID_DIR="$ROOT/.dev-pids"
CONSOLE_JAR=( "$SERVER_DIR"/app/ruleforge-console-app/target/ruleforge-console-app*.jar )
EXECUTOR_JAR=( "$SERVER_DIR"/app/ruleforge-executor-app/target/ruleforge-executor-app*.jar )

mkdir -p "$LOGS_DIR" "$PID_DIR"

stop_local() {
    local svc=$1
    local pidfile="$PID_DIR/$svc.pid"
    if [ -f "$pidfile" ]; then
        local pid=$(cat "$pidfile")
        echo "Stopping $svc (PID $pid)..."
        kill "$pid" 2>/dev/null || true
        rm -f "$pidfile"
    else
        echo "$svc not running locally"
    fi
}

# V5.53:.env 里的 JDBC URL 含有 `?` `&` `=` 字符,纯 `set -a; . .env; set +a` 不会被 export
# (bash 行解析不带引号会截断在第一个 & / ?)。生成一份值带引号包裹的副本到 /tmp,
# 再 source 这份,Java 子进程能拿到完整 URL。
load_env() {
    local env_quoted=/tmp/.ruleforge.env.quoted
    {
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in
                ''|\#*) echo "$line" ;;
                *=*)
                    local key="${line%%=*}"
                    local val="${line#*=}"
                    printf '%s=%q\n' "$key" "$val"
                    ;;
                *) echo "$line" ;;
            esac
        done < "$ROOT/.env"
    } > "$env_quoted"
    set -a
    . "$env_quoted"
    set +a
}

start_console() {
    stop_local console
    load_env
    export CONSOLE_PORT=8180
    export SERVER_ADDRESS=0.0.0.0
    export EXECUTOR_URL=http://127.0.0.1:8280
    export LOG_FILE="$LOGS_DIR/console.log"
    # V5.53:FlowDefinitionRepo(ruleforge-decision jar,executor 用)要求 ruleforge.console.url
    # — console-app 也引用了 ruleforge-decision,这里要假装是 executor 视角,
    # 指向自己(console)作 self-call。
    export RULEFORGE_CONSOLE_URL=http://127.0.0.1:8180

    echo ">>> Starting console-app (port 8180, 0.0.0.0, dev DB 192.168.3.36)"
    java -jar "${CONSOLE_JAR[0]}" > "$LOGS_DIR/console.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/console.pid"
    echo "  PID: $pid"
    echo "  Log: $LOGS_DIR/console.log"
}

start_executor() {
    stop_local executor
    load_env
    export EXECUTOR_PORT=8280
    export SERVER_ADDRESS=0.0.0.0
    export CONSOLE_URL=http://127.0.0.1:8180
    export LOG_FILE="$LOGS_DIR/executor.log"

    echo ">>> Starting executor-app (port 8280, 0.0.0.0, dev DB 192.168.3.36)"
    java -jar "${EXECUTOR_JAR[0]}" > "$LOGS_DIR/executor.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/executor.pid"
    echo "  PID: $pid"
    echo "  Log: $LOGS_DIR/executor.log"
}

start_ui() {
    stop_local ui
    echo ">>> Starting console-ui vite dev (port 5173, 0.0.0.0)"
    cd "$UI_DIR"
    npm run dev > "$LOGS_DIR/ui.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/ui.pid"
    echo "  PID: $pid"
    echo "  Log: $LOGS_DIR/ui.log"
}

status() {
    for svc in console executor ui; do
        local pidfile="$PID_DIR/$svc.pid"
        if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
            echo "$svc: RUNNING (PID $(cat "$pidfile"))"
        else
            echo "$svc: not running"
        fi
    done
    echo ""
    echo "Listening ports:"
    ss -tlnp 2>&1 | grep -E ":(5173|8180|8280)" || echo "  (none of the expected ports)"
    echo ""
    echo "Dev DB (192.168.3.36:3306):"
    mysql -uroot -p123456 -h 192.168.3.36 -e "SELECT VERSION();" 2>&1 | grep -v Warning | head -3
    echo ""
    echo "LAN IP:"
    ip -4 addr show 2>&1 | grep "192\.168\." | head -3
}

case "${1:-status}" in
  build)
    echo ">>> mvn package -DskipTests (console + executor)"
    cd "$SERVER_DIR"
    mvn clean package -DskipTests -B -ntp \
        -pl app/ruleforge-console-app,app/ruleforge-executor-app -am
    ;;
  console)  start_console ;;
  executor) start_executor ;;
  ui)       start_ui ;;
  all)
    start_console
    sleep 2
    start_executor
    sleep 2
    start_ui
    echo ""
    echo "✅ All local services starting. Wait ~30s, then check:"
    echo "   tail -f $LOGS_DIR/console.log"
    echo ""
    echo "   LAN access URL (前端):  http://$(ip -4 addr show 2>&1 | grep -oP '192\.168\.[0-9.]+' | head -1):5173"
    echo "   LAN access URL (后端):  http://$(ip -4 addr show 2>&1 | grep -oP '192\.168\.[0-9.]+' | head -1):8180"
    echo "   Stop: $0 stop"
    ;;
  stop)
    stop_local ui
    stop_local executor
    stop_local console
    ;;
  status)   status ;;
  *)
    echo "Usage: $0 {build|console|executor|ui|all|stop|status}"
    exit 1
    ;;
esac
