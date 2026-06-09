package com.ruleforge.console.controller;

import com.ruleforge.model.ExposeAction;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.action.Method;
import com.ruleforge.model.library.action.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/${ruleforge.root.path}/actioneditor")
@RequiredArgsConstructor
public class ActionEditorController {

    private final ApplicationContext applicationContext;

    @PostMapping("/loadMethods")
    public List<Method> loadMethods(@RequestParam String beanId) {
        Object o = applicationContext.getBean(beanId);
        Object bean = getTarget(o);
        List<Method> list = new ArrayList<>();
        java.lang.reflect.Method[] methods = bean.getClass().getMethods();
        for (java.lang.reflect.Method m : methods) {
            ExposeAction action = m.getAnnotation(ExposeAction.class);
            if (action == null) {
                continue;
            }
            Method method = new Method();
            method.setMethodName(m.getName());
            method.setName(action.value());
            method.setParameters(buildParameters(m));
            list.add(method);
        }
        return list;
    }

    private Object getTarget(Object proxy) {
        if (!AopUtils.isAopProxy(proxy)) {
            return proxy;
        }
        try {
            if (AopUtils.isJdkDynamicProxy(proxy)) {
                return getJdkDynamicProxyTargetObject(proxy);
            } else {
                return getCglibProxyTargetObject(proxy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object getCglibProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        h.setAccessible(true);
        Object dynamicAdvisedInterceptor = h.get(proxy);
        Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
        advised.setAccessible(true);
        return ((AdvisedSupport) advised.get(dynamicAdvisedInterceptor)).getTargetSource().getTarget();
    }

    private Object getJdkDynamicProxyTargetObject(Object proxy) throws Exception {
        Field h = proxy.getClass().getSuperclass().getDeclaredField("h");
        h.setAccessible(true);
        AopProxy aopProxy = (AopProxy) h.get(proxy);
        Field advised = aopProxy.getClass().getDeclaredField("advised");
        advised.setAccessible(true);
        return ((AdvisedSupport) advised.get(aopProxy)).getTargetSource().getTarget();
    }

    private List<Parameter> buildParameters(java.lang.reflect.Method m) {
        List<Parameter> parameters = new ArrayList<>();
        Class<?>[] classes = m.getParameterTypes();
        for (int i = 0; i < classes.length; i++) {
            Parameter p = new Parameter();
            p.setName("参数" + i);
            p.setType(buildDatatype(classes[i]));
            parameters.add(p);
        }
        return parameters;
    }

    private Datatype buildDatatype(Class<?> clazz) {
        if (clazz.equals(String.class)) return Datatype.String;
        else if (clazz.equals(BigDecimal.class)) return Datatype.BigDecimal;
        else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) return Datatype.Boolean;
        else if (clazz.equals(Date.class)) return Datatype.Date;
        else if (clazz.equals(Double.class) || clazz.equals(double.class)) return Datatype.Double;
        else if (Enum.class.isAssignableFrom(clazz)) return Datatype.Enum;
        else if (clazz.equals(Float.class) || clazz.equals(float.class)) return Datatype.Float;
        else if (clazz.equals(Integer.class) || clazz.equals(int.class)) return Datatype.Integer;
        else if (clazz.equals(Character.class) || clazz.equals(char.class)) return Datatype.Char;
        else if (List.class.isAssignableFrom(clazz)) return Datatype.List;
        else if (clazz.equals(Long.class) || clazz.equals(long.class)) return Datatype.Long;
        else if (Map.class.isAssignableFrom(clazz)) return Datatype.Map;
        else if (Set.class.isAssignableFrom(clazz)) return Datatype.Set;
        else return Datatype.Object;
    }
}
