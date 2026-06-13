//! P6 micro-benchmark: 2000 facts × 3 rules, fire-cycle throughput.
//!
//! **V5.46 rewrite** — 镜像 Java `EvalBenchmark.java` workload
//! (mariofusco/drools-benchmark EvalBenchmark.run() adapted to RuleForge
//! 语义)做 Java vs Rust 横向对比。Workload:
//!
//! - 1000 Person + 1000 Address(共 2000 fact)
//! - 3 条 rule,期望 fire 3 次(3 个 special Person/Address pair)
//! - 单次完整 insert(2000 fact) + fire_rules 耗时
//!
//! 跑法:`cargo bench -p rf-rule`
//!
//! **V5.46 simplification** — 跟 Java `no_eval` 一样用独立 criteria(无 AndNode
//! join)。Java 端 DRL 4 grammar 不支持 cross-pattern binding 提取,Rust 端
//! 的 AndNode 行为跟 Java 端对不上(都测了 0 fired — 行为 gap),所以两边都
//! 退到 "3 个独立 criteria 各匹配 1 个 special fact" 的最简 workload:
//!   - Person(name == "Mario") / Person(name == "Duncan") / Person(name == "Toshiya")
//!   - 期望 fire 3 次(每个 special Person 命中 1 个 rule)
//! 3 个 Address fact 没用 — 留着对应 Java workload 规模(2000 fact),但不参
//! 与任何 criteria。
//!
//! 跟前一个 bench 区别:1 fact / 10 rules(原 P6)→ 2000 fact / 3 rules(本 V5.46)。
//! 1-fact case 测的是 engine 自身 wire-up 成本,2000-fact case 测 alpha
//! filtering + 节点 propagate 成本 — 这才是生产 decision 实际场景。

use criterion::{black_box, criterion_group, criterion_main, Criterion};
use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::RuleEngine;
use rf_rule::deserialize::{KnowledgePackage, KnowledgePackageWrapper};
use rf_rule::model::left::LeftType;
use rf_rule::model::left_part::{Left, LeftPart};
use rf_rule::model::op::Op;
use rf_rule::model::rule::{Lhs, Rhs, Rule};
use rf_rule::model::value::Value;
use rf_rule::model::{Criteria, Line, Rete, ReteNode};
use rf_rule::rete_engine::ReteRuleEngine;
use serde_json::{json, Value as JsonValue};

const N: usize = 1000;

/// 3 条 rule,各匹配 1 个 special Person name(Mario / Duncan / Toshiya)。
/// 跟 Java EvalBenchmark no_eval 一样,无 AndNode join。
fn build_eval_engine() -> ReteRuleEngine {
    let mut nodes: Vec<ReteNode> = Vec::new();
    let person_otn_id = 1;
    let crit_ids = [2, 3, 4];
    let term_ids = [10, 11, 12];
    let names = ["Mario", "Duncan", "Toshiya"];

    let mut person_lines: Vec<Line> = Vec::new();
    for i in 0..3 {
        person_lines.push(Line {
            from_node_id: person_otn_id,
            to_node_id: crit_ids[i],
            from: None,
            to: None,
        });
        nodes.push(ReteNode::Criteria {
            id: crit_ids[i],
            debug: false,
            criteria: name_crit(names[i]),
            lines: vec![Line {
                from_node_id: crit_ids[i],
                to_node_id: term_ids[i],
                from: None,
                to: None,
            }],
        });
        nodes.push(ReteNode::Terminal {
            id: term_ids[i],
            rule: Rule {
                id: format!("R{}", i + 1),
                name: format!("R{}", i + 1),
                rule_type: None,
                file: None,
                salience: 0,
                effective_date: None,
                expires_date: None,
                enabled: true,
                debug: false,
                activation_group: None,
                agenda_group: None,
                auto_focus: false,
                ruleflow_group: None,
                lhs: Lhs::default(),
                rhs: Rhs::default(),
                r#loop: false,
                remark: None,
                with_else: false,
            },
        });
    }

    let person_otn = ReteNode::ObjectType {
        id: person_otn_id,
        object_type_class: Some("Person".into()),
        lines: person_lines,
    };

    let kp = KnowledgePackage {
        rete: Rete {
            object_type_nodes: vec![person_otn],
            activation_group_retes_map: Default::default(),
            agenda_group_retes_map: Default::default(),
        },
        with_else_rules: Default::default(),
    };
    let mut wrap = KnowledgePackageWrapper::from_parts("bench", kp, nodes, None);
    wrap.build_deserialize();
    ReteRuleEngine::from_wrapper(&wrap)
}

/// `Person(name == "Mario")` — 用 EQ + Variable ref。
fn name_crit(name: &str) -> Criteria {
    Criteria {
        op: Op::Equals,
        left: Left {
            left_type: LeftType::Variable,
            left_part: LeftPart::Variable {
                variable_category: Some("Person".into()),
                variable_label: Some("name".into()),
                variable_name: Some("name".into()),
                datatype: Some("String".into()),
            },
            arithmetic: None,
        },
        value: Some(Value::Constant {
            constant_name: None,
            constant_label: None,
            constant_category: None,
            constant_value: Some(json!(name)),
        }),
    }
}

/// 准备 1000 Person fact(其中 3 个 special):
/// - persons[250] = "Mario"
/// - persons[500] = "Duncan"
/// - persons[750] = "Toshiya"
/// 其余 random UUID,无匹配。
///
/// **不再 insert Address fact** — V5.46 simplification 阶段决定无
/// AndNode join,Address 不参与匹配(OTN 是 no-op decoration,insert
/// 也无意义,徒增 bench 时间)。Java EvalBenchmark 仍 insert 1000
/// Address 是因为 Java 端两条 rule 都有 `Address(street == ...)`
/// criteria — Rust 端这个 workload 退化掉了,Address 不必插。
fn prepare_facts() -> Vec<JsonValue> {
    let mut persons = Vec::with_capacity(N);
    for i in 0..N {
        persons.push(json!({"name": format!("uuid-p-{}", i)}));
    }
    persons[250] = json!({"name": "Mario"});
    persons[500] = json!({"name": "Duncan"});
    persons[750] = json!({"name": "Toshiya"});
    persons
}

/// 单次 run(per-fact-fresh 模式):每个 fact 单独 fire 一次,模拟
/// production per-request 模式。
///
/// **V5.46 limitation** — Rust `rf-rule` `EvaluationContext::clean()` 只在
/// unit test 里调,production 路径**不**调 — 也就是多 fact 一次 fire_rules
/// 时,criteria_value_map 缓存第一个 fact 的 `false` 一直复用,后面的
/// fact 全被错判为不匹配。这是 Rust engine 真 bug,留 V5.46+ 单独 PR 修。
/// 本 bench workaround:每个 fact 单独 fire 一次(用 fresh `FlowContext`),
/// 模拟 production per-request 模式。
async fn run_once_per_fire(engine: &ReteRuleEngine, persons: &[JsonValue]) -> usize {
    let mut total = 0;
    for p in persons {
        let mut ctx = FlowContext::new("bench");
        ctx.vars.assert_fact("Person", p.clone());
        let res = engine.fire_rules(&mut ctx).await.unwrap();
        total += res.fired_rules.len();
    }
    total
}

/// 单次 run(1-shot 模式):insert 1000 fact 一次,fire_rules 一次。
/// **有 bug** — 第一条 fact 不匹配时,cached false 复用,后面 999 条
/// 全错判。只用来跟 Java 端 1-shot 数字对比,期望差几个数量级。
async fn run_once_oneshot(engine: &ReteRuleEngine, persons: &[JsonValue]) -> usize {
    let mut ctx = FlowContext::new("bench");
    for p in persons {
        ctx.vars.assert_fact("Person", p.clone());
    }
    let res = engine.fire_rules(&mut ctx).await.unwrap();
    res.fired_rules.len()
}

fn bench_fire(c: &mut Criterion) {
    let engine = build_eval_engine();
    let persons = prepare_facts();
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();

    // sanity: per-fact-fresh 模式应 fire 3 次
    let fired = rt.block_on(run_once_per_fire(&engine, &persons));
    assert_eq!(fired, 3, "V5.46 bench per-fire 应 fire 3 条 rule,实际 {fired}");

    c.bench_function("fire_3_rules_1000_facts_per_fire", |b| {
        b.iter(|| {
            let n = rt.block_on(async {
                run_once_per_fire(black_box(&engine), black_box(&persons)).await
            });
            black_box(n);
        });
    });

    c.bench_function("fire_3_rules_1000_facts_oneshot_buggy", |b| {
        b.iter(|| {
            let n = rt.block_on(async {
                run_once_oneshot(black_box(&engine), black_box(&persons)).await
            });
            black_box(n);
        });
    });
}

criterion_group!(benches, bench_fire);
criterion_main!(benches);
