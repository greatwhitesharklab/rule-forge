# 引擎核心技术债 Roadmap

V5.75 引擎架构审计产出。两项大重构经评估**不适合塞进 cleanup PR**(动 RETE 热路径 + 多日工作量),
作为独立聚焦 PR 推进。containment 已写入 `CLAUDE.md` 的"代码优雅"原则,确保新代码不加深耦合。

---

## TD-1 · `ruleforge-core` 去 Spring(`ApplicationContextAware` 静态 lookup)

**现状**(V5.75 审计):`CLAUDE.md` 声称 core "引擎逻辑不依赖 Spring",但代码里:

| 耦合形态 | 规模 | 位置 |
|---|---|---|
| `import org.springframework.*` | 23 文件 | 全模块 |
| `implements ApplicationContextAware` | 15 类 | action/builder/parse/runtime 各处 |
| `context.getApplicationContext().getBean(id)` 运行时 lookup | 4 处 | ConsolePrintAction / ScoringAction / ExecuteMethodAction / ScoreRule |
| `Utils` 静态 holder(`getApplicationContext` + functionDescriptorMap + debugWriters) | 1 类(最严重) | `Utils.java` |
| `Context` 接口暴露 `getApplicationContext()` | 接口泄漏 | `runtime/rete/Context.java` |
| XML 装配 | 360 行 / 120 bean | `ruleforge-core-context.xml` |

**典型模式**:15 个 ApplicationContextAware 类在 `setApplicationContext` 里 `getBeansOfType(X.class)` 收集插件 bean;
4 个 action 类运行时按 id `getBean`;Utils 当静态服务定位器。

**为什么不在 V5.75 做**:动 `ContextImpl`/`EvaluationContextImpl`(每会话构造、RETE 求值热路径)+ 重写 120-bean XML +
改 `Context` 接口 API + 改测试 mock 基建(`KnowledgeSessionTest` 直接 set `Utils.getApplicationContext()`)。
多日工作量,需先补 ContextImpl/EvaluationContextImpl 的特征化测试。

**推荐做法**(独立 PR,按子任务多次 commit):
1. 新增 `EngineBeanRegistry`(显式注册表,Spring 装配注入 `List<X>`,引擎逻辑只认注册表接口,不认 `ApplicationContext`)
2. 15 个 ApplicationContextAware 改构造注入注册表
3. `Context` 接口去掉 `getApplicationContext()`;4 个 action 的 `getBean(id)` 改注入
4. `ContextImpl`/`EvaluationContextImpl` 构造器从 `ApplicationContext` 改为直接收 `AssertorEvaluator`/`ValueCompute`
5. `Utils` 去 `ApplicationContextAware`,变纯函数工具类(静态 holder 全移走)
6. XML 装配逐步迁 Java `@Configuration`(可选,降 XML 维护成本)
7. 全程 `ruleforge-core` 584 测试绿为门槛

**验收**:`grep -r "org.springframework" ruleforge-core/src/main` 只剩 `config/` 包。

---

## TD-2 · `model/` → `runtime/` 分层倒置(RETE node/activity 融合)

**现状**(V5.75 审计):47 个 `model/` 文件反向 `import com.ruleforge.runtime.*`,集中在:

| 被引类型 | 次数 | 含义 |
|---|---|---|
| `runtime.WorkingMemory` | 20 | 函数描述符/动作收集器要访问 fact 存储 |
| `runtime.rete.EvaluationContext` | 12 | RETE 节点求值要上下文 |
| `runtime.rete.Context` / `Activity` | 各 6 | **node 即 activity** —— 节点自带执行行为 |
| 其余(TerminalActivity/ReteInstance/Path/...) | 各 1-2 | 长尾 |

**根因**:这个引擎的 RETE 网络模型与执行是**有意融合**的 —— `AndNode`/`OrNode`/`CriteriaNode` 等结构类
直接持有/实现 `Activity`(执行行为),`model.rete` 与 `runtime.rete` 是同一套对象图的两侧。

**为什么不在 V5.75 做**:彻底分开 = 把每个 rete 节点拆成"定义(`model`) + 执行器(`runtime`)",
是动 584 测试覆盖的 RETE 内核的大版本重构(数周)。且当前融合**性能达标**(V5.46:0.16ms/2000fact,快过 Drools)、
**测试齐全**,没有功能/性能驱动,纯架构整洁诉求不足以承担该风险。

**containment**(已写入 `CLAUDE.md` "代码优雅"原则):
- `model/rete` node/activity 融合标为 **grandfathered** 架构决策
- **新代码不得加深** model→runtime 反向 import;可逆的 import(如函数描述符的 WorkingMemory 访问)逐步清理
- node/activity 彻底拆分留作未来大版本(需先补 RETE 求值的 characterization test)

**推荐做法**(独立大版本,先测试后重构):
1. 补 characterization test 锁定现有 RETE 求值行为(节点遍历序、激活组、生效/过期窗、reevaluate)
2. 引入 `model.rete.def`(纯结构:节点定义 + 连线)与 `runtime.rete.exec`(执行器)分离
3. 逐节点类型迁移(ObjectTypeNode → CriteriaNode → Join/And/Or → Terminal),每类一个 commit + 全量回归
4. `WorkingMemory` 访问改传参(函数描述符不再 import runtime)

**验收**:`grep -r "import com.ruleforge.runtime" ruleforge-core/src/main/java/com/ruleforge/model` = 0。

---

## 已在 V5.75 完成的安全清理(对照)

| 项 | commit | 内容 |
|---|---|---|
| 代码优雅原则 | V5.75.1 | CLAUDE.md 加硬约束 + 整理;TD-1/TD-2 containment 写入 |
| README 过期说法 | V5.75.2 | "V5.43 删除老路径" → 如实描述双路径迁移策略 |
| Utils 去业务逻辑 | V5.75.3 | `buildElseRule` 移到 `model.rule.ElseRuleBuilder` |
| KnowledgeSessionImpl | V5.75.4 | 时间窗 helper 抽取 + 构造器整理 + 反编译来源标注 |
