# 开发环境搭建

## 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+（前端开发）
- MySQL 8.0+

## 后端

### 编译

```bash
cd server

# 编译全部模块
mvn compile

# 编译单个模块及其依赖
mvn compile -pl ruleforge-core -am

# 打包（跳过测试）
mvn clean package -DskipTests
```

### 数据库

项目使用 Flyway 管理数据库迁移。启动 console-app 时会自动执行迁移脚本。

配置双数据源：
- **app 数据源**: 业务数据库
- **ruleforge 数据源**: 规则引擎数据库

### 启动

- Console-app: 端口 8081
- Executor-app: 端口 8082

关键配置项：
- `ruleforge.exec.url` — console 指向 executor 的地址
- `ruleforge.console.url` — executor 指向 console 的地址

## 前端

```bash
cd console-ui
npm install
```

具体命令参考 `console-ui/package.json`。

前端使用 React + bpmn-js 构建可视化规则设计器。
