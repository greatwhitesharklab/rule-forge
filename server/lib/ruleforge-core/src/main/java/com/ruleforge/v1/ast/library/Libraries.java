package com.ruleforge.v1.ast.library;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V1 四库容器(V7.4.1)。执行时 RuleAsset + Libraries 一起传,V1FlowRunner 按 type 解析:
 * vl→Schema(schemaRef 派生),pl/cl→会话参数 Map,al→自定义动作(V7.4.1 al 实施)。
 *
 * <p>项目级共享:一个项目一套库,多 RuleAsset 引用同一 vl(schemaRef)/pl/cl。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Libraries {
    /** 变量库(vl):fact schema 定义,RuleAsset.schemaRef 引用。 */
    private Library vl;
    /** 常量库(cl):CEL constant.{key}。 */
    private Library cl;
    /** 参数库(pl):CEL param.{key}。 */
    private Library pl;
    /** 动作库(al):自定义动作(V7.4.1)。 */
    private Library al;

    public Library getVl() {
        return vl;
    }

    public void setVl(Library vl) {
        this.vl = vl;
    }

    public Library getCl() {
        return cl;
    }

    public void setCl(Library cl) {
        this.cl = cl;
    }

    public Library getPl() {
        return pl;
    }

    public void setPl(Library pl) {
        this.pl = pl;
    }

    public Library getAl() {
        return al;
    }

    public void setAl(Library al) {
        this.al = al;
    }
}
