package com.ruleforge.datasource.jcompiler;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V5.23 — Per-datasource classloader pool for AI-generated Java classes.
 *
 * <p>Each datasource has its own isolated {@link IsolatedLoader} (a
 * {@link URLClassLoader} subclass that exposes {@code defineClass} via
 * {@link DefiningClassLoader}). This ensures:
 * <ul>
 *   <li>One DS's classes can't accidentally depend on / collide with another's</li>
 *   <li>Updating a DS creates a new loader, atomically replacing the old reference</li>
 *   <li>{@link #close(Long)} releases the loader and frees the loaded class
 *       for GC (URLClassLoader holds strong refs to loaded classes via internal
 *       cache; closing the loader drops them)</li>
 * </ul>
 *
 * <p>Keyed by {@code datasourceId} (Long). Thread-safe via ConcurrentHashMap.
 */
@Slf4j
public class ClassLoaderPool {

    private final ConcurrentHashMap<Long, IsolatedLoader> loaders = new ConcurrentHashMap<>();

    /**
     * Returns the loaded class for the given id, loading it from {@code classBytes}
     * if not already cached. Cached on subsequent calls.
     *
     * @throws ClassFormatError if the bytes are not a valid .class
     */
    public Class<?> getOrLoad(Long datasourceId, String fqcn, byte[] classBytes) {
        return loaders.computeIfAbsent(datasourceId, id ->
            new IsolatedLoader(id, currentClasspathUrls(), ClassLoaderPool.class.getClassLoader())
        ).defineClassCached(fqcn, classBytes);
    }

    /**
     * Returns the cached instance for the given id, or loads + instantiates if absent.
     * Uses a no-arg constructor.
     */
    public IJavaDataSource getOrLoadInstance(Long datasourceId, String fqcn, byte[] classBytes) {
        Class<?> clazz = getOrLoad(datasourceId, fqcn, classBytes);
        try {
            return (IJavaDataSource) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "class " + fqcn + " cannot be instantiated as IJavaDataSource: " + e.getMessage(), e);
        }
    }

    /**
     * Closes the loader for the given id and removes it from the pool.
     * Safe to call multiple times. No-op if id was never loaded.
     */
    public void close(Long datasourceId) {
        IsolatedLoader old = loaders.remove(datasourceId);
        if (old != null) {
            try { old.close(); } catch (Exception e) {
                log.debug("close loader for {}: {}", datasourceId, e.getMessage());
            }
        }
    }

    /** Number of currently-cached loaders. For tests / diagnostics. */
    public int size() {
        return loaders.size();
    }

    /** Evicts all loaders. For tests. */
    public void clear() {
        loaders.keySet().forEach(this::close);
    }

    private static URL[] currentClasspathUrls() {
        // Capture the current thread's classloader URLs as the parent classpath
        // for the isolated loader. This way the LLM-generated class can resolve
        // IJavaDataSource, JDK types, and any other class on the host's classpath.
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (system instanceof URLClassLoader ucl) {
            return ucl.getURLs();
        }
        return new URL[0];
    }

    /**
     * Per-DS loader. Subclasses {@link URLClassLoader} to expose the protected
     * {@code defineClass} method via {@link DefiningClassLoader}.
     */
    static final class IsolatedLoader extends DefiningClassLoader {
        private final Long datasourceId;
        private final ConcurrentHashMap<String, Class<?>> defined = new ConcurrentHashMap<>();

        IsolatedLoader(Long id, URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.datasourceId = id;
        }

        Class<?> defineClassCached(String fqcn, byte[] bytes) {
            return defined.computeIfAbsent(fqcn, name -> {
                Class<?> c = defineClass(name, bytes, 0, bytes.length);
                resolveClass(c);
                return c;
            });
        }
    }

    /**
     * Exposes the protected {@code defineClass} from {@link ClassLoader} /
     * {@link URLClassLoader} so the pool can call it from outside the package.
     */
    static class DefiningClassLoader extends URLClassLoader {
        DefiningClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
