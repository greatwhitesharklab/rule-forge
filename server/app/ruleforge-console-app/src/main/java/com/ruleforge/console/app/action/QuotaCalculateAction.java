package com.ruleforge.console.app.action;

import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @author Fred
 * @date 2025/12/17 11:12
 */
@Service("quotaCalculateAction")
@ActionBean(name = "额度计算")
public class QuotaCalculateAction {
    private final Logger logger = LoggerFactory.getLogger(QuotaCalculateAction.class);

    /**
     * 提额公式: 新额度 = max(min(min(客户当前额度*调额系数, 单次提额cap) + 客户当前额度, by评级额度cap), 当前在贷余额)
     */
    @ActionMethod(name = "提额额度计算")
    @ActionMethodParameter(names = {"客户当前额度", "调额系数", "单次提额cap", "评级额度cap", "当前在贷余额"})
    public String increaseQuota(Object currentQuota, Object adjustCoefficient, Object singleIncreaseCap,
                                Object ratingQuotaCap, Object currentLoanBalance) {
        BigDecimal currentQuotaVal = currentQuota == null ? BigDecimal.ZERO : new BigDecimal(currentQuota.toString());
        BigDecimal adjustCoefficientVal = adjustCoefficient == null ? BigDecimal.ZERO : new BigDecimal(adjustCoefficient.toString());
        BigDecimal singleIncreaseCapVal = singleIncreaseCap == null ? BigDecimal.ZERO : new BigDecimal(singleIncreaseCap.toString());
        BigDecimal ratingQuotaCapVal = ratingQuotaCap == null ? BigDecimal.ZERO : new BigDecimal(ratingQuotaCap.toString());
        BigDecimal currentLoanBalanceVal = currentLoanBalance == null ? BigDecimal.ZERO : new BigDecimal(currentLoanBalance.toString());

        logger.info("提额计算参数 - 客户当前额度({}) 调额系数({}) 单次提额cap({}) 评级额度cap({}) 当前在贷余额({})",
                currentQuotaVal, adjustCoefficientVal, singleIncreaseCapVal, ratingQuotaCapVal, currentLoanBalanceVal);

        // 新额度 = max(min(min(客户当前额度*调额系数, 单次提额cap) + 客户当前额度, by评级额度cap), 当前在贷余额)
        // step1: 客户当前额度 * 调额系数
        BigDecimal step1 = currentQuotaVal.multiply(adjustCoefficientVal);
        // step2: min(step1, 单次提额cap)
        BigDecimal step2 = step1.min(singleIncreaseCapVal);
        // step3: step2 + 客户当前额度
        BigDecimal step3 = step2.add(currentQuotaVal);
        // step4: min(step3, 评级额度cap)
        BigDecimal step4 = step3.min(ratingQuotaCapVal);
        // step5: max(step4, 当前在贷余额)
        BigDecimal result = step4.max(currentLoanBalanceVal);

        // 百位向上取整
        BigDecimal total = result.divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_UP);
        String str = String.valueOf(total.multiply(BigDecimal.valueOf(100)));

        logger.info("提额计算结果：{}", str);
        return str;
    }

    /**
     * 降额公式: 新额度 = max(客户当前额度*降额保留系数, 当前在贷余额, 最低保底额度)
     */
    @ActionMethod(name = "降额额度计算")
    @ActionMethodParameter(names = {"客户当前额度", "降额保留系数", "当前在贷余额", "最低保底额度"})
    public String decreaseQuota(Object currentQuota, Object retentionRate, Object currentLoanBalance, Object minQuotaFloor) {
        BigDecimal currentQuotaVal = currentQuota == null ? BigDecimal.ZERO : new BigDecimal(currentQuota.toString());
        BigDecimal retentionRateVal = retentionRate == null ? new BigDecimal("0.8") : new BigDecimal(retentionRate.toString());
        BigDecimal currentLoanBalanceVal = currentLoanBalance == null ? BigDecimal.ZERO : new BigDecimal(currentLoanBalance.toString());
        BigDecimal minQuotaFloorVal = minQuotaFloor == null ? BigDecimal.ZERO : new BigDecimal(minQuotaFloor.toString());

        logger.info("降额计算参数 - 客户当前额度({}) 降额保留系数({}) 当前在贷余额({}) 最低保底额度({})", currentQuotaVal, retentionRateVal, currentLoanBalanceVal, minQuotaFloorVal);

        // 新额度 = max(客户当前额度*降额保留系数, 当前在贷余额, 最低保底额度)
        // step1: 客户当前额度 * 降额保留系数
        BigDecimal step1 = currentQuotaVal.multiply(retentionRateVal);
        // step2: max(step1, 当前在贷余额, 最低保底额度)
        BigDecimal result = step1.max(currentLoanBalanceVal).max(minQuotaFloorVal);

        // 百位向上取整
        BigDecimal total = result.divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_UP);
        String str = String.valueOf(total.multiply(BigDecimal.valueOf(100)));

        logger.info("降额计算结果：{}", str);
        return str;
    }
}
