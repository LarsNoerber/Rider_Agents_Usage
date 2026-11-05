using System;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Threading;
using AgentsUsage.VisualStudio.Providers.Codex;
using AgentsUsage.VisualStudio.Providers.Copilot;
using AgentsUsage.VisualStudio.Settings;
using Microsoft.ServiceHub.Framework;

namespace AgentsUsage.VisualStudio.Application
{
    internal sealed class UsageRefreshCoordinator : IDisposable
    {
        private readonly UsageOptions options;
        private readonly CodexUsageReader codex = new CodexUsageReader();
        private readonly CopilotUsageReader copilot;
        private readonly DispatcherTimer timer = new DispatcherTimer();
        private readonly CancellationTokenSource lifetime = new CancellationTokenSource();
        private CancellationTokenSource activeRead;
        private bool disposed;
        private int visibleViews;
        private int settingsRevision;
        public CodexUsage Codex { get; private set; }
        public CopilotUsage Copilot { get; private set; }
        public AgentsUsage.VisualStudio.Core.ProviderUsage Claude { get; private set; }
        public AgentsUsage.VisualStudio.Core.ProviderUsage Cursor { get; private set; }
        private readonly Providers.Claude.ClaudeUsageReader claude = new Providers.Claude.ClaudeUsageReader();
        private readonly Providers.Cursor.CursorUsageReader cursor = new Providers.Cursor.CursorUsageReader();
        public Core.UsageHistory History { get; } = new Core.UsageHistory();
        public bool Refreshing { get; private set; }
        public event EventHandler Changed;

        public UsageRefreshCoordinator(UsageOptions options, IServiceBroker broker)
        {
            this.options = options;
            copilot = new CopilotUsageReader(broker);
            options.Changed += OnOptionsChanged;
            timer.Tick += OnTick;
            ConfigureTimer();
        }

        public void SetViewVisible(bool visible)
        {
            if (disposed) return;
            visibleViews = Math.Max(0, visibleViews + (visible ? 1 : -1));
            ConfigureTimer();
            if (visible) _ = RefreshAsync();
        }

        private bool ShouldRead => !disposed && (visibleViews > 0 || options.ShowStatusBar) && (options.CodexEnabled || options.CopilotEnabled || options.ClaudeEnabled || options.CursorEnabled);

        private void ConfigureTimer()
        {
            timer.Stop();
            timer.Interval = TimeSpan.FromSeconds(Math.Max(10, Math.Min(3600, options.RefreshIntervalSeconds)));
            if (ShouldRead) timer.Start();
            else activeRead?.Cancel();
        }

        private void OnOptionsChanged(object sender, EventArgs args)
        {
            if (disposed) return;
            settingsRevision++;
            activeRead?.Cancel();
            if (!options.CodexEnabled) Codex = null;
            if (!options.CopilotEnabled) Copilot = null;
            if (!options.ClaudeEnabled) Claude = null;
            if (!options.CursorEnabled) Cursor = null;
            ConfigureTimer();
            Changed?.Invoke(this, EventArgs.Empty);
            _ = RefreshAsync();
        }

        private void OnTick(object sender, EventArgs args) { _ = RefreshAsync(); }

        public async Task RefreshAsync()
        {
            if (!ShouldRead || Refreshing) return;
            Refreshing = true;
            // Capture choices before leaving the UI context; a later revision makes this batch obsolete.
            var revision = settingsRevision;
            var readCodex = options.CodexEnabled;
            var readCopilot = options.CopilotEnabled;
            var readClaude = options.ClaudeEnabled;
            var readCursor = options.CursorEnabled;
            var path = options.CodexPath;
            var reads = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
            activeRead = reads;
            Changed?.Invoke(this, EventArgs.Empty);
            try
            {
                var codexTask = readCodex ? Task.Run(() => codex.ReadAsync(path, reads.Token)) : Task.FromResult<CodexUsage>(null);
                var copilotTask = readCopilot ? Task.Run(() => copilot.ReadAsync(reads.Token)) : Task.FromResult<CopilotUsage>(null);
                var claudeTask = readClaude ? Task.Run(() => claude.ReadAsync(reads.Token)) : Task.FromResult<Core.ProviderUsage>(null);
                var cursorTask = readCursor ? Task.Run(() => cursor.ReadAsync(reads.Token)) : Task.FromResult<Core.ProviderUsage>(null);
                await Task.WhenAll(codexTask, copilotTask, claudeTask, cursorTask);
                // Cancellation may arrive after a reader completes but before its result is published.
                if (disposed || revision != settingsRevision || reads.IsCancellationRequested) return;
                Claude = await claudeTask;
                Cursor = await cursorTask;
                Codex = await codexTask;
                Copilot = await copilotTask;
                History.Record("OpenAI / 5-hour", 100 - Codex?.FiveHourLeft);
                History.Record("OpenAI / Weekly", 100 - Codex?.WeeklyLeft);
                History.Record("Copilot", Copilot?.Primary?.PercentUsed);
                foreach (var usage in new[] { Claude, Cursor })
                    if (usage != null) foreach (var quota in usage.Quotas) History.Record(usage.Name + " / " + quota.Title, quota.PercentUsed);
            }
            catch (OperationCanceledException) { }
            finally
            {
                activeRead = null;
                reads.Dispose();
                Refreshing = false;
                if (!disposed)
                {
                    Changed?.Invoke(this, EventArgs.Empty);
                    // Settings changed during the read: immediately replace the cancelled batch.
                    if (revision != settingsRevision && ShouldRead) _ = RefreshAsync();
                }
                else lifetime.Dispose();
            }
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            timer.Stop();
            timer.Tick -= OnTick;
            options.Changed -= OnOptionsChanged;
            lifetime.Cancel();
            codex.Dispose();
            Changed = null;
            if (!Refreshing) lifetime.Dispose();
        }
    }
}
