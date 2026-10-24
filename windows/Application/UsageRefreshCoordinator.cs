using System.Windows.Threading;
using System.Security;
using AgentMeter.Windows.Core;
using AgentMeter.Windows.Core.History;
using AgentMeter.Windows.Core.Processes;
using AgentMeter.Windows.Providers.Claude;
using AgentMeter.Windows.Providers.Codex;
using AgentMeter.Windows.Providers.Copilot;
using AgentMeter.Windows.Providers.Cursor;
using AgentMeter.Windows.Providers.Gemini;
using AgentMeter.Windows.Providers.OpenRouter;
using AgentMeter.Windows.Providers.Kilo;
using AgentMeter.Windows.Providers.Cline;
using AgentMeter.Windows.Providers.OpenCode;
using AgentMeter.Windows.Providers.Junie;
using AgentMeter.Windows.Settings;

namespace AgentMeter.Windows.Application;

internal sealed partial class UsageRefreshCoordinator : IDisposable
{
    // Scheduling and lifetime live here; Readers shares reads/publication, Login coordinates sign-in.
    private readonly Dispatcher dispatcher;
    private readonly DispatcherTimer timer;
    private readonly Dictionary<string, IUsageReader> readers;
    // The same agent process must not receive overlapping refresh and sign-in requests.
    private readonly SemaphoreSlim gate = new(1);
    private readonly UsageSnapshotStore savedUsage = new();
    private CancellationTokenSource cancellation = new();
    private UsageSettings settings;
    private bool disposed;
    private bool refreshing;
    private bool signingIn;
    // Results from an older settings generation must never overwrite the current UI.
    private int generation;

    public event Action<UsageSnapshot>? Updated;
    public event Action<bool>? BusyChanged;

    public UsageRefreshCoordinator(Dispatcher dispatcher, UsageSettings settings)
    {
        this.dispatcher = dispatcher;
        this.settings = settings;
        IUsageReader[] sources =
        [
            new CodexReader(),
            new CopilotReader(),
            new ClaudeReader(),
            new CursorReader(),
            new GeminiReader(),
            new OpenRouterReader(),
            new KiloReader(),
            new ClineReader(),
            new OpenCodeReader(),
            new JunieReader()
        ];
        readers = sources.ToDictionary(r => r.Id);
        timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(settings.RefreshSeconds) };
        timer.Tick += OnTick;
        timer.Start();
    }

    private async void OnTick(object? sender, EventArgs args)
    {
        if (!refreshing && !signingIn) await RefreshAsync();
    }

    public void RestoreSavedUsage()
    {
        if (disposed) return;
        foreach (var id in settings.EnabledProviders)
            if (savedUsage.Get(id) is UsageSnapshot snapshot) Updated?.Invoke(snapshot);
    }

    public async Task<bool> RefreshConnectionAsync(string provider)
    {
        // Login monitoring uses the same gate, cache and publication checks as a full refresh.
        if (disposed || signingIn || !settings.EnabledProviders.Contains(provider) || !readers.TryGetValue(provider, out var reader)) return false;
        var token = cancellation.Token;
        var version = generation;
        try
        {
            await gate.WaitAsync(token);
            try
            {
                if (disposed || version != generation) return false;
                var selected = settings;
                var snapshot = await ReadProviderAsync(reader, settings, token);
                await PublishSnapshotAsync(snapshot, version, token);
                return snapshot.SignedIn == true;
            }
            finally { gate.Release(); }
        }
        catch (Exception) { return false; }
    }

    public void SetOpenRouterSessionKey(SecureString? key)
    {
        if (!disposed) ((OpenRouterReader)readers["openrouter"]).SetSessionKey(key);
    }

    public async Task ApplyAsync(UsageSettings next)
    {
        if (disposed) return;
        // Cancel queued/active work before starting reads with the new provider selection.
        settings = next;
        generation++;
        cancellation.Cancel();
        cancellation.Dispose();
        cancellation = new();
        timer.Interval = TimeSpan.FromSeconds(settings.RefreshSeconds);
        await RefreshAsync();
    }

    public async Task RefreshAsync()
    {
        if (disposed || signingIn) return;
        var version = generation;
        var token = cancellation.Token;
        try
        {
            await gate.WaitAsync(token);
            try
            {
                if (disposed || version != generation) return;
                refreshing = true;
                // The widget buffers per-provider updates until this batch leaves the busy state.
                BusyChanged?.Invoke(true);
                var selected = settings;
                if (!selected.EnabledProviders.Contains("junie")) ((JunieReader)readers["junie"]).Pause();
                var activeReaders = readers.Values.Where(reader => selected.EnabledProviders.Contains(reader.Id));
                // The gate serializes batches; independent providers within a batch run concurrently.
                await Task.WhenAll(activeReaders.Select(async reader =>
                {
                    var snapshot = await ReadProviderAsync(reader, selected, token);
                    await PublishSnapshotAsync(snapshot, version, token);
                }));
            }
            finally
            {
                refreshing = false;
                if (!disposed) BusyChanged?.Invoke(false);
                gate.Release();
            }
        }
        catch (OperationCanceledException) { }
    }

    public void Dispose()
    {
        if (disposed) return;
        disposed = true;
        generation++;
        timer.Stop();
        timer.Tick -= OnTick;
        cancellation.Cancel();
        foreach (var reader in readers.Values) reader.Dispose();
        Updated = null;
        BusyChanged = null;
        cancellation.Dispose();
    }
}
