package com.ruleforge.debug;

import java.io.IOException;
import java.util.List;


public interface DebugWriter {
    void write(List<MessageItem> items) throws IOException;
}
