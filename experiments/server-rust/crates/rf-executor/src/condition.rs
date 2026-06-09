//! UEL condition expression evaluator — minimal subset, ported from Java
//! `ConditionEvaluator`.
//!
//! Grammar supported:
//! ```text
//!   expression := '${' comparand '}'
//!   comparand  := path (op comparand)?
//!   path       := ident ('.' ident)*
//!   op         := '>' | '<' | '>=' | '<=' | '==' | '!='
//! ```
//!
//! Both sides of the operator are resolved via [`crate::vars::resolve_path`]
//! — a literal `42` parses as `Number(42)`, `"hello"` as `String("hello")`,
//! and `vars.applicant.age` walks the JSON object graph.
//!
//! Not supported (out of v0 scope, matches Java Phase 1):
//!   - `&&` / `||` / parentheses
//!   - function calls
//!   - ternary `? :`
//!   - list / map literals
//!
//! A flow that needs those should pre-compute into a single boolean var
//! and reference it without an operator.

use std::sync::OnceLock;

use regex::Regex;
use serde_json::Value;

use crate::error::FlowError;
use crate::vars::{resolve_path, Vars};

/// UEL operator matcher. Three capture groups: `path`, `op`, `rhs`.
/// Java's regex uses `[\w]+` for identifier; same here.
static UEL: OnceLock<Regex> = OnceLock::new();

fn uel_regex() -> &'static Regex {
    UEL.get_or_init(|| Regex::new(r"^([\w]+(?:\.[\w]+)*)\s*(>=|<=|!=|==|>|<)\s*(.+)$").unwrap())
}

pub struct ConditionEvaluator;

impl ConditionEvaluator {
    /// Evaluate a `${...}` expression against the given vars.
    /// Empty / None expression is vacuously `true` (the gateway edge with
    /// no condition is "always true", same as Java).
    pub fn evaluate(expression: &str, vars: &Vars) -> Result<bool, FlowError> {
        let trimmed = expression.trim();
        if trimmed.is_empty() {
            return Ok(true);
        }
        // Strip the ${...} wrapper if present.
        let inner = if trimmed.starts_with("${") && trimmed.ends_with('}') {
            &trimmed[2..trimmed.len() - 1]
        } else {
            trimmed
        };

        let re = uel_regex();

        let Some(caps) = re.captures(inner) else {
            // No operator — treat as a truthy variable reference.
            let v = Self::resolve(inner, vars)?;
            return Ok(v.as_bool().unwrap_or(false));
        };

        let lhs = Self::resolve(&caps[1], vars)?;
        let rhs = Self::resolve(&caps[3], vars)?;
        let op = &caps[2];
        Self::compare(&lhs, &rhs, op)
    }

    /// Resolve a token to a `Value`. Mirrors Java `resolveValue`:
    /// numbers → Number, "true"/"false" → Bool, quoted strings → String,
    /// everything else → dot-path lookup against vars.
    fn resolve(token: &str, vars: &Vars) -> Result<Value, FlowError> {
        let token = token.trim();
        // Quoted string
        if token.len() >= 2
            && ((token.starts_with('"') && token.ends_with('"'))
                || (token.starts_with('\'') && token.ends_with('\'')))
        {
            return Ok(Value::String(token[1..token.len() - 1].to_string()));
        }
        // Boolean
        if token.eq_ignore_ascii_case("true") {
            return Ok(Value::Bool(true));
        }
        if token.eq_ignore_ascii_case("false") {
            return Ok(Value::Bool(false));
        }
        // null
        if token == "null" {
            return Ok(Value::Null);
        }
        // Number — try int first, then float
        if let Ok(n) = token.parse::<i64>() {
            return Ok(Value::Number(n.into()));
        }
        if let Ok(n) = token.parse::<f64>() {
            return serde_json::Number::from_f64(n)
                .map(Value::Number)
                .ok_or_else(|| FlowError::Script(format!("invalid float literal: {token}")));
        }
        // Variable path
        let root = Value::Object(
            vars.as_object()
                .iter()
                .map(|(k, v)| (k.clone(), v.clone()))
                .collect(),
        );
        Ok(resolve_path(&root, token).cloned().unwrap_or(Value::Null))
    }

    fn compare(lhs: &Value, rhs: &Value, op: &str) -> Result<bool, FlowError> {
        if lhs.is_null() || rhs.is_null() {
            return Ok(match op {
                "==" => lhs == rhs,
                "!=" => lhs != rhs,
                _ => false,
            });
        }
        if let (Some(a), Some(b)) = (lhs.as_f64(), rhs.as_f64()) {
            return Ok(match op {
                ">" => a > b,
                "<" => a < b,
                ">=" => a >= b,
                "<=" => a <= b,
                "==" => (a - b).abs() < f64::EPSILON,
                "!=" => (a - b).abs() >= f64::EPSILON,
                _ => return Err(FlowError::Script(format!("unknown op: {op}"))),
            });
        }
        if let (Some(a), Some(b)) = (lhs.as_str(), rhs.as_str()) {
            return Ok(match op {
                "==" => a == b,
                "!=" => a != b,
                ">" => a > b,
                "<" => a < b,
                ">=" => a >= b,
                "<=" => a <= b,
                _ => return Err(FlowError::Script(format!("unknown op: {op}"))),
            });
        }
        if let (Some(a), Some(b)) = (lhs.as_bool(), rhs.as_bool()) {
            return Ok(match op {
                "==" => a == b,
                "!=" => a != b,
                _ => return Err(FlowError::Script(format!("bool op not supported: {op}"))),
            });
        }
        Err(FlowError::Script(format!(
            "cannot compare {lhs:?} with {rhs:?} using op {op}"
        )))
    }
}
