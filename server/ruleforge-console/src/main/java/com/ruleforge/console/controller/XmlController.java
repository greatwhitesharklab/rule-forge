package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.parse.deserializer.*;
import com.ruleforge.runtime.BuiltInActionLibraryBuilder;
import com.ruleforge.console.storage.RepositoryResourceProvider;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/xml")
@RequiredArgsConstructor
public class XmlController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final ActionLibraryDeserializer actionLibraryDeserializer;
    private final VariableLibraryDeserializer variableLibraryDeserializer;
    private final ConstantLibraryDeserializer constantLibraryDeserializer;
    private final RuleSetDeserializer ruleSetDeserializer;
    private final DecisionTableDeserializer decisionTableDeserializer;
    private final ScriptDecisionTableDeserializer scriptDecisionTableDeserializer;
    private final DecisionTreeDeserializer decisionTreeDeserializer;
    private final ScorecardDeserializer scorecardDeserializer;
    private final ComplexScorecardDeserializer complexScorecardDeserializer;
    private final ParameterLibraryDeserializer parameterLibraryDeserializer;
    private final BuiltInActionLibraryBuilder builtInActionLibraryBuilder;
    private List<Deserializer<?>> deserializers = new ArrayList<>(10);

    @PostConstruct
    public void init() {
        this.deserializers = Lists.newArrayList(
                this.actionLibraryDeserializer,
                this.variableLibraryDeserializer,
                this.constantLibraryDeserializer,
                this.ruleSetDeserializer,
                this.decisionTableDeserializer,
                this.scriptDecisionTableDeserializer,
                this.decisionTreeDeserializer,
                this.parameterLibraryDeserializer,
                this.scorecardDeserializer,
                this.complexScorecardDeserializer
        );
    }

    @PostMapping
    public List<Object> loadXml(@RequestParam String files) throws ServletException, IOException {
        List<Object> result = new ArrayList<>();

        boolean isaction = false;
        if (files != null) {
            if (files.startsWith("builtinactions")) {
                isaction = true;
            } else {
                files = Utils.decodeURL(files);
                String[] paths = files.split(";");
                for (String path : paths) {
                    path = Utils.toUTF8(path);
                    String[] subpaths = path.split(",");
                    path = subpaths[0];
                    String version = null;
                    if (subpaths.length == 2) {
                        version = subpaths[1];
                    }
                    try {
                        InputStream inputStream = null;
                        if (StringUtils.isEmpty(version)) {
                            inputStream = ruleforgeRepositoryService.readFile(path, null);
                        } else {
                            inputStream = ruleforgeRepositoryService.readFile(path, version);
                        }
                        try {
                            Element element = parseXml(inputStream);
                            for (Deserializer<?> des : deserializers) {
                                if (des.support(element)) {
                                    result.add(des.deserialize(element));
                                    if (des instanceof ActionLibraryDeserializer) {
                                        isaction = true;
                                    }
                                    break;
                                }
                            }
                        } finally {
                            inputStream.close();
                        }
                    } catch (Exception ex) {
                        throw new RuleException(ex);
                    }
                }
            }
        }
        if (isaction) {
            List<SpringBean> beans = builtInActionLibraryBuilder.getBuiltInActions();
            if (!beans.isEmpty()) {
                ActionLibrary al = new ActionLibrary();
                al.setSpringBeans(beans);
                result.add(al);
            }
        }
        return result;
    }

    protected Element parseXml(InputStream stream) {
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(stream);
            return document.getRootElement();
        } catch (DocumentException e) {
            throw new RuleException(e);
        }
    }

}
