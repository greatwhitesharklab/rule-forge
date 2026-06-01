package com.ruleforge.runtime;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.Parameter;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author Jacky.gao
 * @since 2015年11月26日
 */
public class BuiltInActionLibraryBuilder implements ApplicationContextAware {
    private List<SpringBean> builtInActions = new ArrayList<>();

    public List<SpringBean> getBuiltInActions() {
        return builtInActions;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println("Load built in actions...");
        String[] names = applicationContext.getBeanDefinitionNames();
        if (names == null) return;
        for (String name : names) {
            Object obj = null;
            try {
                obj = applicationContext.getBean(name);
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
