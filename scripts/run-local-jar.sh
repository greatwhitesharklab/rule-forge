#!/bin/bash
# 本地 jar 启动:连 dev server MySQL(读 .env),后台跑 console + executor
# 用法:./scripts/run-local-jar.sh console|executor|all
#
# 与 scripts/dev-local.sh 区别:dev-local.sh 走 mvn spring-boot:run(本地 Java,
# Docker 跑 MySQL/UI/Model-Service);本脚本跑已打好的 fat jar,不依赖 Docker,
# MySQL 指向远端 dev server(.env 里的 APP_DB_URL / RF_DB_URL)。
#
# 前置:
#   1. server/ 已 mvn package(生成 app/*/target/ruleforge-*-app.jar)
#   2. .env 配好 APP_DB_URL / RF_DB_URL(指向可连的 MySQL)
#
# 已知坑(本地连 dev server MySQL 时):
#   - console-app 的 clickhouseDataSource 默认指向 dev 内网 192.168.3.36:8123,
#     本机连不上会卡请求线程。.env 里设 CH_DB_URL=jdbc:clickhouse://127.0.0.1:1/...
#     让连接快失败(health 会聚合报 503,但业务端点不受影响)。
#   - 详见 application.yml + DataSourceConfig#clickhouseDataSource(@Bean 无条件创建)。
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)

# 加载 .env(逐行,不二次解析值里的 & ?)
[ -f "$ROOT/.env" ] || { echo "❌ 缺 .env"; exit 1; }
while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue ;; *=*) k="${line%%=*}"; v="${line#*=}"; v="${v%$'\r'}"; export "$k=$v" ;; esac
done < "$ROOT/.env"

mkdir -p "$ROOT/.dev-logs" "$ROOT/.dev-pids" "$ROOT/data/riskruleforge"

start_one() {
    local svc=$1
    local jar="$ROOT/server/app/ruleforge-${svc}-app/target/ruleforge-${svc}-app.jar"
    [ -f "$jar" ] || { echo "❌ 找不到 $jar(先 mvn package)"; exit 1; }

    # 停旧的
    local pidf="$ROOT/.dev-pids/${svc}.pid"
    if [ -f "$pidf" ]; then
        local old=$(cat "$pidf")
        kill "$old" 2>/dev/null && echo "  停旧 $svc (PID $old)"
        rm -f "$pidf"
    fi

    echo ">>> 启动 $svc"
    echo "    APP_DB_URL=${APP_DB_URL%%\?*}"
    nohup java -jar "$jar" > "$ROOT/.dev-logs/${svc}.log" 2>&1 &
    local realpid=$!
    disown 2>/dev/null || true
    echo "$realpid" > "$pidf"
    echo "    PID: $realpid"
    echo "    Log: .dev-logs/${svc}.log"
}

TARGET="${1:-all}"
case "$TARGET" in
    console|executor) start_one "$TARGET" ;;
    all) start_one console; start_one executor ;;
    *) echo "用法: $0 [console|executor|all]"; exit 1 ;;
esac
