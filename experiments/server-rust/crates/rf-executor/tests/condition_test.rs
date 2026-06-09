//! UEL condition evaluator tests — covers the subset we support.

use rf_executor::condition::ConditionEvaluator;
use rf_executor::vars::Vars;

fn vars_with(pairs: &[(&str, serde_json::Value)]) -> Vars {
    let mut v = Vars::new();
    for (k, val) in pairs {
        v.insert(*k, val.clone());
    }
    v
}

#[test]
fn empty_expression_is_true() {
    let v = Vars::new();
    // Empty expression is vacuously true (the gateway edge with no
    // condition is "always true", same as Java).
    assert!(ConditionEvaluator::evaluate("", &v).unwrap());
    // Whitespace-only is also true.
    assert!(ConditionEvaluator::evaluate("   ", &v).unwrap());
}

#[test]
fn number_literal_compares() {
    let v = Vars::new();
    assert!(ConditionEvaluator::evaluate("${1 < 2}", &v).unwrap());
    assert!(!ConditionEvaluator::evaluate("${1 > 2}", &v).unwrap());
    assert!(ConditionEvaluator::evaluate("${3 == 3}", &v).unwrap());
    assert!(ConditionEvaluator::evaluate("${3 != 4}", &v).unwrap());
    assert!(ConditionEvaluator::evaluate("${3 >= 3}", &v).unwrap());
    assert!(ConditionEvaluator::evaluate("${3 <= 3}", &v).unwrap());
}

#[test]
fn var_path_resolves() {
    let v = vars_with(&[("age", 20_i64.into())]);
    assert!(ConditionEvaluator::evaluate("${age >= 18}", &v).unwrap());
    assert!(!ConditionEvaluator::evaluate("${age < 18}", &v).unwrap());
}

#[test]
fn dotted_path_walks_object() {
    let v = vars_with(&[(
        "applicant",
        serde_json::json!({ "age": 25, "income": 12000 }),
    )]);
    assert!(ConditionEvaluator::evaluate("${applicant.age >= 18}", &v).unwrap());
    assert!(ConditionEvaluator::evaluate("${applicant.income > 10000}", &v).unwrap());
}

#[test]
fn string_compares() {
    let v = vars_with(&[("name", "alice".into())]);
    assert!(ConditionEvaluator::evaluate("${name == 'alice'}", &v).unwrap());
    assert!(!ConditionEvaluator::evaluate("${name == 'bob'}", &v).unwrap());
    // Double quotes also OK
    assert!(ConditionEvaluator::evaluate(r#"${name == "alice"}"#, &v).unwrap());
}

#[test]
fn boolean_literal() {
    let v = Vars::new();
    assert!(ConditionEvaluator::evaluate("${true}", &v).unwrap());
    assert!(!ConditionEvaluator::evaluate("${false}", &v).unwrap());
}

#[test]
fn missing_var_is_null_and_only_eq_ne_match() {
    let v = Vars::new();
    // missing var resolves to null
    assert!(ConditionEvaluator::evaluate("${absent == null}", &v).unwrap());
    assert!(!ConditionEvaluator::evaluate("${absent > 0}", &v).unwrap());
}

#[test]
fn no_operator_truthy_var() {
    let v = vars_with(&[("flag", true.into())]);
    assert!(ConditionEvaluator::evaluate("${flag}", &v).unwrap());
    let v = vars_with(&[("flag", false.into())]);
    assert!(!ConditionEvaluator::evaluate("${flag}", &v).unwrap());
}
