import crossSpawn = require("cross-spawn");
import {ChildProcess} from "node:child_process";
import * as readline from "node:readline";

export interface CodexUsage {
    fiveHourLeft?: number;
    fiveHourReset?: number;
    weeklyLeft?: number;
    weeklyReset?: number;
    credits?: number;
    plan?: string;
    updatedAt: number;
    error?: string;
}

type JsonObject = Record<string, unknown>;
type PendingResponse = {
    resolve: (value: JsonObject) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
};

/** Reads only the quota that Codex itself reports through its local app-server. */
export class CodexUsageReader {
    private activeProcess: ChildProcess | undefined;
    private disposed = false;

    async read(configuredPath: string, extensionVersion: string): Promise<CodexUsage> {
        if (this.disposed) throw new Error("Codex usage reader is disposed");

        const command = configuredPath.trim() || "codex";
        const child = crossSpawn(command, ["app-server", "--stdio"], {
            stdio: ["pipe", "pipe", "ignore"],
            windowsHide: true,
        });
        this.activeProcess = child;

        const pending = new Map<string, PendingResponse>();
        // JSON-RPC notifications can interleave with responses; only matching IDs complete requests.
        const lines = readline.createInterface({input: child.stdout!, crlfDelay: Infinity});
        let processError: Error | undefined;

        lines.on("line", (line) => {
            let message: JsonObject;
            try {
                const parsed: unknown = JSON.parse(line);
                if (!isObject(parsed)) return;
                message = parsed;
            } catch {
                return;
            }

            if (message.id === undefined || message.id === null) return;
            const request = pending.get(String(message.id));
            if (!request) return;
            pending.delete(String(message.id));
            clearTimeout(request.timeout);
            if (isObject(message.error)) {
                request.reject(new Error(stringValue(message.error.message) ?? "Codex CLI request failed"));
            } else {
                request.resolve(message);
            }
        });

        child.on("error", (error) => {
            processError = error;
            rejectPending(pending, error);
        });
        child.on("exit", (code) => {
            if (pending.size > 0) {
                rejectPending(pending, new Error(processError?.message ?? `Codex CLI closed the connection (${code ?? "unknown"})`));
            }
        });

        const request = (id: number, method: string, params: JsonObject | null = null): Promise<JsonObject> => {
            if (!child.stdin || child.stdin.destroyed) return Promise.reject(new Error("Codex CLI input stream is unavailable"));
            return new Promise((resolve, reject) => {
                const key = String(id);
                const timeout = setTimeout(() => {
                    pending.delete(key);
                    reject(new Error("Codex CLI did not respond within 30 seconds"));
                }, 30_000);
                pending.set(key, {resolve, reject, timeout});
                child.stdin!.write(`${JSON.stringify({id, method, params})}\n`, (error) => {
                    if (error && pending.delete(key)) {
                        clearTimeout(timeout);
                        reject(error);
                    }
                });
            });
        };

        try {
            // Initialize the protocol before reading optional account metadata and quota windows.
            await request(1, "initialize", {
                clientInfo: {name: "agents-usage-vscode", version: extensionVersion},
            });
            child.stdin?.write(`${JSON.stringify({method: "initialized"})}\n`);

            let plan: string | undefined;
            try {
                const accountResponse = await request(2, "account/read", {refreshToken: false});
                const account = objectValue(objectValue(accountResponse.result)?.account);
                plan = stringValue(account?.planType)
                    ?? (stringValue(account?.type) === "apiKey" ? "API key" : undefined);
            } catch {
                // Quota reporting can still work when account metadata is unavailable.
            }

            const response = await request(3, "account/rateLimits/read");
            return parseRateLimits(response, plan);
        } catch (error) {
            if (processError) throw new Error("Could not start the Codex CLI. Check the configured path and installation.");
            throw error instanceof Error ? error : new Error("Codex CLI usage could not be read");
        } finally {
            // Each refresh owns a short-lived app-server process and all of its request timers.
            lines.close();
            for (const request of pending.values()) clearTimeout(request.timeout);
            pending.clear();
            if (this.activeProcess === child) this.activeProcess = undefined;
            child.kill();
        }
    }

    dispose(): void {
        this.disposed = true;
        this.activeProcess?.kill();
        this.activeProcess = undefined;
    }
}

function parseRateLimits(response: JsonObject, accountPlan?: string): CodexUsage {
    const result = objectValue(response.result);
    const snapshot = objectValue(objectValue(result?.rateLimitsByLimitId)?.codex)
        ?? objectValue(result?.rateLimits);
    if (!snapshot) return {
        updatedAt: Date.now(),
        plan: accountPlan,
        error: "Codex app-server did not return usage data"
    };

    const primary = objectValue(snapshot.primary);
    const secondary = objectValue(snapshot.secondary);
    const fiveHourLeft = remaining(primary);
    const weeklyLeft = remaining(secondary);
    return {
        fiveHourLeft,
        fiveHourReset: epochSeconds(primary?.resetsAt),
        weeklyLeft,
        weeklyReset: epochSeconds(secondary?.resetsAt),
        credits: finiteNumber(objectValue(snapshot.credits)?.balance),
        plan: stringValue(snapshot.planType) ?? stringValue(objectValue(result?.rateLimits)?.planType) ?? accountPlan,
        updatedAt: Date.now(),
        error: fiveHourLeft === undefined && weeklyLeft === undefined
            ? "No 5-hour or weekly quota was reported by Codex"
            : undefined,
    };
}

function remaining(window: JsonObject | undefined): number | undefined {
    const used = finiteNumber(window?.usedPercent);
    return used === undefined ? undefined : 100 - Math.round(Math.max(0, Math.min(100, used)));
}

function epochSeconds(value: unknown): number | undefined {
    const parsed = finiteNumber(value);
    return parsed === undefined ? undefined : Math.trunc(parsed);
}

function finiteNumber(value: unknown): number | undefined {
    const parsed = typeof value === "number" ? value : typeof value === "string" ? Number(value) : Number.NaN;
    return Number.isFinite(parsed) ? parsed : undefined;
}

function stringValue(value: unknown): string | undefined {
    return typeof value === "string" && value.trim() ? value : undefined;
}

function objectValue(value: unknown): JsonObject | undefined {
    return isObject(value) ? value : undefined;
}

function isObject(value: unknown): value is JsonObject {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

function rejectPending(pending: Map<string, PendingResponse>, error: Error): void {
    for (const request of pending.values()) {
        clearTimeout(request.timeout);
        request.reject(error);
    }
    pending.clear();
}
