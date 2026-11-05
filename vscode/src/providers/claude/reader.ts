import {readFile} from "node:fs/promises";
import {homedir} from "node:os";
import {isAbsolute, join} from "node:path";
import {ProviderUsage} from "../../core/quota";

export async function readClaude(signal: AbortSignal): Promise<ProviderUsage> {
    const result: ProviderUsage = {name: "Claude Code", quotas: [], updatedAt: Date.now()};
    try {
        const directory = process.env.CLAUDE_CONFIG_DIR;
        const auth = JSON.parse(await readFile(join(directory && isAbsolute(directory) ? directory : join(homedir(), ".claude"), ".credentials.json"), "utf8")).claudeAiOauth;
        if (!auth?.accessToken || (auth.expiresAt && auth.expiresAt <= Date.now())) throw new Error();
        const response = await fetch("https://api.anthropic.com/api/oauth/usage", {
            signal, redirect: "error", headers: {
                Authorization: `Bearer ${auth.accessToken}`,
                "anthropic-beta": "oauth-2025-04-20",
                "User-Agent": "claude-code/2.1.131"
            }
        });
        if (!response.ok) return {...result, error: `Claude quota request failed (HTTP ${response.status}).`};
        const data = await response.json() as Record<string, { utilization?: number; resets_at?: string }>;
        result.plan = typeof auth.subscriptionType === "string" ? auth.subscriptionType : undefined;
        for (const [key, title] of [["five_hour", "Session"], ["seven_day", "Weekly"], ["seven_day_sonnet", "Sonnet"], ["seven_day_opus", "Opus"]]) {
            const value = data[key];
            if (typeof value?.utilization === "number" && Number.isFinite(value.utilization)) result.quotas.push({
                title,
                percentUsed: Math.max(0, Math.min(100, value.utilization)),
                reset: value.resets_at ? Date.parse(value.resets_at) / 1000 : undefined
            });
        }
        if (!result.quotas.length) result.error = "Claude did not report subscription quotas.";
    } catch {
        result.error = "Claude quota unavailable. Sign in with Claude Code and check your connection.";
    }
    return result;
}
