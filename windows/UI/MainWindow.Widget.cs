using System.Windows;
using System.Windows.Controls;
using AgentMeter.Windows.Settings;
using AgentMeter.Windows.UI.Components;

namespace AgentMeter.Windows.UI;

// Desktop widget preferences and dashboard/tray navigation.
public partial class MainWindow
{
    private void WidgetOpacityChanged(object sender, RoutedPropertyChangedEventArgs<double> args)
    {
        if (WidgetOpacityValue != null) WidgetOpacityValue.Text = $"{args.NewValue:0}%";
        if (initializingWidget || closed) return;
        settings = settings with { WidgetOpacity = Math.Clamp(args.NewValue / 100, 0.2, 1) };
        if (widget != null) widget.Opacity = settings.WidgetOpacity;
        SettingsStore.Save(settings);
    }

    private void WidgetStyleSelectionChanged(object sender, SelectionChangedEventArgs args)
    {
        if (WidgetStyleInput.SelectedItem is not WidgetStyleChoice choice) return;
        if (WidgetStyleDescription != null) WidgetStyleDescription.Text = choice.Description;
        if (!initializingWidget && !closed) SaveWidgetStyle(choice.Id);
    }

    private void WidgetColorSelectionChanged(object sender, SelectionChangedEventArgs args)
    {
        if (!initializingWidget && !closed && WidgetColorInput.SelectedItem is WidgetColorChoice choice)
            SaveWidgetColors(choice.Id);
    }

    private void SaveWidgetOptions(Func<UsageSettings, UsageSettings> change)
    {
        if (closed || initializingWidget) return;
        settings = change(settings);
        widgetOptions.Apply(settings);
        ApplyWidgetProviderChoices();
        widget?.Apply(settings);
        SettingsStatus.Text = SettingsStore.Save(settings) ? "Widget settings saved." : "Could not save widget settings. Changes apply for this session.";
    }

    private void WidgetProviderSelectionChanged(object sender, RoutedEventArgs args)
    {
        if (closed || initializingWidget) return;
        SaveWidgetOptions(s => s with { WidgetVisibleProviders = widgetChoices.Where(pair => pair.Value.IsChecked == true).Select(pair => pair.Key).ToArray() });
    }

    private void ApplyWidgetProviderChoices()
    {
        var wasInitializing = initializingWidget;
        initializingWidget = true;
        try
        {
            foreach (var (id, choice) in widgetChoices)
            {
                choice.IsChecked = (settings.WidgetVisibleProviders ?? settings.EnabledProviders).Contains(id);
                choice.IsEnabled = settings.EnabledProviders.Contains(id);
            }
        }
        finally { initializingWidget = wasInitializing; }
    }

    private void SaveWidgetColors(string style)
    {
        if (closed || settings.WidgetColorStyle == style) return;
        settings = settings with { WidgetColorStyle = style };
        WidgetColorInput.SelectedValue = style;
        widget?.Apply(settings);
        SettingsStore.Save(settings);
    }

    private void SaveWidgetStyle(string style)
    {
        if (closed || settings.WidgetStyle == style) return;
        settings = settings with { WidgetStyle = style };
        WidgetStyleInput.SelectedValue = style;
        widget?.Apply(settings);
        SettingsStore.Save(settings);
    }

    private void HideDashboardClick(object sender, RoutedEventArgs args) => HideDashboard();

    private void HideDashboard()
    {
        settings = settings with { ShowDesktopWidget = true, WidgetOnly = true, StartInWidgetOnlyMode = true };
        Opacity = 0;
        ShowInTaskbar = false;
        ShowActivated = false;
        widget?.Apply(settings);
        widget?.Show();
        initializingWidget = true;
        DashboardVisibleInput.IsChecked = false;
        initializingWidget = false;
        SettingsStore.Save(settings);
        Hide();
    }

    private void OpenDashboard()
    {
        if (closed) return;
        settings = settings with { ShowDesktopWidget = true, WidgetOnly = false, StartInWidgetOnlyMode = false };
        widget?.Show();
        Opacity = 1;
        ShowInTaskbar = true;
        ShowActivated = true;
        initializingWidget = true;
        DashboardVisibleInput.IsChecked = true;
        initializingWidget = false;
        SettingsStore.Save(settings);
        Show();
        WindowState = WindowState.Normal;
        Activate();
    }

    private void SaveWidgetPosition(double left, double top)
    {
        settings = settings with { WidgetLeft = left, WidgetTop = top };
        SettingsStore.Save(settings);
    }

    private void SaveWidgetPin(bool pinned)
    {
        settings = settings with { WidgetAlwaysOnTop = pinned };
        SettingsStore.Save(settings);
    }
    private void CountdownTick(object? sender, EventArgs args)
    {
        widget?.RefreshTime();
        foreach (var id in settings.EnabledProviders)
            if (cards.TryGetValue(id, out var card))
                card.RefreshCountdowns();
    }
}
