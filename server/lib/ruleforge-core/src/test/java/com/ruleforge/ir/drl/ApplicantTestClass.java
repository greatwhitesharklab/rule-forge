package com.ruleforge.ir.drl;

/**
 * V5.77 — DrlImportGrammarTest 反射测试 fixture。
 *
 * <p>同包下放一个简单 POJO,反射注册用。public 字段 a/b/c 顺序确定,
 * 测试只 assert "至少 1 个字段" 不锁具体顺序。
 */
public class ApplicantTestClass {
    public String a;
    public int b;
    public boolean c;
}
