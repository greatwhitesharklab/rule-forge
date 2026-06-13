parser grammar DrlParser;

options { tokenVocab=DrlLexer; }

/*
 * V5.42.1 — RuleForge DRL 4 Parser grammar(简化版 v2)。
 *
 * <p>Lexer 见 DrlLexer.g4。grammar 范围跟 V5.42 plan 锁定一致:
 * package / dialect / rule(支持 extends,D2)/ query / function / declare /
 * when-then-end / not / exists / eval / from / collect / accumulate 5 内置
 * (count/sum/avg/min/max,reverse 段砍掉,D3)/ salience / timer / no-loop /
 * lock-on-active / agenda-group / activation-group / ruleflow-group / auto-focus /
 * enabled / date-effective / date-expires。
 *
 * <p>v2 改进:
 * <ul>
 *   <li>把 expression precedence 改经典 ANTLR 教程 6 层(避开 LL(*) 决策冲突)</li>
 *   <li>lhsAtomic 优先 drlPattern(Type(...)) 形式,小写 IDENTIFIER 走 constraint</li>
 *   <li>'not in' 在 expr2 单独 alt(避免 string literal split grammar error)</li>
 *   <li>binding prefix $name : 在 lhsOrWithBinding</li>
 * </ul>
 *
 * @since 5.42
 */

// ====================================================================
// === 顶层 compilationUnit ===
// ====================================================================

compilationUnit
    : packageStatement? dialectStatement* importStatement* unitStatement* EOF
    ;

packageStatement
    : DRL_PACKAGE dottedName SEMI?
    ;

// V5.42 D4 决定:顶层 dialect 仅解析后丢弃
dialectStatement
    : DRL_DIALECT STRING SEMI?
    ;

// V5.44.3 — 顶层 import 段,library 替换路径。`import "libs/variables.drl";`
// STRING 形式(V5.44.3 仅支持 library 文件路径引用,不支持 java 类 import)。
// DrlAstVisitor.visitImportStatement 收集到 import 列表,DrlDeserializer 端透传给
// DatatypeResolver(后者查 import 列表再 builtin)。
importStatement
    : DRL_IMPORT STRING SEMI?
    ;

dottedName
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// ====================================================================
// === 单元语句 ===
// ====================================================================

unitStatement
    : ruleStatement
    | queryStatement
    | functionStatement
    | declareStatement
    ;

// ====================================================================
// === rule(支持 extends,D2) ===
// ====================================================================

ruleStatement
    : DRL_RULE ruleName ruleAttributes? extendsClause? whenClause thenClause endClause
    ;

ruleName
    : STRING
    | QUOTED_IDENTIFIER
    | IDENTIFIER
    ;

extendsClause
    : DRL_EXTENDS ruleName
    ;

ruleAttributes
    : LBRACK attribute (COMMA attribute)* RBRACK
    ;

attribute
    : salienceAttr
    | agendaGroupAttr
    | activationGroupAttr
    | ruleflowGroupAttr
    | autoFocusAttr
    | noLoopAttr
    | lockOnActiveAttr
    | enabledAttr
    | dateEffectiveAttr
    | dateExpiresAttr
    | timerAttr
    | dialectAttr
    ;

salienceAttr        : DRL_SALIENCE INT;
agendaGroupAttr     : DRL_AGENDA_GROUP STRING;
activationGroupAttr : DRL_ACTIVATION_GROUP STRING;
ruleflowGroupAttr   : DRL_RULEFLOW_GROUP STRING;
autoFocusAttr       : DRL_AUTO_FOCUS (DRL_TRUE | DRL_FALSE);
noLoopAttr          : DRL_NO_LOOP (DRL_TRUE | DRL_FALSE);
lockOnActiveAttr    : DRL_LOCK_ON_ACTIVE (DRL_TRUE | DRL_FALSE);
enabledAttr         : DRL_ENABLED (DRL_TRUE | DRL_FALSE);
dateEffectiveAttr   : DRL_DATE_EFFECTIVE STRING;
dateExpiresAttr     : DRL_DATE_EXPIRES STRING;
dialectAttr         : DRL_DIALECT STRING;
timerAttr           : DRL_TIMER timerSpec;

timerSpec
    : LPAREN timerCron RPAREN
    | LPAREN timerInt RPAREN
    ;

timerCron : DRL_TIMER_CRON LPAREN STRING RPAREN;
timerInt  : DRL_TIMER_INT LPAREN INT RPAREN;

// ====================================================================
// === when / then / end ===
// ====================================================================

whenClause : DRL_WHEN lhsPattern? ;
thenClause : DRL_THEN rhsConsequence? ;
endClause  : DRL_END (SEMI)? ;

// ====================================================================
// === lhs 条件模式 ===
// ====================================================================

lhsPattern
    : lhsOrWithBinding (COMMA lhsOrWithBinding)*
    ;

// 可选 binding prefix:$ident :
lhsOrWithBinding
    : (DOLLAR IDENTIFIER COLON)? lhsOr
    ;

lhsOr
    : lhsAnd (DRL_OR lhsAnd)*
    ;

lhsAnd
    : lhsUnary (DRL_AND lhsUnary)*
    ;

lhsUnary
    : drlPattern
    | DRL_NOT lhsUnary
    | DRL_EXISTS lhsUnary
    | DRL_EVAL LPAREN expr RPAREN
    | lhsFrom
    | lhsCollect
    | lhsAccumulate
    | expr
    ;

lhsFrom
    : drlPattern DRL_FROM drlPattern
    | drlPattern DRL_FROM expr
    | lhsAtomic DRL_FROM lhsAtomic
    | lhsAtomic DRL_FROM expr
    ;

lhsCollect
    : drlPattern DRL_FROM DRL_COLLECT LPAREN lhsPattern RPAREN
    | DRL_COLLECT LPAREN lhsPattern RPAREN
    ;

lhsAccumulate
    : drlPattern DRL_FROM DRL_ACCUMULATE LPAREN lhsPattern (SEMI | COMMA)
                       accumulateInit (SEMI | COMMA)
                       accumulateAction (SEMI | COMMA)
                       accumulateResult RPAREN
    | DRL_ACCUMULATE LPAREN lhsPattern (SEMI | COMMA)
                       accumulateInit (SEMI | COMMA)
                       accumulateAction (SEMI | COMMA)
                       accumulateResult RPAREN
    ;

// D3:reverse 段 grammar rule 缺失 → accumulate 函数定义不包含 reverse
// V5.50.1:initBody 3 alt(expr 链 / 标识符赋值链 / statementBlock)支持 lhsAccumulateCount 等 DRL
accumulateInit
    : DRL_INIT LPAREN initBody RPAREN
    ;

initBody
    : (IDENTIFIER | DRL_COUNT | DRL_SUM | DRL_AVG | DRL_MIN | DRL_MAX) ASSIGN expr (COMMA expr)*
    | fieldType IDENTIFIER ASSIGN expr   // V5.50.3:int total := 0 typed decl
    | statement (SEMI statement)*
    | expr (COMMA expr)*
    ;

accumulateAction
    : DRL_ACTION LPAREN statementBlock RPAREN
    ;

accumulateResult
    : DRL_RESULT LPAREN expr RPAREN
    ;

// lhsAtomic — V5.42.1 v3:不直接 alt drlPattern(避免 LL(*) 决策冲突),
// lhsUnary 已把 drlPattern 提到最前,这里只保留 constraint(对小写属性) + expr。
lhsAtomic
    : constraint
    | expr
    ;

// DRL pattern:Type(arg, arg, ...) 形式 — 大写 Type + 0+ 内部表达式
drlPattern
    : UPPER_IDENTIFIER LPAREN exprList? RPAREN
    ;

// constraint:identifier operator value(对小写属性约束)
constraint
    : IDENTIFIER operator expr
    | IDENTIFIER LBRACK stringMethod RBRACK
    | IDENTIFIER (DRL_IN | DRL_NOT DRL_IN) LPAREN exprList RPAREN
    | IDENTIFIER operator LPAREN exprList RPAREN
    ;

operator
    : EQ | NEQ | GT | GTE | LT | LTE
    | DRL_MEMBEROF
    | DRL_MATCHES
    | DRL_CONTAINS
    | DRL_SOUNDSLIKE
    | DRL_IN
    | DRL_AND | DRL_OR
    // 'not in' 单独 alt(notInExpr)
    ;

stringMethod
    : DRL_MATCHES (LPAREN expr RPAREN | expr)
    | DRL_CONTAINS (LPAREN expr RPAREN | expr)
    | DRL_STARTS_WITH (LPAREN expr RPAREN | expr)
    | DRL_ENDS_WITH (LPAREN expr RPAREN | expr)
    | DRL_LENGTH
    ;

// ====================================================================
// === rhs:then 段(语义动作) ===
// ====================================================================

rhsConsequence
    : statement (SEMI statement)* SEMI?
    ;

statement
    : assignStatement
    | methodCallStatement
    | returnStatement
    | expr
    ;

// V5.50.3: function body 内 `return expr;` 形式
returnStatement
    : DRL_RETURN expr?
    ;

assignStatement
    : expr ASSIGN expr
    ;

methodCallStatement
    : methodChain
    ;

methodChain
    : methodChainHead (DOT methodCall)*
    | methodCall
    ;

methodChainHead
    : IDENTIFIER
    | DOLLAR IDENTIFIER
    ;

methodCall
    : IDENTIFIER LPAREN exprList? RPAREN
    ;

statementBlock
    : statement (SEMI statement)* SEMI?
    ;

// ====================================================================
// === 表达式(单层,V5.42.1 简化:precedence 不在 grammar 层处理) ===
// ====================================================================
//
// V5.42.1 grammar 简化:DRL 表达式不做 precedence(left-associative 等),
// precedence 留给 V5.42.2 DrlAstVisitor 解析 ParseTree 时处理。
// Grammar 只识别 op token,visitor 自己根据 op 类型建 AST 时排序。
// 牺牲:rule "a > b && c < d" 在 grammar 接受但 visitor 才决定哪个先算。
// 优点:避免 ANTLR 4 LL(*) 决策冲突,grammar 短、生成代码小、debug 简单。
// 注:Drools 6.x 老 MVEL 路径就是平 precedence(MVEL 自己 AST 处理),这条路在生产里
// 验证过 — V5.42.1 沿用这个简化。

expr
    : exprAtom (cmpOp exprAtom | addOp exprAtom | mulOp exprAtom
              | DRL_AND exprAtom | DRL_OR exprAtom
              | DRL_IN LPAREN exprList RPAREN
              | DRL_NOT DRL_IN LPAREN exprList RPAREN)*
    ;

cmpOp
    : EQ | NEQ | GT | GTE | LT | LTE | DRL_IN
    | DRL_NOT DRL_IN
    | DRL_MEMBEROF | DRL_MATCHES | DRL_CONTAINS | DRL_SOUNDSLIKE
    ;

addOp : PLUS | MINUS;
mulOp : STAR | DIV | MOD;

exprAtom
    : LPAREN expr RPAREN
    | atom
    ;

atom
    : literal
    | methodChain
    | IDENTIFIER LBRACK stringMethod RBRACK
    | IDENTIFIER
    | DOLLAR (IDENTIFIER | DRL_COUNT | DRL_SUM | DRL_AVG | DRL_MIN | DRL_MAX)
    | DRL_COUNT | DRL_SUM | DRL_AVG | DRL_MIN | DRL_MAX
    | PLACEHOLDER
    ;

literal
    : STRING
    | INT
    | FLOAT
    | DATE
    | DRL_TRUE
    | DRL_FALSE
    | DRL_NULL
    ;

exprList
    : expr (COMMA expr)*
    ;

// ====================================================================
// === query(基础子集,V5.42.1 第一版) ===
// ====================================================================

queryStatement
    : DRL_QUERY ruleName LPAREN parameters? RPAREN (ARROW | DRL_WHEN) queryBody
    | DRL_QUERY ruleName LPAREN parameters? RPAREN queryBody    // V5.50.3:无 ARROW 也行(裸 query,param 后直接 end)
    ;

queryBody
    : lhsPattern? DRL_END SEMI?
    ;

parameters
    : parameter (COMMA parameter)*
    ;

parameter
    : UPPER_IDENTIFIER DOLLAR (IDENTIFIER | DRL_COUNT | DRL_SUM | DRL_AVG | DRL_MIN | DRL_MAX)  // V5.50.3:Integer $min
    | UPPER_IDENTIFIER (IDENTIFIER | DRL_COUNT | DRL_SUM | DRL_AVG | DRL_MIN | DRL_MAX)         // V5.50.3:Integer x
    | IDENTIFIER COLON (IDENTIFIER | UPPER_IDENTIFIER)        // V5.50.3:min : Integer
    | IDENTIFIER                            // V5.50.3:光 ident
    ;

// ====================================================================
// === function(基础子集) ===
// ====================================================================

functionStatement
    : DRL_FUNCTION returnType? IDENTIFIER LPAREN parameters? RPAREN functionBody
    ;

returnType
    : UPPER_IDENTIFIER  // V5.50.3:Integer / String
    | IDENTIFIER
    ;

functionBody
    : LBRACE statementBlock RBRACE
    ;

// ====================================================================
// === declare(基础子集 + V5.45.1 完整化:annotation + 嵌套 declare) ===
// ====================================================================
//
// V5.42.1 基础 schema:
//   declare X extends Y field1 : T1, T2 field2 : T3 end
//
// V5.45.1 扩展:
//   - annotation 段:出现在 DRL_DECLARE 关键字前 / fields 之间,`@name` 或
//     `@name(args)` 形式,args 是 `IDENTIFIER | STRING | IDENTIFIER = STRING`
//     逗号分隔列表(Drools 6 兼容子集)
//   - 嵌套 declare:父 declare 段 body 内允许再 declare(grammar 接受,
//     语义层由 DrlAstVisitor 把嵌套 declare 也提到顶层 declaredTypes map)

declareStatement
    : annotation* DRL_DECLARE UPPER_IDENTIFIER
          ( extendsDecl | fieldsDecl | annotation | declareStatement )*
      DRL_END SEMI?
    ;

extendsDecl
    : DRL_EXTENDS UPPER_IDENTIFIER
    ;

fieldsDecl
    : IDENTIFIER COLON fieldType (COMMA fieldType)*
    ;

// V5.45.1 — field type 名允许三种形式:
//   - UPPER_IDENTIFIER(String / Integer / Applicant 等大写开头的类名)
//   - IDENTIFIER(double / float / long / short 等 Java primitive 关键字以外的
//     小写标识符)
//   - DRL_TIMER_INT / DRL_TIMER_CRON(int / cron 这俩 token 在 V5.42.1 关键字表里,
//     但 declare 段 type 名需要支持 "int" / "cron" — 否则 DRL 6.x 老 declare 段
//     大量 int 字段全报语法错)
// 不接受其他 DRL 关键字(DRL_END 等)— 这限制是合理的:Drools 6.x 自己也禁。
fieldType
    : UPPER_IDENTIFIER
    | IDENTIFIER
    | DRL_TIMER_INT
    | DRL_TIMER_CRON
    ;

// V5.45.1 — annotation 段(顶层 declareStatement 头,或 fields 之间)
annotation
    : AT IDENTIFIER (LPAREN annotationArgs RPAREN)?
    ;

annotationArgs
    : annotationArg (COMMA annotationArg)*
    ;

annotationArg
    : IDENTIFIER
    | STRING
    | IDENTIFIER ASSIGN STRING
    ;
