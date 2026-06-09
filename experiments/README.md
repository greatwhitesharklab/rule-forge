# experiments/

实验性 / 探索性项目,跟 monorepo 主项目(`server/`、`console-ui/`、`docs-site/`、`model-service/`)**平行但不入主流程**。

## Boundary 规则

放 `experiments/` 下的项目:

- ✅ 可以**不**进 `docker-compose.yml` 主编排(支持服务如 pg 可以加进来,但 Rust 自身 service 不在)
- ✅ **不**进 CI(不跑 `mvn verify` 也不跑 `cargo test`)
- ✅ 不进 K8s deployment
- ✅ Java 团队**没有 review 义务**(PR label 标 `experiments/` 即可)
- ❌ **不**进生产流量

`experiments/<X>/` 升格为 production 时,操作:

```bash
git mv experiments/<X> ./<X>            # 去掉 experiments/ 前缀
# 更新 README/CLAUDE.md 头部,从"实验性"改"生产就绪"
# docker-compose.yml 加 service(从可选变必选)
# 加 .github/workflows/<X>.yml
# CLAUDE.md 拆 monorepo 根 / <X>/ 两份
```

整个迁移建议**1 个 commit** 收尾,标题 `chore: <X> 升格 production — 移出 experiments/`。

## 当前项目

| 目录 | 状态 | 升格条件 |
|---|---|---|
| `server-rust/` | 8 phases 完成 / 5/9 executor / MockRuleEngine | 9 executor 完整 + 真实 rule engine + Java console → Rust invalidate 通知链通 |

## 命名约定

`experiments/<X>/` 内部命名**用 production 命名**,不要带 `experiments-` / `playground-` / `prototype-` 前缀 — 升格时只是去掉父目录前缀,`<X>/` 本身名字不动,git history 噪音最小。
