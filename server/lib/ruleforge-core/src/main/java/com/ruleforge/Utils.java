package com.ruleforge;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯函数工具类:URL/编解码、对象属性、类型推断。
 * <p>
 * 引擎运行期所需的 Spring 静态状态(bean/function/debug)已迁出:
 * 见 {@link com.ruleforge.plugin.EnginePluginRegistry} + {@link com.ruleforge.runtime.EngineContext}。
 */
public final class Utils {
    private Utils() {
    }

    // V5.89 — Class<?> -> (propertyName -> Getter Method) cache, 替换 apache commons
    // PropertyUtils 反射链。Outer keyed on Class (JVM 生命周期稳定);inner 用
    // computeIfAbsent 原子 publish resolve 结果;NO_GETTER sentinel 标记"无 getter"
    // 避免重复扫描 Class.getMethod 链。
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>>
        GETTER_CACHE = new ConcurrentHashMap<>();

    /** V5.89 — never invoked at runtime; NO_GETTER identity marker (see static init). */
    @SuppressWarnings("unused")
    private static String __v589NoGetterSentinel() { return ""; }

    private static final Method NO_GETTER;
    static {
        try {
            NO_GETTER = Utils.class.getDeclaredMethod("__v589NoGetterSentinel");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String decodeURL(String str) {
        if (StringUtils.isBlank(str)) {
            return str;
        }
        try {
            str = URLDecoder.decode(str, "utf-8");
            return str;
        } catch (Exception e) {
            return str;
        }
    }

    public static String decodeContent(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        try {
            content = URLDecoder.decode(content, "utf-8");
            return content;
        } catch (Exception ex) {
            return content;
        }
    }

    public static String encodeURL(String str) {
        if (StringUtils.isBlank(str)) {
            return str;
        }
        try {
            return URLEncoder.encode(str, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuleException(e);
        }
    }

    public static String toUTF8(String text) {
        try {
            if (text == null) {
                return null;
            }
            byte[] fileBytes = text.getBytes("iso8859-1");
            boolean isiso = text.equals(new String(fileBytes, "iso8859-1"));
            if (isiso) {
                text = new String(fileBytes, "utf-8");
            }
            isiso = text.equals(new String(text.getBytes("iso8859-1"), "iso8859-1"));
            if (isiso) {
                text = new String(fileBytes, "utf-8");
            }
            return text;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    /**
     * V5.89 — 直接反射 + 缓存,替换 apache commons PropertyUtils 链。
     *
     * <p>原实现每次调用都走 PropertyUtilsBean.getSimpleProperty ->
     * DefaultResolver.next -> getNestedProperty -> getPropertyDescriptor 链路,
     * JFR 35s 抓出 240+ sample(12% of post-V5.88 hot path)。经 audit 27+ caller
     * 全部传简单属性名(无 nested/indexed/mapped 形态),改用 Class.getMethod +
     * Method.invoke + ConcurrentHashMap 缓存直接命中。
     *
     * <p>实现:
     * <ol>
     *   <li>Map fast path: object instanceof Map 直接 ((Map) obj).get(property),
     *       零反射。覆盖 GeneralEntity extends HashMap 常见 case。</li>
     *   <li>POJO path: Class -> (propertyName -> Getter Method) 二级 cache,
     *       inner map 用 computeIfAbsent 原子 publish;找不到 getter 用 NO_GETTER
     *       sentinel 标记,避免重复扫描 Class。</li>
     *   <li>Method.invoke 直接调;InvocationTargetException 拆包成底层 cause,
     *       对齐 PropertyUtils 抛底层 cause 行为。</li>
     * </ol>
     *
     * <p>线程安全: KnowledgeSessionImpl 多线程 rete 评估,CHM 在并发 read-heavy
     * 场景下 lock-free get,只有首次 cache miss 走 computeIfAbsent brief lock。
     */
    public static Object getObjectProperty(Object object, String property) {
        if (object == null) {
            throw new RuleException("Cannot read property [" + property + "] of null object.");
        }
        // 1) Map fast path — GeneralEntity extends HashMap 走这里, 零反射。
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(property);
        }
        // 2) POJO path — 反射 cache。
        Class<?> clazz = object.getClass();
        ConcurrentHashMap<String, Method> propMap = GETTER_CACHE.get(clazz);
        if (propMap == null) {
            propMap = GETTER_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        }
        Method getter = propMap.get(property);
        if (getter == null) {
            getter = propMap.computeIfAbsent(property, p -> resolveGetter(clazz, p));
        }
        if (getter == NO_GETTER) {
            throw new RuleException("No readable property [" + property
                + "] on class " + clazz.getName());
        }
        try {
            return getter.invoke(object);
        } catch (IllegalAccessException e) {
            throw new RuleException(e);
        } catch (InvocationTargetException e) {
            // 对齐 PropertyUtils 语义: 抛底层 cause, 而不是 InvocationTargetException wrapper。
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            // RuleException ctor 只吃 Exception, Throwable cause 转 Exception
            // (PropertyUtils 自己也这么干, 把 InvocationTargetException 整包给 RuleException ctor)
            if (cause instanceof Exception) {
                throw new RuleException((Exception) cause);
            }
            throw new RuleException(e);
        }
    }

    /**
     * V5.89 — 找 POJO getter, PropertyUtils 语义对齐:
     *   1. {@code get + Capitalize(property)} (no-arg, instance)
     *   2. {@code is + Capitalize(property)} (boolean 返回)
     * 找不到返回 NO_GETTER sentinel。
     * 注: V5.89 故意收窄到 simple-name 形态;nested/indexed/mapped 形态
     * (a.b / a[0] / a(key)) 不再支持,经 audit 27+ call sites 均未使用。
     */
    private static Method resolveGetter(Class<?> clazz, String property) {
        String cap = capitalize(property);
        try {
            return clazz.getMethod("get" + cap);
        } catch (NoSuchMethodException ignore) {
            // fall through
        }
        try {
            Method m = clazz.getMethod("is" + cap);
            if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                return m;
            }
        } catch (NoSuchMethodException ignore) {
            // fall through
        }
        return NO_GETTER;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (Character.isUpperCase(c)) {
            return s;
        }
        char[] arr = s.toCharArray();
        arr[0] = Character.toUpperCase(c);
        return new String(arr);
    }

    public static void setObjectProperty(Object object, String property, Object value) {
        try {
            BeanUtils.setProperty(object, property, value);
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    public static Datatype getDatatype(Object obj) {
        Datatype datatype;
        if (obj == null) {
            datatype = Datatype.Object;
        } else if (obj instanceof Integer) {
            datatype = Datatype.Integer;
        } else if (obj instanceof Long) {
            datatype = Datatype.Long;
        } else if (obj instanceof Double) {
            datatype = Datatype.Double;
        } else if (obj instanceof Float) {
            datatype = Datatype.Float;
        } else if (obj instanceof BigDecimal) {
            datatype = Datatype.BigDecimal;
        } else if (obj instanceof Boolean) {
            datatype = Datatype.Boolean;
        } else if (obj instanceof Date) {
            datatype = Datatype.Date;
        } else if (obj instanceof List) {
            datatype = Datatype.List;
        } else if (obj instanceof Set) {
            datatype = Datatype.Set;
        } else if (obj instanceof Enum) {
            datatype = Datatype.Enum;
        } else if (obj instanceof Map) {
            datatype = Datatype.Map;
        } else if (obj instanceof String) {
            datatype = Datatype.String;
        } else if (obj instanceof Character) {
            datatype = Datatype.Char;
        } else {
            datatype = Datatype.Object;
        }
        return datatype;
    }

    public static BigDecimal toBigDecimal(Object val) {
        try {
            if (val instanceof BigDecimal) {
                return (BigDecimal) val;
            } else if (val == null) {
                throw new IllegalArgumentException("Null can not to BigDecimal.");
            } else if (val instanceof String) {
                String str = (String) val;
                if ("".equals(str.trim())) {
                    return BigDecimal.valueOf(0);
                }
                str = str.trim();
                return new BigDecimal(str);
            } else if (val instanceof Number) {
                return new BigDecimal(val.toString());
            } else if (val instanceof Character) {
                int i = ((Character) val).charValue();
                return new BigDecimal(i);
            }
        } catch (Exception ex) {
            throw new NumberFormatException("Can not convert " + val + " to number.");
        }

        throw new IllegalArgumentException(val.getClass().getName() + " can not to BigDecimal.");
    }
}
