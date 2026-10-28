using System.Windows;
using System.Windows.Controls;
using AgentMeter.Windows.Settings;
using AgentMeter.Windows.UI.Components;

namespace AgentMeter.Windows.UI;

// Context menu actions delegate persisted changes to MainWindow.
internal sealed partial class DesktopWidgetWindow
{
    private ContextMenu CreateContextMenu(Action openDashboard, Action refresh, Action hideDashboard,
        Func<bool> dashboardVisible, Action exit)
    {
        var menu = new ContextMenu();
        var dashboardToggle = new MenuItem();
        dashboardToggle.Click += (_, _) =>
        {
            if (dashboardVisible()) hideDashboard();
            else openDashboard();
        };
        menu.Items.Add(dashboardToggle);
        AddItem(menu, "Refresh all", refresh);
        var pin = new MenuItem { Header = "Always on top", IsCheckable = true, IsChecked = true };
        pin.Click += (_, _) =>
        {
            Topmost = pin.IsChecked;
            PinChanged?.Invoke(Topmost);
        };
        menu.Items.Add(pin);
        var styles = new MenuItem { Header = "Bar style" };
        foreach (var choice in WidgetStyles.Choices)
        {
            var item = new MenuItem { Header = choice.Label, IsCheckable = true, Tag = choice.Id };
            item.Click += (_, _) =>
            {
                SetStyle(choice.Id);
                StyleChanged?.Invoke(choice.Id);
            };
            styles.Items.Add(item);
        }
        menu.Items.Add(styles);
        styles.SubmenuOpened += (_, _) =>
        {
            foreach (MenuItem item in styles.Items) item.IsChecked = (string)item.Tag == widgetStyle;
        };
        var colorStyles = new MenuItem { Header = "Color style" };
        foreach (var choice in WidgetColors.Choices)
        {
            var swatch = new StackPanel { Orientation = Orientation.Horizontal };
            swatch.Children.Add(new Border
            {
                Width = 22,
                Height = 14,
                Background = choice.BadgeBrush,
                BorderBrush = choice.AccentBrush,
                BorderThickness = new Thickness(2),
                CornerRadius = new CornerRadius(3),
                Margin = new Thickness(0, 0, 8, 0)
            });
            swatch.Children.Add(new TextBlock { Text = choice.Label, VerticalAlignment = VerticalAlignment.Center });
            var item = new MenuItem { Header = swatch, IsCheckable = true, Tag = choice.Id };
            item.Click += (_, _) =>
            {
                SetColors(choice.Id);
                ColorStyleChanged?.Invoke(choice.Id);
            };
            colorStyles.Items.Add(item);
        }
        colorStyles.SubmenuOpened += (_, _) =>
        {
            foreach (MenuItem item in colorStyles.Items) item.IsChecked = (string)item.Tag == colors.Id;
        };
        menu.Items.Add(colorStyles);
        AddToggle(menu, "Snap to screen edges", s => s.WidgetSnapToEdges, (s, value) => s with { WidgetSnapToEdges = value });
        AddToggle(menu, "Lock position", s => s.WidgetPositionLocked, (s, value) => s with { WidgetPositionLocked = value });
        AddToggle(menu, "Show reset countdowns", s => s.WidgetShowResets, (s, value) => s with { WidgetShowResets = value });
        AddToggle(menu, "Show data freshness", s => s.WidgetShowFreshness, (s, value) => s with { WidgetShowFreshness = value });
        AddToggle(menu, "Collapse when not in use", s => s.WidgetAutoCollapse, (s, value) => s with { WidgetAutoCollapse = value });
        menu.Opened += (_, _) =>
        {
            menuOpen = true;
            collapseDelay.Stop();
            detailsPopup.IsOpen = false;
            pin.IsChecked = Topmost;
            dashboardToggle.Header = dashboardVisible() ? "Hide dashboard" : "Show dashboard";
        };
        menu.Closed += (_, _) =>
        {
            menuOpen = false;
            ScheduleCollapse();
        };
        menu.Items.Add(new Separator());
        AddItem(menu, "Exit AgentMeter", exit);
        return menu;
    }

    private void AddToggle(ContextMenu menu, string label, Func<UsageSettings, bool> read,
        Func<UsageSettings, bool, UsageSettings> write)
    {
        var item = new MenuItem { Header = label, IsCheckable = true };
        menu.Opened += (_, _) => item.IsChecked = read(options);
        item.Click += (_, _) =>
        {
            var value = item.IsChecked;
            if (OptionsChanged == null) Apply(write(options, value));
            else OptionsChanged.Invoke(s => write(s, value));
        };
        menu.Items.Add(item);
    }

    private static void AddItem(ContextMenu menu, string text, Action action)
    {
        var item = new MenuItem { Header = text };
        item.Click += (_, _) => action();
        menu.Items.Add(item);
    }
}
