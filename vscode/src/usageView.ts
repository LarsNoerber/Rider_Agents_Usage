import * as vscode from "vscode";
import {CodexUsage} from "./codex";
import {CopilotUsage} from "./copilot";

export class UsageView implements vscode.WebviewViewProvider {
    private webview: vscode.Webview | undefined;
    private codex: CodexUsage | undefined;
    private copilot: CopilotUsage | undefined;
    private codexEnabled = true;
    private copilotEnabled = true;

    resolveWebviewView(view: vscode.WebviewView): void {
        this.webview = view.webview;
        view.webview.options = {enableScripts: true};
        // The webview can request only these fixed commands; it cannot supply arbitrary command IDs.
        view.webview.onDidReceiveMessage((message: unknown) => {
            if (typeof message !== "object" || message === null || !("command" in message)) return;
            if (message.command === "refresh") void vscode.commands.executeCommand("agentsUsage.refresh");
            if (message.command === "signIn") void vscode.commands.executeCommand("agentsUsage.signInCopilot");
            if (message.command === "settings") void vscode.commands.executeCommand("agentsUsage.openSettings");
        }, undefined, []);
        this.render();
    }

    setUsage(codex: CodexUsage, copilot: CopilotUsage, codexEnabled: boolean, copilotEnabled: boolean): void {
        this.codex = codex;
        this.copilot = copilot;
        this.codexEnabled = codexEnabled;
        this.copilotEnabled = copilotEnabled;
        this.render();
    }

    private render(): void {
        if (!this.webview) return;
        const rows = [
            ...(this.codexEnabled ? [this.renderCodex()] : []),
            ...(this.copilotEnabled ? [this.renderCopilot()] : []),
        ].join("");
        this.webview.html = this.html(rows || '<p class="empty">Enable an agent in settings to see its usage.</p>');
    }

    private renderCodex(): string {
        const usage = this.codex;
        if (!usage) return providerCard("OpenAI Codex", '<p class="muted">Loading quota...</p>');
        if (usage.error && usage.fiveHourLeft === undefined && usage.weeklyLeft === undefined) {
            return providerCard("OpenAI Codex", `<p class="error">${escapeHtml(usage.error)}</p>`);
        }
        const quotas = [
            quotaRow("5-hour quota", usage.fiveHourLeft, usage.fiveHourReset, true),
            quotaRow("Weekly quota", usage.weeklyLeft, usage.weeklyReset, true),
        ].filter(Boolean).join("");
        const details = [usage.plan && `<span>Plan: ${escapeHtml(usage.plan)}</span>`,
            usage.credits !== undefined && `<span>Credits: ${usage.credits.toLocaleString()}</span>`]
            .filter(Boolean).join("");
        return providerCard("OpenAI Codex", `${quotas}${details ? `<div class="details">${details}</div>` : ""}`);
    }

    private renderCopilot(): string {
        const usage = this.copilot;
        if (!usage) return providerCard("GitHub Copilot", '<p class="muted">Loading quota...</p>');
        if (usage.needsSignIn) return providerCard("GitHub Copilot",
            `<p class="muted">${escapeHtml(usage.error ?? "Sign in to GitHub to view usage.")}</p><button data-command="signIn">Sign in to GitHub</button>`);
        const quota = usage.unlimited ? '<p class="muted">Unlimited quota</p>'
            : usage.percentUsed === undefined ? `<p class="muted">${escapeHtml(usage.error ?? "Usage unavailable")}</p>`
                : quotaRow(usage.quotaLabel ?? "Quota consumed", usage.percentUsed, undefined, false);
        const details = [usage.plan && `<span>Plan: ${escapeHtml(usage.plan)}</span>`,
            usage.usedRequests !== undefined && usage.entitlementRequests !== undefined
            && `<span>${escapeHtml(usage.quotaLabel ?? "Requests")}: ${usage.usedRequests.toLocaleString()} / ${usage.entitlementRequests.toLocaleString()}</span>`,
            usage.resetDate && `<span>Resets: ${escapeHtml(new Date(usage.resetDate).toLocaleString())}</span>`]
            .filter(Boolean).join("");
        return providerCard("GitHub Copilot", `${quota}${details ? `<div class="details">${details}</div>` : ""}`);
    }

    private html(content: string): string {
        // Provider text is escaped by the render helpers; only this nonce permits embedded styles/scripts.
        const nonce = getNonce();
        return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';">
<style nonce="${nonce}">
body{padding:8px 12px;color:var(--vscode-foreground);font-family:var(--vscode-font-family);font-size:var(--vscode-font-size)}
.toolbar{display:flex;justify-content:flex-end;gap:8px;margin-bottom:12px}.card{border:1px solid var(--vscode-panel-border);border-radius:6px;padding:12px;margin:0 0 12px;background:var(--vscode-sideBar-background)}
h2{font-size:13px;font-weight:600;margin:0 0 12px}.quota{margin:10px 0 12px}.quota-head{display:flex;justify-content:space-between;gap:10px;margin-bottom:6px}.value{font-weight:600;color:var(--quota-color)}
.quota.good{--quota-color:var(--vscode-testing-iconPassed, #73c991)}.quota.warning{--quota-color:var(--vscode-editorWarning-foreground, #cca700)}.quota.exhausted{--quota-color:var(--vscode-errorForeground, #f14c4c)}
.quota-bar{display:block;appearance:none;-webkit-appearance:none;width:100%;height:7px;border:0;border-radius:5px;overflow:hidden;background:var(--vscode-input-background, #333)}.quota-bar::-webkit-progress-bar{background:var(--vscode-input-background, #333);border-radius:5px}.quota-bar::-webkit-progress-value{background:var(--quota-color);border-radius:5px}.quota-bar::-moz-progress-bar{background:var(--quota-color);border-radius:5px}.details{display:flex;flex-direction:column;gap:4px;margin-top:10px;color:var(--vscode-descriptionForeground);font-size:0.92em}
.muted{color:var(--vscode-descriptionForeground)}.error{color:var(--vscode-errorForeground)}button{font:inherit;color:var(--vscode-button-foreground);background:var(--vscode-button-background);border:0;border-radius:2px;padding:5px 10px;cursor:pointer}button:hover{background:var(--vscode-button-hoverBackground)}.empty{color:var(--vscode-descriptionForeground)}
</style></head><body><div class="toolbar"><button data-command="refresh">Refresh</button><button data-command="settings">Settings</button></div>
${content}<script nonce="${nonce}">const api=acquireVsCodeApi();document.addEventListener('click',e=>{const b=e.target.closest('[data-command]');if(b)api.postMessage({command:b.dataset.command})});</script></body></html>`;
    }

    dispose(): void {
        this.webview = undefined;
    }
}

function providerCard(title: string, content: string): string {
    return `<section class="card"><h2>${title}</h2>${content}</section>`;
}

function quotaRow(label: string, percent: number | undefined, reset: number | undefined, remaining: boolean): string {
    if (percent === undefined) return "";
    const value = Math.max(0, Math.min(100, percent));
    // Preserve each provider's bar direction while deriving warning colors from consumption.
    const usedPercent = remaining ? 100 - value : value;
    const color = usedPercent >= 90 ? "exhausted" : usedPercent >= 70 ? "warning" : "good";
    const valueText = remaining ? `${Math.round(value)}% remaining` : `${Math.round(value)}% used`;
    const resetText = reset === undefined ? "" : `<div class="details"><span>Resets: ${escapeHtml(new Date(reset * 1000).toLocaleString())}</span></div>`;
    return `<div class="quota ${color}"><div class="quota-head"><span>${label}</span><span class="value">${valueText}</span></div>
<progress class="quota-bar" max="100" value="${value}" aria-label="${label}: ${valueText}">${valueText}</progress>${resetText}</div>`;
}

function escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    }[character]!));
}

function getNonce(): string {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    return Array.from({length: 32}, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}
