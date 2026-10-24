import * as vscode from "vscode";

export interface CopilotUsage {
    plan?: string;
    percentUsed?: number;
    quotaLabel?: string;
    unlimited?: boolean;
    usedRequests?: number;
    entitlementRequests?: number;
    resetDate?: string;
    updatedAt: number;
    error?: string;
    needsSignIn?: boolean;
}

type JsonObject = Record<string, unknown>;

/** Reads Copilot quota through GitHub's internal endpoint using VS Code's GitHub authentication. */
export class CopilotUsageReader {
    private disposed = false;

    async read(interactive = false): Promise<CopilotUsage> {
        if (this.disposed) throw new Error("Copilot usage reader is disposed");

        let session: vscode.AuthenticationSession | undefined;
        try {
            // Background polling stays silent; only the explicit sign-in command may prompt.
            const options = interactive ? {createIfNone: true} : {silent: true};
            session = await vscode.authentication.getSession("github", ["read:user"], options);
        } catch {
            return {updatedAt: Date.now(), needsSignIn: true, error: "Sign in to GitHub to view Copilot usage"};
        }
        if (!session) {
            return {updatedAt: Date.now(), needsSignIn: true, error: "Sign in to GitHub to view Copilot usage"};
        }

        try {
            const response = await fetch("https://api.github.com/copilot_internal/user", {
                headers: {
                    Accept: "application/json",
                    Authorization: `Bearer ${session.accessToken}`,
                    "X-GitHub-Api-Version": "2025-04-01",
                    "Editor-Version": `vscode/${vscode.version}`,
                    "Editor-Plugin-Version": "agents-usage-vscode",
                },
            });
            if (!response.ok) {
                return {
                    updatedAt: Date.now(),
                    error: response.status === 401 || response.status === 403
                        ? "GitHub did not authorize Copilot quota access. Sign in to GitHub and refresh."
                        : `GitHub Copilot quota request failed (HTTP ${response.status})`,
                };
            }
            return parseCopilotUsage(await response.json());
        } catch {
            return {updatedAt: Date.now(), error: "Could not connect to GitHub Copilot quota service"};
        }
    }

    dispose(): void {
        this.disposed = true;
    }
}

function parseCopilotUsage(payload: unknown): CopilotUsage {
    const root = objectValue(payload);
    if (!root) return {updatedAt: Date.now(), error: "GitHub returned an invalid Copilot quota response"};
    const snapshots = objectValue(root.quota_snapshots);
    const plan = stringValue(root.copilot_plan);
    const freePlan = plan?.trim().toLowerCase() === "free";
    const tokenBilling = root.token_based_billing === true || root.token_based_billing_enabled === true;
    // Free's premium snapshot can be an empty, zero-balance placeholder. Its included allowance is chat.
    const categories = freePlan ? ["chat", "completions"]
        : ["premium_models", "premium_interactions", "chat", "completions"];
    const premiumReported = objectValue(snapshots?.premium_models) !== undefined
        || objectValue(snapshots?.premium_interactions) !== undefined;
    const category = categories.find((name) => {
        const snapshot = objectValue(snapshots?.[name]);
        if (!snapshot) return false;
        const entitlement = finiteNumber(snapshot.entitlement);
        const remaining = finiteNumber(snapshot.quota_remaining) ?? finiteNumber(snapshot.remaining);
        const percentage = finiteNumber(snapshot.percent_remaining);
        const unlimited = snapshot.unlimited === true || entitlement === -1;
        if (!freePlan && premiumReported && unlimited && (name === "chat" || name === "completions")) {
            return false; // Unlimited basic chat/completions say nothing about the missing premium allowance.
        }
        if (snapshot.unlimited !== true && entitlement === 0 && (remaining ?? 0) === 0 && percentage === 0) {
            return false; // An empty 0/0 snapshot does not establish an exhausted allowance.
        }
        if (!unlimited && !tokenBilling && entitlement === 0) return false;
        return snapshot.unlimited === true || finiteNumber(snapshot.entitlement) === -1
            || finiteNumber(snapshot.percent_remaining) !== undefined
            || (finiteNumber(snapshot.entitlement) ?? 0) > 0;
    });
    const quota = category ? objectValue(snapshots?.[category]) : undefined;
    if (!quota) return {
        updatedAt: Date.now(),
        plan,
        error: "Copilot has not reported a usable quota for this account",
    };

    const entitlementRequests = finiteNumber(quota.entitlement);
    const unlimited = quota.unlimited === true || entitlementRequests === -1;
    const quotaLabel = tokenBilling && category !== "completions" ? "AI credits"
        : category === "chat" ? "Chat" : category === "completions" ? "Completions" : "Premium requests";
    const resetDate = stringValue(root.quota_reset_date_utc) ?? stringValue(root.quota_reset_date)
        ?? stringValue(quota.reset_date);
    if (unlimited) return {plan, quotaLabel, unlimited: true, resetDate, updatedAt: Date.now()};

    const remainingPercent = finiteNumber(quota.percent_remaining);
    const remainingRequests = finiteNumber(quota.quota_remaining) ?? finiteNumber(quota.remaining);
    const usedRequests = entitlementRequests !== undefined && remainingRequests !== undefined
    && entitlementRequests > 0 ? Math.max(0, entitlementRequests - remainingRequests)
        : undefined;
    // Copilot displays consumption; convert reported remaining quota exactly once here.
    const percentUsed = remainingPercent !== undefined
        ? 100 - remainingPercent
        : entitlementRequests !== undefined && entitlementRequests > 0 && usedRequests !== undefined
            ? 100 * usedRequests / entitlementRequests
            : undefined;

    return {
        plan,
        quotaLabel,
        unlimited: false,
        percentUsed: percentUsed === undefined ? undefined : Math.round(Math.max(0, Math.min(100, percentUsed))),
        usedRequests: tokenBilling ? undefined : usedRequests,
        entitlementRequests: tokenBilling || entitlementRequests === undefined || entitlementRequests <= 0
            ? undefined : entitlementRequests,
        resetDate,
        updatedAt: Date.now(),
        error: percentUsed === undefined ? "Copilot quota details were not available" : undefined,
    };
}

function finiteNumber(value: unknown): number | undefined {
    const parsed = typeof value === "number" ? value
        : typeof value === "string" && value.trim() ? Number(value) : Number.NaN;
    return Number.isFinite(parsed) ? parsed : undefined;
}

function stringValue(value: unknown): string | undefined {
    return typeof value === "string" && value.trim() ? value : undefined;
}

function objectValue(value: unknown): JsonObject | undefined {
    return typeof value === "object" && value !== null && !Array.isArray(value) ? value as JsonObject : undefined;
}
