// Classic isolated content script: never reads page text, cookies, account data or messages from page scripts.
(() => {
    if (window !== window.top) return;
    if (globalThis.__agentMeterBar) {
        void globalThis.__agentMeterBar.update();
        return;
    }
    document.getElementById("agentmeter-page-bar")?.remove();
    // Build one isolated overlay; provider state arrives through extension messages below.
    const host = document.createElement("div");
    host.id = "agentmeter-page-bar";
    host.style.setProperty("all", "initial", "important");
    host.style.setProperty("position", "fixed", "important");
    host.style.setProperty("z-index", "2147483646", "important");
    host.style.setProperty("right", "16px", "important");
    host.style.setProperty("max-width", "calc(100vw - 32px)", "important");
    host.style.setProperty("color-scheme", "dark", "important");
    const root = host.attachShadow({mode: "closed"});
    const style = document.createElement("style");
    style.textContent = `
      :host{--bg:#101925;--surface:#192637;--line:#35445b;--fg:#eef3fb;--muted:#a4b2c6;--good:#67dab1;--warn:#f4cd6b;--bad:#ff8396}
      :host([data-theme="light"]){--bg:#fff;--surface:#f3f6fb;--line:#d6deea;--fg:#1a293b;--muted:#52627a;--good:#087451;--warn:#8b5b00;--bad:#b32040}
      @media(prefers-color-scheme:light){:host([data-theme="system"]){--bg:#fff;--surface:#f3f6fb;--line:#d6deea;--fg:#1a293b;--muted:#52627a;--good:#087451;--warn:#8b5b00;--bad:#b32040}}
      *{box-sizing:border-box} .widget{font:12px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;color:var(--fg);direction:ltr;display:flex;flex-direction:column;gap:8px;align-items:flex-end}
      :host([data-position="bottom"]) .widget{flex-direction:column-reverse}
      button,.provider-link{font:inherit;color:inherit;background:transparent;border:0;cursor:pointer;padding:6px 9px;border-radius:6px;white-space:nowrap}
      .provider-link{display:inline-block;text-decoration:none}
      button:hover,.provider-link:hover{background:var(--surface)}button:disabled{opacity:.5;cursor:default}button:focus-visible,.provider-link:focus-visible{outline:2px solid var(--good);outline-offset:2px}
      .strip,.details{background:var(--bg);border:1px solid var(--line);border-radius:10px;box-shadow:0 5px 24px #0005}
      .strip{display:flex;align-items:center;max-width:100%;padding:3px;gap:2px;flex-wrap:wrap;justify-content:flex-end}
      .brand{color:var(--muted);font-size:10px;letter-spacing:.2px;padding:0 7px;border-right:1px solid var(--line)}
      .move{cursor:grab;touch-action:none;user-select:none;color:var(--muted)}.move:active{cursor:grabbing}
      .summary{display:flex;flex-wrap:wrap;font-variant-numeric:tabular-nums;font-weight:600}.saved{font-size:10px;color:var(--muted);padding:0 5px}
      .details{width:350px;max-width:calc(100vw - 32px);padding:15px;max-height:min(420px,65vh);overflow:auto}
      h2{font-size:14px;line-height:1.4;margin:0 0 10px}p{margin:7px 0;font-size:11px;color:var(--muted)}
      .plan{font-size:10px;color:var(--muted);border:1px solid var(--line);border-radius:12px;padding:2px 7px;margin-left:8px;font-weight:400}
      .quota{padding:9px 0;border-top:1px solid var(--line)}.row{display:flex;justify-content:space-between;gap:8px}.number{font-weight:700;font-variant-numeric:tabular-nums}
      .bar{height:4px;border-radius:5px;background:var(--line);margin-top:6px;overflow:hidden}.fill{height:100%;border-radius:inherit}
      .notice{color:var(--warn)}.actions{display:flex;gap:5px;margin-top:10px}.actions button{border:1px solid var(--line)}
      [hidden]{display:none!important}
    `;
    const widget = document.createElement("section");
    widget.className = "widget";
    widget.setAttribute("aria-label", "AgentMeter usage widget");
    const strip = document.createElement("div");
    strip.className = "strip";
    const brand = document.createElement("span");
    brand.className = "brand";
    brand.textContent = "AgentMeter";
    const summary = document.createElement("div");
    summary.className = "summary";
    const refresh = button("↻", "Refresh usage");
    const collapse = button("−", "Collapse AgentMeter");
    const next = button("›", "Next provider details");
    const toggleDetails = button("▾", "Show quota details");
    const settingsButton = button("⚙", "Page bar settings");
    const moveHandle = button("⠿", "Move bar: drag or use arrow keys");
    moveHandle.className = "move";
    toggleDetails.setAttribute("aria-expanded", "false");
    const restore = button("AgentMeter", "Expand AgentMeter");
    restore.hidden = true;
    const details = document.createElement("div");
    details.className = "details";
    details.hidden = true;
    strip.append(moveHandle, brand, summary, next, refresh, toggleDetails, settingsButton, collapse, restore);
    widget.append(strip, details);
    root.append(style, widget);
    let state = null;
    let chosenProvider = null;
    let disposed = false;
    let busy = false;
    let timer = null;
    let lastUrl = location.href;
    const display = globalThis.__agentMeterDisplay;
    const position = globalThis.__agentMeterPosition(host, moveHandle, () => state.settings, async anchor => {
        const response = await chrome.runtime.sendMessage({type: "pageBarPosition", position: anchor});
        if (!response?.ok || response.error) throw new Error("position");
    }, showError);
    const labels = {codex: "OpenAi", claude: "Claude", cursor: "Cursor", copilot: "GitHub", gemini: "Gemini"};
    const names = {
        codex: "OpenAI Codex",
        claude: "Claude",
        cursor: "Cursor",
        copilot: "GitHub",
        gemini: "Gemini Apps"
    };

    function button(text, label) {
        const element = document.createElement("button");
        element.type = "button";
        element.textContent = text;
        element.setAttribute("aria-label", label);
        element.title = label;
        return element;
    }

    function percent(quota) {
        return quota ? `${Math.round(quota.percent)}%` : "—";
    }

    function color(quota) {
        if (!quota) return "var(--muted)";
        const level = display.level(quota, state.settings.warningPercent);
        return level === "danger" ? "var(--bad)" : level === "warning" ? "var(--warn)" : "var(--good)";
    }

    function value(parent, prefix, quota) {
        parent.append(document.createTextNode(prefix));
        const text = document.createElement("span");
        text.textContent = percent(quota);
        text.style.color = color(quota);
        parent.append(text);
    }

    function paragraph(text, className = "") {
        const element = document.createElement("p");
        element.textContent = text;
        element.className = className;
        return element;
    }

    function render() {
        // Dragging owns placement until release; background publications must not move the bar mid-drag.
        if (!state || disposed || position.dragging()) return;
        const chosen = state.overview?.find(item => item.providerId === chosenProvider);
        if (chosen) state = {...state, ...chosen};
        if (!state.settings.showPageBar || state.context === "disabled" || state.context === "permission" || !labels[state.providerId]) {
            dispose();
            return;
        }
        host.dataset.theme = state.settings.theme;
        host.dataset.position = state.settings.barAnchor ?
            state.settings.barAnchor.y < 0.5 ? "top" : "bottom" : state.settings.pageBarPosition;
        if (!host.isConnected) document.documentElement.append(host);
        const snapshot = state.snapshot;
        const quotas = snapshot?.quotas || [];
        summary.replaceChildren();
        for (const item of state.overview || [state]) {
            const providerButton = document.createElement("a");
            providerButton.className = "provider-link";
            providerButton.textContent = `${labels[item.providerId]} | `;
            providerButton.href = item.websiteUrl;
            providerButton.target = "_blank";
            providerButton.rel = "noopener noreferrer";
            providerButton.title = `Open ${names[item.providerId]} website`;
            providerButton.setAttribute("aria-label", providerButton.title);
            const meters = item.snapshot?.quotas || [];
            providerButton.title += `\n${display.observation(item.snapshot)}`;
            if (item.snapshot?.notice) providerButton.title += `\n${item.snapshot.notice}`;
            else if (!meters.length) providerButton.title += "\nSign in on the provider website and refresh to read quota.";
            const low = meters.filter(quota => display.remaining(quota) <= state.settings.warningPercent);
            if (low.length) providerButton.title += `\nLow remaining quota: ${low.map(quota => quota.title).join(", ")}`;
            if (["codex", "claude", "gemini"].includes(item.providerId)) {
                value(providerButton, item.providerId === "codex" ? "D=" : "5h=", meters.find(quota => quota.title === "Session"));
                value(providerButton, " - W=", meters.find(quota => quota.title === "Weekly"));
            } else value(providerButton, "", meters[0]);
            providerButton.addEventListener("click", event => {
                if (!event.isTrusted) return;
                chosenProvider = item.providerId;
                // Keep this anchor in place for the browser's native link activation.
                // Website click handlers must not intercept the widget's navigation.
                event.stopPropagation();
            });
            summary.append(providerButton);
        }
        next.hidden = (state.overview?.length || 0) < 2 || !restore.hidden;
        refresh.disabled = busy || state.context !== "ready";
        refresh.textContent = busy ? "…" : "↻";
        details.replaceChildren();
        const heading = document.createElement("h2");
        heading.textContent = names[state.providerId];
        if (snapshot?.plan) {
            const plan = document.createElement("span");
            plan.className = "plan";
            plan.textContent = snapshot.plan;
            heading.append(plan);
        }
        details.append(heading);
        for (const quota of quotas) {
            const section = document.createElement("div");
            section.className = "quota";
            const row = document.createElement("div");
            row.className = "row";
            const title = document.createElement("span");
            title.textContent = quota.title;
            const number = document.createElement("span");
            number.className = "number";
            value(number, "", quota);
            number.append(document.createTextNode(quota.consumed ? " used" : " remaining"));
            row.append(title, number);
            const bar = document.createElement("div");
            bar.className = "bar";
            bar.setAttribute("role", "meter");
            bar.setAttribute("aria-label", `${quota.title} ${quota.consumed ? "used" : "remaining"}`);
            bar.setAttribute("aria-valuemin", "0");
            bar.setAttribute("aria-valuemax", "100");
            bar.setAttribute("aria-valuenow", String(quota.percent));
            const fill = document.createElement("div");
            fill.className = "fill";
            fill.style.width = `${quota.percent}%`;
            fill.style.background = color(quota);
            bar.append(fill);
            section.append(row, bar);
            if (quota.consumed) section.append(paragraph(`${Math.round(100 - quota.percent)}% available`));
            if (quota.resetsAt) {
                const countdown = paragraph(display.reset(quota.resetsAt));
                countdown.title = `Resets: ${new Date(quota.resetsAt).toLocaleString()}`;
                section.append(countdown);
            }
            details.append(section);
        }
        if (snapshot?.updatedAt) {
            const freshness = display.observation(snapshot);
            details.append(paragraph(freshness));
            summary.title = freshness;
        } else summary.title = "No measured quota yet. Check provider sign-in and refresh.";
        const notice = snapshot?.notice || (state.context === "usagePageRequired" ? "Open the usage page to update these values." :
            !quotas.length ? "No measured quota yet. Check provider sign-in and refresh." : "");
        if (notice) details.append(paragraph(notice, "notice"));
        if (snapshot?.diagnostic) {
            const labels = {
                session: "OpenAI website session",
                codexUsage: "Codex quota",
                claudeOrganizations: "Claude organization",
                claudeUsage: "Claude quota",
                copilotPages: "GitHub usage",
                injection: "Provider tab reader"
            };
            details.append(paragraph(`Read step: ${labels[snapshot.diagnostic.stage]}${snapshot.diagnostic.status ? ` · HTTP ${snapshot.diagnostic.status}` : ""}`));
        }
        const actions = document.createElement("div");
        actions.className = "actions";
        const usage = button("Open usage page ↗", "Open provider usage page");
        usage.addEventListener("click", event => {
            if (event.isTrusted) void send("pageOpenUsage").catch(showError);
        });
        actions.append(usage);
        details.append(actions);
        position.apply();
    }

    // User actions request fixed worker commands; the overlay itself never reads provider pages.
    async function send(type) {
        const response = await chrome.runtime.sendMessage({type, providerId: chosenProvider || state?.providerId});
        if (response?.error) throw new Error(response.error);
        return response;
    }

    function showError() {
        if (disposed) return;
        details.hidden = false;
        toggleDetails.setAttribute("aria-expanded", "true");
        details.append(paragraph("AgentMeter could not complete this action. Reload this page after updating the extension.", "notice"));
        position.apply();
    }

    async function update() {
        if (disposed) return;
        try {
            state = await send("pageState");
            render();
        } catch {
            dispose();
        }
    }

    toggleDetails.addEventListener("click", event => {
        if (!event.isTrusted) return;
        details.hidden = !details.hidden;
        toggleDetails.setAttribute("aria-expanded", String(!details.hidden));
        position.apply();
    });
    next.addEventListener("click", event => {
        if (!event.isTrusted || !state?.overview?.length) return;
        const index = state.overview.findIndex(item => item.providerId === state.providerId);
        chosenProvider = state.overview[(index + 1) % state.overview.length].providerId;
        render();
    });
    settingsButton.addEventListener("click", event => {
        if (event.isTrusted) void send("pageOpenSettings").catch(showError);
    });
    collapse.addEventListener("click", event => {
        if (!event.isTrusted) return;
        for (const element of [brand, summary, next, refresh, toggleDetails, settingsButton, collapse, details]) element.hidden = true;
        toggleDetails.setAttribute("aria-expanded", "false");
        restore.hidden = false;
        position.apply();
    });
    restore.addEventListener("click", event => {
        if (!event.isTrusted) return;
        for (const element of [brand, summary, next, refresh, toggleDetails, settingsButton, collapse]) element.hidden = false;
        restore.hidden = true;
        position.apply();
    });
    refresh.addEventListener("click", async event => {
        if (!event.isTrusted || busy) return;
        busy = true;
        render();
        try {
            state = await send("pageRefresh");
        } catch {
            showError();
        } finally {
            busy = false;
            render();
        }
    });
    root.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            details.hidden = true;
            toggleDetails.setAttribute("aria-expanded", "false");
            position.apply();
        }
    });
    // Runtime publication and page lifecycle share the same singleton and cleanup path.
    const listener = (message, sender) => {
        if (sender.id !== chrome.runtime.id) return;
        if (message.type === "pageBarRemove") dispose();
        if (message.type === "pageBarState" && !disposed) {
            state = message.state;
            render();
        }
    };
    const startTimer = () => {
        if (timer || disposed) return;
        // Repaint countdowns locally; only a changed SPA URL requests an updated extension state.
        timer = setInterval(() => {
            if (location.href !== lastUrl) {
                lastUrl = location.href;
                void update();
            } else render();
        }, 15000);
    };
    // BFCache can restore this document, so pagehide pauses the timer without destroying the bar.
    const pageHide = () => {
        clearInterval(timer);
        timer = null;
    };
    const pageShow = () => {
        startTimer();
        void update();
    };

    function dispose() {
        if (disposed) return;
        disposed = true;
        clearInterval(timer);
        position.dispose();
        chrome.runtime.onMessage.removeListener(listener);
        window.removeEventListener("pagehide", pageHide);
        window.removeEventListener("pageshow", pageShow);
        host.remove();
        delete globalThis.__agentMeterBar;
    }

    chrome.runtime.onMessage.addListener(listener);
    window.addEventListener("pagehide", pageHide);
    window.addEventListener("pageshow", pageShow);
    globalThis.__agentMeterBar = {update, dispose};
    startTimer();
    void update();
})();
