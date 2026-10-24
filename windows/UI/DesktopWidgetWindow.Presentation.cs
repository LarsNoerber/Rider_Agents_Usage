using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using AgentMeter.Windows.Core;
using AgentMeter.Windows.UI.Components;

namespace AgentMeter.Windows.UI;

// Render complete provider batches; snapshots remain hidden until refresh completion.
internal sealed partial class DesktopWidgetWindow
{
    public void Update(UsageSnapshot snapshot)
    {
        if (closing) return;
        if (refreshing) pendingSnapshots[snapshot.ProviderId] = snapshot;
        else snapshots[snapshot.ProviderId] = snapshot;
        Render();
    }

    public void SetRefreshing(bool value)
    {
        if (closing) return;
        refreshing = value;
        if (value)
        {
            pendingSnapshots.Clear();
            collapseDelay.Stop();
            detailsPopup.IsOpen = false;
            SetCollapsed(false);
        }
        else
        {
            // Publish one coherent batch, including unavailable results, instead of revealing providers one by one.
            foreach (var (id, snapshot) in pendingSnapshots) snapshots[id] = snapshot;
            pendingSnapshots.Clear();
            firstRefreshCompleted = true;
        }
        Render();
        ScheduleCollapse();
    }

    private void Render()
    {
        var loading = WaitingForProviders;
        refreshStatus.Visibility = loading ? Visibility.Visible : Visibility.Collapsed;
        if (loading)
        {
            SetCollapsed(false);
            strip.Visibility = Visibility.Collapsed;
            refreshStatus.Update(selected.Count(pendingSnapshots.ContainsKey), selected.Length, colors);
            return;
        }
        strip.Visibility = collapsed ? Visibility.Collapsed : Visibility.Visible;
        var ids = options.WidgetProviderOrder.Concat(selected).Distinct().Where(selected.Contains).ToArray();
        // Rebuild the visual structure only when selection/order/style changes, reusing segment controls.
        if (!ids.SequenceEqual(renderedProviders) || renderedStyle != widgetStyle || strip.Children.Count == 0)
        {
            detailsPopup.IsOpen = false;
            strip.Children.Clear();
            foreach (var id in ids)
            {
                if (strip.Children.Count > 0 && widgetStyle is "classic" or "compact" or "vertical" or "mini")
                    strip.Children.Add(new Border
                    {
                        Width = widgetStyle == "vertical" ? double.NaN : 1,
                        Height = widgetStyle == "vertical" ? 1 : 18,
                        Margin = widgetStyle == "vertical" ? new Thickness(0, 7, 0, 7)
                            : new Thickness(widgetStyle is "compact" or "mini" ? 7 : 10, 0, widgetStyle is "compact" or "mini" ? 7 : 10, 0),
                        Background = new SolidColorBrush(Color.FromRgb(58, 71, 88)),
                        VerticalAlignment = VerticalAlignment.Center
                    });
                if (!segments.TryGetValue(id, out var segment)) segments[id] = segment = CreateSegment(id);
                segment.Margin = widgetStyle is "cards" or "circles" ? new Thickness(0, 0, 6, 0) : new Thickness(0);
                segment.HorizontalContentAlignment = widgetStyle == "vertical" ? HorizontalAlignment.Left : HorizontalAlignment.Center;
                strip.Children.Add(segment);
            }
            renderedProviders = ids;
            renderedStyle = widgetStyle;
        }
        var contents = new List<FrameworkElement>();
        foreach (var divider in strip.Children.OfType<Border>()) divider.Background = colors.BorderBrush;
        foreach (var id in ids)
        {
            snapshots.TryGetValue(id, out var snapshot);
            var content = WidgetProviderContent.Create(id, snapshot, widgetStyle, colors, options.WidgetShowResets, options.WidgetShowFreshness, options.RefreshSeconds);
            contents.Add(content);
            segments[id].Content = content;
            if (detailsPopup.IsOpen && detailsProvider == id) UpdateDetails(id);
        }
        if (widgetStyle is "cards" or "circles" && contents.Count > 0)
        {
            // Measure the complete batch before assigning a common height to its cards/rings.
            foreach (var content in contents) content.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
            var height = contents.Max(content => content.DesiredSize.Height);
            foreach (var content in contents) content.Height = height;
        }
        if (strip.Children.Count == 0) strip.Children.Add(new TextBlock { Text = "AgentMeter | Select providers in Settings", Foreground = colors.TextBrush });
        if (collapsed) SetCollapsed(true);
    }

    private void UpdateDetails(string id)
    {
        snapshots.TryGetValue(id, out var snapshot);
        detailsPopup.Child = WidgetTooltip.CreatePopup(ProviderCatalog.Get(id).Name, snapshot, options.WidgetPositionLocked, options.RefreshSeconds);
        var content = (FrameworkElement)detailsPopup.Child;
        content.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
        var target = segments[id];
        var availableAbove = Top - SystemParameters.WorkArea.Top;
        if (target.IsVisible && PresentationSource.FromVisual(target) is { } source)
        {
            var screenPoint = target.PointToScreen(new Point(0, 0));
            var screen = System.Windows.Forms.Screen.FromPoint(new System.Drawing.Point(
                (int)Math.Round(screenPoint.X), (int)Math.Round(screenPoint.Y)));
            var scale = source.CompositionTarget.TransformToDevice.M22;
            // Screen coordinates are pixels; popup layout and the window position use WPF units.
            availableAbove = (screenPoint.Y - screen.WorkingArea.Top) / scale;
        }
        var below = availableAbove < content.DesiredSize.Height + 8;
        detailsPopup.Placement = below ? PlacementMode.Bottom : PlacementMode.Top;
        detailsPopup.VerticalOffset = below ? 8 : -8;
    }
}
