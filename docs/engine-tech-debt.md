# 引擎核心技术债 Roadmap

V5.75 引擎架构审计产出。两项大重构经评估**不适合塞进 cleanup PR**(动 RETE 热路径 + 多日工作量),
作为独立聚焦 PR 推进。containment 已写入 `CLAUDE.md` 的"代码优雅"原则,确保新代码不加深耦合。

---

## TD-1 · `ruleforge-core` 去 Spring — ✅ 已完成(V6.0)

**验收**: `grep -r "org.springframework" ruleforge-core/src/main/java | grep -v config/` = **0** ✓

**完成方式**(V6.0,PR #182):

V5.76 PR#140 已清一半(23→9 Spring import,47→21 model→runtime)。V6.0 清剩余:

1. VariableAssignAction:Spring BeanUtils → JDK PropertyDescriptor
2. MemoryKnowledgeCache:@Component → XML bean 注册
3. KnowledgeServiceImpl:@Service+@Value → setter+XML property
4. PropertyConfigurer + RuleForgePropertyPlaceholderConfigurer:移到 config/ 包
5. FileResourceProvider / BsfVariableCollector / BuiltInActionLibraryBuilder / CacheUtils:ApplicationContextAware 收集器移到 config/ 包

非 config/ Spring import:9 → 0。

---

## TD-2 · `model/` → `runtime/` 分层倒置 — ✅ 已完成(V6.1)

**验收**: `grep -r "import com.ruleforge.runtime" ruleforge-core/src/main/java/com/ruleforge/model` = **0** ✓

**完成方式**(V6.1,PR #183-#185):

引入 `com.ruleforge.engine` 中性包,把 model 和 runtime 共享的 15 个接口/类型从 runtime 移到 engine:
Context / EvaluationContext / Activity / WorkingMemory / KnowledgeSession / Path / ValueCompute /
AssertorEvaluator / RuleExecutionResponse / WorkingMemoryFunctionContext / EngineContext /
NodeActivityFactory / KnowledgeSessionFactory / KnowledgePackageWrapper / ReteInstanceFactory。

model 依赖 engine 接口(依赖倒置),runtime 实现 engine 接口。`Rete.newReteInstance()` 返回
Object + 委托 `engine.ReteInstanceFactory` 隐藏 runtime 构造。

**设计决策**:model 类保留 `evaluate()` 方法(Rich Domain Model 模式)。这些方法只依赖 engine
接口,不依赖 runtime 具体实现。原始 TD doc 想要的"model.rete.def(纯结构)与 runtime.rete.exec
(执行器)分离"被判定为 **不值得做** —— model.evaluate() 调 engine 接口是标准依赖倒置,
把 evaluate 搬到 runtime Visitor 只是把方法换个类放,model 仍需 import engine 接口做参数类型。
Fowler 的 Rich Domain Model 模式支持此设计。

---

## 已在 V5.75 完成的安全清理(对照)

| 项 | commit | 内容 |
|---|---|---|
| 代码优雅原则 | V5.75.1 | CLAUDE.md 加硬约束 + 整理;TD-1/TD-2 containment 写入 |
| README 过期说法 | V5.75.2 | "V5.43 删除老路径" → 如实描述双路径迁移策略 |
| Utils 去业务逻辑 | V5.75.3 | `buildElseRule` 移到 `model.rule.ElseRuleBuilder` |
| KnowledgeSessionImpl | V5.75.4 | 时间窗 helper 抽取 + 构造器整理 + 反编译来源标注 |
