package com.ruleforge.console.servlet.common;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

public class ScriptErrorListener extends BaseErrorListener {

    private final List<ErrorInfo> infos = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                            int charPositionInLine, String msg, RecognitionException e) {
        ErrorInfo info = new ErrorInfo();
        info.setMessage(msg);
        info.setLine(line);
        info.setCharPositionInLine(charPositionInLine);
        infos.add(info);
    }

    public List<ErrorInfo> getInfos() {
        return infos;
    }
}
