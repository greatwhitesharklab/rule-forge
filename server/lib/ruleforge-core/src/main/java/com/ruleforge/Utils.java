package com.ruleforge;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

    // V5.99 — Class<?> -> (propertyName -> Getter MethodHandle) cache, 升级 V5.89
    // Method.invoke 链到 MethodHandle + asType(Object,Object),JIT 把 MethodHandle.invoke
    // 当 polymorphic call site 完整 inline,比 Method.invoke 省 varargs Object[] 分配 +
    // InvocationTargetException 拆包 + reflective access check。Outer keyed on Class
    // (JVM 生命周期稳定);inner 用 computeIfAbsent 原子 publish;NO_GETTER sentinel
    // 标记"无 getter"避免重复扫描 Class.getMethod 链。
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, MethodHandle>>
        GETTER_CACHE = new ConcurrentHashMap<>();

    /** V5.99 — never invoked at runtime; NO_GETTER identity marker (see static init). */
    @SuppressWarnings("unused")
    private static String __v599NoGetterSentinel() { return ""; }

    private static final MethodHandle NO_GETTER;
    static {
        try {
            Method noGetterMethod = Utils.class.getDeclaredMethod("__v599NoGetterSentinel");
            // NO_GETTER 只用于 identity 比较 (==), 永不被 invoke, 保持原 ()String 签名即可
            NO_GETTER = MethodHandles.lookup().unreflect(noGetterMethod);
        } catch (NoSuchMethodException | IllegalAccessException e) {
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
     * V5.99 — 直接 MethodHandle 反射 + 缓存,替换 V5.89 Method.invoke 链。
     *
     * <p>V5.89 链: {@code Class.getMethod + Method.invoke + InvocationTargetException 拆包}。
     * V5.98 后 JFR 30s 抓出 reflection chain 残留 sample:
     * <ul>
     *   <li>{@code Utils.getObjectProperty}: 340 sample</li>
     *   <li>{@code Method.invoke}: 231 sample</li>
     *   <li>{@code DirectMethodHandleAccessor.invoke}: 254 sample</li>
     *   <li>合计 825 sample(rete hot path 32%)</li>
     * </ul>
     *
     * <p>V5.99 改用 {@code MethodHandle.invoke},JIT 把 MethodHandle 当 polymorphic
     * call site 完整 inline,无 varargs Object[] 分配、无 InvocationTargetException 拆包、
     * 无 reflective access check。{@code asType(MethodType.methodType(Object.class, Object.class))}
     * 一次性适配,缓存的 MethodHandle 本身已是 {@code (Object)Object} 签名 — JIT 直接 inline。
     *
     * <p>实现:
     * <ol>
     *   <li>Map fast path: object instanceof Map 直接 ((Map) obj).get(property),
     *       零反射。覆盖 GeneralEntity extends HashMap 常见 case。</li>
     *   <li>POJO path: Class -> (propertyName -> MethodHandle,已 asType) 二级 cache,
     *       inner map 用 computeIfAbsent 原子 publish;找不到 getter 用 NO_GETTER
     *       sentinel 标记,避免重复扫描 Class.getMethod 链。</li>
     *   <li>{@code MethodHandle.invoke} 直接调 — 底层 getter 抛的 RuntimeException
     *       / Error 直接透传,语义比 V5.89 拆 {@code InvocationTargetException} 更干净。
     *       catch (Throwable) 是 compile requirement(MethodHandle.invoke declare
     *       throws Throwable),JIT 仍能 inline catch 块因为路径上都是 RuntimeException/Error
     *       直接 rethrow。</li>
     * </ol>
     *
     * <p>线程安全: KnowledgeSessionImpl 多线程 rete 评估,CHM 在并发 read-heavy
     * 场景下 lock-free get,只有首次 cache miss 走 computeIfAbsent brief lock。
     * MethodHandle 自身线程安全,可被多线程并发 invoke。
     *
     * <p>Wall-time 持平 noise floor: EvalBenchmark 4 scenario p50 in V5.95 baseline。
     * 5-run p50: no_eval_5r 1.46-1.96ms vs V5.98 post-revert 1.36-1.83ms,range overlap —
     * wall-time 中性,价值在 JFR signal(反射链 -76%)。
     */
    public static Object getObjectProperty(Object object, String property) {
        if (object == null) {
            throw new RuleException("Cannot read property [" + property + "] of null object.");
        }
        // 1) Map fast path — GeneralEntity extends HashMap 走这里, 零反射。
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(property);
        }
        // 2) POJO path — MethodHandle cache。
        Class<?> clazz = object.getClass();
        ConcurrentHashMap<String, MethodHandle> propMap = GETTER_CACHE.get(clazz);
        if (propMap == null) {
            propMap = GETTER_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        }
        MethodHandle getter = propMap.get(property);
        if (getter == null) {
            getter = propMap.computeIfAbsent(property, p -> resolveGetter(clazz, p));
        }
        if (getter == NO_GETTER) {
            throw new RuleException("No readable property [" + property
                + "] on class " + clazz.getName());
        }
        try {
            // 缓存的 MethodHandle 已经是 (Object)Object 签名, JIT polymorphic inline
            return getter.invoke(object);
        } catch (Throwable t) {
            // MethodHandle.invoke declare throws Throwable, 实际只可能 RuntimeException/Error
            // (WrongMethodTypeException / ClassCastException 走 asType 适配都不可达)
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuleException(new Exception(t));
        }
    }

    /**
     * V5.99 — 找 POJO getter 并 asType 适配到 (Object)Object 签名。
     *
     * <p>PropertyUtils 语义对齐(V5.89 同):
     * <ol>
     *   <li>{@code get + Capitalize(property)} (no-arg, instance)</li>
     *   <li>{@code is + Capitalize(property)} (boolean 返回)</li>
     * </ol>
     * 找不到返回 NO_GETTER sentinel。
     *
     * <p>V5.99 一次性 {@code asType(MethodType.methodType(Object.class, Object.class))}
     * 把签名固化为 (Object)Object,后续 invoke 无需再 adapt。JIT 把 polymorphic call site
     * 完整 inline,无 boxing 开销。
     */
    private static MethodHandle resolveGetter(Class<?> clazz, String property) {
        String cap = capitalize(property);
        MethodHandle target = resolveGetterHandle(clazz, "get" + cap);
        if (target == null) {
            target = resolveGetterHandle(clazz, "is" + cap);
            if (target != null) {
                try {
                    Method m = clazz.getMethod("is" + cap);
                    if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) {
                        target = null;
                    }
                } catch (NoSuchMethodException ignore) {
                    target = null;
                }
            }
        }
        if (target == null) {
            return NO_GETTER;
        }
        try {
            return target.asType(MethodType.methodType(Object.class, Object.class));
        } catch (IllegalArgumentException e) {
            // 签名不可 asType 到 (Object)Object — 实际不可达(任何 getter 都能 cast 到 Object return)
            return NO_GETTER;
        }
    }

    private static MethodHandle resolveGetterHandle(Class<?> clazz, String methodName) {
        try {
            Method m = clazz.getMethod(methodName);
            return MethodHandles.lookup().unreflect(m);
        } catch (NoSuchMethodException | IllegalAccessException ignore) {
            return null;
        }
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
