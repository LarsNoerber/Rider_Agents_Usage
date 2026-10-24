import * as vscode from "vscode";
import {CodexUsageReader, CodexUsage} from "./codex";
import {CopilotUsageReader, CopilotUsage} from "./copilot";
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

    let refreshInFlight: Promise<void> | undefined;
    let timer: NodeJS.Timeout | undefined;
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
                codexStatus.text = `$(pulse) OpenAI D=${five} - W=${week}`;
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
                copilotStatus.text = `$(github) Copilot ${latestCopilot.percentUsed}%`;
                copilotStatus.tooltip = latestCopilot.error ?? "Provider-reported GitHub Copilot quota consumed";
                const used = latestCopilot.percentUsed;
                copilotStatus.color = new vscode.ThemeColor(used >= 90 ? "charts.red" : used >= 70 ? "charts.yellow" : "charts.green");
            }
            copilotStatus.show();
        } else {
            copilotStatus.hide();
        }
    };

    const refresh = (interactiveCopilot = false): Promise<void> => {
        // Commands and polling share one batch so the Codex CLI is never launched twice at once.
        if (refreshInFlight) return refreshInFlight;
        refreshInFlight = (async () => {
            const config = vscode.workspace.getConfiguration("agentsUsage");
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
            latestCodex = codex;
            latestCopilot = copilot;
            view.setUsage(codex, copilot, codexEnabled, copilotEnabled);
            renderStatus();
        })().finally(() => {
            refreshInFlight = undefined;
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
            if (event.affectsConfiguration("agentsUsage.showStatusBar")) renderStatus();
            if (event.affectsConfiguration("agentsUsage.codexPath") || event.affectsConfiguration("agentsUsage.providers")) void refresh();
        }),
        new vscode.Disposable(() => {
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
