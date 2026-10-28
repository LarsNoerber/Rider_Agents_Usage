using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using AgentMeter.Windows;
using AgentMeter.Windows.Application;
using AgentMeter.Windows.Core;
using AgentMeter.Windows.Core.History;
using AgentMeter.Windows.Core.Processes;
using AgentMeter.Windows.Providers.Codex;
using AgentMeter.Windows.Providers.Copilot;
using AgentMeter.Windows.Providers.Claude;
using AgentMeter.Windows.Providers.Gemini;
using AgentMeter.Windows.Settings;
using AgentMeter.Windows.UI;
using AgentMeter.Windows.UI.Components;

internal static class Program
{
    private static int passed;

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            var output = args.FirstOrDefault() ?? Path.Combine(Path.GetTempPath(), "AgentMeter-smoke");
            Directory.CreateDirectory(output);
            var app = new App();
            app.InitializeComponent();
            Check(RenderOptions.ProcessRenderMode == System.Windows.Interop.RenderMode.SoftwareOnly,
                "Application selects software rendering before any native windows are created");
            var node = JsonNode.Parse("{\"number\":3.5,\"textNumber\":\"42\",\"negative\":-1,\"notNumber\":\"NaN\",\"object\":{}}");
            Check(node.Number("number") == 3.5 && node.Number("textNumber") == 42, "Quota JSON numeric and string values");
            Check(node.Number("negative") == null && node.Number("notNumber") == null && node.Number("missing") == null,
                "Missing, negative and non-finite quotas stay unavailable");
            Check(JsonFields.Unix(253402300800) == null && JsonFields.Unix(0)?.Year == 1970, "Reset epoch boundaries");
            Check(JsonFields.Date("2026-10-05T12:00:00Z")?.UtcDateTime.Hour == 12 && JsonFields.Date("bad") == null, "Reset date parsing");
            Check(AgentExecutables.Find("codex", "Z:/AgentMeter-nonexistent/codex.exe") == null,
                "Explicit missing executable does not fall back to another account source");

            using (var http = new ProviderHttp(["api.anthropic.com"]))
            {
                Reject(() => http.SendAsync("http://api.anthropic.com/quota", new Dictionary<string, string>(), CancellationToken.None)
                    .GetAwaiter().GetResult(), "Reject insecure quota destination");
                Reject(() => http.SendAsync("https://other.example/quota", new Dictionary<string, string>(), CancellationToken.None)
                    .GetAwaiter().GetResult(), "Reject another provider destination");
            }
            using (var reader = new CodexReader())
            {
                var result = reader.ReadAsync("Z:/AgentMeter-nonexistent/codex.exe", CancellationToken.None).GetAwaiter().GetResult();
                Check(result.Quotas.Count == 0 && result.Notice != null, "Missing Codex CLI reports unavailable, never 0%");
            }
            using (var reader = new CopilotReader())
            {
                var result = reader.ReadAsync("Z:/AgentMeter-nonexistent/copilot.exe", CancellationToken.None).GetAwaiter().GetResult();
                Check(result.Quotas.Count == 0 && result.Notice != null, "Missing Copilot server reports unavailable, never 0%");
            }
            using (var coordinator = new UsageRefreshCoordinator(app.Dispatcher, new UsageSettings { EnabledProviders = [] }))
            {
                var events = 0;
                coordinator.Updated += _ => events++;
                coordinator.RefreshAsync().GetAwaiter().GetResult();
                Check(events == 0, "Deselected providers emit no reads or updates");
                coordinator.Dispose();
                coordinator.RefreshAsync().GetAwaiter().GetResult();
                Check(events == 0, "Disposed coordinator ignores refresh");
            }
            var apiLogin = ClaudeAuthStatus.FromStatus(true, "api_key", null);
            Check(apiLogin.SignedIn == true && apiLogin.Plan == "Anthropic API" && apiLogin.Quotas.Count == 0,
                "Claude API login is signed in without inventing subscription quota");
            Check(ClaudeAuthStatus.FromStatus(false, null, null).SignedIn == false,
                "Claude official signed-out status restores login action");
            var geminiHome = Path.GetFullPath(Path.Combine(output, "gemini-fixture"));
            var geminiRoot = Path.Combine(geminiHome, ".gemini");
            Directory.CreateDirectory(geminiRoot);
            var originalGeminiHome = Environment.GetEnvironmentVariable("GEMINI_CLI_HOME");
            var originalProject = Environment.GetEnvironmentVariable("GOOGLE_CLOUD_PROJECT");
            var originalProjectId = Environment.GetEnvironmentVariable("GOOGLE_CLOUD_PROJECT_ID");
            try
            {
                Environment.SetEnvironmentVariable("GEMINI_CLI_HOME", geminiHome);
                Environment.SetEnvironmentVariable("GOOGLE_CLOUD_PROJECT", null);
                Environment.SetEnvironmentVariable("GOOGLE_CLOUD_PROJECT_ID", null);
                var authFile = Path.Combine(geminiRoot, "oauth_creds.json");
                if (File.Exists(authFile)) File.Delete(authFile);
                using var gemini = new GeminiReader(enableWebFallback: false);
                var missing = gemini.ReadAsync("", CancellationToken.None).GetAwaiter().GetResult();
                Check(missing.SignedIn == false && missing.Quotas.Count == 0, "Missing official Gemini login restores Sign in without invented quota");
                var geminiCard = new ProviderCard("gemini", "Gemini CLI", "#A4B8FF", _ => { });
                geminiCard.Update(missing);
                Check(Descendants(geminiCard).OfType<Button>().Any(b => b.Content?.ToString() == "Sign in"), "Gemini CLI has an official agent sign-in action");
                File.WriteAllText(authFile, "{}");
                var missingProject = gemini.ReadAsync("", CancellationToken.None).GetAwaiter().GetResult();
                Check(missingProject.Notice?.Contains("project ID", StringComparison.OrdinalIgnoreCase) == true,
                    $"Workspace project is required before authenticated Gemini quota requests (notice: {missingProject.Notice ?? "none"})");
                File.WriteAllText(authFile, "{\"expiry_date\":1}");
                var expired = gemini.ReadWorkspaceAsync("synthetic-project", CancellationToken.None).GetAwaiter().GetResult();
                Check(expired.SignedIn == false && expired.Notice!.Contains("expired"), "Expired Gemini session requires the official CLI to renew it");
                var number = gemini.ReadWorkspaceAsync("123456789", CancellationToken.None).GetAwaiter().GetResult();
                Check(number.Notice!.Contains("numeric"), "Gemini rejects numeric project numbers instead of string IDs");
                File.Delete(authFile);
            }
            finally
            {
                Environment.SetEnvironmentVariable("GEMINI_CLI_HOME", originalGeminiHome);
                Environment.SetEnvironmentVariable("GOOGLE_CLOUD_PROJECT", originalProject);
                Environment.SetEnvironmentVariable("GOOGLE_CLOUD_PROJECT_ID", originalProjectId);
            }
            Check(GeminiLocalConfiguration.ReadProject(["GEMINI_API_KEY=synthetic-ignored", "export GOOGLE_CLOUD_PROJECT_ID='alternate-project'",
                "GOOGLE_CLOUD_PROJECT=main-project # comment"]) == "main-project", "Gemini project parsing respects key priority and ignores API keys");
            var geminiQuota = GeminiQuotaParser.Parse((JsonObject)JsonNode.Parse("{\"currentTier\":{\"name\":\"Code Assist Standard\"}}")!,
                (JsonObject)JsonNode.Parse("{\"buckets\":[{\"modelId\":\"gemini-pro\",\"remainingFraction\":0.97,\"remainingAmount\":\"1455\",\"resetTime\":\"2026-10-05T00:00:00Z\"},{\"modelId\":\"gemini-flash\",\"remainingFraction\":0},{\"modelId\":\"unknown\"},{\"modelId\":\"invalid\",\"remainingFraction\":9},{\"modelId\":\"count-only\",\"remainingAmount\":\"42\"}]}")!);
            Check(geminiQuota.SignedIn == true && geminiQuota.Plan == "Code Assist Standard" && geminiQuota.Quotas.Count == 4,
                "Gemini preserves the server-reported plan and omits absent buckets");
            Check(geminiQuota.Quotas[0].Percent == 97 && !geminiQuota.Quotas[0].Consumed && geminiQuota.Quotas[0].ResetsAt.HasValue &&
                geminiQuota.Quotas[0].Detail!.Contains("1455"), "Gemini fraction becomes remaining percentage with actual counts and reset time");
            Check(geminiQuota.Quotas[1].Percent == 0 && geminiQuota.Quotas[2].Percent == null && geminiQuota.Quotas[3].Percent == null,
                "Gemini exhausted, invalid and count-only buckets remain distinct");
            Check(GeminiQuotaParser.Parse(new(), new()).Quotas.Count == 0, "Empty Gemini quota response never creates a balance");
            var quotaCard = new ProviderCard("gemini", "Gemini CLI", "#A4B8FF", _ => { });
            quotaCard.Update(geminiQuota);
            Render(quotaCard, Path.Combine(output, "gemini-cli-quota.png"), 550, 520);
            Check(Descendants(quotaCard).OfType<Button>().Where(b => b.Content?.ToString() == "Sign in").All(b => b.Visibility == Visibility.Collapsed),
                "Authenticated Gemini CLI hides Sign in");
            Check(AgentLogin.WebProviderUrl("gemini") == "https://gemini.google.com/",
                "Web provider actions have fixed official HTTPS destinations");

            var widget = new DesktopWidgetWindow(() => { }, () => { }, () => { }, () => false, () => { });
            widget.Apply(new UsageSettings { EnabledProviders = ["codex", "jetbrains", "copilot"] });
            widget.Update(new("codex", "Plus", [new("5-hour remaining", 87, false), new("Weekly remaining", 64, false)]));
            widget.Update(new("jetbrains", "AI Pro", [new("Credits remaining", 29, false)]));
            widget.Update(new("copilot", "Pro", [new("Premium requests used", 3, true)]));
            Render((FrameworkElement)widget.Content, Path.Combine(output, "desktop-status-example.png"), 550, 34);
            var texts = Texts(widget.Content).ToList();
            Check(texts.Select(TextOf).SequenceEqual(["OpenAi | D=87% - W=64%", "Copilot | 3%"]),
                "Desktop text matches requested screenshot format");
            Check(!new UsageSettings().EnabledProviders.Contains("jetbrains"), "Windows excludes JetBrains AI, including old saved selections");
            Check(Descendants((DependencyObject)widget.Content).OfType<Border>().Count(b => b.Width == 1 && b.Height == 18) == 1,
                "Desktop agents have visible separators");
            Check(ColorOf(texts.Last().Inlines.OfType<Run>().Last()) == "#FF67DAB1", "Copilot 3% consumed is healthy");
            var copilotSegment = Descendants((DependencyObject)widget.Content).OfType<Button>().Last();
            Check(texts.All(t => t.ToolTip == null) && !ToolTipService.GetIsEnabled(copilotSegment), "Desktop quota details never open on hover");
            Check(!widget.DetailsPopup.IsOpen && widget.DetailsPopup.Placement == PlacementMode.Top && widget.DetailsPopup.VerticalOffset < 0,
                "Desktop click popup is initially closed and positioned above the bar");
            widget.Show();
            copilotSegment.Focus();
            app.Dispatcher.Invoke(() => { }, DispatcherPriority.ContextIdle);
            Check(copilotSegment.FocusVisualStyle == null &&
                copilotSegment.Template.Triggers.OfType<Trigger>().All(t =>
                    t.Property != UIElement.IsKeyboardFocusedProperty && t.Property != UIElement.IsFocusedProperty) &&
                Descendants(copilotSegment).OfType<Border>().All(b => b.BorderThickness == new Thickness(0)),
                "Desktop segment template has hover styling without click/focus frames");
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            app.Dispatcher.Invoke(() => { }, DispatcherPriority.ContextIdle);
            Check(widget.DetailsPopup.IsOpen && ReferenceEquals(widget.DetailsPopup.PlacementTarget, copilotSegment) && !widget.DetailsPopup.StaysOpen,
                "Agent click opens an above-bar popup which dismisses on outside click");
            var richTooltip = (Border)widget.DetailsPopup.Child;
            var popupBottom = richTooltip.PointToScreen(new Point(0, richTooltip.ActualHeight)).Y;
            Check(popupBottom < copilotSegment.PointToScreen(new Point(0, 0)).Y, "Native popup is actually displayed above the clicked agent segment");
            Check(Texts(richTooltip).Any(t => t.Text.Contains("3% consumed, 97% available")),
                "Copilot popup explains 97% available as 3% consumed");
            Render(richTooltip, Path.Combine(output, "desktop-rich-tooltip.png"), 360, 180);
            Check(Descendants(richTooltip).OfType<ProgressBar>().Single().Value == 3, "Desktop tooltip renders the consumed quota as a compact bar");
            widget.Update(new("copilot", "Pro", [new("Premium requests used", 97, true)]));
            Check(widget.DetailsPopup.IsOpen && ReferenceEquals(widget.DetailsPopup.PlacementTarget, copilotSegment),
                "Automatic quota refresh preserves the open popup and its agent anchor");
            Render((FrameworkElement)widget.Content, Path.Combine(output, "desktop-status-refreshed.png"), 550, 34);
            Check(ColorOf(Texts(widget.Content).Last().Inlines.OfType<Run>().Last()) == "#FFFF737D", "Copilot 97% consumed warns of exhaustion");
            Render((FrameworkElement)widget.DetailsPopup.Child, Path.Combine(output, "desktop-click-popup.png"), 360, 180);
            Check(Descendants(widget.DetailsPopup.Child).OfType<ProgressBar>().Single().Value == 97,
                "Open popup refreshes its quota bars automatically");
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            Check(!widget.DetailsPopup.IsOpen, "Repeated agent click closes the details popup");
            Check(widget.DragGrip.Cursor == Cursors.SizeAll &&
                Descendants(widget.DragGrip).OfType<Border>().Count(b => b.Width == 2 && b.Height == 16) == 3,
                "Desktop bar has a visible ridged drag grip with a move cursor");
            var savedPositions = new List<Point>();
            widget.PositionSaved += (left, top) => savedPositions.Add(new Point(left, top));
            var initialPosition = new Point(widget.Left, widget.Top);
            for (var gesture = 0; gesture < 2; gesture++)
            {
                copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                Check(widget.DetailsPopup.IsOpen, "Details reopen before each grip drag");
                widget.DragGrip.RaiseEvent(new MouseButtonEventArgs(Mouse.PrimaryDevice, Environment.TickCount, MouseButton.Left)
                    { RoutedEvent = UIElement.PreviewMouseLeftButtonDownEvent });
                Check(!widget.DetailsPopup.IsOpen, "Grip press dismisses details before starting drag");
                widget.DragGrip.RaiseEvent(new DragStartedEventArgs(0, 0) { RoutedEvent = Thumb.DragStartedEvent });
                widget.DragGrip.RaiseEvent(new DragDeltaEventArgs(12, -8) { RoutedEvent = Thumb.DragDeltaEvent });
                widget.DragGrip.RaiseEvent(new DragCompletedEventArgs(12, -8, false) { RoutedEvent = Thumb.DragCompletedEvent });
            }
            Check(widget.Left == initialPosition.X + 24 && widget.Top == initialPosition.Y - 16 &&
                savedPositions.Count == 2 && savedPositions.Last() == new Point(widget.Left, widget.Top),
                "Repeated grip drags move the bar after popup use and save its final position");
            Render((FrameworkElement)widget.Content, Path.Combine(output, "desktop-drag-grip.png"), 550, 34);
            widget.Top = SystemParameters.WorkArea.Top;
            app.Dispatcher.Invoke(() => { }, DispatcherPriority.ContextIdle);
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            app.Dispatcher.Invoke(() => { }, DispatcherPriority.ContextIdle);
            var belowContent = (FrameworkElement)widget.DetailsPopup.Child;
            Check(widget.DetailsPopup.Placement == PlacementMode.Bottom && widget.DetailsPopup.VerticalOffset > 0,
                "Details choose below the bar at the upper screen edge");
            Check(belowContent.PointToScreen(new Point(0, 0)).Y >
                copilotSegment.PointToScreen(new Point(0, copilotSegment.ActualHeight)).Y,
                "Native details popup actually appears below the upper-edge bar");
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            widget.Top = initialPosition.Y;
            app.Dispatcher.Invoke(() => { }, DispatcherPriority.ContextIdle);
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            Check(widget.DetailsPopup.Placement == PlacementMode.Top && widget.DetailsPopup.VerticalOffset < 0,
                "Details return above the bar when sufficient space is available");
            copilotSegment.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            widget.Update(UsageSnapshot.Unavailable("copilot", "Sign in"));
            Check(TextOf(Texts(widget.Content).Last()) == "Copilot | —", "Missing quota stays unavailable in desktop status");
            widget.Update(new("copilot", "Pro", [new("Premium requests used", null, true, Unlimited: true)]));
            Check(TextOf(Texts(widget.Content).Last()) == "Copilot | Unlimited", "Unlimited quota has no percentage");
            widget.Apply(new UsageSettings { EnabledProviders = ["codex"] });
            Check(Texts(widget.Content).Count() == 1, "Desktop status respects provider selection");
            widget.Apply(new UsageSettings { EnabledProviders = ["gemini"] });
            Render((FrameworkElement)widget.Content, Path.Combine(output, "desktop-web-segments.png"), 300, 34);
            Check(Texts(widget.Content).Select(TextOf).SequenceEqual(["Gemini | —"]), "Gemini web quota stays explicitly unavailable without a reported session");
            widget.Update(geminiQuota);
            Check(TextOf(Texts(widget.Content).First()) == "Gemini | 97%", "Gemini desktop segment shows reported CLI remaining quota");
            widget.Apply(new UsageSettings { EnabledProviders = ["codex"], WidgetOpacity = 0.55 });
            Check(widget.Opacity == 0.55 && widget.AllowsTransparency, "Desktop opacity applies to the whole floating strip");
            widget.Apply(new UsageSettings { EnabledProviders = ["codex"], WidgetOpacity = 0.01 });
            Check(widget.Opacity == 0.2, "Desktop opacity remains visible at a minimum of 20%");
            widget.Apply(new UsageSettings { EnabledProviders = ["codex"], WidgetOpacity = double.NaN });
            Check(widget.Opacity == 1, "Invalid opacity safely defaults to opaque");
            widget.Apply(new UsageSettings { EnabledProviders = [], WidgetLeft = double.PositiveInfinity, WidgetTop = double.NaN });
            Check(double.IsFinite(widget.Left) && double.IsFinite(widget.Top), "Corrupt saved widget coordinates recover on screen");
            Check(TextOf(Texts(widget.Content).Single()).Contains("Select providers"), "Empty provider selection leaves dashboard instructions");
            widget.Shutdown();

            var tooltip = new ToolTip { Content = "GitHub Copilot Pro\n3% consumed, 97% available\nUpdated 15:58", Style = (Style)app.FindResource(typeof(ToolTip)) };
            Render(tooltip, Path.Combine(output, "desktop-tooltip.png"), 320, 96);
            Check(((SolidColorBrush)tooltip.Background).Color.ToString() == "#FF19212D" &&
                Texts(tooltip).Any(t => ((SolidColorBrush)t.Foreground).Color.ToString() == "#FFE9EEF7"),
                "Desktop tooltip uses an explicit dark surface and readable light text");
            using (var icon = Branding.TrayIcon()) Check(icon.Width == 32 && Branding.WindowIcon().PixelWidth > 0,
                "Embedded AgentMeter logo is valid for both tray and windows");
            using (var icon = Branding.TrayIcon())
            using (var bitmap = icon.ToBitmap())
                Check(bitmap.GetPixel(0, bitmap.Height / 2).A == 0 && bitmap.GetPixel(bitmap.Width / 2, bitmap.Height / 2).A > 0,
                    "Tray logo background is transparent while the logo remains visible");
            var windowLogo = new FormatConvertedBitmap(Branding.WindowIcon(), PixelFormats.Bgra32, null, 0);
            var logoPixels = new byte[windowLogo.PixelWidth * windowLogo.PixelHeight * 4];
            windowLogo.CopyPixels(logoPixels, windowLogo.PixelWidth * 4, 0);
            Check(logoPixels[(windowLogo.PixelHeight / 2 * windowLogo.PixelWidth) * 4 + 3] == 0 &&
                logoPixels[(windowLogo.PixelHeight / 2 * windowLogo.PixelWidth + windowLogo.PixelWidth / 2) * 4 + 3] > 0,
                "Embedded EXE/window logo has a transparent background");

            var history = new UsageHistory();
            var now = DateTimeOffset.Now;
            var observation = new UsageSnapshot("codex", "Plus", [new("5-hour remaining", 71, false), new("Weekly remaining", 61, false)], UpdatedAt: now);
            history.Record(observation);
            history.Record(observation);
            Check(history.Observations.Count == 1 && history.Observations[0].Consumed["5-hour remaining"] == 29,
                "History converts remaining to consumption and ignores countdown duplicates");
            history.Record(new("codex", "Plus", [new("Unlimited", null, false, Unlimited: true)], UpdatedAt: now.AddMinutes(1)));
            Check(history.Observations.Count == 1, "Unlimited and unavailable quotas do not create fabricated history");
            history.Record(observation with { UpdatedAt = now.AddHours(25) });
            Check(history.Observations.Count == 1, "History retains no more than 24 hours of observed data");

            var card = new ProviderCard("copilot", "GitHub Copilot", "#84B8FF", _ => { });
            var signIn = Descendants(card).OfType<Button>().Single(b => b.Content?.ToString() == "Sign in");
            card.Update(new("copilot", "Pro", [], "No finite quota") { SignedIn = true });
            Check(signIn.Visibility == Visibility.Collapsed, "Signed-in account hides Sign in even without a finite quota");
            card.Update(UsageSnapshot.Unavailable("copilot", "Connection unavailable"));
            Check(signIn.Visibility == Visibility.Collapsed, "Temporary quota failure preserves known sign-in state");
            card.Update(UsageSnapshot.Unavailable("copilot", "Session expired") with { SignedIn = false });
            Check(signIn.Visibility == Visibility.Visible, "Expired official session restores Sign in");
            card.Update(new("copilot", "Pro", [new("Premium requests used", 3, true)]));
            Render(card, Path.Combine(output, "copilot-97-available.png"), 550, 160);
            Check(Texts(card).Any(t => TextOf(t).Contains("97% available")), "Copilot card explicitly shows available balance");
            card.Update(new("copilot", "Pro", [new("Premium requests used", 97, true, DateTimeOffset.Now.AddHours(2))]));
            Render(card, Path.Combine(output, "quota-card-example.png"), 550, 160);
            var bar = Descendants(card).OfType<ProgressBar>().Single();
            Check(bar.Value == 97 && ((SolidColorBrush)bar.Foreground).Color.ToString() == "#FFFF737D", "Copilot card grows with consumption and uses warning color");
            card.Update(new("codex", "Plus", [new("5-hour remaining", 87, false)]));
            Check(Descendants(card).OfType<ProgressBar>().Single().Value == 87, "OpenAI card shows remaining quota");
            card.Update(UsageSnapshot.Unavailable("codex", "Sign in with the official agent"));
            Check(!Descendants(card).OfType<ProgressBar>().Any(), "Unavailable card has no false quota bar");

            var detailsCard = new ProviderCard("codex", "OpenAI Codex", "#67DAB1", _ => { });
            detailsCard.Update(observation with { UpdatedAt = now.AddHours(-2) });
            detailsCard.Update(observation with { Quotas = [new("5-hour remaining", 63, false, now.AddHours(3)), new("Weekly remaining", 54, false)], UpdatedAt = now.AddHours(-1) });
            var badge = Descendants(detailsCard).OfType<Button>().Single(b => b.Content?.ToString()?.StartsWith("Plus") == true);
            badge.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            Render(detailsCard, Path.Combine(output, "subscription-details.png"), 550, 740);
            Check(Texts(detailsCard).All(t => !t.Text.StartsWith("Subscription:", StringComparison.Ordinal)) &&
                Texts(detailsCard).Any(t => t.Text.Contains("Automatic refresh: every 300 seconds")) &&
                Texts(detailsCard).All(t => !t.Text.Contains("Usage available", StringComparison.Ordinal)),
                "Plan badge opens supplemental details without repeating plan or quota status");
            var historyChart = Descendants(detailsCard).OfType<UsageHistoryChart>().Single();
            var detailsPanel = Descendants(detailsCard).OfType<StackPanel>().Single(p => p.Children.Contains(historyChart));
            Check(detailsPanel.Visibility == Visibility.Visible && historyChart.History.Observations.Count == 2, "Badge displays actual collected consumption history");
            detailsCard.Update(observation);
            Render(detailsCard, Path.Combine(output, "subscription-details-refreshed.png"), 550, 740);
            Check(detailsPanel.Visibility == Visibility.Visible && badge.Content?.ToString()?.EndsWith("▴") == true, "Subscription details stay expanded across refreshes");
            var range = Descendants(detailsCard).OfType<ComboBox>().Single();
            Check(Texts(range).Any(t => ((SolidColorBrush)t.Foreground).Color.ToString() == "#FFD8E3F2"), "Closed dropdown uses readable text on the dark surface");
            Check((range.Foreground as SolidColorBrush)?.Color.ToString() == "#FFF1F1F1" &&
                (range.Background as SolidColorBrush)?.Color.ToString() == "#FF242424" &&
                range.Template?.FindName("DropDownToggle", range) is ToggleButton,
                "History range uses a custom high-contrast dark dropdown template");
            Check((badge.Foreground as SolidColorBrush)?.Color.ToString() == "#FFFFFFFF" &&
                (badge.Background as SolidColorBrush)?.Color != Colors.Transparent,
                "Subscription plan badge has a distinct filled surface and readable label");
            Check(range.Foreground is SolidColorBrush rangeText && rangeText.Color.ToString() == "#FFF1F1F1" &&
                range.Background is SolidColorBrush rangeSurface && rangeSurface.Color.ToString() == "#FF242424" &&
                range.Template?.FindName("DropDownToggle", range) is ToggleButton,
                "History range uses a custom high-contrast dark dropdown template");
            Check(badge.Foreground is SolidColorBrush badgeText && badgeText.Color.ToString() == "#FFFFFFFF" &&
                badge.Background is SolidColorBrush badgeSurface && badgeSurface.Color != Colors.Transparent,
                "Subscription plan badge has a distinct filled surface and readable label");
            var dropdownItem = new ComboBoxItem { Content = "24 hours", ContentTemplate = range.ItemTemplate };
            Render(dropdownItem, Path.Combine(output, "dropdown-item.png"), 120, 30);
            Check(Texts(dropdownItem).Any(t => ((SolidColorBrush)t.Foreground).Color.ToString() == "#FFD8E3F2"), "Dropdown options use readable text on the dark surface");
            var contextItem = new MenuItem { Header = "Open dashboard" };
            Render(contextItem, Path.Combine(output, "context-menu-item.png"), 180, 32);
            Check(Texts(contextItem).Any(t => ((SolidColorBrush)t.Foreground).Color.ToString() == "#FFD8E3F2"), "Desktop context menu uses readable text on the dark surface");
            range.SelectedIndex = 2;
            Check(historyChart.Hours == 24, "History range switches to 24 hours");
            badge.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            Check(detailsPanel.Visibility == Visibility.Collapsed, "Plan badge also collapses subscription details");

            var dashboard = new MainWindow(new UsageSettings { EnabledProviders = [], ShowDesktopWidget = true });
            Render((FrameworkElement)dashboard.Content, Path.Combine(output, "dashboard-unavailable.png"), 980, 720);
            var refreshButton = Descendants((DependencyObject)dashboard.Content).OfType<Button>().Single(b => b.Content?.ToString() == "Refresh");
            Check(refreshButton.Background is SolidColorBrush buttonBackground && buttonBackground.Color.ToString() == "#FF151B25",
                "Buttons use a dark background in the dashboard theme");
            Check(Descendants((DependencyObject)dashboard.Content).OfType<Button>().Any(b => b.Content?.ToString() == "Desktop widget"),
                "Dashboard and desktop-mode button load successfully");
            var settingsTab = (TabItem)dashboard.FindName("SettingsTab");
            ((TabControl)dashboard.FindName("MainTabs")).SelectedItem = settingsTab;
            Render((FrameworkElement)dashboard.Content, Path.Combine(output, "dashboard-settings.png"), 700, 500);
            Check(Descendants((DependencyObject)dashboard.Content).OfType<ScrollViewer>().Any(s => s.VerticalScrollBarVisibility == ScrollBarVisibility.Auto),
                "Settings remain scrollable in the compact dashboard");
            Check(dashboard.Icon != null && dashboard.FindName("ExitButton") is Button,
                "Dashboard has the AgentMeter icon and explicit Exit button");
            // Exercise startup, native chrome and the widget with no selected providers or saved user settings.
            dashboard.ShowActivated = false;
            dashboard.ShowInTaskbar = false;
            dashboard.Show();
            dashboard.Dispatcher.Invoke(() => { }, DispatcherPriority.ApplicationIdle);
            Render(dashboard, Path.Combine(output, "dashboard-window.png"), 980, 720);
            Check(Descendants(dashboard).Contains((DependencyObject)dashboard.Content),
                "The shown native Window template includes the dashboard content");
            var didClose = false;
            dashboard.Closed += (_, _) => didClose = true;
            dashboard.Close();
            Check(!didClose, "Normal dashboard close is canceled for tray operation");
            dashboard.ExitApplication();
            Check(didClose, "Explicit Exit closes the dashboard and disposes services");
            Console.WriteLine($"PASS: {passed} checks. WPF renderings: {output}");
            Console.WriteLine("No provider credentials, account quota requests, sign-ins, installations or Store submissions were performed.");
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine($"FAIL: {error.GetType().Name}: {error.Message}");
            return 1;
        }
    }

    private static string ColorOf(Run run) => ((SolidColorBrush)run.Foreground).Color.ToString();
    private static string TextOf(TextBlock text) => new TextRange(text.ContentStart, text.ContentEnd).Text.TrimEnd('\r', '\n');
    private static IEnumerable<TextBlock> Texts(object root) => Descendants((DependencyObject)root).OfType<TextBlock>();
    private static IEnumerable<DependencyObject> Descendants(DependencyObject root)
    {
        yield return root;
        if (root is UIElement { Visibility: Visibility.Collapsed }) yield break;
        if (root is not Visual && root is not System.Windows.Media.Media3D.Visual3D) yield break;
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(root); index++)
            foreach (var child in Descendants(VisualTreeHelper.GetChild(root, index))) yield return child;
    }
    private static void Render(FrameworkElement element, string path, int width, int height)
    {
        element.Measure(new Size(width, height));
        element.Arrange(new Rect(0, 0, width, height));
        element.UpdateLayout();
        var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(element);
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var stream = File.Create(path);
        encoder.Save(stream);
    }
    private static void Check(bool condition, string name)
    {
        if (!condition) throw new InvalidOperationException(name);
        passed++;
    }
    private static void Reject(Action action, string name)
    {
        try { action(); }
        catch (InvalidOperationException) { passed++; return; }
        throw new InvalidOperationException(name);
    }
}
