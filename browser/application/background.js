import {providerById} from "../providers/catalog.js";
import {updateSettings, normalizeBarAnchor} from "../settings/settings.js";
import {
    refreshCurrentPage,
    stateForCurrentPage,
    stateForBar,
    invalidate,
    updateAlarm,
    clearSavedUsage
} from "./coordinator.js";
import {syncPageBars, publishPageBars, removePageBars, isProviderPageSender} from "./page-bars.js";

// Reconcile scripts when an unpacked extension/worker is reloaded, as well as at install/startup.
void syncPageBars().catch(() => {
});
chrome.runtime.onInstalled.addListener(details => {
    if (details.reason === "install") void chrome.runtime.openOptionsPage();
    void syncPageBars().catch(() => {
    });
    void updateAlarm().catch(() => {
    });
});
chrome.runtime.onStartup.addListener(() => {
    void syncPageBars().catch(() => {
    });
    void updateAlarm().catch(() => {
    });
});
chrome.alarms.onAlarm.addListener(alarm => {
    if (alarm.name === "usage") void refreshCurrentPage().catch(() => {
    });
});
chrome.tabs.onActivated.addListener(() => {
    // Keep other providers' background reads when the active tab changes.
    void refreshCurrentPage().catch(() => {
    });
});
chrome.windows.onFocusChanged.addListener(windowId => {
    if (windowId !== chrome.windows.WINDOW_ID_NONE) void refreshCurrentPage().catch(() => {
    });
});
chrome.tabs.onUpdated.addListener((_id, change, tab) => {
    if (!tab.active) return;
    if (change.url || change.status === "loading") {
        void chrome.action.setBadgeText({text: ""}).catch(() => {
        });
    }
    if (change.status === "complete") void refreshCurrentPage().catch(() => {
    });
});
chrome.permissions.onRemoved.addListener(() => {
    // Invalidate reads first, then clear measurements and rebuild bars for the remaining permissions.
    invalidate();
    void (async () => {
        await clearSavedUsage();
        await removePageBars();
        await syncPageBars();
    })().catch(() => {
    });
});
chrome.permissions.onAdded.addListener(() => {
    void syncPageBars().catch(() => {
    });
    void refreshCurrentPage().catch(() => {
    });
});
chrome.storage.onChanged.addListener((changes, area) => {
    if ((area === "session" && changes.readings) || (area === "local" && changes.usageCache))
        void publishPageBars().catch(() => {
        });
});
chrome.runtime.onMessage.addListener((message, sender, reply) => {
    // Extension documents can change settings; content scripts use the restricted page-bar route.
    const extensionUi = sender.id === chrome.runtime.id && sender.url?.startsWith(chrome.runtime.getURL("ui/"));
    if (!extensionUi && (sender.id !== chrome.runtime.id || !sender.tab || message?.target === "quotaReader")) return false;
    const handle = async () => {
        // Isolated page bars may request their own state, active-tab refresh, and fixed usage link only.
        // The only writable page-bar choice is a bounded display position.
        if (!extensionUi) {
            if (!await isProviderPageSender(sender)) return {error: "Website access is not allowed."};
            const tab = await chrome.tabs.get(sender.tab.id);
            if (new URL(tab.url).origin !== new URL(sender.url).origin) return {error: "Page changed."};
            switch (message?.type) {
                case "pageState":
                    return stateForBar(tab, message.providerId);
                case "pageBarPosition": {
                    const barAnchor = normalizeBarAnchor(message.position);
                    if (!barAnchor) return {error: "Invalid bar position."};
                    await updateSettings({barAnchor});
                    await publishPageBars();
                    return {ok: true};
                }
                case "pageOpenSettings":
                    await chrome.tabs.create({url: chrome.runtime.getURL("ui/dashboard.html#page-bar-settings")});
                    return {ok: true};
                case "pageRefresh": {
                    await refreshCurrentPage();
                    return stateForBar(await chrome.tabs.get(tab.id), message.providerId);
                }
                case "pageOpenUsage": {
                    const state = await stateForBar(tab, message.providerId);
                    const provider = providerById(state.providerId);
                    if (!provider) return {error: "No provider enabled."};
                    await chrome.tabs.create({url: provider.url});
                    return {ok: true};
                }
                case "pageOpenProvider": {
                    const state = await stateForBar(tab, message.providerId);
                    const provider = providerById(state.providerId);
                    if (!provider) return {error: "No provider enabled."};
                    await chrome.tabs.create({url: new URL(provider.origin).origin + "/"});
                    return {ok: true};
                }
                default:
                    return {error: "Unknown page action."};
            }
        }
        // Only trusted extension UI documents reach these privileged commands.
        switch (message?.type) {
            case "state":
                return stateForCurrentPage(message.tabId, message.providerId);
            case "refresh":
                return refreshCurrentPage(message.tabId, message.providerId);
            case "settings":
            case "settingsPatch": {
                const value = message.type === "settingsPatch" ? message.patch : message.settings;
                const keys = ["selected", "showPageBar", "allSitesBar", "pageBarPosition", "barAnchor", "warningPercent", "refreshSeconds", "style", "theme"];
                const patch = Object.fromEntries(keys.filter(key => Object.hasOwn(value || {}, key)).map(key => [key, value[key]]));
                if (Object.hasOwn(patch, "selected")) invalidate();
                const settings = await updateSettings(patch);
                if (Object.hasOwn(patch, "refreshSeconds")) await updateAlarm();
                if (["selected", "showPageBar", "allSitesBar"].some(key => Object.hasOwn(patch, key))) await syncPageBars();
                else await publishPageBars();
                // Confirm persisted/displayed choices without waiting for quota requests.
                void refreshCurrentPage().catch(() => {
                });
                return {settings};
            }
            case "openUsage": {
                const provider = providerById(message.providerId);
                if (!provider) throw new Error("provider");
                await chrome.tabs.create({url: provider.url});
                return {ok: true};
            }
            case "clear":
                await clearSavedUsage();
                return stateForCurrentPage();
            default:
                return {error: "Unknown action."};
        }
    };
    void handle().then(reply, () => reply({error: "AgentMeter could not complete this action. Please try again."}));
    // Keep the message channel open while the asynchronous action resolves.
    return true;
});
