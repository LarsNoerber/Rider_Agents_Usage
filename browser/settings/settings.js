import {providers} from "../providers/catalog.js";

const ids = providers.map(provider => provider.id);

export function normalizeBarAnchor(value) {
    return value && typeof value.x === "number" && Number.isFinite(value.x) &&
    typeof value.y === "number" && Number.isFinite(value.y) ?
        {x: Math.max(0, Math.min(1, value.x)), y: Math.max(0, Math.min(1, value.y))} : null;
}

export function normalizeSettings(value = {}) {
    if (!value || typeof value !== "object") value = {};
    // An explicitly empty selection stays empty; only a missing/invalid selection uses defaults.
    const selected = Array.isArray(value.selected) ? ids.filter(id => value.selected.includes(id)) : [...ids];
    return {
        selected,
        // Preserve the stored identifier; one selection now controls reads and display.
        barProviders: [...selected],
        showPageBar: value.showPageBar !== false,
        allSitesBar: value.allSitesBar === true,
        pageBarPosition: value.pageBarPosition === "top" ? "top" : "bottom",
        barAnchor: normalizeBarAnchor(value.barAnchor),
        warningPercent: typeof value.warningPercent === "number" && Number.isFinite(value.warningPercent) ?
            Math.max(1, Math.min(100, Math.round(value.warningPercent))) : 25,
        refreshSeconds: Math.max(60, Math.min(3600, Number(value.refreshSeconds) || 300)),
        style: ["compact", "cards", "circles"].includes(value.style) ? value.style : "cards",
        theme: ["dark", "light", "system"].includes(value.theme) ? value.theme : "dark"
    };
}

export async function readSettings() {
    const {settings} = await chrome.storage.local.get("settings");
    return normalizeSettings(settings);
}

export async function writeSettings(value) {
    const settings = normalizeSettings(value);
    await chrome.storage.local.set({settings});
    return settings;
}

let writes = Promise.resolve();

export function updateSettings(patch) {
    // Read inside the write queue so rapid controls merge with the latest persisted choices.
    const writing = writes.catch(() => {
    }).then(async () => writeSettings({...await readSettings(), ...patch}));
    writes = writing;
    return writing;
}
