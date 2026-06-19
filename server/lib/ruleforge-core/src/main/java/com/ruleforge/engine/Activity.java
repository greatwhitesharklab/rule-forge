package com.ruleforge.engine;
import com.ruleforge.runtime.rete.Instance;
import com.ruleforge.runtime.rete.Path;
import java.util.List;


/**
 * @author Jacky.gao
 * @since 2015年1月12日
 */
public interface Activity extends Instance {
    List<Path> getPaths();
}
