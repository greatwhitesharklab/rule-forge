package com.ruleforge.console.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.console.servlet.respackage.HttpSessionKnowledgeCache;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.DoTestDto;
import com.ruleforge.console.model.ReadTestDataExcelResult;
import com.ruleforge.console.service.TestService;
import com.ruleforge.console.util.ExcelUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Fred
 * @since 2025/8/17 13:33
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/packageeditor")
@RequiredArgsConstructor
public class TestController {
    public static final String KB_KEY = "_kb";
    public static final String VCS_KEY = "_vcs";
    public static final String IMPORT_EXCEL_DATA = "_import_excel_data";
    public static final String EXPORT_EXCEL_TEST_DATA = "_export_excel_test_data";
    private final KnowledgeBuilder knowledgeBuilder;
    private final HttpSessionKnowledgeCache httpSessionKnowledgeCache;
    private final ExternalRepository externalRepository;
    private final TestService testService;
    private final RuntimeService flowableRuntimeService;

    @PostMapping("/doTest")
    public Map<String, Object> doTest(HttpServletRequest req, @RequestBody DoTestDto data) throws Exception {
        List<Map<String, Object>> list = data.getData().get(0);
        List<VariableCategory> variableCategories = mapToVariableCategories(list);
        Map<VariableCategory, Object> facts = new HashMap<>();
        for (VariableCategory vc : variableCategories) {
            String clazz = vc.getClazz();
            Object entity;
            if (vc.getName().equals(VariableCategory.PARAM_CATEGORY)) {
                entity = new HashMap<String, Object>();
            } else {
                entity = new GeneralEntity(clazz);
            }
            for (Variable var : vc.getVariables()) {
                buildObject(entity, var);
            }
            facts.put(vc, entity);
        }
        String flowId = data.getFlowId();
        long start = System.currentTimeMillis();
        KnowledgeBase knowledgeBase = (KnowledgeBase) httpSessionKnowledgeCache.get(req, KB_KEY);
        if (knowledgeBase == null) {
//            knowledgeBase = buildKnowledgeBase(req);
            return null;
        }
        KnowledgePackage knowledgePackage = knowledgeBase.getKnowledgePackage();
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);
        Map<String, Object> parameters = null;
        for (Object obj : facts.values()) {
            if (!(obj instanceof GeneralEntity) && (obj instanceof HashMap)) {
                parameters = (Map<String, Object>) obj;
            } else {
                session.insert(obj);
            }
        }
        ExecutionResponse response = null;
        if (StringUtils.isNotEmpty(flowId)) {
            // Flow execution via Flowable
            Map<String, Object> flowVariables = new HashMap<>();
            for (Object obj : facts.values()) {
                if (obj instanceof GeneralEntity) {
                    flowVariables.put(((GeneralEntity) obj).getTargetClass(), obj);
                } else if (obj instanceof Map) {
                    flowVariables.putAll((Map<String, Object>) obj);
                }
            }
            ProcessInstance processInstance = flowableRuntimeService.startProcessInstanceByKey(flowId, flowVariables);
            Map<String, Object> resultVars = flowableRuntimeService.getVariables(processInstance.getId());
            // Use session parameters to capture results
            session.getParameters().putAll(resultVars);
            response = session.fireRules(); // No-op just for response wrapper
        } else {
            if (parameters == null) {
                response = session.fireRules();
            } else {
                response = session.fireRules(parameters);
            }
        }
        for (VariableCategory vc : facts.keySet()) {
            Object obj = facts.get(vc);
            if (obj == null) {
                continue;
            }
            if (obj instanceof Map && !(obj instanceof GeneralEntity)) {
                obj = session.getParameters();
            }
            for (Variable var : vc.getVariables()) {
                buildVariableValue(obj, var);
            }
        }
        long end = System.currentTimeMillis();
        long elapse = end - start;
        session.writeLogFile();
        ExecutionResponseImpl res = (ExecutionResponseImpl) response;
        List<RuleInfo> firedRules = res.getFiredRules();
        List<RuleInfo> matchedRules = res.getMatchedRules();
        StringBuffer sb = new StringBuffer();
        sb.append("耗时：").append(elapse).append("ms");
        if (StringUtils.isEmpty(flowId)) {
            sb.append("，");
            sb.append("匹配的规则共").append(matchedRules.size()).append("个");
            if (!matchedRules.isEmpty()) {
                buildRulesName(matchedRules, sb);
            }
            sb.append("；");
            sb.append("触发的规则共").append(firedRules.size()).append("个");
            buildRulesName(firedRules, sb);
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("info", sb.toString());
        resultMap.put("data", variableCategories);
        return resultMap;
    }

    @PostMapping("/doBatchTest")
    public Map<String, Object> doBatchTest(HttpServletRequest req, @RequestBody DoTestDto doTestModel) throws Exception {
        try {
            List<ApplicationAllVariableCategoryMap> rowList = (List<ApplicationAllVariableCategoryMap>) this.httpSessionKnowledgeCache.get(req, IMPORT_EXCEL_DATA);
            if (rowList == null) {
                throw new RuleException("Import excel data for test has expired, please import the excel and try again.");
            }

            if (rowList.isEmpty()) {
                throw new RuleException("Import data is empty.");
            }

            KnowledgeBase knowledgeBase = buildKnowledgeBase(req, doTestModel.getFiles());
            BatchTestFlowMap flowMap = new BatchTestFlowMap();
            String project = doTestModel.getProject();
            String packageId = doTestModel.getPackageId();
            String decisionPath = project.concat("/").concat(packageId);
            Map<String, Object> result = this.testService.doBatchFlowTest(decisionPath, knowledgeBase.getKnowledgePackage(), doTestModel.getFlowId(), rowList, flowMap);
//            // todo 保存结果excel
            ByteArrayOutputStream wb = new ByteArrayOutputStream();
            ExcelUtils.writeExcelWithVariableCategories(knowledgeBase.getResourceLibrary().getVariableCategories(), rowList, null, (BatchTestFlowMap) result.get("flowMap"), wb);
            httpSessionKnowledgeCache.put(req, EXPORT_EXCEL_TEST_DATA, wb);

            return result;
        } catch (Exception e) {
            log.error("doBatchTest error", e);
            throw e;
        }
    }

    @GetMapping("/exportExcelTemplate")
    public void exportExcelTemplate(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        List<VariableCategory> variableCategories = (List<VariableCategory>) httpSessionKnowledgeCache.get(req, VCS_KEY);
        if (variableCategories == null) {
//            KnowledgeBase knowledgeBase = buildKnowledgeBase(req);
//            variableCategories = knowledgeBase.getResourceLibrary().getVariableCategories();
            return;
        }

        try {
            // 设置HTTP响应
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=ruleforge-batch-test-template.xlsx");
            OutputStream respOutputStream = resp.getOutputStream();
            ExcelUtils.writeExcelWithVariableCategories(variableCategories, null, null, null, respOutputStream);

            respOutputStream.flush();
            respOutputStream.close();
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw e;
        }
    }

    @PostMapping("/importExcelTemplate")
    public Map<String, Object> importExcelTemplate(HttpServletRequest req,
                                                   @RequestParam String targetFiles,
                                                   @RequestParam MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);

        List<VariableCategory> variableCategories = (List<VariableCategory>) httpSessionKnowledgeCache.get(req, VCS_KEY);
        if (variableCategories == null) {
            result.put("msg", "取不到变量库");
            return result;
        }

        ReadTestDataExcelResult testDataExcelResult = ExcelUtils.readTestDataExcel(file, variableCategories);
        if (testDataExcelResult.getErrorMsgDtoList().isEmpty()) {
            this.httpSessionKnowledgeCache.put(req, IMPORT_EXCEL_DATA, testDataExcelResult.getApplicationAllVariableCategoryMapList());
            result.put("status", true);
        } else {
            // todo
            ByteArrayOutputStream wb = new ByteArrayOutputStream();
            ExcelUtils.writeExcelWithVariableCategories(variableCategories, testDataExcelResult.getApplicationAllVariableCategoryMapList(), testDataExcelResult.getErrorMsgDtoList(), null, wb);
            httpSessionKnowledgeCache.put(req, EXPORT_EXCEL_TEST_DATA, wb);
            result.put("data", testDataExcelResult.getErrorMsgDtoList());
            result.put("msg", "导入excel有错误");
        }

        return result;
    }

    @PostMapping("/exportBatchTestExcel")
    public void exportBatchTestExcel(HttpServletRequest req, HttpServletResponse resp, @RequestParam String prefix) throws Exception {
        ByteArrayOutputStream wb = (ByteArrayOutputStream) this.httpSessionKnowledgeCache.get(req, EXPORT_EXCEL_TEST_DATA);
        resp.setContentType("application/x-xls");
        resp.setHeader("Content-Disposition", String.format("attachment; filename=%stest_result.xlsx", prefix));
        OutputStream outputStream = resp.getOutputStream();
        wb.writeTo(outputStream);
        outputStream.flush();
        outputStream.close();
    }

    @PostMapping("/exportExcelData")
    public void exportExcelData(HttpServletRequest req, HttpServletResponse resp,
                                @RequestParam("startTime") String startDateStr,
                                @RequestParam("endTime") String endDateStr,
                                @RequestParam(required = false) String projectName,
                                @RequestParam(required = false) String packageName) throws Exception {
        List<VariableCategory> variableCategories = (List<VariableCategory>) httpSessionKnowledgeCache.get(req, VCS_KEY);

        if (variableCategories == null) {
//            KnowledgeBase knowledgeBase = buildKnowledgeBase(req);
//            variableCategories = knowledgeBase.getResourceLibrary().getVariableCategories();
            return;
        }

        try {
            // 获取历史数据
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date startDate = sdf.parse(startDateStr);
            Date endDate = sdf.parse(endDateStr);
            JSONArray data = this.externalRepository.findDataByDate(startDate, endDate, projectName, packageName);

            // 创建Sheet数据映射
            Map<String, List<List<Object>>> sheetDataMap = new HashMap<>();
            for (VariableCategory vc : variableCategories) {
                List<List<Object>> sheetData = createHistorySheetData(vc, data);
                sheetDataMap.put(vc.getName(), sheetData);
            }

            // 使用ExcelUtils写入Excel
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ExcelUtils.writeExcel(outputStream, sheetDataMap);

            resp.setContentType("application/x-xls");
            resp.setHeader("Content-Disposition", "attachment; filename=ruleforge-batch-data.xlsx");
            int dataSize = 0;
            if (data != null) {
                dataSize = data.size();
            }
            resp.setHeader("data-size", String.format("%d", dataSize));
            OutputStream respOutputStream = resp.getOutputStream();
            outputStream.writeTo(respOutputStream);

            respOutputStream.flush();
            respOutputStream.close();
        } catch (Exception e) {
            log.error("导出Excel数据失败", e);
            throw e;
        }
    }

    /**
     * 创建历史数据Sheet
     */
    private List<List<Object>> createHistorySheetData(VariableCategory vc, JSONArray data) {
        List<List<Object>> sheetData = new ArrayList<>();

        // 创建表头
        List<Object> headers = new ArrayList<>();
        List<Variable> variables = vc.getVariables();
        for (Variable var : variables) {
            headers.add(var.getLabel());
        }
        sheetData.add(headers);

        // 创建数据行
        if (data != null) {
            for (Object obj : data.toArray()) {
                JSONObject jobj = (JSONObject) obj;
                Object dataSource = jobj.get(vc.getClazz());
                if (dataSource == null) {
                    continue;
                }

                JSONObject dataSourceJobj = (JSONObject) dataSource;
                List<Object> row = new ArrayList<>();

                for (Variable var : variables) {
                    Object value = "";
                    if (dataSourceJobj.get(var.getName()) != null) {
                        switch (var.getType()) {
                            case Integer:
                                value = dataSourceJobj.getInteger(var.getName());
                                break;
                            case Double:
                                value = dataSourceJobj.getDouble(var.getName());
                                break;
                            case Long:
                                value = dataSourceJobj.getLong(var.getName());
                                break;
                            case BigDecimal:
                                value = dataSourceJobj.getBigDecimal(var.getName());
                                break;
                            case String:
                            default:
                                value = dataSourceJobj.getString(var.getName());
                        }
                    }
                    row.add(value);
                }
                sheetData.add(row);
            }
        }

        return sheetData;
    }

    private void instanceChildObject(Object obj, String propertyName) {
        int pointIndex = propertyName.indexOf(".");
        if (pointIndex == -1) {
            return;
        }
        String name = propertyName.substring(0, pointIndex);
        propertyName = propertyName.substring(pointIndex + 1);
        try {
            Object instance = PropertyUtils.getProperty(obj, name);
            if (instance != null) {
                instanceChildObject(instance, propertyName);
                return;
            }
            Object targetEntity = new GeneralEntity(name);
            PropertyUtils.setProperty(obj, name, targetEntity);
            instanceChildObject(targetEntity, propertyName);
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    private List<GeneralEntity> buildList(String value) {
        try {
            List<GeneralEntity> result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(value, HashMap.class);
            if (map.containsKey("rows")) {
                List<Object> list = (List<Object>) map.get("rows");
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
                        entity.putAll((Map) obj);
                        result.add(entity);
                    }
                }
                return result;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    private Set<GeneralEntity> buildSet(String value) {
        try {
            Set<GeneralEntity> result = new HashSet<>();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(value, HashMap.class);
            if (map.containsKey("rows")) {
                List<Object> list = (List<Object>) map.get("rows");
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
                        entity.putAll((Map) obj);
                        result.add(entity);
                    }
                }
                return result;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    private void buildObject(Object obj, Variable var) {
        String name = var.getName();
        if (name.contains(".")) {
            instanceChildObject(obj, name);
        }
        String defaultValue = var.getDefaultValue();
        if (StringUtils.isBlank(defaultValue)) {
            return;
        }
        Datatype type = var.getType();
        if (type.equals(Datatype.List)) {
            Utils.setObjectProperty(obj, name, buildList(defaultValue));
        } else if (type.equals(Datatype.Set)) {
            Utils.setObjectProperty(obj, name, buildSet(defaultValue));
        } else if (type.equals(Datatype.Map)) {
        } else {
            Object value = type.convert(defaultValue);
            Utils.setObjectProperty(obj, name, value);
        }
    }

    private void buildRulesName(List<RuleInfo> firedRules, StringBuffer sb) {
        sb.append("：");
        int i = 0;
        for (RuleInfo rule : firedRules) {
            if (i > 0) {
                sb.append("，");
            }
            sb.append(rule.getName());
            i++;
        }
    }

    private void buildVariableValue(Object object, Variable var) {
        String name = var.getName();
        Object value = Utils.getObjectProperty(object, name);
        if (value != null) {
            Datatype type = var.getType();
            if (type.equals(Datatype.List) || type.equals(Datatype.Set)) {
                //var.setDefaultValue(value.toString());
            } else {
                String str = type.convertObjectToString(value);
                var.setDefaultValue(str);
            }
        }
    }

    private List<VariableCategory> mapToVariableCategories(List<Map<String, Object>> mapList) {
        List<VariableCategory> list = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            VariableCategory category = new VariableCategory();
            list.add(category);
            for (String key : map.keySet()) {
                switch (key) {
                    case "name":
                        category.setName((String) map.get(key));
                        break;
                    case "clazz":
                        category.setClazz((String) map.get(key));
                        break;
                    case "variables":
                        List<Map<String, Object>> variables = (List<Map<String, Object>>) map.get(key);
                        if (variables != null) {
                            for (Map<String, Object> m : variables) {
                                Variable var = new Variable();
                                category.addVariable(var);
                                for (String varName : m.keySet()) {
                                    switch (varName) {
                                        case "name":
                                            var.setName((String) m.get(varName));
                                            break;
                                        case "label":
                                            var.setLabel((String) m.get(varName));
                                            break;
                                        case "type":
                                            var.setType(Datatype.valueOf((String) m.get(varName)));
                                            break;
                                        case "defaultValue":
                                            var.setDefaultValue((String) m.get(varName));
                                            break;
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        }
        return list;
    }

    private KnowledgeBase buildKnowledgeBase(HttpServletRequest req, String files) throws RuleException {
        files = Utils.decodeURL(files);
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        String[] paths = files.split(";");
        for (String path : paths) {
            String[] subPaths = path.split(",");
            path = subPaths[0];
            String version = null;
            if (subPaths.length > 1) {
                version = subPaths[1];
            }
            resourceBase.addResource(path, version, true);
        }
        KnowledgeBase knowledgeBase = this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
        this.httpSessionKnowledgeCache.remove(req, KB_KEY);
        this.httpSessionKnowledgeCache.put(req, KB_KEY, knowledgeBase);
        return knowledgeBase;
    }

}
