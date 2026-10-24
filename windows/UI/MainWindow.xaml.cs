using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;
using AgentMeter.Windows.Application;
using AgentMeter.Windows.Core;
using AgentMeter.Windows.Settings;
using AgentMeter.Windows.UI.Components;

namespace AgentMeter.Windows.UI;

public partial class MainWindow : Window
{
    private readonly Dictionary<string, ProviderCard> cards = new();
    private readonly Dictionary<string, CheckBox> choices = new();
    private readonly Dictionary<string, CheckBox> widgetChoices = new();
    private readonly Dictionary<string, bool?> connectionStates = new();
    private readonly CancellationTokenSource lifetime = new();
    private readonly DispatcherTimer countdown = new() { Interval = TimeSpan.FromMinutes(1) };
    private readonly UsageRefreshCoordinator coordinator;
    private UsageSettings settings;
    private bool closed;
    private bool loginRunning;
    private DesktopWidgetWindow? widget;
    private readonly WidgetOptionsPanel widgetOptions = new();
    private bool initializingWidget = true;
    private bool updatingOpenRouterSelection;
    private bool openRouterPersistenceFailed;
    private TrayHost? tray;
    private bool exiting;

    public MainWindow() : this(SettingsStore.Load()) { }

    internal MainWindow(UsageSettings initialSettings)
    {
        InitializeComponent();
        GeneralSettingsNode.IsSelected = true;
        Icon = Branding.WindowIcon();
        AppLogo.Source = Icon;
        settings = initialSettings;

        // Populate controls before attaching selection handlers, avoiding refreshes during construction.
        WidgetOptionsHost.Content = widgetOptions;
        widgetOptions.Apply(settings);
        widgetOptions.Changed += SaveWidgetOptions;
        WidgetColorInput.ItemsSource = WidgetColors.Choices;
        WidgetColorInput.SelectedValue = settings.WidgetColorStyle;
        WidgetStyleInput.ItemsSource = WidgetStyles.Choices;
        WidgetStyleInput.SelectedValue = settings.WidgetStyle;
        WidgetOpacityInput.Value = settings.WidgetOpacity * 100;
        IntervalInput.Text = settings.RefreshSeconds.ToString();
        CodexPathInput.Text = settings.CodexPath;
        CopilotPathInput.Text = settings.CopilotPath;
        ClaudePathInput.Text = settings.ClaudePath;
        CursorPathInput.Text = settings.CursorPath;
        GeminiPathInput.Text = settings.GeminiPath;
        GeminiProjectInput.Text = settings.GeminiProjectId;
        KiloPathInput.Text = settings.KiloPath;
        ClinePathInput.Text = settings.ClinePath;
        OpenCodePathInput.Text = settings.OpenCodePath;
        JuniePathInput.Text = settings.JuniePath;
        JunieProjectInput.Text = settings.JunieProjectPath;

        // Provider activation and widget visibility are separate persisted choices.
        foreach (var provider in ProviderCatalog.All)
        {
            var (id, name, accent) = (provider.Id, provider.Name, provider.Accent);
            var choice = new CheckBox { Content = name, IsChecked = settings.EnabledProviders.Contains(id) };
            choices[id] = choice;
            ProviderChoices.Children.Add(choice);
            cards[id] = new(id, name, accent, Login) { RefreshSeconds = settings.RefreshSeconds };
            var widgetChoice = new CheckBox
            {
                Content = name,
                Tag = id,
                IsChecked = (settings.WidgetVisibleProviders ?? settings.EnabledProviders).Contains(id),
                IsEnabled = settings.EnabledProviders.Contains(id),
                Margin = new Thickness(0, 3, 14, 3)
            };
            widgetChoices[id] = widgetChoice;
            WidgetProviderChoices.Children.Add(widgetChoice);
        }

        // Compose the coordinator and widget after all provider controls are available.
        coordinator = new(Dispatcher, settings);
        coordinator.Updated += Updated;
        coordinator.BusyChanged += BusyChanged;
        widget = new(OpenDashboard, () => RefreshClick(this, new RoutedEventArgs()), HideWidget, ExitApplication);
        widget.PositionSaved += SaveWidgetPosition;
        widget.PinChanged += SaveWidgetPin;
        widget.StyleChanged += SaveWidgetStyle;
        widget.ColorStyleChanged += SaveWidgetColors;
        widget.OptionsChanged += SaveWidgetOptions;
        WidgetEnabled.IsChecked = settings.ShowDesktopWidget;
        widget.Apply(settings);
        initializingWidget = false;

        // User-driven changes can now flow through the coordinator and the widget settings path.
        foreach (var choice in choices.Values)
        {
            choice.Checked += SelectionChanged;
            choice.Unchecked += SelectionChanged;
        }
        foreach (var choice in widgetChoices.Values)
        {
            choice.Checked += WidgetProviderSelectionChanged;
            choice.Unchecked += WidgetProviderSelectionChanged;
        }
        RebuildCards();

        Loaded += OnLoaded;
        Closing += OnClosing;
        StateChanged += OnStateChanged;
        Closed += OnClosed;
        countdown.Tick += CountdownTick;
        countdown.Start();
    }

    private async void OnLoaded(object sender, RoutedEventArgs args)
    {
        // Let the window draw before initializing tray integration or starting provider reads.
        await Dispatcher.InvokeAsync(() => { }, DispatcherPriority.ContextIdle);
        if (closed) return;
        tray ??= new TrayHost(Dispatcher, OpenDashboard, () => DesktopModeClick(this, new RoutedEventArgs()),
            () => RefreshClick(this, new RoutedEventArgs()), ExitApplication);
        if (settings.ShowDesktopWidget) widget?.Show();
        if (settings.ShowDesktopWidget && settings.WidgetOnly) Hide();
        coordinator.RestoreSavedUsage();
        await coordinator.RefreshAsync();
    }

    private void Updated(UsageSnapshot snapshot)
    {
        if (!closed) UpdateConnectionState(snapshot);
        if (!closed && snapshot.ProviderId == "openrouter") OpenRouterKeyStatus.Text = snapshot.SignedIn == true
            ? openRouterPersistenceFailed ? "Connected for this session. Windows could not save the key; reconnect to retry saving."
                : "Connected. Saved keys are loaded automatically after restarting."
            : snapshot.Notice ?? "OpenRouter usage is unavailable.";
        if (!closed && cards.TryGetValue(snapshot.ProviderId, out var card)) card.Update(snapshot);
        if (!closed) widget?.Update(snapshot);
    }

    private void BusyChanged(bool busy)
    {
        widget?.SetRefreshing(busy);
        RefreshButton.IsEnabled = !busy && !loginRunning;
        RefreshStatus.Text = busy ? "Refreshing…" : $"Refresh finished {DateTime.Now:HH:mm:ss}";
    }

    private void RebuildCards()
    {
        ApplyWidgetProviderChoices();
        Cards.Children.Clear();
        foreach (var provider in ProviderCatalog.All)
            if (settings.EnabledProviders.Contains(provider.Id)) Cards.Children.Add(cards[provider.Id]);
        if (Cards.Children.Count == 0)
            Cards.Children.Add(new TextBlock { Text = "Select a provider in Settings to display usage.", TextWrapping = TextWrapping.Wrap });
    }

}
