import {providers, providerById} from "../providers/catalog.js";
import {percentText, quotaColor, ageText, resetText} from "../core/format.js";

const $ = id => document.getElementById(id);
let state;
let busy = false;
let tabId;
let preferredId;
const contextText = {
    unsupportedSite: "No provider selected. Enable a provider in Settings to see its quota.",
    permission: "Allow access to this provider's website. AgentMeter reads only usage values.",
    disabled: "This provider is disabled. Enable it in Settings.",
    usagePageRequired: "The toolbar shows the last saved quota. Open the usage page to update it.",
    loading: "The usage page is loading. Refresh once its values appear.", ready: ""
};

async function send(message) {
    const result = await chrome.runtime.sendMessage({...message, tabId, providerId: message.providerId ?? preferredId});
    if (result?.error) throw new Error(result.error);
    return result;
}

function render(value) {
    state = value;
    const provider = providerById(state.providerId);
    const snapshot = state.snapshot;
    const choices = providers.filter(item => state.settings.selected.includes(item.id) || item.id === state.providerId);
    $("provider-select").replaceChildren(...choices.map(item => {
        const option = document.createElement("option");
        option.value = item.id;
        option.textContent = item.name;
        return option;
    }));
    $("provider-select").value = state.providerId || "";
    $("provider-select").disabled = busy || !choices.length;
    document.body.dataset.theme = state.settings.theme;
    $("quotas").dataset.style = state.settings.style;
    $("provider-name").textContent = provider?.name || "Current page";
    $("provider-name").style.color = provider?.color || "";
    $("plan").hidden = !snapshot?.plan;
    $("plan").textContent = snapshot?.plan || "";
    $("context").textContent = state.context === "ready" && !snapshot ?
        "No reading yet. Sign in to this provider in your browser, then choose Refresh." : contextText[state.context] || "";
    $("context").hidden = !$("context").textContent;
    $("allow").hidden = state.context !== "permission";
    $("open").hidden = !provider || state.context === "disabled";
    $("refresh").disabled = busy || state.context !== "ready";
    $("provider-links").hidden = Boolean(provider);
    $("quotas").replaceChildren();
    for (const quota of snapshot?.quotas || []) {
        const card = document.createElement("section");
        card.className = "quota";
        card.style.setProperty("--quota-color", quotaColor(quota, state.settings.warningPercent));
        card.style.setProperty("--quota-angle", `${quota.percent * 3.6}deg`);
        const heading = document.createElement("h3");
        heading.textContent = quota.title === "Session" && provider?.id === "codex" ? "Daily / session" : quota.title;
        const number = document.createElement("strong");
        number.textContent = percentText(quota.percent);
        const direction = document.createElement("span");
        direction.className = "direction";
        direction.textContent = quota.consumed ? "used" : "remaining";
        const row = document.createElement("div");
        row.className = "quota-row";
        const value = document.createElement("div");
        value.className = "quota-value";
        value.append(number, direction);
        row.append(heading, value);
        const bar = document.createElement("div");
        bar.className = "bar";
        bar.setAttribute("role", "meter");
        bar.setAttribute("aria-label", `${heading.textContent} ${direction.textContent}`);
        bar.setAttribute("aria-valuemin", "0");
        bar.setAttribute("aria-valuemax", "100");
        bar.setAttribute("aria-valuenow", String(quota.percent));
        const fill = document.createElement("div");
        fill.style.width = `${quota.percent}%`;
        bar.append(fill);
        card.append(row, bar);
        if (quota.consumed) {
            const remaining = document.createElement("p");
            remaining.className = "secondary";
            remaining.textContent = `${percentText(100 - quota.percent)} available`;
            card.append(remaining);
        }
        if (quota.resetsAt) {
            const reset = document.createElement("p");
            reset.className = "secondary";
            reset.textContent = resetText(quota.resetsAt);
            reset.title = new Date(quota.resetsAt).toLocaleString();
            card.append(reset);
        }
        $("quotas").append(card);
    }
    $("freshness").textContent = snapshot?.updatedAt ? `${snapshot.isCached ? "Saved value · " : "Read · "}${ageText(snapshot.updatedAt)}` : "";
    $("freshness").title = snapshot?.updatedAt ? new Date(snapshot.updatedAt).toLocaleString() : "";
    $("error").hidden = !snapshot?.notice;
    $("error").textContent = snapshot?.notice || "";
    if (snapshot?.diagnostic && snapshot.notice) {
        const labels = {
            session: "OpenAI website session", codexUsage: "Codex quota", claudeOrganizations: "Claude organization",
            claudeUsage: "Claude quota", copilotPages: "GitHub usage", injection: "Provider tab reader"
        };
        $("error").textContent += ` Read step: ${labels[snapshot.diagnostic.stage]}${snapshot.diagnostic.status ? ` · HTTP ${snapshot.diagnostic.status}` : ""}.`;
    }
}

async function perform(action) {
    if (busy) return;
    busy = true;
    $("refresh").disabled = true;
    $("refresh").textContent = "Reading…";
    $("allow").disabled = true;
    $("error").hidden = true;
    $("quotas").setAttribute("aria-busy", "true");
    try {
        render(await action());
    } catch (error) {
        $("error").textContent = error.message;
        $("error").hidden = false;
    } finally {
        busy = false;
        $("provider-select").disabled = !$("provider-select").options.length;
        $("allow").disabled = false;
        $("refresh").textContent = "Refresh";
        $("refresh").disabled = state?.context !== "ready";
        $("quotas").setAttribute("aria-busy", "false");
    }
}

$("settings").addEventListener("click", () => void chrome.runtime.openOptionsPage());
$("provider-select").addEventListener("change", () => {
    preferredId = $("provider-select").value;
    void perform(async () => {
        const initial = await send({type: "state"});
        render(initial);
        return initial.context === "ready" ? send({type: "refresh"}) : initial;
    });
});
$("refresh").addEventListener("click", () => void perform(() => send({type: "refresh"})));
$("allow").addEventListener("click", () => {
    const provider = providerById(state?.providerId);
    if (!provider) return;
    const request = chrome.permissions.request({origins: [provider.origin]});
    void perform(async () => {
        if (!await request) throw new Error("Website access was not granted.");
        return send({type: "refresh"});
    });
});
$("open").addEventListener("click", () => {
    void send({type: "openUsage", providerId: state?.providerId}).catch(error => {
        $("error").textContent = error.message;
        $("error").hidden = false;
    });
});
for (const provider of providers) {
    const link = document.createElement("a");
    link.href = provider.url;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = provider.name;
    $("links").append(link);
}
const timer = setInterval(() => {
    if (state && !busy) render(state);
}, 30000);
window.addEventListener("pagehide", () => clearInterval(timer), {once: true});
void perform(async () => {
    const [tab] = await chrome.tabs.query({active: true, currentWindow: true});
    tabId = tab?.id;
    const initial = await send({type: "state"});
    render(initial);
    return initial.context === "ready" ? send({type: "refresh"}) : initial;
});
