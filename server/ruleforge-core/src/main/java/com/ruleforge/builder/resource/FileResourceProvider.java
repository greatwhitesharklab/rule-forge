package com.ruleforge.builder.resource;

import com.ruleforge.exception.RuleException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Jacky.gao
 * @author fred
 * 2014年12月22日
 */
public class FileResourceProvider implements ResourceProvider, ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Override
    public Resource provide(String path, String version, String projectVersion, boolean containSnapshot) {
        try {
            InputStream inputStream = applicationContext.getResource(path).getInputStream();
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

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
