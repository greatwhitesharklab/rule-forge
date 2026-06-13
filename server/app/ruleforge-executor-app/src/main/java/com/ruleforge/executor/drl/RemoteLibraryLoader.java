package com.ruleforge.executor.drl;

import com.ruleforge.ir.drl.DatatypeResolver;
import com.ruleforge.ir.drl.LibraryLoader;
import com.ruleforge.ir.drl.LibraryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * V5.45.2 — executor-app 远程 library 加载器。
 *
 * <p>executor-app 不持有项目资源,通过 HTTP 调 console-app 拿 library .drl 内容。
 * 跟 V5.45.2 KnowledgeBuilder 集成:本 loader 返 declare types map,跟
 * LocalLibraryLoader 行为一致,只是 file IO 换成 HTTP GET。
 *
 * <p>console-app 端点约定:
 * <ul>
 *   <li>{@code GET /fileSource?path={libraryPath}} — 返 library 文件原始内容
 *       (V5.44.4 既有端点,console-app 拉 .drl 拉 .xml 都走这条)
 * </ul>
 *
 * <p>失败策略:HTTP 4xx/5xx / timeout / IO 错 → 返空 map + log warn,不抛错
 * (跟 V5.45.2 LibraryLoader SPI 契约一致)。
 *
 * @since 5.45
 */
public class RemoteLibraryLoader implements LibraryLoader {

    private static final Logger log = LoggerFactory.getLogger(RemoteLibraryLoader.class);

    private final RestTemplate restTemplate;
    private final String consoleBaseUrl;
    private final LibraryParser parser = new LibraryParser();

    public RemoteLibraryLoader(RestTemplate restTemplate, String consoleBaseUrl) {
        this.restTemplate = restTemplate;
        this.consoleBaseUrl = consoleBaseUrl;
    }

    @Override
    public Map<String, DatatypeResolver.TypeInfo> loadLibrary(String libraryPath, String basePath) {
        String url = consoleBaseUrl + "/fileSource?path=" + libraryPath;
        try {
            // console /fileSource 返 String(文件原始内容)
            String drlText = restTemplate.getForObject(url, String.class);
            if (drlText == null) {
                log.warn("V5.45.2 RemoteLibraryLoader: console returned null body for {}", libraryPath);
                return Collections.emptyMap();
            }
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drlText);
            log.info("V5.45.2 RemoteLibraryLoader: loaded library {} ({} types, {} inner imports)",
                libraryPath, r.types().size(), r.innerImports().size());
            return r.types();
        } catch (RestClientException e) {
            log.warn("V5.45.2 RemoteLibraryLoader: failed to fetch {}: {}",
                libraryPath, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
