// Shared by isolated provider-page readers and the offscreen reader's detached quota markup.
(() => {
    const visible = element => element instanceof HTMLElement && element.getClientRects().length > 0 &&
        getComputedStyle(element).visibility !== "hidden";
    const text = element => (element.innerText || element.textContent || "").replace(/\s+/g, " ").trim();

    function plan(names, root = document) {
        for (const element of root.querySelectorAll("span,button,h1,h2,h3,p")) {
            if (root === document && !visible(element)) continue;
            const value = text(element);
            if (value.length > 50) continue;
            for (const name of names) {
                if (value.toLowerCase() === name.toLowerCase() || value.toLowerCase() === `${name.toLowerCase()} plan`)
                    return name;
            }
        }
        return null;
    }

    function resetTime(container) {
        // Only machine-readable reset times; relative/localized page prose is never saved or guessed.
        const times = [...container.querySelectorAll("time[datetime]")];
        if (times.length !== 1 || !/reset|erneuer|zurücksetz/i.test(text(container))) return null;
        const date = Date.parse(times[0].getAttribute("datetime"));
        return Number.isFinite(date) ? new Date(date).toISOString() : null;
    }

    function readDom(specs, names, root = document) {
        const quotas = [];
        let labelsFound = false;
        let percentagesFound = false;
        for (const spec of specs) {
            const candidates = [];
            for (const element of root.querySelectorAll("h1,h2,h3,h4,p,span,div,label,dt,dd,td,th,strong,b")) {
                if ((root === document && !visible(element)) || element.children.length > 3) continue;
                const label = text(element);
                if (label.length > 100 || !spec.label.test(label)) continue;
                labelsFound = true;
                // Find a small section with one unambiguous percent. Never inspect whole-page text or conversations.
                let container = element;
                for (let depth = 0; depth < 8 && container; depth++, container = container.parentElement) {
                    if (["BODY", "HTML", "MAIN"].includes(container.tagName)) break;
                    const context = text(container);
                    if (context.length > 700) break;
                    const percentages = [...context.matchAll(/(?<![\d.,+\-])(\d{1,3}(?:[.,]\d{1,2})?)\s*%/g)];
                    if (percentages.length > 1) break;
                    if (percentages.length !== 1) continue;
                    percentagesFound = true;
                    const percent = Number(percentages[0][1].replace(",", "."));
                    if (percent < 0 || percent > 100) break;
                    // Require explicit direction next to the percentage; ambiguous meters are unavailable.
                    const around = context.slice(Math.max(0, percentages[0].index - 24), percentages[0].index + percentages[0][0].length + 32);
                    const used = /(?:^|[^\p{L}])(?:used|consumed|verbraucht|genutzt)(?:$|[^\p{L}])/iu.test(around);
                    const remaining = /(?:^|[^\p{L}])(?:remaining|available|left|verbleibend|verbleiben|verfügbar|übrig)(?:$|[^\p{L}])/iu.test(around);
                    if (used === remaining) continue;
                    candidates.push({
                        title: spec.title, percent: spec.consumed === used ? percent : 100 - percent,
                        consumed: spec.consumed, resetsAt: resetTime(container)
                    });
                    break;
                }
            }
            const unique = [...new Map(candidates.map(value => [`${value.percent}:${value.resetsAt}`, value])).values()];
            if (unique.length === 1) quotas.push(unique[0]);
        }
        return {
            quotas, plan: quotas.length ? plan(names, root) : null,
            error: quotas.length ? null : !labelsFound ? "noLabels" : !percentagesFound ? "noPercentages" : "noDirection"
        };
    }

    async function request(path, options = {}, origin = location.origin) {
        // The caller supplies its fixed provider origin; page markup cannot redirect credentials elsewhere.
        const url = new URL(path, origin);
        if (url.origin !== origin || url.protocol !== "https:") throw new Error("destination");
        const response = await fetch(url, {
            ...options, credentials: "include", redirect: "error",
            cache: "no-store", signal: AbortSignal.timeout(15000)
        });
        if (!response.ok) {
            const error = new Error(response.status === 429 ? "rateLimited" : [401, 403].includes(response.status) ? "rejected" : "failed");
            error.status = response.status;
            throw error;
        }
        const reader = response.body.getReader();
        const chunks = [];
        let length = 0;
        try {
            for (; ;) {
                const {value, done} = await reader.read();
                if (done) break;
                length += value.byteLength;
                if (length > 2097152) throw new Error("failed");
                chunks.push(value);
            }
        } finally {
            await reader.cancel().catch(() => {
            });
        }
        const bytes = new Uint8Array(length);
        let offset = 0;
        for (const chunk of chunks) {
            bytes.set(chunk, offset);
            offset += chunk.byteLength;
        }
        return new TextDecoder().decode(bytes);
    }

    function parseHtml(html) {
        // Template parsing is inert: scripts, custom elements and resource links
        // stay inactive. Never insert these contents into a live document or reparse them.
        const template = document.createElement("template");
        template.innerHTML = html;
        const excluded = new Set([
            "style", "link", "base", "meta", "img", "iframe", "frame", "frameset",
            "object", "embed", "source", "video", "audio", "template"
        ]);
        for (const element of template.content.querySelectorAll("*")) {
            const tag = element.localName;
            const json = tag === "script" && element.getAttribute("type")?.toLowerCase() === "application/json";
            if (element.namespaceURI !== "http://www.w3.org/1999/xhtml" || excluded.has(tag) ||
                (tag === "script" && !json)) {
                element.remove();
                continue;
            }
            // Retain only the data consumed by quota readers. Fragment URLs are
            // inert metadata and still pass the Copilot reader's origin/path checks.
            const datetime = tag === "time" ? element.getAttribute("datetime") : null;
            const fragment = tag === "include-fragment" ?
                element.getAttribute("data-agentmeter-src") || element.getAttribute("src") : null;
            for (const attribute of [...element.attributes]) element.removeAttributeNode(attribute);
            if (json) element.setAttribute("type", "application/json");
            if (datetime !== null) element.setAttribute("datetime", datetime);
            if (fragment !== null) element.setAttribute("data-agentmeter-src", fragment);
        }
        return template.content;
    }

    globalThis.__agentMeterPage = Object.freeze({
        readDom, request, parseHtml, origin: location.origin, pathname: location.pathname,
        selectedOrganization: () => document.cookie.split(";").map(part => part.trim())
            .find(part => part.startsWith("lastActiveOrg="))?.slice("lastActiveOrg=".length)
    });
})();
