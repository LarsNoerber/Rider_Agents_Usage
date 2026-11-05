using System;
using System.IO;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using AgentsUsage.VisualStudio.Core;

namespace AgentsUsage.VisualStudio.Providers.Claude
{
    internal sealed class ClaudeUsageReader
    {
        public async Task<ProviderUsage> ReadAsync(CancellationToken cancellation)
        {
            var result = new ProviderUsage { Name = "Claude Code" };
            try
            {
                var directory = Environment.GetEnvironmentVariable("CLAUDE_CONFIG_DIR");
                if (string.IsNullOrEmpty(directory) || !Path.IsPathRooted(directory)) directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".claude");
                var auth = QuotaHttp.Local(Path.Combine(directory, ".credentials.json"))["claudeAiOauth"];
                var token = (string)auth?["accessToken"];
                if (string.IsNullOrEmpty(token) || (long?)auth["expiresAt"] <= DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()) throw new IOException();
                var request = new HttpRequestMessage(HttpMethod.Get, "https://api.anthropic.com/api/oauth/usage");
                request.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
                request.Headers.TryAddWithoutValidation("anthropic-beta", "oauth-2025-04-20");
                request.Headers.TryAddWithoutValidation("User-Agent", "claude-code/2.1.131");
                var data = await QuotaHttp.ReadAsync(request, cancellation).ConfigureAwait(false);
                result.Plan = (string)auth["subscriptionType"];
                var keys = new[] { "five_hour", "seven_day", "seven_day_sonnet", "seven_day_opus" };
                var titles = new[] { "Session", "Weekly", "Sonnet", "Opus" };
                for (var i = 0; i < keys.Length; i++)
                {
                    var value = data[keys[i]];
                    var percent = QuotaHttp.Percent(value?["utilization"]);
                    DateTimeOffset reset;
                    if (percent.HasValue) result.Quotas.Add(new Quota { Title = titles[i], PercentUsed = percent.Value, Reset = DateTimeOffset.TryParse((string)value["resets_at"], out reset) ? (DateTimeOffset?)reset : null });
                }
                if (result.Quotas.Count == 0) result.Error = "Claude did not report subscription quotas.";
            }
            catch (OperationCanceledException)
            {
                cancellation.ThrowIfCancellationRequested();
                result.Error = "Claude quota request timed out.";
            }
            catch { result.Error = "Claude quota unavailable. Sign in with Claude Code and check your connection."; }
            return result;
        }
    }
}
