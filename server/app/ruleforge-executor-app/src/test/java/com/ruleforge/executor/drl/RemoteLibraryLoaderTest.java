package com.ruleforge.executor.drl;

import com.ruleforge.ir.drl.DatatypeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * V5.45.2 — RemoteLibraryLoader BDD(MockRestServiceServer 测 HTTP 行为)。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>正常 GET 响应含 .drl 内容 → 解析 declare types</li>
 *   <li>GET 4xx/5xx → 返空 map + log warn,不抛错</li>
 *   <li>GET 返 null body → 返空 map</li>
 * </ol>
 */
@DisplayName("V5.45.2 — RemoteLibraryLoader BDD")
class RemoteLibraryLoaderTest {

    @Nested
    @DisplayName("Given console /fileSource 返 library .drl,When loadLibrary,Then 解析 types")
    class SuccessResponse {

        @Test
        @DisplayName("GET /fileSource?path=libs/x.drl 返 declare Applicant → 1 TypeInfo")
        void success() {
            RestTemplate rt = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(rt);
            String drl = "declare Applicant\n    age : int\nend\n";
            server.expect(requestTo("http://console/fileSource?path=libs/x.drl"))
                .andExpect(method(GET))
                .andRespond(withSuccess(drl, MediaType.TEXT_PLAIN));

            RemoteLibraryLoader loader = new RemoteLibraryLoader(rt, "http://console");
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary("libs/x.drl", "/test");

            assertEquals(1, types.size());
            assertNotNull(types.get("Applicant"));
            assertEquals(List.of("age"), types.get("Applicant").getFields());
            server.verify();
        }
    }

    @Nested
    @DisplayName("Given console 返 5xx,When loadLibrary,Then 返空 map 不抛错")
    class ServerError {

        @Test
        @DisplayName("GET 500 → 返空 map,RemoteLibraryLoader 不抛 RestClientException")
        void serverError() {
            RestTemplate rt = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(rt);
            server.expect(requestTo("http://console/fileSource?path=libs/missing.drl"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            RemoteLibraryLoader loader = new RemoteLibraryLoader(rt, "http://console");
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary("libs/missing.drl", "/test");
            assertTrue(types.isEmpty());
            server.verify();
        }
    }

    @Nested
    @DisplayName("Given console 返 null body,When loadLibrary,Then 返空 map")
    class NullBody {

        @Test
        @DisplayName("GET 200 null body → 返空 map")
        void nullBody() {
            // getForObject String 在 null body 时返 null — 我们的 loader 检测 null 后返空 map
            // 但 MockRestServiceServer withSuccess(null) 不会真发 null,改测 withStatus(204 No Content)
            RestTemplate rt = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(rt);
            server.expect(requestTo("http://console/fileSource?path=libs/empty.drl"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

            RemoteLibraryLoader loader = new RemoteLibraryLoader(rt, "http://console");
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary("libs/empty.drl", "/test");
            assertTrue(types.isEmpty());
            server.verify();
        }
    }
}
