package com.ruleforge.console.repository;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableLibrary;

import java.util.Date;
import java.util.List;

public interface ExternalRepository {

    JSONArray findDataByDate(Date start, Date end);

    JSONArray findDataByDate(Date start, Date end, String projectId, String packageId);

    JSONArray findDataByLimit(Integer limit, String projectId, String packageId);

    JSONObject findDataByAppId(String appId, String projectId);

    boolean saveProcessItem(List<SaveProcessItemDto> saveProcessItemDtoList);

    List<Variable> generalEntityToVariables(String clazz);

    VariableLibrary generalEntityToVariableLibrary();

    boolean addVariable(String clazz, Variable variable) throws Exception;
}
