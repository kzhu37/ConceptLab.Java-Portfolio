(() => {
  "use strict";

  const status = document.getElementById("runtime-status");
  const overlay = document.getElementById("launch-overlay");
  const detail = document.getElementById("launch-detail");
  const progress = document.getElementById("launch-progress");
  const display = document.getElementById("conceptlab-display");
  const retryButton = document.getElementById("retry-launch");
  const resetButton = document.getElementById("reset-demo");
  const params = new URLSearchParams(window.location.search);
  const resetRequested = params.get("reset") === "1";
  const launchToken = typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  const delay = (milliseconds) => new Promise((resolve) => {
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

  const setStatus = (text) => {
    status.textContent = text;
    detail.textContent = text;
  };

  const failLaunch = (error) => {
    console.error("ConceptLab browser launch failed", error);
    const message = error && error.message ? error.message : "Unknown startup error";
    setStatus("ConceptLab could not start. You can retry without losing browser data.");
    detail.textContent = `Startup failed: ${message}`;
    retryButton.classList.remove("hidden");
  };

  resetButton.addEventListener("click", () => {
    const next = new URL(window.location.href);
    next.searchParams.set("reset", "1");
    window.location.assign(next.toString());
  });

  retryButton.addEventListener("click", () => window.location.reload());

  async function waitForJavaReady(expectedToken) {
    const deadline = Date.now() + 150_000;
    while (Date.now() < deadline) {
      try {
        const marker = await cjFileBlob("/files/.conceptlab/browser-ready.txt");
        if ((await marker.text()).trim() === expectedToken) {
          return;
        }
      } catch {
        // The Java process creates the marker after storage and Swing are ready.
      }
      await delay(750);
    }
    throw new Error("The Java application did not finish starting within 150 seconds");
  }

  async function launch() {
    try {
      setStatus("Checking the Java browser artifact...");
      const jarCheck = await fetch("/ConceptLab-browser.jar", { method: "HEAD", cache: "no-store" });
      if (!jarCheck.ok) {
        throw new Error(`Browser JAR unavailable (HTTP ${jarCheck.status})`);
      }

      setStatus("Loading CheerpJ 4.3 and Java 17...");
      await cheerpjInit({
        version: 17,
        status: "none",
        clipboardMode: "java",
        natives: {
          Java_Main_browserApiFetch,
        },
        javaProperties: [
          "user.home=/files",
          "conceptlab.browser=true",
          `conceptlab.api.url=${window.location.origin}/api/conceptlab/ai`,
          `conceptlab.resetDemo=${resetRequested ? "true" : "false"}`,
          `conceptlab.launchToken=${launchToken}`,
        ],
        preloadProgress: (done, total) => {
          if (total > 0) {
            const pct = Math.max(4, Math.min(92, Math.round((done / total) * 92)));
            progress.style.width = `${pct}%`;
            setStatus(`Loading Java runtime... ${pct}%`);
          }
        },
      });

      setStatus("Preparing the bundled demo StudySet...");
      const seedResponse = await fetch("/demo/newtonian-mechanics.clab", { cache: "no-store" });
      if (!seedResponse.ok) {
        throw new Error(`Demo StudySet unavailable (HTTP ${seedResponse.status})`);
      }
      const seedText = await seedResponse.text();
      cheerpOSAddStringFile("/str/conceptlab-demo.clab", seedText);

      setStatus("Starting the real ConceptLab Swing application...");
      progress.style.width = "96%";
      cheerpjCreateDisplay(-1, -1, display);

      const run = cheerpjRunJar("/app/ConceptLab-browser.jar");
      await Promise.race([
        waitForJavaReady(launchToken),
        run.then((exitCode) => {
          throw new Error(`Java application exited with code ${exitCode}`);
        }),
      ]);

      progress.style.width = "100%";
      overlay.classList.add("done");
      display.dataset.javaReady = "true";
      status.textContent = "ConceptLab Java is running";
      if (resetRequested) {
        const clean = new URL(window.location.href);
        clean.searchParams.delete("reset");
        window.history.replaceState({}, "", clean.toString());
      }

      run.then((exitCode) => {
        failLaunch(new Error(`Java application exited with code ${exitCode}`));
      }).catch(failLaunch);
    } catch (error) {
      failLaunch(error);
    }
  }

  window.addEventListener("unhandledrejection", (event) => {
    console.error("Unhandled browser runtime rejection", event.reason);
  });

  launch();
})();
