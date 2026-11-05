import * as vscode from "vscode";
import {CodexUsageReader, CodexUsage} from "./codex";
import {CopilotUsageReader, CopilotUsage} from "./copilot";
import {readClaude} from "./providers/claude/reader";
import {readCursor} from "./providers/cursor/reader";
import {ProviderUsage} from "./core/quota";
import {UsageView} from "./usageView";

export function activate(context: vscode.ExtensionContext): void {
    // Readers own provider access; the view and status items render the latest completed refresh.
    const codexReader = new CodexUsageReader();
    const copilotReader = new CopilotUsageReader();
    const view = new UsageView();
    const webview = vscode.window.registerWebviewViewProvider("agentsUsage.usage", view);
    const codexStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    codexStatus.name = "Agents Usage: Codex";
    codexStatus.command = "agentsUsage.openUsage";
    const copilotStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 99);
    copilotStatus.name = "Agents Usage: GitHub Copilot";
    copilotStatus.command = "agentsUsage.openUsage";

    let disposed = false;
    let revision = 0;
    let abort: AbortController | undefined;
    const extraStatus = ["Claude Code", "Cursor"].map(name => {
        const item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 98);
        item.name = name; item.command = "agentsUsage.openUsage"; context.subscriptions.push(item); return item;
    });
    let refreshInFlight: Promise<void> | undefined;
    let timer: NodeJS.Timeout | undefined;
    let latestExtras: (ProviderUsage | undefined)[] = [];
    let latestCodex: CodexUsage | undefined;
    let latestCopilot: CopilotUsage | undefined;

    const renderStatus = (): void => {
        const config = vscode.workspace.getConfiguration("agentsUsage");
        const showStatusBar = config.get<boolean>("showStatusBar", true);
        const codexEnabled = config.get<boolean>("providers.codex.enabled", true);
        const copilotEnabled = config.get<boolean>("providers.copilot.enabled", true);

        if (showStatusBar && codexEnabled) {
            if (!latestCodex) {
                codexStatus.text = "$(pulse) Codex Usage";
                codexStatus.tooltip = "Click to open Agents Usage and read the Codex quota";
                codexStatus.color = undefined;
            } else if (latestCodex.error && latestCodex.fiveHourLeft === undefined && latestCodex.weeklyLeft === undefined) {
                codexStatus.text = "$(warning) Codex Usage";
                codexStatus.tooltip = latestCodex.error;
                codexStatus.color = new vscode.ThemeColor("statusBarItem.warningForeground");
            } else {
                const five = latestCodex.fiveHourLeft === undefined ? "-" : `${latestCodex.fiveHourLeft}%`;
                const week = latestCodex.weeklyLeft === undefined ? "-" : `${latestCodex.weeklyLeft}%`;
                codexStatus.text = `OpenAi | D=${five} - W=${week}`;
                codexStatus.tooltip = latestCodex.error ?? "Click to open Agents Usage";
                const lowest = Math.min(latestCodex.fiveHourLeft ?? 100, latestCodex.weeklyLeft ?? 100);
                codexStatus.color = new vscode.ThemeColor(lowest < 20 ? "charts.red" : lowest < 50 ? "charts.yellow" : "charts.green");
            }
            codexStatus.show();
        } else {
            codexStatus.hide();
        }

        if (showStatusBar && copilotEnabled) {
            if (!latestCopilot) {
                copilotStatus.text = "$(github) Copilot Usage";
                copilotStatus.tooltip = "Click to open Agents Usage and read GitHub Copilot quota";
                copilotStatus.color = undefined;
            } else if (latestCopilot.needsSignIn) {
                copilotStatus.text = "$(sign-in) Copilot: Sign in";
                copilotStatus.tooltip = "Sign in to GitHub to view provider-reported Copilot usage";
                copilotStatus.color = undefined;
            } else if (latestCopilot.unlimited) {
                copilotStatus.text = "$(github) Copilot | Unlimited";
                copilotStatus.tooltip = "GitHub reports no usage limit for this quota";
                copilotStatus.color = undefined;
            } else if (latestCopilot.percentUsed === undefined) {
                copilotStatus.text = "$(warning) Copilot Usage";
                copilotStatus.tooltip = latestCopilot.error ?? "Copilot quota is unavailable";
                copilotStatus.color = new vscode.ThemeColor("statusBarItem.warningForeground");
            } else {
                copilotStatus.text = `Copilot | ${latestCopilot.percentUsed}%`;
                copilotStatus.tooltip = latestCopilot.error ?? "Provider-reported GitHub Copilot quota consumed";
                const used = latestCopilot.percentUsed;
                copilotStatus.color = new vscode.ThemeColor(used >= 90 ? "charts.red" : used >= 70 ? "charts.yellow" : "charts.green");
            }
            copilotStatus.show();
        } else {
            copilotStatus.hide();
        }
        latestExtras.forEach((usage, i) => {
                const item = extraStatus[i];
                if (!usage || !showStatusBar || !config.get<boolean>(`providers.${i === 0 ? "claude" : "cursor"}.enabled`, true)) { item.hide(); return; }
                const used = usage.quotas[0]?.percentUsed;
                item.text = `${usage.name} | ${used === undefined ? "?" : Math.round(i === 0 ? 100 - used : used) + "%"}`;
                item.tooltip = `${usage.name} ? Plan: ${usage.plan ?? "Unknown"}\n${usage.error ?? "Open AgentMeter"}`;
                item.color = used === undefined ? undefined : new vscode.ThemeColor(used >= 90 ? "charts.red" : used >= 70 ? "charts.yellow" : "charts.green");
                item.show();
            });

    };

    const refresh = (interactiveCopilot = false): Promise<void> => {
        // Commands and polling share one batch so the Codex CLI is never launched twice at once.
        if (disposed) return Promise.resolve();
        if (refreshInFlight) return refreshInFlight;
        const completedRevision = revision;
        refreshInFlight = (async () => {
            const currentRevision = revision;
            abort = new AbortController();
            const timeout = setTimeout(() => abort?.abort(), 20000);
            const config = vscode.workspace.getConfiguration("agentsUsage");
            const extrasPromise = Promise.all(["claude", "cursor"].map((id, i) => config.get<boolean>(`providers.${id}.enabled`, true) ? (i === 0 ? readClaude : readCursor)(abort!.signal) : Promise.resolve(undefined)));
            const codexEnabled = config.get<boolean>("providers.codex.enabled", true);
            const copilotEnabled = config.get<boolean>("providers.copilot.enabled", true);
            const codexPath = config.get<string>("codexPath", "");
            const extensionVersion = String(context.extension.packageJSON.version);
            const [codex, copilot] = await Promise.all([
                codexEnabled ? codexReader.read(codexPath, extensionVersion).catch((error): CodexUsage => ({
                    updatedAt: Date.now(),
                    error: error instanceof Error ? error.message : "Codex usage could not be read",
                })) : Promise.resolve(latestCodex ?? {
                    updatedAt: Date.now(),
                    error: "OpenAI Codex usage is disabled in settings"
                }),
                copilotEnabled ? copilotReader.read(interactiveCopilot) : Promise.resolve(latestCopilot ?? {
                    updatedAt: Date.now(), error: "GitHub Copilot usage is disabled in settings",
                }),
            ]);
            const extras = await extrasPromise;
            clearTimeout(timeout);
            if (disposed || currentRevision !== revision) return;
            view.setExtraUsage(extras.filter((value): value is ProviderUsage => value !== undefined));
            latestExtras = extras;
            latestCodex = codex;
            latestCopilot = copilot;
            view.setUsage(codex, copilot, codexEnabled, copilotEnabled);
            renderStatus();
        })().finally(() => {
            refreshInFlight = undefined;
            if (!disposed && revision !== completedRevision) void refresh();
        });
        return refreshInFlight;
    };

    const configureTimer = (): void => {
        // Replace the previous timer whenever the refresh interval changes.
        if (timer) clearInterval(timer);
        const seconds = vscode.workspace.getConfiguration("agentsUsage").get<number>("refreshIntervalSeconds", 60);
        timer = setInterval(() => void refresh(), seconds * 1000);
    };

    // VS Code disposes UI registrations, event subscriptions, timers and readers together.
    context.subscriptions.push(
        webview,
        codexStatus,
        copilotStatus,
        view,
        vscode.commands.registerCommand("agentsUsage.openUsage", async () => {
            await vscode.commands.executeCommand("workbench.view.extension.agentsUsage");
            await refresh();
        }),
        vscode.commands.registerCommand("agentsUsage.refresh", refresh),
        vscode.commands.registerCommand("agentsUsage.signInCopilot", () => refresh(true)),
        vscode.commands.registerCommand("agentsUsage.openSettings", () =>
            vscode.commands.executeCommand("workbench.action.openSettings", `@ext:${context.extension.id}`)),
        vscode.authentication.onDidChangeSessions((event) => {
            if (event.provider.id === "github") void refresh();
        }),
        vscode.workspace.onDidChangeConfiguration((event) => {
            if (event.affectsConfiguration("agentsUsage.refreshIntervalSeconds")) configureTimer();
            if (event.affectsConfiguration("agentsUsage.showStatusBar")) { renderStatus(); revision++; void refresh(); }
            if (event.affectsConfiguration("agentsUsage.codexPath") || event.affectsConfiguration("agentsUsage.providers")) { revision++; abort?.abort(); view.applySettings(); renderStatus(); void refresh(); }
        }),
        new vscode.Disposable(() => {
            disposed = true;
            abort?.abort();
            if (timer) clearInterval(timer);
            codexReader.dispose();
            copilotReader.dispose();
        }),
    );

    configureTimer();
    renderStatus();
    void refresh();
}

export function deactivate(): void {
    // Resources are disposed through ExtensionContext.subscriptions.
}
