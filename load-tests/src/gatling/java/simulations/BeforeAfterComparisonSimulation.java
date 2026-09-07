package simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Runs a handful of "before" and "after" endpoint pairs side by side
 * under real concurrent load, so the HTML report
 * (load-tests/build/reports/gatling/...) shows the improvement as a
 * reproducible load-test artifact instead of a one-off manual curl.
 *
 * Deliberately does NOT include the Issue #001 (N+1) or Issue #009
 * (full-result-set) endpoints here: both return the ENTIRE orders
 * table as JSON with no pagination, which was fine to demonstrate
 * once manually against the original ~20k-row seed, but is no longer
 * a reasonable thing to hit repeatedly under concurrent load now that
 * Issue #010's benchmark bulk-loaded 500k more rows (523,501 total) --
 * a good reminder that "which endpoints are safe to load-test" is
 * itself scale-dependent. Their before/after numbers are already
 * measured and documented in docs/audit/001 and docs/audit/009.
 *
 * Run with: ./gradlew :load-tests:gatlingRun
 * (app must be running on :8080 first, e.g. ./gradlew bootRun in another shell)
 */
public class BeforeAfterComparisonSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json");

    // Issue #003 -- offset vs keyset pagination, equivalent deep page
    private final ScenarioBuilder offsetDeepPage = scenario("Issue #003 before: offset pagination, deep page")
            .exec(http("GET /api/orders/page?page=999").get("/api/orders/page?page=999&size=20"));

    private final ScenarioBuilder keysetDeepPage = scenario("Issue #003 after: keyset pagination, equivalent depth")
            .exec(http("GET /api/orders/keyset?cursor=21").get("/api/orders/keyset?cursor=21&size=20"));

    // Issue #006 -- repeated lookups of the same hot product, cached vs not.
    // (The endpoint itself always uses @Cacheable now that Issue #006 is
    // fixed -- there's no separate "uncached" route left to hit, since the
    // fix replaced the behavior in place rather than adding a variant
    // endpoint. This scenario demonstrates the CACHED behavior holding up
    // under concurrent load, which a one-off sequential curl couldn't show.)
    private final ScenarioBuilder productLookupCached = scenario("Issue #006 after: cached product lookup under load")
            .exec(http("GET /api/products/1").get("/api/products/1"));

    // Issue #010 -- daily revenue aggregation, index in place, under load
    private final ScenarioBuilder dailyRevenueAggregation = scenario("Issue #010 after: daily revenue, indexed")
            .exec(http("GET /api/reports/daily-revenue?days=7").get("/api/reports/daily-revenue?days=7"));

    {
        setUp(
                offsetDeepPage.injectOpen(rampUsers(30).during(Duration.ofSeconds(10))).protocols(httpProtocol),
                keysetDeepPage.injectOpen(rampUsers(30).during(Duration.ofSeconds(10))).protocols(httpProtocol),
                productLookupCached.injectOpen(rampUsers(50).during(Duration.ofSeconds(10))).protocols(httpProtocol),
                dailyRevenueAggregation.injectOpen(rampUsers(30).during(Duration.ofSeconds(10))).protocols(httpProtocol)
        );
    }
}
