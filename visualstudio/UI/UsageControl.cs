using System;
using System.Diagnostics;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using AgentsUsage.VisualStudio.Application;
using AgentsUsage.VisualStudio.Providers.Codex.UI;
using AgentsUsage.VisualStudio.Providers.Copilot.UI;
using AgentsUsage.VisualStudio.Settings;
using AgentsUsage.VisualStudio.UI.Components;
using Microsoft.VisualStudio.PlatformUI;

namespace AgentsUsage.VisualStudio.UI
{
    internal sealed class UsageControl : UserControl, IDisposable
    {
        private readonly UsageOptions options;
        private readonly UsageRefreshCoordinator coordinator;
        private readonly StackPanel content = new StackPanel();
        private readonly Button refreshButton = new Button { Content = "Refresh", Padding = new Thickness(10, 4, 10, 4) };
        private bool subscribed;
        private bool disposed;

        public UsageControl(UsageOptions options, UsageRefreshCoordinator coordinator, Action openSettings)
        {
            this.options = options;
            this.coordinator = coordinator;
            SetResourceReference(BackgroundProperty, EnvironmentColors.ToolWindowBackgroundBrushKey);
            SetResourceReference(ForegroundProperty, EnvironmentColors.ToolWindowTextBrushKey);
            var root = new DockPanel { Margin = new Thickness(12) };
            var header = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };
            header.Children.Add(QuotaElements.Icon(28));
            header.Children.Add(QuotaElements.Label("AgentMeter", 16, true));
            DockPanel.SetDock(header, Dock.Top);
            root.Children.Add(header);
            var toolbar = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };
            toolbar.Children.Add(refreshButton);
            var settings = new Button { Content = "Settings", Padding = new Thickness(10, 4, 10, 4), Margin = new Thickness(8, 0, 0, 0) };
            settings.Click += (sender, args) => openSettings();
            toolbar.Children.Add(settings);
            var repository = new Button { Content = "GitHub", Padding = new Thickness(10, 4, 10, 4), Margin = new Thickness(8, 0, 0, 0) };
            repository.Click += (sender, args) => Process.Start(new ProcessStartInfo("https://github.com/larsnoerber/Rider_Agents_Usage") { UseShellExecute = true });
            toolbar.Children.Add(repository);
            DockPanel.SetDock(toolbar, Dock.Top);
            root.Children.Add(toolbar);
            var version = typeof(UsageControl).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion.Split('+')[0];
            var footer = QuotaElements.Label("Version " + (version ?? "Unknown"), 11);
            footer.Margin = new Thickness(0, 10, 0, 0);
            DockPanel.SetDock(footer, Dock.Bottom);
            root.Children.Add(footer);
            root.Children.Add(new ScrollViewer { Content = content, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
            Content = root;
            Loaded += OnLoaded;
            Unloaded += OnUnloaded;
            refreshButton.Click += OnRefresh;
            Render();
        }

        private void OnLoaded(object sender, RoutedEventArgs args)
        {
            if (disposed || subscribed) return;
            subscribed = true;
            coordinator.Changed += OnChanged;
            coordinator.SetViewVisible(true);
            Render();
        }
        private void OnUnloaded(object sender, RoutedEventArgs args) { Unsubscribe(); }
        private void OnRefresh(object sender, RoutedEventArgs args) { _ = coordinator.RefreshAsync(); }
        private void OnChanged(object sender, EventArgs args) { if (!disposed) Render(); }

        private void Render()
        {
            refreshButton.IsEnabled = !coordinator.Refreshing;
            content.Children.Clear();
            if (options.CodexEnabled) content.Children.Add(CodexUsagePanel.Create(coordinator.Codex));
            if (options.CopilotEnabled) content.Children.Add(CopilotUsagePanel.Create(coordinator.Copilot));
            if (options.ClaudeEnabled) content.Children.Add(ProviderQuotaPanel.Create("Claude Code", coordinator.Claude, true));
            if (options.CursorEnabled) content.Children.Add(ProviderQuotaPanel.Create("Cursor", coordinator.Cursor, false));
            if (options.CodexEnabled || options.CopilotEnabled || options.ClaudeEnabled || options.CursorEnabled)
            {
                var history = new StackPanel();
                history.Children.Add(QuotaElements.Label("Weekly observations", 15, true));
                history.Children.Add(QuotaElements.Label("Observed consumption ranges over the last seven days in this editor session. Resets can reduce consumption; ranges are not total spend."));
                foreach (var summary in coordinator.History.Summaries())
                    if (summary.StartsWith("OpenAI") ? options.CodexEnabled : summary.StartsWith("Copilot") ? options.CopilotEnabled : summary.StartsWith("Claude") ? options.ClaudeEnabled : options.CursorEnabled)
                        history.Children.Add(QuotaElements.Label(summary));
                content.Children.Add(QuotaElements.Card(history));
            }
            if (!options.CodexEnabled && !options.CopilotEnabled && !options.ClaudeEnabled && !options.CursorEnabled)
                content.Children.Add(QuotaElements.Label("Enable an agent in Settings to see its quota.", 13));
        }

        private void Unsubscribe()
        {
            if (!subscribed) return;
            subscribed = false;
            coordinator.Changed -= OnChanged;
            coordinator.SetViewVisible(false);
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            Unsubscribe();
            Loaded -= OnLoaded;
            Unloaded -= OnUnloaded;
            refreshButton.Click -= OnRefresh;
        }
    }
}
