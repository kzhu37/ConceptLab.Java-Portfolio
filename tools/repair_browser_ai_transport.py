#!/usr/bin/env python3
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


browser_path = Path("browser.js")
browser = browser_path.read_text(encoding="utf-8")
delay_marker = '''  const delay = (milliseconds) => new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
'''
bridge_block = '''  const delay = (milliseconds) => new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });

  const AI_BRIDGE_PATH = "/api/conceptlab/ai";
  const AI_BRIDGE_TIMEOUT_MS = 45_000;

  async function Java_Main_browserApiFetch(_lib, rawUrl, rawBody) {
    let endpoint;
    try {
      endpoint = new URL(String(rawUrl), window.location.origin);
    } catch {
      return JSON.stringify({ status: 0, retryAfter: "", body: "", transportError: "invalid_endpoint" });
    }

    if (
      endpoint.origin !== window.location.origin
      || endpoint.pathname !== AI_BRIDGE_PATH
      || endpoint.search
      || endpoint.hash
    ) {
      return JSON.stringify({ status: 0, retryAfter: "", body: "", transportError: "invalid_endpoint" });
    }

    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), AI_BRIDGE_TIMEOUT_MS);
    try {
      const response = await fetch(endpoint.toString(), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-ConceptLab-Client": "browser-v1",
        },
        body: String(rawBody),
        credentials: "same-origin",
        cache: "no-store",
        signal: controller.signal,
      });
      const body = await response.text();
      return JSON.stringify({
        status: response.status,
        retryAfter: response.headers.get("retry-after") || "",
        body,
        transportError: "",
      });
    } catch (error) {
      const transportError = error && error.name === "AbortError" ? "timeout" : "network";
      return JSON.stringify({ status: 0, retryAfter: "", body: "", transportError });
    } finally {
      window.clearTimeout(timer);
    }
  }
'''
browser = replace_once(browser, delay_marker, bridge_block, "browser native bridge")
browser = replace_once(
    browser,
    '''        clipboardMode: "java",
        javaProperties: [
''',
    '''        clipboardMode: "java",
        natives: {
          Java_Main_browserApiFetch,
        },
        javaProperties: [
''',
    "register CheerpJ native",
)
browser_path.write_text(browser, encoding="utf-8")


build_path = Path("browser/build-browser.py")
build = build_path.read_text(encoding="utf-8")
build = replace_once(
    build,
    '''    private static final int GROQ_FORBIDDEN_ITEMS_PER_BATCH = 30;

    // Core color palette.''',
    '''    private static final int GROQ_FORBIDDEN_ITEMS_PER_BATCH = 30;

    // Browser-only network transport implemented by browser.js through CheerpJ natives.
    private static native String browserApiFetch(String url, String requestBody);

    // Core color palette.''',
    "native declaration",
)

header_patch = r'''    source = replace_once(
        source,
        '.header("Authorization", "Bearer " + apiKey)\n                        .timeout',
        '.header("Authorization", "Bearer " + apiKey)\n                        .header("X-ConceptLab-Client", BROWSER_MODE ? "browser-v1" : "desktop-v1")\n                        .timeout',
        "browser request marker",
    )
'''
transport_patch = header_patch + r'''
    source = replace_once(
        source,
        '''        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
''',
        '''        HttpClient client = BROWSER_MODE
                ? null
                : HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();
''',
        "skip Java HttpClient initialization in browser",
    )

    browser_transport_marker = '''                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GROQ_API_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-ConceptLab-Client", BROWSER_MODE ? "browser-v1" : "desktop-v1")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody, StandardCharsets.UTF_8))
                        .build();

                System.err.println("[ConceptLab][API] " + phase
                        + " model=" + model
                        + " attempt " + (attempt + 1) + "/" + maxAttempts
                        + " maxOutputTokens=" + requestMaxTokens);
                long t0 = System.currentTimeMillis();
                HttpResponse<String> resp = client.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long elapsed = System.currentTimeMillis() - t0;
                System.err.println("[ConceptLab][API] HTTP " + resp.statusCode() + " in " + elapsed + "ms");
'''
    browser_transport_replacement = '''                System.err.println("[ConceptLab][API] " + phase
                        + " model=" + model
                        + " attempt " + (attempt + 1) + "/" + maxAttempts
                        + " maxOutputTokens=" + requestMaxTokens);
                long t0 = System.currentTimeMillis();
                int responseStatus;
                String responseBody;
                String responseRetryAfter = "";

                if (BROWSER_MODE) {
                    String transportEnvelope = browserApiFetch(GROQ_API_URL, reqBody);
                    if (transportEnvelope == null || transportEnvelope.isBlank()) {
                        throw new IOException("Browser AI transport returned no metadata");
                    }

                    Object parsedTransport;
                    try {
                        parsedTransport = new JsonParser(transportEnvelope).parse();
                    } catch (RuntimeException parseEx) {
                        throw new IOException("Browser AI transport returned invalid metadata", parseEx);
                    }
                    if (!(parsedTransport instanceof Map)) {
                        throw new IOException("Browser AI transport returned invalid metadata");
                    }

                    Map<String, Object> transport = (Map<String, Object>) parsedTransport;
                    Object statusValue = transport.get("status");
                    if (!(statusValue instanceof Number)) {
                        throw new IOException("Browser AI transport returned invalid status metadata");
                    }
                    responseStatus = ((Number) statusValue).intValue();
                    responseBody = jsonStr(transport, "body", "");
                    responseRetryAfter = jsonStr(transport, "retryAfter", "");

                    if (responseStatus == 0) {
                        String transportError = jsonStr(transport, "transportError", "network");
                        if ("timeout".equalsIgnoreCase(transportError)) {
                            throw new ApiCallException(
                                    "Browser AI request timed out", false, 408, 1000L, null);
                        }
                        if ("invalid_endpoint".equalsIgnoreCase(transportError)) {
                            throw new ApiCallException(
                                    "Browser AI endpoint was rejected", false, 400, null);
                        }
                        throw new IOException("Browser AI network request failed");
                    }
                } else {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(GROQ_API_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .header("X-ConceptLab-Client", "desktop-v1")
                            .timeout(Duration.ofSeconds(120))
                            .POST(HttpRequest.BodyPublishers.ofString(reqBody, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> resp = client.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    responseStatus = resp.statusCode();
                    responseBody = resp.body();
                    responseRetryAfter = resp.headers().firstValue("retry-after").orElse("");
                }

                long elapsed = System.currentTimeMillis() - t0;
                System.err.println("[ConceptLab][API] HTTP " + responseStatus + " in " + elapsed + "ms");
'''
    source = replace_once(
        source,
        browser_transport_marker,
        browser_transport_replacement,
        "browser native API transport",
    )
    source = replace_once(
        source,
        "                if (resp.statusCode() == 200) {\n",
        "                if (responseStatus == 200) {\n",
        "browser response status",
    )
    source = replace_once(
        source,
        "new JsonParser(resp.body()).parse()",
        "new JsonParser(responseBody).parse()",
        "browser response body parser",
    )
    source = replace_once(
        source,
        '''                String bodySnippet = resp.body() == null
                        ? ""
                        : resp.body().substring(0, Math.min(500, resp.body().length()));
''',
        '''                String bodySnippet = responseBody == null
                        ? ""
                        : responseBody.substring(0, Math.min(500, responseBody.length()));
''',
        "browser error body",
    )
    source = replace_once(
        source,
        '''                boolean tokenRelated = isTokenRelatedFailure(resp.statusCode(), bodySnippet);
                long retryDelayMillis = groqRetryDelayMillis(resp, attempt);
                lastEx = new ApiCallException(
                        "API error: HTTP " + resp.statusCode(),
                        tokenRelated,
                        resp.statusCode(),
                        retryDelayMillis,
                        null
                );
                if (resp.statusCode() == 413 && requestMaxTokens > GROQ_MIN_OUTPUT_TOKENS) {
''',
        '''                boolean tokenRelated = isTokenRelatedFailure(responseStatus, bodySnippet);
                long parsedRetryDelayMillis = parseGroqDurationMillis(responseRetryAfter);
                long retryDelayMillis = parsedRetryDelayMillis > 0L
                        ? Math.min(60_000L, parsedRetryDelayMillis + 250L)
                        : 1000L * (attempt + 1);
                lastEx = new ApiCallException(
                        "API error: HTTP " + responseStatus,
                        tokenRelated,
                        responseStatus,
                        retryDelayMillis,
                        null
                );
                if (responseStatus == 413 && requestMaxTokens > GROQ_MIN_OUTPUT_TOKENS) {
''',
        "browser error metadata",
    )
'''
build = replace_once(build, header_patch, transport_patch, "insert browser transport patch")

old_failure_helper = '''    private static String friendlyFailureCategory(Throwable cause) {
        String raw = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
'''
new_failure_helper = '''    private static String friendlyFailureCategory(Throwable cause) {
        StringBuilder detail = new StringBuilder();
        Throwable cursor = cause;
        for (int depth = 0; cursor != null && depth < 6; depth++) {
            if (cursor.getMessage() != null && !cursor.getMessage().isBlank()) {
                if (detail.length() > 0) {
                    detail.append(' ');
                }
                detail.append(cursor.getMessage());
            }
            cursor = cursor.getCause();
        }
        String raw = detail.toString().toLowerCase(Locale.ROOT);
'''
build = replace_once(build, old_failure_helper, new_failure_helper, "cause-aware failure message")
build_path.write_text(build, encoding="utf-8")


static_path = Path("tests/browser-static-check.js")
static = static_path.read_text(encoding="utf-8")
static = replace_once(
    static,
    '''assert.match(browser, /conceptlab\\.launchToken=/);
assert.match(browser, /browser-ready\\.txt/);
''',
    '''assert.match(browser, /conceptlab\\.launchToken=/);
assert.match(browser, /browser-ready\\.txt/);
assert.match(browser, /Java_Main_browserApiFetch/);
assert.match(browser, /natives:\\s*\\{/);
assert.match(browser, /X-ConceptLab-Client/);
assert.match(browser, /endpoint\\.origin !== window\\.location\\.origin/);
assert.match(browser, /endpoint\\.pathname !== AI_BRIDGE_PATH/);
''',
    "browser bridge static assertions",
)
static = replace_once(
    static,
    '''assert.match(build, /appHome\\.resolve\\("browser-ready\\.txt"\\)/);
''',
    '''assert.match(build, /appHome\\.resolve\\("browser-ready\\.txt"\\)/);
assert.match(build, /native String browserApiFetch/);
assert.match(build, /browserApiFetch\\(GROQ_API_URL, reqBody\\)/);
assert.match(build, /skip Java HttpClient initialization in browser/);
''',
    "generated transport static assertions",
)
static_path.write_text(static, encoding="utf-8")

print("Browser AI transport repair patch applied.")
