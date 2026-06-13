package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.45.2 — Library .drl 文件解析器。
 *
 * <p>Library 文件跟主 DRL 文件**不同**:library 只能含 declare 段 + 顶层 import
 * 段,不允许 rule / query / function(那些是业务规则,library 是 type 元数据)。
 *
 * <p>使用场景:
 * <ol>
 *   <li>KnowledgeBuilder parse 主 DRL 文件 → 拿到顶层 import 路径列表</li>
 *   <li>对每条 import,KnowledgeBuilder 调 {@link LibraryLoader#loadLibrary} 拿
 *       library 文件内容</li>
 *   <li>loader 调 {@link #parseLibraryDrl} 解析 library 内容,拿到 declare types
 *       + 内部 import 列表</li>
 *   <li>KnowledgeBuilder 把 declare types register 进 {@link DatatypeResolver},
 *       内部 import 列表走 BFS 递归加载(环检测)</li>
 * </ol>
 *
 * <p>纯函数 — 每次调用都 new DrlLexer/DrlParser,无状态共享。
 *
 * @since 5.45
 */
public class LibraryParser {

    /**
     * 解析一段 library .drl 文本。
     *
     * @param drlText library 文件内容
     * @return 解析结果(types + innerImports);若 library 含 rule / query / function
     *         段,抛 {@link DrlParseException}(library 文件**必须**纯 declare + import)
     */
    public LibraryParseResult parseLibraryDrl(String drlText) {
        DrlLexer lexer = new DrrlExceptionThrowingLexer(CharStreams.fromString(drlText));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlParser parser = new DrlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                throw new DrlParseException(
                    "Library .drl parse error line " + line + ":" + charPositionInLine
                    + " " + msg, e);
            }
        });
        DrlParser.CompilationUnitContext tree = parser.compilationUnit();
        if (parser.getNumberOfSyntaxErrors() > 0) {
            throw new DrlParseException("Library .drl has syntax errors, see above");
        }

        // 1. 收集 declare 段
        Map<String, DatatypeResolver.TypeInfo> types = new LinkedHashMap<>();
        // 2. 收集顶层 import 段(用于 BFS 递归加载)
        List<String> innerImports = new java.util.ArrayList<>();
        // 3. 校验:library 文件不允许 rule / query / function
        for (DrlParser.UnitStatementContext u : tree.unitStatement()) {
            if (u.ruleStatement() != null) {
                throw new DrlParseException(
                    "V5.45.2 library .drl 不允许含 rule 段(只 declare + import): "
                    + u.getText(), u);
            }
            if (u.queryStatement() != null) {
                throw new DrlParseException(
                    "V5.45.2 library .drl 不允许含 query 段(只 declare + import): "
                    + u.getText(), u);
            }
            if (u.functionStatement() != null) {
                throw new DrlParseException(
                    "V5.45.2 library .drl 不允许含 function 段(只 declare + import): "
                    + u.getText(), u);
            }
        }

        // 4. 用 DrlAstVisitor 收集 declare + import — V5.45.1 visitor 已有 declaredTypes /
        //    getImports(),V5.45.2 直接复用,避免重复实现
        DrlAstVisitor visitor = new DrlAstVisitor(new DatatypeResolver());
        visitor.visit(tree);
        types.putAll(visitor.getDeclaredTypes());
        innerImports.addAll(visitor.getImports());

        return new LibraryParseResult(types, innerImports);
    }

    /**
     * V5.45.2 解析结果。
     *
     * @param types declare 段抽出的 type 列表(key=type 名,value=TypeInfo 含 fields +
     *              extendsName + annotations)
     * @param innerImports 顶层 import 段收集到的 library 路径(供 KnowledgeBuilder
     *                     BFS 递归加载)
     */
    public record LibraryParseResult(
        Map<String, DatatypeResolver.TypeInfo> types,
        List<String> innerImports) {
    }

    // 简单包装:lexer 层错误也走 DrlParseException
    private static class DrrlExceptionThrowingLexer extends DrlLexer {
        public DrrlExceptionThrowingLexer(org.antlr.v4.runtime.CharStream input) {
            super(input);
            this.removeErrorListeners();
            this.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
                    throw new DrlParseException(
                        "Library .drl lexer error line " + line + ":" + charPositionInLine
                        + " " + msg, e);
                }
            });
        }
    }
}
