package com.ruleforge.executor.service.impl;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecResourceProvider implements ResourceProvider {

    private final RestTemplate consoleRestTemplate;

    @Override
    public Resource provide(String path, String version, String projectVersion, boolean containSnapshot) {
        log.info("ExecResourceProvider path: {} version: {} projectVersion: {}", path, version, projectVersion);
        if (StringUtils.hasText(version)) {
            return new Resource(sendRequest(path + ":" + version, projectVersion), path, projectVersion);
        } else {
            return new Resource(sendRequest(path, projectVersion), path, projectVersion);
        }
    }

    @Override
    public boolean support(String path) {
        return path.startsWith("/");
    }

    private String sendRequest(String path, String projectVersion) {
        String url = "/ruleforge/frame/fileSource";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("path", path);
        map.add("env", "test");
        map.add("projectVersion", projectVersion);
        // Pass gitTag if projectVersion looks like a version number (not LATEST or snapshot)
        if (StringUtils.hasText(projectVersion)
                && !"LATEST".equalsIgnoreCase(projectVersion)
                && !"snapshot".equalsIgnoreCase(projectVersion)) {
            // Build tag name from path: extract project and packageId
            // path format: /projectName/.../packageId/...
            String gitTag = buildGitTag(path, projectVersion);
            if (gitTag != null) {
                map.add("gitTag", gitTag);
            }
        }
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ParameterizedTypeReference<Map<String, String>> responseType =
                new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, String>> response = this.consoleRestTemplate.exchange(
                url, HttpMethod.POST, request, responseType);
        return response.getBody().get("content");
    }

    /**
     * Build a Git tag from the file path and version.
     * Attempts to extract packageId from path segments.
     * Tag format: pkg/{packageId}/{version}
     */
    private String buildGitTag(String path, String version) {
        // Path format: /projectName/资源包.rp/packageId/file.xml
        // We try to find the package segment
        if (path == null || !path.startsWith("/")) return null;
        String[] parts = path.split("/");
        // parts[0] = "", parts[1] = projectName, parts[2+] = path segments
        // Look for segment after 资源包.rp or .rp
        for (int i = 2; i < parts.length - 1; i++) {
            if (parts[i].endsWith(".rp")) {
                String packageId = parts[i + 1];
                if (StringUtils.hasText(packageId)) {
                    return "pkg/" + packageId + "/" + version;
                }
            }
        }
        return null;
    }
}
