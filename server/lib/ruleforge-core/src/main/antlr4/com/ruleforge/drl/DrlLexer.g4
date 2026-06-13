lexer grammar DrlLexer;

/*
 * V5.42.1 — RuleForge DRL 4 Lexer grammar。
 *
 * <p>从 Drools 6.x 公开 EBNF 参考(https://docs.jboss.org/drools/release/6.5.0.Final/
 * drools-docs/html_single/index.html#drl_Language_Reference),**完全自己写** —
 * 不 fork 任何第三方 ANTLR 移植版(网上 fork 多数 EPL/Apache 混,license 不干净)。
 * 关键字 / token / fragment 全部 RuleForge 原创。
 *
 * <p>设计目标(跟 V5.42 plan 锁定一致):
 * <ul>
 *   <li>支持 DRL 4 完整子集(package / dialect / rule / query / function / declare /
 *       when-then-end / not / exists / from / collect / accumulate 5 内置 /
 *       extends(D2) / salience / timer / no-loop / lock-on-active 等)</li>
 *   <li><b>不</b>支持 import(lexer 缺失 → 报 token 错;DatatypeResolver 兜底再查)</li>
 *   <li><b>不</b>支持 accumulate reverse 段(D3 决定,grammar rule 缺失 → 报语法错)</li>
 *   <li>占位符 {@code ${...}} 当 generic token(grammar 层放行,留 V5.42.3a 展开器替换)</li>
 * </ul>
 *
 * <p>License: Apache 2.0,generated code header 见 pom.xml maven-antlr-plugin 配置。
 *
 * @since 5.42
 */

// ====================================================================
// === 关键字(DRL 4 全部大写,lexer 模式优先) ===
// ====================================================================

DRL_PACKAGE        : 'package';
// DRL_DIALECT 字面量 'dialect',同时用于顶层 "dialect \"mvel\"" 和 rule attribute "dialect(\"mvel\")",
// grammar 用 context 区分(顶层 + rule attribute 槽位)。RuleForge D4 决定:Rule 本身不加 dialect 字段,
// 顶层 dialect 字符串 visitor 解析后丢弃 — DRL 走自家 visitor,dialect 仅作可选顶层 attribute。
DRL_DIALECT        : 'dialect';
// V5.44.3 — 新增 IMPORT 关键字,支持 `import "libs/variables.drl";` 顶层声明。
// 走 library 替换路径:library 从 .xml (V5.43 删老 RuleSetDeserializer 前的兜底)转 DRL
// import 段。DatatypeResolver 优先查 import 列表,再 builtin。
DRL_IMPORT         : 'import';
DRL_RULE           : 'rule';
DRL_QUERY          : 'query';
DRL_FUNCTION       : 'function';
DRL_DECLARE        : 'declare';
DRL_WHEN           : 'when';
DRL_THEN           : 'then';
DRL_END            : 'end';
DRL_EXTENDS        : 'extends';   // V5.42 D2:支持 rule X extends Y
DRL_NOT            : 'not';
DRL_EXISTS         : 'exists';
DRL_EVAL           : 'eval';
DRL_FROM           : 'from';
DRL_COLLECT        : 'collect';
DRL_ACCUMULATE     : 'accumulate';
DRL_INIT           : 'init';
DRL_ACTION         : 'action';
DRL_RESULT         : 'result';
// D3: reverse 段 grammar rule 缺失 → 不定义 DRL_REVERSE(grammar reject)

DRL_TRUE           : 'true';
DRL_FALSE          : 'false';
DRL_NULL           : 'null';

// ====================================================================
// === 顶层 attribute 关键字(放 [] 内) ===
// ====================================================================

DRL_SALIENCE       : 'salience';
DRL_AGENDA_GROUP   : 'agenda-group';
DRL_ACTIVATION_GROUP : 'activation-group';
DRL_RULEFLOW_GROUP : 'ruleflow-group';
DRL_AUTO_FOCUS     : 'auto-focus';
DRL_NO_LOOP        : 'no-loop';
DRL_LOCK_ON_ACTIVE : 'lock-on-active';
DRL_ENABLED        : 'enabled';
DRL_DATE_EFFECTIVE : 'date-effective';
DRL_DATE_EXPIRES   : 'date-expires';
DRL_TIMER          : 'timer';
// 注:不再有 DRL_DIALECT_ATTR,顶层 dialect 和 rule attribute dialect 共享 DRL_DIALECT token。

// ====================================================================
// === accumulate 内置函数(5 个,D3 决定 reverse 段砍掉) ===
// ====================================================================

DRL_COUNT          : 'count';
DRL_SUM            : 'sum';
DRL_AVG            : 'avg';
DRL_MIN            : 'min';
DRL_MAX            : 'max';

// timer 关键字('cron' / 'int' 当 LPAREN 内的子关键字,parser 里不能直接 string literal)
DRL_TIMER_CRON     : 'cron';
DRL_TIMER_INT      : 'int';

// string method 关键字(matches/contains 已在上面,split grammar 不允许 parser 重复 string literal)
DRL_STARTS_WITH    : 'starts-with';
DRL_ENDS_WITH      : 'ends-with';
DRL_LENGTH         : 'length';

// ====================================================================
// === 表达式运算符 ===
// ====================================================================

DRL_AND            : '&&';
DRL_OR             : '||';
COMMA              : ',';
EQ                 : '==';
NEQ                : '!=';
GT                 : '>';
GTE                : '>=';
LT                 : '<';
LTE                : '<=';

DRL_MEMBEROF       : 'memberOf';
DRL_MATCHES        : 'matches';
DRL_CONTAINS       : 'contains';
DRL_SOUNDSLIKE     : 'soundslike';
DRL_IN             : 'in';
// 'not in' 在 parser 层处理:not 关键字后跟 in 关键字

PLUS               : '+';
MINUS              : '-';
STAR               : '*';
DIV                : '/';
MOD                : '%';

// ====================================================================
// === 标点 ===
// ====================================================================

LPAREN             : '(';
RPAREN             : ')';
LBRACK             : '[';
RBRACK             : ']';
LBRACE             : '{';
RBRACE             : '}';
SEMI               : ';';
COLON              : ':';
DOT                : '.';
AT                 : '@';
HASH               : '#';
QUESTION           : '?';
ASSIGN             : ':=';
ARROW              : '->';
DOLLAR             : '$';
DOLLAR_LBRACE      : '${';

// ====================================================================
// === 字面量 ===
// ====================================================================

STRING             : '"' (~["\\] | '\\' .)* '"';
CHAR               : '\'' (~['\\] | '\\' .)* '\'';


fragment DECIMAL   : '0' | [1-9] [0-9]*;
INT                : [+-]? DECIMAL;
FLOAT              : [+-]? DECIMAL '.' [0-9]+ ([eE] [+-]? [0-9]+)?;

DATE               : [0-9][0-9][0-9][0-9] '-' [0-9][0-9] '-' [0-9][0-9];

// 占位符 ${...} body — grammar 层放行,留 V5.42.3a 展开器
PLACEHOLDER        : DOLLAR_LBRACE (~[}])* '}';

// 顺序:QUOTED_IDENTIFIER 在 STRING 之前定义(双引号内容,两者模式一样 → 最长匹配胜;
// 实际就是同一 token,RuleForge V5.42.1 用 QUOTED_IDENTIFIER 统一表示"双引号字符串",
// 跟 Drools 6 EBNF 的"STRING"完全等价)。

// V5.42.1 — split identifier:大写开头 = CLASS(DRL pattern Type),小写开头 = 普通 ident
// 严格跟 DRL 6/7/8 约定一致(大写=FactType,小写=property/function/var),
// 解决 ANTLR4 LL(*) 决策冲突(同 IDENTIFIER LPAREN ... 是 pattern 还是 function call)。
UPPER_IDENTIFIER   : [A-Z] [a-zA-Z0-9_]*;
IDENTIFIER         : [a-z_] [a-zA-Z0-9_]*;
QUOTED_IDENTIFIER  : '"' (~["\\] | '\\' .)* '"';

// ====================================================================
// === 空白 + 注释(skip) ===
// ====================================================================

WS                 : [ \t\r\n\f]+ -> skip;
LINE_COMMENT       : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT      : '/*' .*? '*/' -> skip;
