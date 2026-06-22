package com.ruleforge.config;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.Parameter;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author Jacky.gao
 * @since 2015年11月26日
 *
 * <p>V6.13.4a: 去除 {@code ApplicationContextAware} — 改 {@code @Component} + 构造注入
 * {@link ListableBeanFactory}(Spring 的 {@code ApplicationContext} 接口继承自
 * {@link ListableBeanFactory},足够提供 {@code getBeanDefinitionNames} + {@code getBean}
 * 用于扫描所有带 {@link ActionBean} 注解的 bean)。
 *
 * <p>V6.13.4f: 显式 {@code @Component("ruleforge.builtInActionLibraryBuilder")} 指定 bean name
 * 匹配 {@code ruleforge-core-context.xml} 的 {@code <property ref="ruleforge.builtInActionLibraryBuilder">}
 * (ResourceLibraryBuilder 注入用)。默认 name(builtInActionLibraryBuilder)会让该 XML ref 断。
 */
@Component("ruleforge.builtInActionLibraryBuilder")
public class BuiltInActionLibraryBuilder {
    private final ListableBeanFactory beanFactory;
    private List<SpringBean> builtInActions = new ArrayList<>();

    public BuiltInActionLibraryBuilder(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public List<SpringBean> getBuiltInActions() {
        return builtInActions;
    }

    @PostConstruct
    void init() {
        // V6.13.4a: 删原 `System.out.println("Load built in actions...")` 启动噪音
        String[] names = beanFactory.getBeanDefinitionNames();
        if (names == null) return;
        for (String name : names) {
            Object obj = null;
            try {
                obj = beanFactory.getBean(name);
            } catch (Exception ex) {
                continue;
            }
            if (obj == null) {
                continue;
            }
            ActionBean aa = obj.getClass().getAnnotation(ActionBean.class);
            if (aa == null) {
                continue;
            }
            SpringBean bean = new SpringBean();
            bean.setId(name);
            bean.setName(aa.name());
            bean.setMethods(buildMethod(obj.getClass().getMethods()));
            builtInActions.add(bean);
        }
    }

    private List<com.ruleforge.model.library.action.Method> buildMethod(Method[] methods) {
        List<com.ruleforge.model.library.action.Method> list = new ArrayList<com.ruleforge.model.library.action.Method>();
        for (Method m : methods) {
            ActionMethod methodAnnotation = m.getAnnotation(ActionMethod.class);
            if (methodAnnotation == null) continue;
            String name = methodAnnotation.name();
            String methodName = m.getName();
            com.ruleforge.model.library.action.Method libMethod = new com.ruleforge.model.library.action.Method();
            libMethod.setMethodName(methodName);
            libMethod.setName(name);
            list.add(libMethod);
            ActionMethodParameter mp = m.getAnnotation(ActionMethodParameter.class);
            List<String> parameterNames = new ArrayList<String>();
            if (mp != null) {
                String pnames[] = mp.names();
                for (String pname : pnames) {
                    parameterNames.add(pname);
                }
            }
            libMethod.setParameters(buildParameters(m, parameterNames));
        }
        return list;
    }

    private List<Parameter> buildParameters(Method m, List<String> parameterNames) {
        List<Parameter> list = new ArrayList<Parameter>();
        Class<?>[] pclasses = m.getParameterTypes();
        for (int i = 0; i < pclasses.length; i++) {
            Class<?> c = pclasses[i];
            String name = "";
            if (parameterNames.size() > i) {
                name = parameterNames.get(i);
            }
            Parameter p = new Parameter();
            p.setName(name);
            p.setType(buildDatatype(c));
            list.add(p);
        }
        return list;
    }

    private Datatype buildDatatype(Class<?> clazz) {
        if (clazz.getName().equals("java.lang.Object")) {
            return Datatype.Object;
        }
        if (clazz.isAssignableFrom(Integer.class) || clazz.isAssignableFrom(int.class)) {
            return Datatype.Integer;
        } else if (clazz.isAssignableFrom(Long.class) || clazz.isAssignableFrom(long.class)) {
            return Datatype.Long;
        } else if (clazz.isAssignableFrom(Double.class) || clazz.isAssignableFrom(double.class)) {
            return Datatype.Double;
        } else if (clazz.isAssignableFrom(Float.class) || clazz.isAssignableFrom(float.class)) {
            return Datatype.Float;
        } else if (clazz.isAssignableFrom(BigDecimal.class)) {
            return Datatype.BigDecimal;
        } else if (clazz.isAssignableFrom(Boolean.class) || clazz.isAssignableFrom(boolean.class)) {
            return Datatype.Boolean;
        } else if (clazz.isAssignableFrom(Date.class)) {
            return Datatype.Date;
        } else if (clazz.isAssignableFrom(List.class)) {
            return Datatype.List;
        } else if (clazz.isAssignableFrom(Set.class)) {
            return Datatype.Set;
        } else if (clazz.isAssignableFrom(Enum.class)) {
            return Datatype.Enum;
        } else if (clazz.isAssignableFrom(Map.class)) {
            return Datatype.Map;
        } else if (clazz.isAssignableFrom(String.class)) {
            return Datatype.String;
        } else if (clazz.isAssignableFrom(Character.class) || clazz.isAssignableFrom(char.class)) {
            return Datatype.Char;
        } else {
            return Datatype.Object;
        }
    }
}
