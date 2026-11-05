using System;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using AgentsUsage.VisualStudio.Core;
namespace AgentsUsage.VisualStudio.Providers.Cursor
{
    internal sealed class CursorUsageReader
    {
        public async Task<ProviderUsage> ReadAsync(CancellationToken cancellation)
        {
            var result = new ProviderUsage { Name = "Cursor" };
            try
            {
                var token = (string)QuotaHttp.Local(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Cursor", "auth.json"))["accessToken"];
                var encoded = token.Split('.')[1].Replace('-', '+').Replace('_', '/');
                var payload = JObject.Parse(Encoding.UTF8.GetString(Convert.FromBase64String(encoded.PadRight((encoded.Length + 3) / 4 * 4, '='))));
                var user = ((string)payload["sub"]).Split('|');
                var id = user[user.Length - 1];
                if (!System.Text.RegularExpressions.Regex.IsMatch(id, "^[A-Za-z0-9._-]+$") || ((long?)payload["exp"] ?? 0) <= DateTimeOffset.UtcNow.ToUnixTimeSeconds() + 60) throw new IOException();
                var request = new HttpRequestMessage(HttpMethod.Get, "https://cursor.com/api/usage-summary");
                request.Headers.TryAddWithoutValidation("Cookie", "WorkosCursorSessionToken=" + id + "%3A%3A" + token);
                request.Headers.TryAddWithoutValidation("Origin", "https://cursor.com");
                request.Headers.TryAddWithoutValidation("Referer", "https://cursor.com/dashboard");
                JObject data;
                try { data = await QuotaHttp.ReadAsync(request, cancellation).ConfigureAwait(false); }
                catch (HttpRequestException failure) when (failure.Message.Contains("403"))
                {
                    var fallback = new HttpRequestMessage(HttpMethod.Post, "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage");
                    fallback.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
                    fallback.Headers.TryAddWithoutValidation("Connect-Protocol-Version", "1");
                    fallback.Content = new StringContent("{}", Encoding.UTF8, "application/json");
                    data = await QuotaHttp.ReadAsync(fallback, cancellation).ConfigureAwait(false);
                }
                result.Plan = (string)data["membershipType"];
                foreach (var quota in new[] { data["individualUsage"]?["plan"] ?? data["planUsage"], data["individualUsage"]?["overall"], data["teamUsage"]?["pooled"] })
                {
                    if (quota == null) continue;
                    var percent = QuotaHttp.Percent(quota["totalPercentUsed"]);
                    var limit = (double?)quota["limit"];
                    var used = (double?)quota["used"] ?? limit - (double?)quota["remaining"];
                    if (!percent.HasValue && limit > 0 && used.HasValue) percent = QuotaHttp.Percent(new JValue(100 * used.Value / limit.Value));
                    if (!percent.HasValue) continue;
                    result.Quotas.Add(new Quota { Title = "Included plan", PercentUsed = percent.Value });
                    break;
                }
                if (result.Quotas.Count == 0) result.Error = "Cursor did not report a finite quota for this plan.";
            }
            catch (OperationCanceledException) { cancellation.ThrowIfCancellationRequested(); result.Error = "Cursor quota request timed out."; }
            catch { result.Error = "Cursor quota unavailable. Sign in to the Cursor agent and check your connection."; }
            return result;
        }
    }
}
