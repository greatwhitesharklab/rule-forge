# V1 al 动作库部署指南(DevOps)

> 适用版本:V7.4.1+。把业务逻辑(Java 方法)暴露给 V1 规则,让规则 `INVOKE` 调用。

## 1. al 是什么

al = **Action Library**,V1 四库之一(vl/cl/pl/al)。`al` 让规则在命中时**反射调 Java bean 方法**,把外部计算/查外部服务/算额度等业务逻辑嵌入决策流。

**与 pl/cl/vl 的区别**:

| 库 | 作用 | V1 引用方式 |
|---|---|---|
| vl | 变量库(共享 Schema) | `schemaRef` 派生 |
| cl | 常量库(项目级) | CEL `constant.xxx` |
| pl | 参数库(运行时传参) | CEL `param.xxx` |
| **al** | **外部 Java 方法(bean 反射)** | **action `type: INVOKE`,`ref: "beanId.method"`** |

## 2. 端到端流程

```
┌──────────┐   INVOKE   ┌────────────────┐   反射   ┌──────────┐
│ V1 规则  │ ─────────► │ InvokeAction   │ ───────► │ Spring   │
│ (JSON)   │  ref=      │ (V7.4.1b)      │  beanId  │ Bean     │
│          │  "x.y"     │ EngineContext  │ .method  │ (al)     │
└──────────┘            │ .getBean(id)   │          └──────────┘
                       └────────────────┘
```

`InvokeAction.execute`(`V1ActionRhs.java:204-242`)拆 `ref="beanId.method"` → `EngineContext.getBean(beanId)` 拿到 Spring bean → `method.invoke(bean, args)` 反射调 → 可选 `target` 写回 return 值到 fact。

## 3. ⚠️ 部署关键:bean 必须在正确的 JVM

`EngineContext.getBean(beanId)` 是**当前 JVM 进程内**的 Spring `ApplicationContext.getBean` 查找。**不会跨进程/跨 HTTP**。

| 运行时 | 走的 JVM | 需要的 bean 注册位置 |
|---|---|---|
| **console `/v1/execute`**(画布"运行") | `console-app` (8180) | `server/app/ruleforge-console-app/` |
| **executor `/v1/exec`**(生产执行已发布流) | `executor-app` (8280) | `server/app/ruleforge-executor-app/` |

→ **同一个 bean 必须在 console 和 executor 两边都注册**,否则 console 能跑、executor 跑不通(反之亦然)。这是 devops 部署检查清单必看项。

> 发布(`POST /v1/publish`)只冻结**画布 JSON + 库文件引用**,不打包 bean 类。bean 类必须随 console/executor app 一起部署。

## 4. 写一个 al bean(完整示例)

**场景**: 风控规则命中"VIP 客户"时,调 `QuotaCalculateAction.increaseQuota` 算提额。

### 4.1 创建 bean 类

`server/app/ruleforge-console-app/src/main/java/com/yourorg/ruleforge/action/QuotaCalculateAction.java`:

```java
package yourorg.ruleforge.action;

import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;
import org.springframework.stereotype.Service;

@Service("quotaCalculateAction")       // ← beanId = "quotaCalculateAction"(供 INVOKE ref 引用)
@ActionBean(name = "额度计算")          // ← 显示名(al 库文件 + UI 提示)
public class QuotaCalculateAction {

    // 方法名 = Java 真实方法名(invoke 反射按 methodName + arity 匹配)
    @ActionMethod(name = "提额额度计算")
    @ActionMethodParameter(names = {"客户当前额度", "调额系数", "单次提额cap", "评级额度cap", "当前在贷余额"})
    public String increaseQuota(Object currentQuota, Object adjustCoefficient,
                                Object singleIncreaseCap, Object ratingQuotaCap,
                                Object currentLoanBalance) {
        // 你的业务逻辑
        return "120000";
    }
}
```

**executor 端同款类**(路径同结构,包名可不同):

`server/app/ruleforge-executor-app/src/main/java/yourorg/ruleforge/action/QuotaCalculateAction.java` —— 复制同一份类(同 beanId `"quotaCalculateAction"`,同方法名 `increaseQuota`)。两边的逻辑/版本必须一致。

### 4.2 注解说明

| 注解 | 作用 | 必填? |
|---|---|---|
| `@Service("beanId")` | Spring bean 名 = `INVOKE` ref 的 beanId | 是 |
| `@ActionBean(name = "...")` | al 库元数据:显示名 | 是(否则 `BuiltInActionLibraryBuilder` 不扫) |
| `@ActionMethod(name = "...")` | al 库元数据:方法显示名 | 是(否则方法不暴露) |
| `@ActionMethodParameter(names = {...})` | al 库元数据:形参名(仅文档,运行时反射按 arity) | 否 |

`BuiltInActionLibraryBuilder.@PostConstruct init()` 启动时扫所有 bean,只保留有 `@ActionBean` 注解的进 al 内建库。

### 4.3 V1 规则里 INVOKE

V1 规则 action:

```json
{
  "type": "INVOKE",
  "ref": "quotaCalculateAction.increaseQuota",
  "args": [
    {"name": "客户当前额度",  "ref": "currentQuota"},
    {"name": "调额系数",     "value": 1.2},
    {"name": "单次提额cap",   "value": 50000},
    {"name": "评级额度cap",   "ref": "ratingCap"},
    {"name": "当前在贷余额",  "ref": "loanBalance"}
  ],
  "target": "newQuota"
}
```

字段语义:
- `ref`: `"<beanId>.<methodName>"`,**点分**。`beanId` 严格匹配 `@Service("...")` 的 bean 名;`methodName` 匹配 Java 方法名(非 `@ActionMethod.name` 显示名)。
- `args[]`: 每个 arg 可 `value`(字面量)或 `ref`(fact 字段引用,只读 fact 字段);不能两个都设(后端 `value` 优先于 `ref`,语义同 `SET_VARIABLE`)。
- `target`: 可选。设了 → bean 方法的返回值(`!= null`)写回 `fact[target]`。

## 5. 部署清单

每次新增/修改 al bean,**两侧 app 同步部署**:

1. [ ] `console-app` 编译 + 部署(含新 bean 类)
2. [ ] `executor-app` 编译 + 部署(同款 bean 类,**beanId + methodName 必须一致**)
3. [ ] 重启 console + executor(让 `@PostConstruct` 重扫 al 库)
4. [ ] 验证:`console /v1/execute` 跑一次(走 console JVM)→ 成功;`executor /v1/exec` 跑一次(走 executor JVM)→ 成功

## 6. 故障排查

| 症状 | 原因 | 排查 |
|---|---|---|
| `al: bean [xxx] 未注册(EngineContext.getBean 返 null)` | beanId 拼错 或 该 JVM 没注册 | 检查 `@Service("xxx")` 的 xxx 与 ref beanId 完全一致;确认运行 JVM 的 app 包含此 bean 类 |
| `al: xxx.yyy(3 args) 未找到` | 方法名错 / 参数数量错(arity) | `methodName` 匹配 Java 方法名(非 `@ActionMethod.name`);反射按 arity 匹配,重载要 arity 不同 |
| `console 能跑 / executor 报 bean null` | executor-app 没部署此 bean | 见 §5:executor 端复制 bean 类 + 重启 |
| INVOKE 完全没触发 / rule 不 fire | RuleSet RETE 路径:fact 必须是 `GeneralEntity`,且满足 condition(参考 [[rule-types]]) | console 走 `/v1/execute` 自动包 `GeneralEntity`;executor `/v1/exec` 同样;若裸 `LinkedHashMap` 不 fire |
| 修改 bean 后 V1 画布 al 库下拉没新方法 | 启动时 `@PostConstruct` 已扫,需重启 | 重启 console + executor |

## 7. 进阶:al 库文件(`.v1lib.json` al 类型)

V7.4 起,V1 支持把 al 元数据写进独立库文件(`.v1lib.json`,`LibraryType=ACTION`),画布引用共享。这是**文档性**的 —— 运行时反射调 bean 不依赖此文件,但能让 al 方法在 UI 里有名字/参数提示。

```json
{
  "id": "loanActions",
  "type": "ACTION",
  "name": "风控动作库",
  "entries": [
    {"id": "quotaCalculateAction.increaseQuota", "name": "提额额度计算"}
  ]
}
```

**可省略**:al 直接靠 `@Service` + `@ActionBean` 注解,文件只是元数据。生产建议:console/executor 部署 al 类即可,库文件可选(给画布补全提示用)。

## 8. 完整链路参考

- 后端 INVOKE 实现: `V1ActionRhs.InvokeAction` (V7.4.1b)
- 反射 bean 查找: `EngineContext.getBean(beanId)` (静态桥 → `ListableBeanFactory.getBean`)
- al 库扫描: `BuiltInActionLibraryBuilder.@PostConstruct init()`
- V1 INVOKE UI: `ActionsEditor` + `InvokeActionFields` (V7.9, V7.10 共享 `v1-flow/ActionEditor.tsx`)
- 部署全栈: `docs-site/deployment/production.md`, `docs-site/deployment/docker-compose.md`

---

> **版本**: V7.4.1b (al 后端) + V7.9 (INVOKE UI) + V7.10 (共享 ActionEditor)。变更请同步更新本文件。
