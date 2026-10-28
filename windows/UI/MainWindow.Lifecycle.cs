using System.ComponentModel;
using System.Windows;

namespace AgentMeter.Windows.UI;

// Window ownership, tray behavior and deterministic shutdown.
public partial class MainWindow
{
    private void PinChanged(object sender, RoutedEventArgs args) => Topmost = PinWindow.IsChecked == true;
    private void MinimizeClick(object sender, RoutedEventArgs args) => WindowState = WindowState.Minimized;
    private void MaximizeClick(object sender, RoutedEventArgs args) => WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    private void CloseToTrayClick(object sender, RoutedEventArgs args) => Close();

    private void OnClosing(object? sender, CancelEventArgs args)
    {
        if (exiting) return;
        args.Cancel = true;
        HideDashboard();
    }

    private void OnStateChanged(object? sender, EventArgs args)
    {
        if (WindowState == WindowState.Minimized && !exiting) HideDashboard();
    }

    private void ExitClick(object sender, RoutedEventArgs args) => ExitApplication();

    internal void ExitApplication()
    {
        if (exiting) return;
        exiting = true;
        Close();
        System.Windows.Application.Current.Shutdown();
    }

    private void OnClosed(object? sender, EventArgs args)
    {
        closed = true;
        // Stop background work before detaching listeners and disposing provider processes.
        lifetime.Cancel();
        OpenRouterKeyInput.Clear();
        countdown.Stop();
        widgetOptions.Changed -= SaveWidgetOptions;
        countdown.Tick -= CountdownTick;
        coordinator.Updated -= Updated;
        coordinator.BusyChanged -= BusyChanged;
        coordinator.Dispose();
        tray?.Dispose();
        if (widget != null)
        {
            widget.PositionSaved -= SaveWidgetPosition;
            widget.PinChanged -= SaveWidgetPin;
            widget.StyleChanged -= SaveWidgetStyle;
            widget.ColorStyleChanged -= SaveWidgetColors;
            widget.OptionsChanged -= SaveWidgetOptions;
            widget.Shutdown();
        }
        lifetime.Dispose();
        Loaded -= OnLoaded;
        Closed -= OnClosed;
        Closing -= OnClosing;
        StateChanged -= OnStateChanged;
        foreach (var choice in choices.Values)
        {
            choice.Checked -= SelectionChanged;
            choice.Unchecked -= SelectionChanged;
        }
        foreach (var choice in widgetChoices.Values)
        {
            choice.Checked -= WidgetProviderSelectionChanged;
            choice.Unchecked -= WidgetProviderSelectionChanged;
        }
    }
}
