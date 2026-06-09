package com.ruleforge.console.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.*;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.decision.entity.RuleVariableDef;
import com.ruleforge.decision.repository.DatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author fred
 * @since 2019-09-25 4:43 PM
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalRepositoryImpl implements ExternalRepository {

    private final DatasourceRepository datasourceRepository;

    @Override
    public JSONArray findDataByDate(Date start, Date end) {
        return findDataByDate(start, end, null, null);
    }

    @Override
    public JSONArray findDataByDate(Date start, Date end, String projectId, String packageId) {
        return JSON.parseArray("[{\"com.ruleforge.dev.model.OutputModel\":{\"annualRate\":1,\"appId\":1,\"approvalReason\":\"fhp\"}},{\"com.ruleforge.dev.model.OutputModel\":{\"annualRate\":1,\"appId\":1,\"approvalReason\":\"fhp\"}}]");
    }

    @Override
    public JSONArray findDataByLimit(Integer limit, String projectId, String packageId) {
        return JSON.parseArray("[{\"com.ruleforge.dev.model.OutputModel\":{\"annualRate\":1,\"appId\":1,\"approvalReason\":\"fhp\"}},{\"com.ruleforge.dev.model.OutputModel\":{\"annualRate\":1,\"appId\":1,\"approvalReason\":\"fhp\"}}]");
    }

    @Override
    public JSONObject findDataByAppId(String appId, String projectId) {
        return JSON.parseObject("{\"com.ruleforge.dev.model.OutputModel\":{\"annualRate\":1,\"appId\":\"app01\",\"approvalReason\":\"fhp\"}\n                ,\"SampleClazz\":{\"appId\":\"app01\",\"passFlag\":\"2\"}}");
    }

    @Override
    public boolean saveProcessItem(List<SaveProcessItemDto> saveProcessItemDtoList) {
        log.info("saveProcessItemModelList {}", saveProcessItemDtoList);
        return true;
    }

    @Override
    public List<Variable> generalEntityToVariables(String clazz) {
        List<Variable> variables = new ArrayList<>();
        // 根据 clazz 从 nd_rule_variable_def 表查询对应变量定义，按 sort_no 排序，仅取启用的
        List<RuleVariableDef> defs = datasourceRepository.findVariableDefsByClazz(clazz);

        if (defs != null) {
            for (RuleVariableDef def : defs) {
                Variable v = new Variable();
                v.setName(def.getName());
                v.setLabel(def.getLabel());
                v.setType(resolveDatatype(def.getDatatype()));
                v.setAct(resolveAct(def.getAct()));
                if (def.getDefaultValue() != null) {
                    v.setDefaultValue(def.getDefaultValue());
                }
                variables.add(v);
            }
        }
        return variables;
    }

    @Override
    public VariableLibrary generalEntityToVariableLibrary() {
        VariableLibrary variableLibrary = new VariableLibrary();
        List<VariableCategory> variableCategoryList = new ArrayList<>();

        VariableCategory variableCategory = new VariableCategory();
        variableCategory.setName("样例Clazz");
        variableCategory.setClazz("SampleClazz");
        variableCategory.setType(CategoryType.Custom);
        variableCategory.setVariables(generalEntityToVariables("SampleClazz"));
        variableCategoryList.add(variableCategory);

        variableCategory = new VariableCategory();
        variableCategory.setName("输出数据源model");
        variableCategory.setClazz("com.ruleforge.dev.model.TOutputModel");
        variableCategory.setType(CategoryType.Custom);
        variableCategory.setVariables(generalEntityToVariables("SampleClazz"));
        variableCategoryList.add(variableCategory);

        variableLibrary.setVariableCategories(variableCategoryList);
        return variableLibrary;
    }

    @Override
    public boolean addVariable(String clazz, Variable variable) throws Exception {
        return true;
    }

    // 映射数据库中的 datatype 字段到 RuleForge 的 Datatype
    private Datatype resolveDatatype(String datatype) {
        if (datatype == null || datatype.trim().isEmpty()) {
            return Datatype.String;
        }
        String val = datatype.trim();
        try {
            return Datatype.valueOf(val);
        } catch (Exception ignore) {
            // 兼容大小写与常见别名
            String lower = val.toLowerCase();
            switch (lower) {
                case "string":
                    return Datatype.String;
                case "int":
                case "integer":
                    return Datatype.Integer;
                case "long":
                    return Datatype.Long;
                case "double":
                case "float":
                    return Datatype.Double;
                case "boolean":
                    return Datatype.Boolean;
                case "date":
                    return Datatype.Date;
                case "bigdecimal":
                case "decimal":
                    return Datatype.BigDecimal;
                case "list":
                    return Datatype.List;
                case "object":
                    return Datatype.Object;
                default:
                    return Datatype.String;
            }
        }
    }

    // 映射数据库中的 act 字段到 RuleForge 的 Act
    private Act resolveAct(String act) {
        if (act == null || act.trim().isEmpty()) {
            return Act.InOut;
        }
        String val = act.trim();
        try {
            return Act.valueOf(val);
        } catch (Exception ignore) {
            String lower = val.toLowerCase();
            switch (lower) {
                case "in":
                case "input":
                    return Act.In;
                case "out":
                case "output":
                    return Act.Out;
                default:
                    return Act.InOut;
            }
        }
    }
}
