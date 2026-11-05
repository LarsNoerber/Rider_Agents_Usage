import {readFile} from "node:fs/promises";
import {homedir} from "node:os";
import {isAbsolute, join} from "node:path";
import {ProviderUsage} from "../../core/quota";

export async function readCursor(signal: AbortSignal): Promise<ProviderUsage> {
    const result: ProviderUsage = {name: "Cursor", quotas: [], updatedAt: Date.now()};
    try {
        const root = process.platform === "win32" ? join(process.env.APPDATA || join(homedir(), "AppData", "Roaming"), "Cursor")
            : process.platform === "darwin" ? join(homedir(), ".cursor")
                : join(process.env.XDG_CONFIG_HOME && isAbsolute(process.env.XDG_CONFIG_HOME) ? process.env.XDG_CONFIG_HOME : join(homedir(), ".config"), "cursor");
        const token = JSON.parse(await readFile(join(root, "auth.json"), "utf8")).accessToken;
        const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));
        const user = String(payload.sub).split("|").pop()!;
        if (!/^[A-Za-z0-9._-]+$/.test(user) || !Number.isFinite(payload.exp) || payload.exp <= Date.now() / 1000 + 60) throw new Error();
        let response = await fetch("https://cursor.com/api/usage-summary", {
            signal,
            redirect: "error",
            headers: {
                Cookie: `WorkosCursorSessionToken=${user}%3A%3A${token}`,
                Origin: "https://cursor.com",
                Referer: "https://cursor.com/dashboard"
            }
        });
        if (response.status === 403) response = await fetch("https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage", {
            method: "POST",
            body: "{}",
            signal,
            redirect: "error",
            headers: {
                Authorization: `Bearer ${token}`,
                "Connect-Protocol-Version": "1",
                "Content-Type": "application/json"
            }
        });
        if (!response.ok) return {...result, error: `Cursor quota request failed (HTTP ${response.status}).`};
        const data = await response.json() as any;
        result.plan = typeof data.membershipType === "string" ? data.membershipType : undefined;
        for (const quota of [data.individualUsage?.plan ?? data.planUsage, data.individualUsage?.overall, data.teamUsage?.pooled]) {
            const used = quota?.totalPercentUsed ?? (quota?.limit > 0 ? 100 * (quota.used ?? quota.limit - quota.remaining) / quota.limit : undefined);
            if (typeof used !== "number" || !Number.isFinite(used)) continue;
            result.quotas.push({title: "Included plan", percentUsed: Math.max(0, Math.min(100, used))});
            break;
        }
        if (!result.quotas.length) result.error = "Cursor did not report a finite quota for this plan.";
    } catch {
        result.error = "Cursor quota unavailable. Sign in to the Cursor agent and check your connection.";
    }
    return result;
}
