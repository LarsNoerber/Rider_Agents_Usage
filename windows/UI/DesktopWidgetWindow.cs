using System.Windows;
using System.Diagnostics;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using AgentMeter.Windows.Core;
using AgentMeter.Windows.Settings;
using AgentMeter.Windows.UI.Components;

namespace AgentMeter.Windows.UI;

internal sealed partial class DesktopWidgetWindow : Window
{
    private readonly WrapPanel strip = new() { Orientation = Orientation.Horizontal };
    private readonly Border surface;
    private readonly TextBlock collapsedLabel = new() { Text = "AgentMeter", FontSize = 12, VerticalAlignment = VerticalAlignment.Center, Visibility = Visibility.Collapsed };
    private readonly DispatcherTimer collapseDelay = new() { Interval = TimeSpan.FromMilliseconds(650) };
    private UsageSettings options = new();
    private bool collapsed;
    private bool menuOpen;
    private readonly Thumb dragGrip = new()
    {
        Width = 20,
        MinHeight = 20,
        Cursor = Cursors.SizeAll,
        Margin = new Thickness(0, 0, 8, 0),
        VerticalAlignment = VerticalAlignment.Stretch
    };
    private readonly Dictionary<string, UsageSnapshot> snapshots = new();
    private readonly Dictionary<string, UsageSnapshot> pendingSnapshots = new();
    private readonly WidgetRefreshStatus refreshStatus = new();
    private bool refreshing;
    private bool firstRefreshCompleted;
    private bool WaitingForProviders => selected.Length > 0 &&
        (refreshing || !firstRefreshCompleted || selected.Any(id => !snapshots.ContainsKey(id)));
    private readonly Dictionary<string, Button> segments = new();
    private readonly Popup detailsPopup = new()
    {
        Placement = PlacementMode.Top,
        VerticalOffset = -8,
        AllowsTransparency = true,
        StaysOpen = false,
        PopupAnimation = PopupAnimation.Fade
    };
    private string[] renderedProviders = [];
    private string widgetStyle = "classic";
    private WidgetColorChoice colors = WidgetColors.Choices[0];
    private string? renderedStyle;
    private string? detailsProvider;
    private string? dismissedProvider;
    private Point? dragStart;
    private bool dragging;
    private string? lastDismissedProvider;
    private long dismissedAt;
    private readonly Action dashboardAction;
    internal Popup DetailsPopup => detailsPopup;
    internal Thumb DragGrip => dragGrip;
    private string[] selected = [];
    private bool closing;
    public event Action<double, double>? PositionSaved;
    public event Action<bool>? PinChanged;
    public event Action<string>? StyleChanged;
    public event Action<string>? ColorStyleChanged;
    public event Action<Func<UsageSettings, UsageSettings>>? OptionsChanged;

    public DesktopWidgetWindow(Action openDashboard, Action refresh, Action hideDashboard,
        Func<bool> dashboardVisible, Action exit)
    {
        dashboardAction = openDashboard;
        Title = "AgentMeter desktop status";
        Icon = Branding.WindowIcon();
        WindowStyle = WindowStyle.None;
        AllowsTransparency = true;
        ResizeMode = ResizeMode.NoResize;
        SizeToContent = SizeToContent.WidthAndHeight;
        ShowInTaskbar = false;
        Topmost = true;
        Background = new SolidColorBrush(Color.FromRgb(7, 13, 22));
        FontFamily = new FontFamily("Segoe UI");
        FontSize = 14;
        MaxWidth = SystemParameters.WorkArea.Width;
        var gripSurface = new FrameworkElementFactory(typeof(Border));
        gripSurface.SetValue(Border.BackgroundProperty, Brushes.Transparent);
        var ridges = new FrameworkElementFactory(typeof(StackPanel));
        ridges.SetValue(StackPanel.OrientationProperty, Orientation.Horizontal);
        ridges.SetValue(FrameworkElement.HorizontalAlignmentProperty, HorizontalAlignment.Center);
        ridges.SetValue(FrameworkElement.VerticalAlignmentProperty, VerticalAlignment.Center);
        for (var index = 0; index < 3; index++)
        {
            var ridge = new FrameworkElementFactory(typeof(Border));
            ridge.SetValue(FrameworkElement.WidthProperty, 2d);
            ridge.SetValue(FrameworkElement.HeightProperty, 16d);
            ridge.SetValue(FrameworkElement.MarginProperty, new Thickness(1, 0, 1, 0));
            ridge.SetValue(Border.CornerRadiusProperty, new CornerRadius(1));
            ridge.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(139, 159, 184)));
            ridges.AppendChild(ridge);
        }
        gripSurface.AppendChild(ridges);
        dragGrip.Template = new ControlTemplate(typeof(Thumb)) { VisualTree = gripSurface };
        System.Windows.Automation.AutomationProperties.SetName(dragGrip, "Move desktop status bar");
        dragGrip.PreviewMouseLeftButtonDown += (_, _) => PrepareGripDrag();
        dragGrip.DragStarted += (_, _) =>
        {
            dragging = true;
            detailsPopup.IsOpen = false;
            collapseDelay.Stop();
        };
        dragGrip.DragDelta += (_, args) =>
        {
            if (!options.WidgetPositionLocked)
            {
                Left += args.HorizontalChange;
                Top += args.VerticalChange;
            }
        };
        dragGrip.DragCompleted += (_, _) =>
        {
            dragging = false;
            FinishMove();
            ScheduleCollapse();
        };
        var layout = new DockPanel { LastChildFill = true };
        DockPanel.SetDock(dragGrip, Dock.Left);
        layout.Children.Add(dragGrip);
        layout.Children.Add(collapsedLabel);
        layout.Children.Add(refreshStatus);
        layout.Children.Add(strip);
        surface = new Border
        {
            Padding = new Thickness(6, 6, 10, 6),
            BorderBrush = new SolidColorBrush(Color.FromRgb(58, 71, 88)),
            BorderThickness = new Thickness(1),
            Child = layout
        };
        Content = surface;
        detailsPopup.Closed += (_, _) =>
        {
            lastDismissedProvider = detailsProvider;
            dismissedAt = Stopwatch.GetTimestamp();
            detailsProvider = null;
            ScheduleCollapse();
        };
        collapseDelay.Tick += CollapseTick;
        MouseEnter += (_, _) =>
        {
            collapseDelay.Stop();
            SetCollapsed(false);
        };
        MouseLeave += (_, _) => ScheduleCollapse();
        IsVisibleChanged += (_, _) =>
        {
            if (!IsVisible)
            {
                detailsPopup.IsOpen = false;
                collapseDelay.Stop();
            }
            else ScheduleCollapse();
        };
        Loaded += (_, _) =>
        {
            WidgetPlacement.Constrain(this, options.WidgetSnapToEdges);
            PositionSaved?.Invoke(Left, Top);
            ScheduleCollapse();
        };
        SizeChanged += (_, _) =>
        {
            if (IsVisible && !dragging) WidgetPlacement.Constrain(this, false);
        };
        surface.MouseLeftButtonDown += (_, args) =>
        {
            detailsPopup.IsOpen = false;
            if (args.ClickCount == 2) openDashboard();
            else if (!options.WidgetPositionLocked && args.ButtonState == MouseButtonState.Pressed)
            {
                dragging = true;
                try
                {
                    DragMove();
                    FinishMove();
                }
                finally
                {
                    dragging = false;
                    ScheduleCollapse();
                }
            }
        };
        surface.ContextMenu = CreateContextMenu(openDashboard, refresh, hideDashboard, dashboardVisible, exit);
        Closing += (_, args) =>
        {
            if (!closing)
            {
                args.Cancel = true;
                hideDashboard();
            }
        };
    }

    public void Apply(UsageSettings settings)
    {
        if (closing) return;
        options = settings;
        selected = settings.EnabledProviders.Intersect(settings.WidgetVisibleProviders ?? settings.EnabledProviders).ToArray();
        dragGrip.IsEnabled = !settings.WidgetPositionLocked;
        dragGrip.Cursor = settings.WidgetPositionLocked ? Cursors.Arrow : Cursors.SizeAll;
        if (!settings.WidgetAutoCollapse) SetCollapsed(false);
        SetColors(settings.WidgetColorStyle);
        SetStyle(settings.WidgetStyle);
        Topmost = settings.WidgetAlwaysOnTop;
        Opacity = double.IsFinite(settings.WidgetOpacity) ? Math.Clamp(settings.WidgetOpacity, 0.2, 1) : 1;
        var area = WidgetPlacement.WorkArea(this);
        Left = settings.WidgetLeft is double left && double.IsFinite(left) ? left : area.Left + 24;
        Top = settings.WidgetTop is double top && double.IsFinite(top) ? top : area.Bottom - 80;
        if (IsVisible) WidgetPlacement.Constrain(this, false);
        Render();
        ScheduleCollapse();
    }

    private void SetStyle(string value)
    {
        var next = WidgetStyles.Choices.Any(choice => choice.Id == value) ? value : "classic";
        if (widgetStyle != next) detailsPopup.IsOpen = false;
        widgetStyle = next;
        strip.Orientation = next == "vertical" ? Orientation.Vertical : Orientation.Horizontal;
        strip.ItemWidth = next == "vertical" ? Math.Min(260, SystemParameters.WorkArea.Width - 50) : double.NaN;
        Background = Brushes.Transparent;
        surface.Background = colors.SurfaceBrush;
        surface.CornerRadius = new CornerRadius(next == "classic" ? 0 : next is "compact" or "mini" ? 16 : 10);
        surface.Padding = next is "compact" or "mini" ? new Thickness(5, 4, 8, 4) : new Thickness(6, 6, 10, 6);
        strip.MaxWidth = Math.Max(80, SystemParameters.WorkArea.Width - 50);
        Render();
    }

    private void SetColors(string value)
    {
        colors = WidgetColors.Resolve(value);
        surface.Background = colors.SurfaceBrush;
        surface.BorderBrush = colors.BorderBrush;
        Render();
    }

    public void Shutdown()
    {
        closing = true;
        collapseDelay.Stop();
        collapseDelay.Tick -= CollapseTick;
        detailsPopup.IsOpen = false;
        Close();
    }
}
