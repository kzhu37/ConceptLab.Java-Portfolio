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
        clipboardMode: "permission",
        javaProperties: [
          "user.home=/files",
          "conceptlab.browser=true",
          `conceptlab.api.url=${window.location.origin}/api/conceptlab/ai`,
          `conceptlab.resetDemo=${resetRequested ? "true" : "false"}`,
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
      run.then((exitCode) => {
        if (exitCode !== 0) {
          failLaunch(new Error(`Java application exited with code ${exitCode}`));
        }
      }).catch(failLaunch);

      // Give the Swing frame time to paint before removing the outer loading layer.
      window.setTimeout(() => {
        progress.style.width = "100%";
        overlay.classList.add("done");
        status.textContent = "ConceptLab Java is running";
        if (resetRequested) {
          const clean = new URL(window.location.href);
          clean.searchParams.delete("reset");
          window.history.replaceState({}, "", clean.toString());
        }
      }, 3200);
    } catch (error) {
      failLaunch(error);
    }
  }

  window.addEventListener("unhandledrejection", (event) => {
    console.error("Unhandled browser runtime rejection", event.reason);
  });

  launch();
})();
