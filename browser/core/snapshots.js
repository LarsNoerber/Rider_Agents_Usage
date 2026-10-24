// Provider output crosses into extension storage/UI only through these fixed public labels.
const titles = new Set([
    "Session", "Weekly", "Sonnet weekly", "Opus weekly", "Premium", "Chat", "Included credits",
    "Inline suggestions", "Included plan", "Auto", "API", "Shared team pool"
]);
const plans = new Set([
    "Free", "Plus", "Pro", "Max", "Max 5x", "Max 20x", "Team", "Teams", "Business", "Enterprise",
    "Ultra", "Pro+", "Student", "Gemini Apps"
]);
const stages = new Set(["session", "codexUsage", "claudeOrganizations", "claudeUsage", "copilotPages", "injection"]);
export const notices = Object.freeze({
    permission: "Allow access to this provider to read its usage page.",
    closed: "Open the usage page and keep it open for automatic updates.",
    loading: "Waiting for the usage page to finish loading.",
    unsupported: "No recognized quota found. Check sign-in and open the usage section. The page format may have changed.",
    noLabels: "No supported quota section found on this usage page. The page may still be loading or use a different layout.",
    noPercentages: "The usage page has recognized quota sections but no readable percentages. Amounts and activity counts are not quota percentages.",
    noDirection: "A percentage was found, but its used/remaining meaning was not explicit. AgentMeter has not guessed a quota value.",
    failed: "The usage read failed. Check the usage page and try again.",
    rejected: "The provider rejected the usage request. Check sign-in on its website.",
    organizationRequired: "Select your Claude organization on its website, then refresh. AgentMeter will not choose between accounts.",
    noQuota: "The provider's quota response contains no numeric allowance. Check whether its own Usage page shows a subscription quota for this account.",
    claudeNoQuota: "Claude returned no quota percentages. Usage bars are documented for paid subscription plans; a Free account may not report these values. Missing quota data alone does not identify your plan.",
    quotaFormat: "The quota request succeeded, but its response did not contain the supported quota fields. Open the provider's Usage page to check its current format.",
    rateLimited: "The provider asked us to wait. Automatic retry is paused for 15 minutes."
});

export function sanitizeSnapshot(id, value) {
    // Reject ambiguous directions and out-of-range values instead of inferring a quota.
    const quotas = Array.isArray(value?.quotas) ? value.quotas.slice(0, 8).flatMap(quota => {
        if (!quota || !titles.has(quota.title) || typeof quota.percent !== "number" || !Number.isFinite(quota.percent) ||
            quota.percent < 0 || quota.percent > 100 || typeof quota.consumed !== "boolean") return [];
        const reset = typeof quota.resetsAt === "string" ? Date.parse(quota.resetsAt) : NaN;
        return [{
            title: quota.title, percent: quota.percent, consumed: quota.consumed,
            resetsAt: Number.isFinite(reset) ? new Date(reset).toISOString() : null
        }];
    }) : [];
    return {
        providerId: id, quotas, plan: plans.has(value?.plan) ? value.plan : null,
        notice: quotas.length ? null : notices[value?.error] || notices.unsupported,
        error: value?.error === "rateLimited" ? "rateLimited" : null,
        diagnostic: stages.has(value?.diagnostic?.stage) ? {
            stage: value.diagnostic.stage,
            status: Number.isInteger(value.diagnostic.status) && value.diagnostic.status >= 300 && value.diagnostic.status <= 599 ? value.diagnostic.status : null
        } : null,
        updatedAt: quotas.length ? new Date().toISOString() : null, isCached: false
    };
}

export function restoreSnapshot(id, value) {
    // Restoration validates the payload again and never makes a saved reading look fresh.
    const snapshot = sanitizeSnapshot(id, value);
    const time = typeof value?.updatedAt === "string" ? Date.parse(value.updatedAt) : NaN;
    if (!snapshot.quotas.length || !Number.isFinite(time) || time > Date.now()) return null;
    return {...snapshot, plan: null, updatedAt: new Date(time).toISOString(), isCached: true};
}

export function numericCache(snapshot) {
    return {quotas: snapshot.quotas, updatedAt: snapshot.updatedAt};
}
