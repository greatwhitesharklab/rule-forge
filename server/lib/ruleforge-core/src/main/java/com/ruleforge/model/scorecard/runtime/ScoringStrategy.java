package com.ruleforge.model.scorecard.runtime;

import com.ruleforge.runtime.rete.Context;

/**
 * @author Jacky.gao
 * @since 2016年9月26日
 */
public interface ScoringStrategy {
	/**
	 * 计算得分方法
	 * @param scorecard 当前评分卡对象
	 * @param context 运行时上下文对象
	 * @return 返回最终的得分值
	 */
	Object calculate(Scorecard scorecard,Context context);
}
