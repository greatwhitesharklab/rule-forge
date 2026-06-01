package com.ruleforge.console.storage;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.exception.RuleException;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RepositoryResourceProvider implements ResourceProvider {
    private final RuleForgeRepositoryService ruleforgeRepositoryService;

    @Override
    public Resource provide(String path, String version, String projectVersion, boolean containSnapshot) {
        InputStream inputStream;
        try {
            if (StringUtils.isEmpty(version) || version.equalsIgnoreCase("latest")) {
                inputStream = ruleforgeRepositoryService.readFile(path, null, projectVersion, containSnapshot);
            } else {
                inputStream = ruleforgeRepositoryService.readFile(path, version);
            }
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            IOUtils.closeQuietly(inputStream);
            return new Resource(content, path, projectVersion);
        } catch (Exception e) {
            throw new RuleException(String.format("path:%s version：%s %s", path, version, e));
        }
    }

    public boolean support(String path) {
        return path.startsWith("/");
    }
}
