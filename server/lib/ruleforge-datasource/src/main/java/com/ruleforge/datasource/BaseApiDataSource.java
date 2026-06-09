package com.ruleforge.datasource;

import java.util.Map;

/**
 * V5.23 — Abstract base class for third-party API data sources.
 *
 * <p>LLM-generated subclasses extend this class. The class itself does no HTTP work — it only
 * declares the contract. The five framework interceptors (rate limit, circuit breaker, retry,
 * cache, audit) are applied by the {@link DataSourceRegistry} at registration time, not by
 * the subclass.
 *
 * <h2>What subclasses MUST implement</h2>
 * <ul>
 *   <li>{@link #getName()} — unique identifier (used by decision flow to reference this DS)</li>
 *   <li>{@link #getSchema()} — output field names + types (for BA review and IDE autocomplete)</li>
 *   <li>{@link #fetch(Vars)} — the actual API call + response parsing</li>
 * </ul>
 *
 * <h2>What subclasses MUST NOT do</h2>
 * <ul>
 *   <li>Do not implement rate limiting / retries / circuit breaking — the framework does this</li>
 *   <li>Do not write to any database — auditing is the framework's job</li>
 *   <li>Do not catch and swallow {@code ApiCallException} — let it propagate so the
 *       framework can apply circuit-breaker / retry logic correctly</li>
 *   <li>Do not use {@code static} state — instances may be reloaded at runtime</li>
 * </ul>
 *
 * <h2>Example subclass (LLM-generated, see V5.23 docs)</h2>
 * <pre>{@code
 * @Component("zm_credit")
 * public class ZmCreditApiDataSource extends BaseApiDataSource {
 *     @Autowired protected RestTemplate restTemplate;
 *
 *     @Override public String getName() { return "zm_credit"; }
 *
 *     @Override public Map<String, String> getSchema() {
 *         return Map.of("zm_score", "INT", "zm_blacklisted", "BOOLEAN");
 *     }
 *
 *     @Override public Vars fetch(Vars inputs) {
 *         ResponseEntity<JsonNode> resp = restTemplate.exchange(...);
 *         Vars out = new Vars();
 *         out.put("zm_score", resp.getBody().get("zm_score").asInt());
 *         out.put("zm_blacklisted", resp.getBody().get("zm_blacklisted").asBoolean());
 *         return out;
 *     }
 * }
 * }</pre>
 */
public abstract class BaseApiDataSource {

    /**
     * Unique name used by decision flow to reference this data source.
     * Must be stable across deploys (stored in git + referenced by flow XML).
     */
    public abstract String getName();

    /**
     * Output field schema — used by BA review UI to show what the data source returns.
     * Map of field name → Java type name (e.g. "INT", "STRING", "BOOLEAN", "DECIMAL").
     * May be empty if the data source has dynamic output.
     */
    public abstract Map<String, String> getSchema();

    /**
     * Call the third-party API and return the parsed result.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>Validate required inputs up front (throw {@link IllegalArgumentException})</li>
     *   <li>Use {@code restTemplate} (Spring-injected) for HTTP calls — do not construct your own client</li>
     *   <li>Throw {@link ApiCallException} for upstream errors (4xx/5xx/timeout/parse failure)</li>
     *   <li>Return a {@link Vars} with the parsed fields</li>
     * </ul>
     *
     * @param inputs input variables (decision flow provides these)
     * @return output variables (decision flow merges into its own context)
     * @throws ApiCallException on any upstream or parsing failure
     */
    public abstract Vars fetch(Vars inputs) throws ApiCallException;
}
