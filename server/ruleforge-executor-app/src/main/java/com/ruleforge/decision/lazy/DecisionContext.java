package com.ruleforge.decision.lazy;

/**
 * 决策流请求上下文，通过 ThreadLocal 传递请求级参数到 RestDataSourceProvider
 */
public class DecisionContext {

    private static final ThreadLocal<DecisionContext> HOLDER = new ThreadLocal<>();

    private String loanZone;
    private String orbitCode;

    public static DecisionContext current() {
        return HOLDER.get();
    }

    public static DecisionContext init(String loanZone, String orbitCode) {
        DecisionContext ctx = new DecisionContext();
        ctx.loanZone = loanZone;
        ctx.orbitCode = orbitCode;
        HOLDER.set(ctx);
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getLoanZone() {
        return loanZone;
    }

    public String getOrbitCode() {
        return orbitCode;
    }
}
