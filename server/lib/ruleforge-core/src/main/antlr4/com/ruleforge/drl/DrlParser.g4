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
    : lhsAtomic DRL_FROM lhsAtomic
    | lhsAtomic DRL_FROM expr
    ;

lhsCollect
    : lhsAtomic DRL_FROM DRL_COLLECT LPAREN lhsPattern RPAREN
    | DRL_COLLECT LPAREN lhsPattern RPAREN
    ;

lhsAccumulate
    : lhsAtomic DRL_FROM DRL_ACCUMULATE LPAREN lhsPattern SEMI
                       accumulateInit SEMI
                       accumulateAction SEMI
                       accumulateResult RPAREN
    | DRL_ACCUMULATE LPAREN lhsPattern SEMI
                       accumulateInit SEMI
                       accumulateAction SEMI
                       accumulateResult RPAREN
    ;

// D3:reverse 段 grammar rule 缺失 → accumulate 函数定义不包含 reverse
accumulateInit
    : DRL_INIT LPAREN expr RPAREN
    | DRL_INIT LPAREN statementBlock RPAREN
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
    | IDENTIFIER operator LPAREN exprList RPAREN
    ;

operator
    : EQ | NEQ | GT | GTE | LT | LTE
    | DRL_MEMBEROF
    | DRL_MATCHES
    | DRL_CONTAINS
    | DRL_SOUNDSLIKE
    | DRL_IN
    // 'not in' 单独 alt(notInExpr)
    ;

stringMethod
    : DRL_MATCHES LPAREN expr RPAREN
    | DRL_CONTAINS LPAREN expr RPAREN
    | DRL_STARTS_WITH LPAREN expr RPAREN
    | DRL_ENDS_WITH LPAREN expr RPAREN
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
    | expr
    ;

assignStatement
    : expr ASSIGN expr
    ;

methodCallStatement
    : methodChain
    ;

methodChain
    : methodChainHead (DOT methodCall)+
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
              | DRL_AND exprAtom | DRL_OR exprAtom)*
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
    | IDENTIFIER
    | DOLLAR IDENTIFIER
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
    : DRL_QUERY ruleName LPAREN parameters? RPAREN (ARROW queryBody)?
    ;

queryBody
    : lhsPattern? DRL_END SEMI?
    ;

parameters
    : parameter (COMMA parameter)*
    ;

parameter
    : IDENTIFIER COLON IDENTIFIER
    | IDENTIFIER
    ;

// ====================================================================
// === function(基础子集) ===
// ====================================================================

functionStatement
    : DRL_FUNCTION returnType? IDENTIFIER LPAREN parameters? RPAREN functionBody
    ;

returnType
    : IDENTIFIER
    ;

functionBody
    : LBRACE statementBlock RBRACE
    ;

// ====================================================================
// === declare(基础子集) ===
// ====================================================================

declareStatement
    : DRL_DECLARE IDENTIFIER (extendsDecl | fieldsDecl)* DRL_END SEMI?
    ;

extendsDecl
    : DRL_EXTENDS IDENTIFIER
    ;

fieldsDecl
    : IDENTIFIER COLON IDENTIFIER (COMMA IDENTIFIER)*
    ;
