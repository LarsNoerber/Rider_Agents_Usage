using System.IO;
using System.Windows;
using System.Windows.Controls;
using AgentMeter.Windows.Settings;
using Microsoft.Win32;

namespace AgentMeter.Windows.UI;

// Configuration navigation and saved provider choices.
public partial class MainWindow
{
    private void StartWithWindowsChanged(object sender, RoutedEventArgs args)
    {
        if (initializingWidget || closed || StartWithWindowsInput.IsChecked is not bool enabled) return;
        try
        {
            using var runKey = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run")
                ?? throw new InvalidOperationException("Windows startup settings are unavailable.");
            var executable = Environment.ProcessPath;
            if (string.IsNullOrWhiteSpace(executable)) throw new InvalidOperationException("Application path is unavailable.");
            if (enabled) runKey.SetValue("AgentMeter", $"\"{executable}\"");
            else runKey.DeleteValue("AgentMeter", false);
            settings = settings with { StartWithWindows = enabled };
            SettingsStatus.Text = SettingsStore.Save(settings) ? "Startup preference saved." : "Could not save settings.";
        }
        catch (Exception error) when (error is UnauthorizedAccessException or System.Security.SecurityException or IOException or InvalidOperationException)
        {
            initializingWidget = true;
            StartWithWindowsInput.IsChecked = settings.StartWithWindows;
            initializingWidget = false;
            SettingsStatus.Text = "Could not update Windows startup.";
        }
    }

    private async void SelectionChanged(object sender, RoutedEventArgs args)
    {
        if (closed || updatingOpenRouterSelection) return;
        settings = settings with { EnabledProviders = choices.Where(pair => pair.Value.IsChecked == true).Select(pair => pair.Key).ToArray() };
        SettingsStatus.Text = SettingsStore.Save(settings) ? "Provider selection saved." : "Could not save settings. Selection applies for this session.";
        RebuildCards();
        widget?.Apply(settings);
        await coordinator.ApplyAsync(settings);
    }

    private async void SaveClick(object sender, RoutedEventArgs args)
    {
        if (!int.TryParse(IntervalInput.Text, out var seconds) || seconds is < 30 or > 3600)
        {
            SettingsStatus.Text = "Enter a refresh interval between 30 and 3600 seconds.";
            return;
        }
        settings = settings with
        {
            RefreshSeconds = seconds,
            StartInWidgetOnlyMode = DashboardVisibleInput.IsChecked != true,
            ShowDesktopWidget = true,
            CodexPath = CodexPathInput.Text.Trim(),
            CopilotPath = CopilotPathInput.Text.Trim(),
            ClaudePath = ClaudePathInput.Text.Trim(),
            CursorPath = CursorPathInput.Text.Trim(),
            GeminiPath = GeminiPathInput.Text.Trim(),
            GeminiProjectId = GeminiProjectInput.Text.Trim(),
            KiloPath = KiloPathInput.Text.Trim(),
            ClinePath = ClinePathInput.Text.Trim(),
            OpenCodePath = OpenCodePathInput.Text.Trim(),
            JuniePath = JuniePathInput.Text.Trim(),
            JunieProjectPath = JunieProjectInput.Text.Trim()
        };
        using (var pendingKey = OpenRouterKeyInput.SecurePassword)
        {
            if (pendingKey.Length > 0) ApplyEnteredOpenRouterKey(pendingKey);
        }
        SettingsStatus.Text = SettingsStore.Save(settings) ? "Settings saved." : "Could not save settings. Changes apply for this session.";
        foreach (var card in cards.Values) card.RefreshSeconds = settings.RefreshSeconds;
        widget?.Apply(settings);
        widget?.Show();
        if (!settings.StartInWidgetOnlyMode)
        {
            Opacity = 1;
            ShowInTaskbar = true;
            ShowActivated = true;
            Show();
            WindowState = WindowState.Normal;
            Activate();
        }
        else
        {
            Opacity = 1;
            ShowInTaskbar = true;
            ShowActivated = true;
            Hide();
        }
        await coordinator.ApplyAsync(settings);
    }

    private async void RefreshClick(object sender, RoutedEventArgs args) => await coordinator.RefreshAsync();

    private void SettingsPageChanged(object sender, RoutedPropertyChangedEventArgs<object> args)
    {
        if (SettingsGeneralPage == null || args.NewValue is not TreeViewItem { Tag: string id }) return;
        ShowSettingsPage(id);
    }

    private void ShowSettingsPage(string id)
    {
        var pages = new (string Id, FrameworkElement Page, string Title, string Description)[]
        {
            ("general", SettingsGeneralPage, "General", "Provider visibility and automatic refresh."),
            ("appearance", SettingsAppearancePage, "Desktop widget / Appearance", "Layout, colors and opacity. Changes save immediately."),
            ("behavior", SettingsBehaviorPage, "Desktop widget / Behavior", "Movement, countdowns, freshness and provider order."),
            ("codex", SettingsCodexPage, "Providers / OpenAI Codex", "Official CLI connection and remaining quotas."),
            ("copilot", SettingsCopilotPage, "Providers / GitHub Copilot", "Official Language Server connection and consumed quota."),
            ("claude", SettingsClaudePage, "Providers / Claude Code", "Subscription connection and locally reported usage."),
            ("cursor", SettingsCursorPage, "Providers / Cursor", "Official Cursor Agent connection."),
            ("gemini", SettingsGeminiPage, "Providers / Gemini", "Gemini Apps, CLI and Workspace connections."),
            ("openrouter", SettingsOpenRouterPage, "Providers / OpenRouter", "API key, remaining key budget and spending."),
            ("kilo", SettingsKiloPage, "Providers / Kilo", "Credit balance and local token/cost statistics."),
            ("cline", SettingsClinePage, "Providers / Cline", "Recorded local token usage and estimated costs."),
            ("opencode", SettingsOpenCodePage, "Providers / OpenCode", "Official connections and local cost/token statistics."),
            ("junie", SettingsJuniePage, "Providers / Junie", "Official CLI connection, reported balance and project statistics.")
        };
        foreach (var page in pages) page.Page.Visibility = page.Id == id ? Visibility.Visible : Visibility.Collapsed;
        var current = pages.FirstOrDefault(page => page.Id == id);
        if (current.Page == null) return;
        SettingsPageTitle.Text = current.Title;
        SettingsPageDescription.Text = current.Description;
        SettingsPageScroll.ScrollToTop();
    }

    private void NavigateSettingsPage(string id)
    {
        SettingsTab.IsSelected = true;
        foreach (var root in SettingsNavigation.Items.OfType<TreeViewItem>())
        {
            var item = root.Tag as string == id ? root : root.Items.OfType<TreeViewItem>().FirstOrDefault(node => node.Tag as string == id);
            if (item == null) continue;
            root.IsExpanded = true;
            item.IsSelected = true;
            ShowSettingsPage(id);
            break;
        }
    }

    private void ProviderLoginClick(object sender, RoutedEventArgs args)
    {
        if (sender is Button { Tag: string id }) Login(id);
    }
}
