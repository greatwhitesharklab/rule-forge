package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceType;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.model.library.constant.ConstantLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rule.Library;
import com.ruleforge.runtime.BuiltInActionLibraryBuilder;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Jacky.gao
 * @author fred
 * 2015年2月16日
 */
public class ResourceLibraryBuilder extends AbstractBuilder {
    private BuiltInActionLibraryBuilder builtInActionLibraryBuilder;

    @SuppressWarnings("unchecked")
    public ResourceLibrary buildResourceLibrary(Collection<Library> libraries) {
        return buildResourceLibrary(libraries, false);
    }

    @SuppressWarnings("unchecked")
    public ResourceLibrary buildResourceLibrary(Collection<Library> libraries, boolean isContainSnapshot) {
        if (libraries == null) {
            libraries = Collections.EMPTY_LIST;
        }
        List<ConstantLibrary> constantLibraryLibs = new ArrayList<>();
        List<ActionLibrary> actionLibraryLibs = new ArrayList<>();
        List<VariableLibrary> variableCategoryLibs = new ArrayList<>();
        List<VariableCategory> parameterVariableCategories = new ArrayList<>();
        ResourceBase resourceBase = newResourceBase();
        for (Library lib : libraries) {
            resourceBase.addResource(lib.getPath(), lib.getVersion(), isContainSnapshot);
        }
        for (Resource resource : resourceBase.getResources()) {
            String content = resource.getContent();
            Element root = parseResource(content);
            for (ResourceBuilder<?> builder : resourceBuilders) {
                if (!builder.support(root)) {
                    continue;
                }
                Object object = builder.build(root);
                ResourceType type = builder.getType();
                if (type.equals(ResourceType.ActionLibrary)) {
                    ActionLibrary al = (ActionLibrary) object;
                    actionLibraryLibs.add(al);
                } else if (type.equals(ResourceType.VariableLibrary)) {
                    VariableLibrary vl = (VariableLibrary) object;
                    variableCategoryLibs.add(vl);
                } else if (type.equals(ResourceType.ConstantLibrary)) {
                    ConstantLibrary cl = (ConstantLibrary) object;
                    constantLibraryLibs.add(cl);
                } else if (type.equals(ResourceType.ParameterLibrary)) {
                    VariableCategory category = (VariableCategory) object;
                    parameterVariableCategories.add(category);
                }
                break;
            }
        }
        if (!parameterVariableCategories.isEmpty()) {
            VariableCategory category = parameterVariableCategories.get(0);
            for (VariableCategory vc : parameterVariableCategories) {
                if (vc.equals(category)) {
                    continue;
                }
                if (vc.getVariables() == null) {
                    continue;
                }
                for (Variable v : vc.getVariables()) {
                    category.addVariable(v);
                }
            }
            VariableLibrary parameterLib = new VariableLibrary();
            parameterLib.addVariableCategory(category);
            variableCategoryLibs.add(parameterLib);
        }
        List<SpringBean> builtInActions = builtInActionLibraryBuilder.getBuiltInActions();
        if (!builtInActions.isEmpty()) {
            ActionLibrary al = new ActionLibrary();
            al.setSpringBeans(builtInActions);
            actionLibraryLibs.add(al);
        }
        return new ResourceLibrary(variableCategoryLibs, actionLibraryLibs, constantLibraryLibs);
    }

    public void setBuiltInActionLibraryBuilder(
            BuiltInActionLibraryBuilder builtInActionLibraryBuilder) {
        this.builtInActionLibraryBuilder = builtInActionLibraryBuilder;
    }
}
