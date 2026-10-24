using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Media;
using System.Windows.Threading;
using AgentsUsage.VisualStudio.Application;
using AgentsUsage.VisualStudio.Settings;
using AgentsUsage.VisualStudio.UI.Components;
using Microsoft.VisualStudio.PlatformUI;

namespace AgentsUsage.VisualStudio.UI
{
    /// <summary>Isolates the optional WPF shell insertion point; leaves the IDE's status text untouched.</summary>
    internal sealed class StatusBarHost : IDisposable
    {
        private readonly UsageOptions options;
        private readonly UsageRefreshCoordinator coordinator;
        private readonly Action openUsage;
        private readonly Button button = new Button
        {
            Padding = new Thickness(4, 0, 4, 0), BorderThickness = new Thickness(0),
            Background = Brushes.Transparent, VerticalAlignment = VerticalAlignment.Center,
            HorizontalContentAlignment = HorizontalAlignment.Left
        };
        private readonly DispatcherTimer attachTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(10) };
        private DockPanel panel;
        private bool disposed;

        public StatusBarHost(UsageOptions options, UsageRefreshCoordinator coordinator, Action openUsage)
        {
            this.options = options;
            this.coordinator = coordinator;
            this.openUsage = openUsage;
            button.SetResourceReference(Control.ForegroundProperty, EnvironmentColors.ToolWindowTextBrushKey);
            button.Click += OnClick;
            coordinator.Changed += OnChanged;
            attachTimer.Tick += OnAttach;
            attachTimer.Start();
            Update();
        }

        private void OnClick(object sender, RoutedEventArgs args) { openUsage(); }
        private void OnChanged(object sender, EventArgs args) { Update(); }
        private void OnAttach(object sender, EventArgs args) { Attach(); }

        private void Update()
        {
            if (disposed) return;
            var row = new StackPanel { Orientation = Orientation.Horizontal };
            row.Children.Add(QuotaElements.Icon(18));
            if (options.CodexEnabled)
            {
                var text = new TextBlock { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 12, 0) };
                text.Inlines.Add(new Run("OpenAi | D="));
                AddPercent(text, coordinator.Codex?.FiveHourLeft, true);
                text.Inlines.Add(new Run(" - W="));
                AddPercent(text, coordinator.Codex?.WeeklyLeft, true);
                row.Children.Add(text);
            }
            if (options.CopilotEnabled)
            {
                var text = new TextBlock { VerticalAlignment = VerticalAlignment.Center };
                text.Inlines.Add(new Run("Copilot | "));
                if (coordinator.Copilot?.Primary?.Unlimited == true) text.Inlines.Add(new Run("Unlimited"));
                else AddPercent(text, coordinator.Copilot?.Primary?.PercentUsed, false);
                row.Children.Add(text);
            }
            if (!options.CodexEnabled && !options.CopilotEnabled) row.Children.Add(new TextBlock { Text = "AgentMeter" });
            button.Content = row;
            button.ToolTip = "Open AgentMeter\nOpenAI plan: " + (coordinator.Codex?.Plan ?? "Unknown")
                + "\nCopilot plan: " + (coordinator.Copilot?.Plan ?? "Unknown")
                + (coordinator.Codex?.Error == null ? "" : "\n" + coordinator.Codex.Error)
                + (coordinator.Copilot?.Error == null ? "" : "\n" + coordinator.Copilot.Error);
            Attach();
        }

        private static void AddPercent(TextBlock text, int? value, bool remaining)
        {
            var run = new Run(value.HasValue ? value.Value + "%" : "—");
            if (value.HasValue) run.Foreground = QuotaElements.Color(value.Value, remaining);
            text.Inlines.Add(run);
        }

        private void Attach()
        {
            if (disposed) return;
            if (!options.ShowStatusBar)
            {
                Detach();
                attachTimer.Stop();
                return;
            }
            if (!attachTimer.IsEnabled) attachTimer.Start();
            if (panel?.IsLoaded == true && panel.Children.Contains(button)) return;
            var window = System.Windows.Application.Current?.MainWindow;
            var found = FindPanel(window);
            if (found == panel && panel != null && panel.Children.Contains(button)) return;
            Detach();
            if (found == null) return;
            panel = found;
            DockPanel.SetDock(button, Dock.Right);
            // Keep the shell's last child as the fill area for its own status message.
            panel.Children.Insert(0, button);
        }

        private static DockPanel FindPanel(DependencyObject root)
        {
            if (root == null) return null;
            if (root is DockPanel candidate && candidate.Name == "StatusBarPanel") return candidate;
            var count = VisualTreeHelper.GetChildrenCount(root);
            for (var index = 0; index < count; index++)
            {
                var found = FindPanel(VisualTreeHelper.GetChild(root, index));
                if (found != null) return found;
            }
            return null;
        }

        private void Detach()
        {
            panel?.Children.Remove(button);
            panel = null;
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            attachTimer.Stop();
            attachTimer.Tick -= OnAttach;
            coordinator.Changed -= OnChanged;
            button.Click -= OnClick;
            Detach();
        }
    }
}
