using System.IO;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace AgentsUsage.VisualStudio.Core
{
    internal static class QuotaHttp
    {
        public static async Task<JObject> ReadAsync(HttpRequestMessage request, CancellationToken cancellation)
        {
            using (request)
            using (var client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false, UseCookies = false }) { Timeout = System.TimeSpan.FromSeconds(20), MaxResponseContentBufferSize = 2 * 1024 * 1024 })
            using (var response = await client.SendAsync(request, cancellation).ConfigureAwait(false))
            {
                response.EnsureSuccessStatusCode();
                return JObject.Parse(await response.Content.ReadAsStringAsync().ConfigureAwait(false));
            }
        }
        public static JObject Local(string path)
        {
            if (new FileInfo(path).Length > 1024 * 1024) throw new IOException();
            return JObject.Parse(File.ReadAllText(path));
        }
        public static int? Percent(JToken value)
        {
            double number;
            return double.TryParse((string)value, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out number) && !double.IsNaN(number) && !double.IsInfinity(number)
                ? (int?)System.Math.Round(System.Math.Max(0, System.Math.Min(100, number))) : null;
        }
    }
}
