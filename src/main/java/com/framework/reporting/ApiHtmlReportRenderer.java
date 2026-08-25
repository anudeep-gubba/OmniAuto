package com.framework.reporting;

import com.framework.config.ConfigManager;
import com.framework.reporting.ApiReportModel.ApiCallEvent;
import com.framework.reporting.ApiReportModel.AssertionEvent;
import com.framework.reporting.ApiReportModel.Outcome;
import com.framework.reporting.ApiReportModel.TestEvent;
import com.framework.reporting.ApiReportModel.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders the suite's collected API {@link TestRecord}s as one self-contained HTML file under
 * {@code reports/api/} - a Newman/Postman-style dashboard (summary cards, tests grouped by
 * module, one collapsed row per test, Expected/Actual instead of raw assertion sentences).
 * Built from scratch with no reporting library at all - ported line-for-line from the
 * standalone {@code RestAssuredTestNG} framework's {@code HtmlReportRenderer} this project's API
 * reporting now matches.
 *
 * <p>No external CSS/JS/fonts - everything is inlined, because this file has to open standalone
 * from disk (double-click, {@code file://}, a CI artifact download) with no network access
 * assumed.</p>
 *
 * <p>API-call status pills (200/400/500, ...) are colored purely by HTTP status class for
 * at-a-glance reading - they are <b>not</b> the test's pass/fail signal. A test that
 * deliberately asserts a 400/404 is exactly as green as one that asserts a 200; only the
 * Validations section (driven by actual {@code Verify}/{@code assertStatusCode} outcomes) and
 * the row's own check/cross icon decide red vs. green.</p>
 */
final class ApiHtmlReportRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiHtmlReportRenderer.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final AtomicInteger ID_SEQ = new AtomicInteger();

    private ApiHtmlReportRenderer() {
    }

    static void render(long suiteStartMillis, long suiteEndMillis, List<TestRecord> tests) {
        File reportDir = new File(new File(System.getProperty("user.dir"), "reports"), "api");
        reportDir.mkdirs();
        String fileName = ConfigManager.isReportOverwriteEnabled()
                ? "index.html"
                : "report-" + FILE_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(suiteStartMillis)) + ".html";
        File reportFile = new File(reportDir, fileName);

        String html = buildHtml(suiteStartMillis, suiteEndMillis, tests);
        try {
            Files.writeString(reportFile.toPath(), html, StandardCharsets.UTF_8);
            LOGGER.info("API report written to '{}'.", reportFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("Failed to write API report to '{}': {}", reportFile.getAbsolutePath(), e.getMessage());
        }
    }

    private static String buildHtml(long suiteStartMillis, long suiteEndMillis, List<TestRecord> tests) {
        int total = tests.size();
        long passed = tests.stream().filter(t -> t.outcome == Outcome.PASS).count();
        long failed = tests.stream().filter(t -> t.outcome == Outcome.FAIL).count();
        long skipped = tests.stream().filter(t -> t.outcome == Outcome.SKIP).count();
        long durationMs = suiteEndMillis - suiteStartMillis;

        List<ApiCallEvent> calls = tests.stream()
                .flatMap(t -> t.events.stream())
                .filter(ApiCallEvent.class::isInstance)
                .map(ApiCallEvent.class::cast)
                .toList();
        double avgResponseMs = calls.isEmpty() ? 0 : calls.stream().mapToLong(ApiCallEvent::durationMs).average().orElse(0);
        ApiCallEvent slowest = calls.stream().max(Comparator.comparingLong(ApiCallEvent::durationMs)).orElse(null);

        Map<String, List<TestRecord>> byModule = new LinkedHashMap<>();
        for (TestRecord test : tests) {
            byModule.computeIfAbsent(test.module, m -> new java.util.ArrayList<>()).add(test);
        }

        StringBuilder html = new StringBuilder(64 * 1024);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(escape(ConfigManager.getApiReportTitle())).append("</title>")
            .append("<style>").append(CSS).append("</style></head><body>");

        html.append("<header class=\"top\"><div class=\"top-inner\">")
            .append("<h1>").append(escape(ConfigManager.getApiReportName())).append("</h1>")
            .append("<p class=\"meta\">Environment: <b>").append(escape(ConfigManager.getEnvironment().name())).append("</b>")
            .append(" &nbsp;&middot;&nbsp; Base URL: <b>").append(escape(ConfigManager.getApiBaseUrl())).append("</b>")
            .append(" &nbsp;&middot;&nbsp; Executed: <b>").append(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(suiteStartMillis)))
            .append("</b></p></div></header>");

        html.append("<section class=\"summary\">")
            .append(card(String.valueOf(total), "Tests", "neutral"))
            .append(card(String.valueOf(passed), "Passed", "pass"))
            .append(card(String.valueOf(failed), "Failed", "fail"))
            .append(card(String.valueOf(skipped), "Skipped", "skip"))
            .append(card(formatDuration(durationMs), "Duration", "neutral"))
            .append("</section>");

        html.append("<section class=\"perf\">")
            .append("<span>Average Response: <b>").append(Math.round(avgResponseMs)).append(" ms</b></span>")
            .append("<span>Total API Calls: <b>").append(calls.size()).append("</b></span>");
        if (slowest != null) {
            html.append("<span>Slowest Call: <b>").append(escape(slowest.method())).append(" ")
                .append(escape(slowest.endpoint())).append(" &mdash; ").append(slowest.durationMs()).append(" ms</b></span>");
        }
        html.append("</section>");

        html.append("<section class=\"toolbar\">")
            .append("<input id=\"search\" type=\"search\" placeholder=\"Search test name or endpoint…\" oninput=\"filterTests()\">")
            .append("<div id=\"tagFilters\" class=\"tags\">");
        for (String tag : distinctTags(tests)) {
            html.append("<button type=\"button\" class=\"tag\" data-tag=\"").append(escape(tag))
                .append("\" onclick=\"toggleTag(this)\">").append(escape(tag)).append("</button>");
        }
        html.append("</div></section>");

        html.append("<main class=\"modules\">");
        for (Map.Entry<String, List<TestRecord>> entry : byModule.entrySet()) {
            List<TestRecord> moduleTests = entry.getValue();
            long modulePassed = moduleTests.stream().filter(t -> t.outcome == Outcome.PASS).count();
            html.append("<section class=\"module\"><h2>").append(escape(entry.getKey()))
                .append(" <span class=\"module-count\">(").append(modulePassed).append("/").append(moduleTests.size())
                .append(" passed)</span></h2>");
            for (TestRecord test : moduleTests) {
                html.append(renderTestRow(test));
            }
            html.append("</section>");
        }
        if (tests.isEmpty()) {
            html.append("<p class=\"empty\">No tests ran.</p>");
        }
        html.append("</main>");

        html.append("<p id=\"noMatches\" class=\"empty\" hidden>No tests match your filters.</p>");
        html.append("<footer class=\"foot\">Generated by OmniAuto</footer>");
        html.append("<script>").append(JS).append("</script>");
        html.append("</body></html>");
        return html.toString();
    }

    private static String renderTestRow(TestRecord test) {
        List<ApiCallEvent> calls = test.events.stream()
                .filter(ApiCallEvent.class::isInstance).map(ApiCallEvent.class::cast).toList();
        List<AssertionEvent> assertions = test.events.stream()
                .filter(AssertionEvent.class::isInstance).map(AssertionEvent.class::cast).toList();

        String icon = switch (test.outcome) {
            case PASS -> "✓";
            case FAIL -> "✗";
            case SKIP -> "⚠";
        };

        String rowRight;
        if (calls.size() == 1) {
            ApiCallEvent call = calls.get(0);
            rowRight = "<span class=\"method\">" + escape(call.method()) + "</span>"
                    + "<span class=\"endpoint\">" + escape(call.endpoint()) + "</span>"
                    + statusPill(call.statusCode())
                    + "<span class=\"duration\">" + call.durationMs() + " ms</span>";
        } else if (calls.isEmpty()) {
            rowRight = "<span class=\"duration\">" + test.durationMs() + " ms</span>";
        } else {
            rowRight = "<span class=\"calls-count\">" + calls.size() + " calls</span>"
                    + "<span class=\"duration\">" + test.durationMs() + " ms total</span>";
        }

        String searchEndpoint = calls.isEmpty() ? "" : calls.get(0).endpoint();

        StringBuilder row = new StringBuilder();
        row.append("<details class=\"test-row outcome-").append(test.outcome.name().toLowerCase()).append("\" data-tags=\"")
           .append(escape(String.join(" ", test.groups))).append("\" data-search=\"")
           .append(escape((test.name + " " + searchEndpoint).toLowerCase())).append("\">");
        row.append("<summary><span class=\"icon\">").append(icon).append("</span>")
           .append("<span class=\"test-name\">").append(escape(test.name)).append("</span>")
           .append("<span class=\"row-right\">").append(rowRight).append("</span></summary>");

        row.append("<div class=\"detail\">");
        if (test.description != null && !test.description.isBlank()) {
            row.append("<p class=\"description\">").append(escape(test.description)).append("</p>");
        }
        if (test.outcome == Outcome.FAIL && test.errorMessage != null && !test.errorMessage.isBlank()) {
            row.append("<div class=\"failure-box\"><b>Error</b><pre>").append(escape(test.errorMessage)).append("</pre></div>");
        }
        if (test.outcome == Outcome.SKIP) {
            String reason = test.errorMessage != null && !test.errorMessage.isBlank() ? test.errorMessage : "No reason given.";
            row.append("<div class=\"skip-box\"><b>Skipped</b><pre>").append(escape(reason)).append("</pre></div>");
        }
        for (TestEvent event : test.events) {
            if (event instanceof ApiCallEvent call) {
                row.append(renderCall(call));
            }
        }
        if (!assertions.isEmpty()) {
            row.append("<div class=\"validations\"><b>Validations</b><ul>");
            for (AssertionEvent a : assertions) {
                row.append(renderAssertion(a));
            }
            row.append("</ul></div>");
        }
        row.append("</div></details>");
        return row.toString();
    }

    private static String renderCall(ApiCallEvent call) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"call\">");
        sb.append("<div class=\"call-line\"><span class=\"method\">").append(escape(call.method())).append("</span>")
          .append("<span class=\"url\">").append(escape(call.url())).append("</span>")
          .append(statusPill(call.statusCode()))
          .append("<span class=\"duration\">").append(call.durationMs()).append(" ms</span></div>");

        sb.append("<div class=\"req-res\">");
        sb.append("<details class=\"body-block\"><summary>Request</summary>");
        sb.append(sectionLabel("Headers")).append(headersBlock(call.requestHeaders()));
        sb.append(sectionLabel("Body")).append(jsonBlock(call.requestBody()));
        sb.append("</details>");

        sb.append("<details class=\"body-block\"><summary>Response</summary>");
        sb.append(sectionLabel("Headers")).append(headersBlock(call.responseHeaders()));
        sb.append(sectionLabel("Body")).append(jsonBlock(call.responseBody()));
        sb.append("</details>");
        sb.append("</div></div>");
        return sb.toString();
    }

    private static String renderAssertion(AssertionEvent a) {
        StringBuilder sb = new StringBuilder();
        sb.append("<li class=\"").append(a.passed() ? "pass" : "fail").append("\">")
          .append("<span class=\"icon\">").append(a.passed() ? "✓" : "✗").append("</span> ")
          .append("<span class=\"assertion-label\">").append(escape(a.label())).append("</span>");
        if (a.expected() != null) {
            sb.append("<div class=\"expected-actual\">")
              .append("<span>Expected: <code>").append(escape(a.expected())).append("</code></span>")
              .append("<span>Actual: <code>").append(escape(a.actual())).append("</code></span>")
              .append("</div>");
        }
        if (a.detail() != null && !a.detail().isBlank()) {
            sb.append("<div class=\"assertion-detail\">").append(escape(a.detail())).append("</div>");
        }
        sb.append("</li>");
        return sb.toString();
    }

    private static String jsonBlock(String content) {
        if (content == null || content.isBlank()) {
            return "<p class=\"empty\">(no body)</p>";
        }
        String id = "body-" + ID_SEQ.incrementAndGet();
        return "<div class=\"json-block\"><button type=\"button\" class=\"copy\" onclick=\"copyBlock('" + id + "', this)\">Copy</button>"
             + "<pre id=\"" + id + "\">" + escape(content) + "</pre></div>";
    }

    /** Small bold "Headers"/"Body" label above each, so a reader doesn't have to infer which is which purely from formatting. */
    private static String sectionLabel(String label) {
        return "<div class=\"section-label\">" + escape(label) + "</div>";
    }

    /** {@code <pre>} rather than the old escape-then-replace(\n, "&lt;br&gt;") div - preserves newlines/indentation natively, and (with the matching CSS) wraps a long unbroken value (a Bearer JWT, e.g.) instead of overflowing the block's own border. */
    private static String headersBlock(String content) {
        if (content == null || content.isBlank()) {
            return "<p class=\"empty\">(no headers)</p>";
        }
        return "<pre class=\"headers\">" + escape(content) + "</pre>";
    }

    private static String statusPill(int statusCode) {
        String cls = statusCode >= 500 ? "s5xx" : statusCode >= 400 ? "s4xx" : statusCode >= 300 ? "s3xx" : "s2xx";
        return "<span class=\"status-pill " + cls + "\">" + statusCode + "</span>";
    }

    private static String card(String value, String label, String tone) {
        return "<div class=\"card " + tone + "\"><div class=\"card-value\">" + value + "</div>"
             + "<div class=\"card-label\">" + escape(label) + "</div></div>";
    }

    /** {@code smoke}/{@code regression} first (they're the run-selector tags every test tends to carry), then the rest alphabetically. */
    private static List<String> distinctTags(List<TestRecord> tests) {
        TreeSet<String> tags = new TreeSet<>();
        for (TestRecord t : tests) {
            tags.addAll(t.groups);
        }
        List<String> ordered = new java.util.ArrayList<>();
        for (String priority : List.of("smoke", "regression")) {
            if (tags.remove(priority)) {
                ordered.add(priority);
            }
        }
        ordered.addAll(tags);
        return ordered;
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        return String.format("%.1f s", ms / 1000.0);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS = """
            :root{--bg:#f4f5f7;--panel:#fff;--border:#e2e5ea;--text:#1f2430;--muted:#6b7280;
                --green:#16a34a;--green-bg:#ecfdf3;--red:#dc2626;--red-bg:#fef2f2;
                --amber:#d97706;--amber-bg:#fffbeb;--blue:#2563eb;--blue-bg:#eff6ff;--grey-bg:#f1f2f4;}
            *{box-sizing:border-box;}
            body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
                background:var(--bg);color:var(--text);font-size:14px;}
            .top{background:#111827;color:#fff;}
            .top-inner{max-width:1100px;margin:0 auto;padding:20px 24px;}
            .top h1{margin:0 0 6px;font-size:20px;}
            .top .meta{margin:0;color:#cbd5e1;font-size:13px;}
            .summary{max-width:1100px;margin:16px auto 0;padding:0 24px;display:grid;
                grid-template-columns:repeat(5,1fr);gap:12px;}
            .card{background:var(--panel);border:1px solid var(--border);border-radius:8px;
                padding:14px;text-align:center;}
            .card-value{font-size:24px;font-weight:700;}
            .card-label{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em;}
            .card.pass .card-value{color:var(--green);}
            .card.fail .card-value{color:var(--red);}
            .card.skip .card-value{color:var(--amber);}
            .perf{max-width:1100px;margin:12px auto 0;padding:10px 24px;display:flex;gap:24px;
                background:var(--panel);border:1px solid var(--border);border-radius:8px;
                margin-left:auto;margin-right:auto;font-size:13px;color:var(--muted);}
            .perf b{color:var(--text);}
            .toolbar{max-width:1100px;margin:16px auto 0;padding:0 24px;display:flex;gap:12px;
                align-items:center;flex-wrap:wrap;}
            #search{flex:1;min-width:220px;padding:8px 12px;border:1px solid var(--border);
                border-radius:6px;font-size:13px;}
            .tags{display:flex;gap:6px;flex-wrap:wrap;}
            .tag{border:1px solid var(--border);background:var(--panel);color:var(--muted);
                border-radius:999px;padding:5px 12px;font-size:12px;cursor:pointer;}
            .tag.active{background:var(--blue);border-color:var(--blue);color:#fff;}
            .modules{max-width:1100px;margin:16px auto 40px;padding:0 24px;}
            .module{margin-bottom:20px;}
            .module h2{font-size:14px;margin:0 0 8px;color:var(--text);}
            .module-count{color:var(--muted);font-weight:400;font-size:12px;}
            .test-row{background:var(--panel);border:1px solid var(--border);border-left:4px solid var(--border);
                border-radius:6px;margin-bottom:8px;}
            .test-row.outcome-pass{border-left-color:var(--green);}
            .test-row.outcome-fail{border-left-color:var(--red);}
            .test-row.outcome-skip{border-left-color:var(--amber);}
            .test-row summary{list-style:none;cursor:pointer;padding:10px 14px;display:flex;
                align-items:center;gap:10px;}
            .test-row summary::-webkit-details-marker{display:none;}
            .test-row .icon{font-weight:700;width:16px;text-align:center;}
            .outcome-pass .icon{color:var(--green);}
            .outcome-fail .icon{color:var(--red);}
            .outcome-skip .icon{color:var(--amber);}
            .test-name{flex:1;font-weight:500;}
            .row-right{display:flex;align-items:center;gap:10px;color:var(--muted);font-size:12px;}
            .method{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-weight:700;color:var(--blue);}
            .endpoint,.url{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;}
            .url{overflow-wrap:anywhere;}
            .status-pill{border-radius:4px;padding:2px 7px;font-size:11px;font-weight:700;}
            .status-pill.s2xx{background:var(--green-bg);color:var(--green);}
            .status-pill.s3xx{background:var(--blue-bg);color:var(--blue);}
            .status-pill.s4xx{background:var(--amber-bg);color:var(--amber);}
            .status-pill.s5xx{background:var(--red-bg);color:var(--red);}
            .detail{padding:0 14px 14px 40px;border-top:1px solid var(--border);}
            .description{color:var(--muted);font-style:italic;margin:10px 0;}
            .failure-box{background:var(--red-bg);border:1px solid #fecaca;border-radius:6px;
                padding:10px 12px;margin:10px 0;}
            .failure-box pre{white-space:pre-wrap;margin:6px 0 0;font-size:12px;}
            .skip-box{background:var(--amber-bg);border:1px solid #fde68a;border-radius:6px;
                padding:10px 12px;margin:10px 0;}
            .skip-box pre{white-space:pre-wrap;margin:6px 0 0;font-size:12px;}
            .call{margin:10px 0;padding:10px;background:var(--grey-bg);border-radius:6px;}
            .call-line{display:flex;align-items:center;flex-wrap:wrap;gap:10px;font-size:12px;margin-bottom:6px;}
            .req-res{display:flex;gap:10px;flex-wrap:wrap;}
            .body-block{flex:1;min-width:240px;background:var(--panel);border:1px solid var(--border);
                border-radius:6px;}
            .body-block summary{cursor:pointer;padding:6px 10px;font-size:12px;font-weight:600;
                color:var(--muted);list-style:none;}
            .body-block summary::-webkit-details-marker{display:none;}
            .section-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;
                color:var(--muted);padding:8px 10px 4px;border-top:1px solid var(--border);}
            .section-label:first-of-type{border-top:none;}
            .headers{margin:0;padding:0 10px 8px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
                font-size:11px;color:var(--muted);white-space:pre-wrap;word-break:break-word;
                overflow-wrap:anywhere;max-height:200px;overflow:auto;box-sizing:border-box;}
            .json-block{position:relative;}
            .json-block pre{margin:0;padding:10px;font-size:12px;white-space:pre-wrap;word-break:break-word;
                overflow-wrap:anywhere;max-height:320px;overflow:auto;border-top:1px solid var(--border);
                box-sizing:border-box;}
            .json-block .copy{position:absolute;top:6px;right:8px;font-size:11px;border:1px solid var(--border);
                background:var(--panel);border-radius:4px;padding:2px 8px;cursor:pointer;}
            .validations{margin-top:12px;}
            .validations ul{list-style:none;margin:8px 0 0;padding:0;}
            .validations li{padding:8px 10px;border-radius:6px;margin-bottom:4px;font-size:13px;}
            .validations li.pass{background:var(--green-bg);}
            .validations li.fail{background:var(--red-bg);}
            .validations .icon{font-weight:700;}
            .validations li.pass .icon{color:var(--green);}
            .validations li.fail .icon{color:var(--red);}
            .expected-actual{display:flex;gap:20px;margin-top:4px;padding-left:22px;font-size:12px;color:var(--muted);}
            .expected-actual code{background:rgba(0,0,0,.06);border-radius:3px;padding:1px 5px;color:var(--text);}
            .assertion-detail{padding-left:22px;margin-top:4px;font-size:12px;color:var(--muted);white-space:pre-wrap;}
            .empty{text-align:center;color:var(--muted);padding:24px;}
            .foot{text-align:center;color:var(--muted);font-size:12px;padding:20px;}
            @media(max-width:720px){.summary{grid-template-columns:repeat(2,1fr);}.perf{flex-direction:column;gap:6px;}}
            """;

    private static final String JS = """
            function activeTags(){
                return Array.from(document.querySelectorAll('.tag.active')).map(function(b){return b.dataset.tag;});
            }
            function toggleTag(btn){
                btn.classList.toggle('active');
                filterTests();
            }
            function filterTests(){
                var query = document.getElementById('search').value.trim().toLowerCase();
                var tags = activeTags();
                var rows = document.querySelectorAll('.test-row');
                var anyVisible = false;
                rows.forEach(function(row){
                    var matchesSearch = !query || row.dataset.search.indexOf(query) !== -1;
                    var rowTags = row.dataset.tags.split(' ');
                    var matchesTags = tags.length === 0 || tags.some(function(t){return rowTags.indexOf(t) !== -1;});
                    var visible = matchesSearch && matchesTags;
                    row.hidden = !visible;
                    if (visible) { anyVisible = true; }
                });
                document.querySelectorAll('.module').forEach(function(section){
                    var hasVisible = Array.from(section.querySelectorAll('.test-row')).some(function(r){return !r.hidden;});
                    section.hidden = !hasVisible;
                });
                document.getElementById('noMatches').hidden = anyVisible;
            }
            function copyBlock(id, btn){
                var text = document.getElementById(id).textContent;
                var done = function(){
                    var original = btn.textContent;
                    btn.textContent = 'Copied!';
                    setTimeout(function(){ btn.textContent = original; }, 1200);
                };
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(done, function(){});
                }
            }
            """;
}
