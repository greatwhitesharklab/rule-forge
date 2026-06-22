package com.ruleforge.config;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.builder.resource.Resource;

import com.ruleforge.exception.RuleException;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Jacky.gao
 * @author fred
 * 2014年12月22日
 *
 * <p>V6.13.4a: 去除 {@code ApplicationContextAware} — 改构造注入 {@link ResourceLoader}。
 * 之前 {@code applicationContext.getResource(path)} 等价于 {@code resourceLoader.getResource(path)},
 * Spring 自动配置 ResourceLoader 时 ApplicationContext 也是 ResourceLoader。
 */
@Component
public class FileResourceProvider implements ResourceProvider {
    private final ResourceLoader resourceLoader;

    public FileResourceProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Resource provide(String path, String version, String projectVersion, boolean containSnapshot) {
        try {
            InputStream inputStream = resourceLoader.getResource(path).getInputStream();
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            IOUtils.closeQuietly(inputStream);
            return new Resource(content, path, projectVersion);
        } catch (IOException e) {
            throw new RuleException(e);
        }
    }

    public boolean support(String path) {
        return path.startsWith("classpath:") || path.startsWith("file:") || path.startsWith("WEB-INF/");
    }
}
