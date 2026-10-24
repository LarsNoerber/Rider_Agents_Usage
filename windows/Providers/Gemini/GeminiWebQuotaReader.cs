using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using AgentMeter.Windows.Core;

namespace AgentMeter.Windows.Providers.Gemini;

/// <summary>Reads Gemini Apps web limits through its private, undocumented usage RPC.</summary>
internal sealed class GeminiWebQuotaReader : IDisposable
{
    private const string Host = "gemini.google.com";
    private const string RpcId = "jSf9Qc";
    private readonly HttpClient client = new(new HttpClientHandler { AllowAutoRedirect = false, UseCookies = false })
    { Timeout = TimeSpan.FromSeconds(20) };
    private UsageSnapshot? lastReported;
    private bool disposed;

    public async Task<UsageSnapshot> ReadAsync(CancellationToken token)
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        UsageSnapshot? failure = null;
        foreach (var cookie in GeminiAppCookieReader.AvailableSessions())
        {
            token.ThrowIfCancellationRequested();
            var snapshot = await ReadSessionAsync(cookie, token);
            token.ThrowIfCancellationRequested();
            ObjectDisposedException.ThrowIf(disposed, this);
            if (snapshot.Quotas.Count > 0)
            {
                lastReported = snapshot;
                return snapshot;
            }
            if (failure == null || failure.SignedIn == false) failure = snapshot;
        }
        if (failure != null) return RetainReportedQuota(failure);
        GeminiAppCookieReader.TryRead(out _, out var reason);
        // An inaccessible browser store cannot establish whether the app is signed in.
        return RetainReportedQuota(Unavailable(reason));
    }

    private UsageSnapshot RetainReportedQuota(UsageSnapshot failure)
    {
        // Keep numeric observations only; never retain session material between refreshes.
        if (lastReported?.UpdatedAt is not { } at || DateTimeOffset.Now - at > TimeSpan.FromHours(24)) return failure;
        return lastReported with
        {
            Notice = $"Showing last reported quota from {at.LocalDateTime:g}. Automatic retry pending. {failure.Notice}"
        };
    }

    private async Task<UsageSnapshot> ReadSessionAsync(string cookie, CancellationToken token)
    {
        var stage = "Gemini usage page";
        try
        {
            var page = await SendAsync(new HttpRequestMessage(HttpMethod.Get, $"https://{Host}/usage"), cookie, token);
            stage = "Gemini usage page bootstrap";
            var csrf = ReadBootstrapValue(page, "SNlM0e");
            var build = ReadBootstrapValue(page, "cfb2h");
            var session = ReadBootstrapValue(page, "FdrFJe");
            var query = new List<string>
            {
                "rpcids=" + Uri.EscapeDataString(RpcId), "source-path=%2Fusage",
                "hl=en", "_reqid=" + Random.Shared.Next(100_000, 999_999).ToString(CultureInfo.InvariantCulture), "rt=c"
            };
            if (build != null) query.Add("bl=" + Uri.EscapeDataString(build));
            if (session != null) query.Add("f.sid=" + Uri.EscapeDataString(session));
            var request = new HttpRequestMessage(HttpMethod.Post, $"https://{Host}/_/BardChatUi/data/batchexecute?{string.Join('&', query)}");
            var form = new Dictionary<string, string>
            {
                ["f.req"] = "[[[\"jSf9Qc\",\"[]\",null,\"generic\"]]]"
            };
            // Some frontend responses omit SNlM0e. Let the RPC establish authentication;
            // readable local cookies alone do not prove the Google session is still accepted.
            if (!string.IsNullOrWhiteSpace(csrf)) form["at"] = csrf;
            request.Content = new FormUrlEncodedContent(form);
            request.Headers.Referrer = new Uri($"https://{Host}/usage");
            stage = "Gemini quota request";
            var response = await SendAsync(request, cookie, token);
            stage = "Gemini quota response";
            return ParseResponse(response);
        }
        catch (WebException)
        { return Unavailable("Gemini Apps rejected AgentMeter's local cookie request. The Gemini app may still be signed in. Open Gemini Apps to check the account, then refresh."); }
        catch (HttpRequestException error) when (error.StatusCode.HasValue)
        { return Unavailable($"Gemini Apps request failed (HTTP {(int)error.StatusCode.Value}). The private usage interface may have changed."); }
        catch (HttpRequestException error)
        { return Unavailable($"{stage} failed. Check the sign-in and connection ({error.InnerException?.GetType().Name ?? "network error"})."); }
        catch (OperationCanceledException) when (!token.IsCancellationRequested)
        { return Unavailable("Gemini Apps usage request timed out. Try refreshing again."); }
        catch (Exception error) when (error is JsonException or InvalidDataException or ArgumentException or FormatException or OverflowException)
        { return Unavailable("Gemini Apps returned an unsupported usage response. Its private interface may have changed."); }
        finally
        {
            // Cookie material is used only for these requests and never retained on the reader.
            cookie = "";
        }
    }

    private async Task<string> SendAsync(HttpRequestMessage request, string cookie, CancellationToken token)
    {
        using (request)
        {
            request.Headers.TryAddWithoutValidation("Cookie", cookie);
            request.Headers.TryAddWithoutValidation("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36");
            request.Headers.Accept.ParseAdd("text/html,application/json,*/*");
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(token);
            deadline.CancelAfter(TimeSpan.FromSeconds(20));
            using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, deadline.Token);
            if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
                throw new WebException("Authentication rejected.");
            if (!response.IsSuccessStatusCode) throw new HttpRequestException("Gemini request failed.", null, response.StatusCode);
            await using var stream = await response.Content.ReadAsStreamAsync(deadline.Token);
            using var buffer = new MemoryStream();
            var chunk = new byte[8192];
            int count;
            while ((count = await stream.ReadAsync(chunk, deadline.Token)) > 0)
            {
                if (buffer.Length + count > 2_097_152) throw new InvalidDataException();
                buffer.Write(chunk, 0, count);
            }
            return Encoding.UTF8.GetString(buffer.ToArray());
        }
    }

    private static string? ReadBootstrapValue(string html, string name)
    {
        var match = Regex.Match(html, "\\\"" + Regex.Escape(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"", RegexOptions.CultureInvariant);
        if (!match.Success) return null;
        try { return JsonSerializer.Deserialize<string>("\"" + match.Groups[1].Value + "\""); }
        catch (JsonException) { return null; }
    }

    private static UsageSnapshot ParseResponse(string body)
    {
        JsonDocument document;
        try { document = ParseFirstJsonValue(body); }
        catch (JsonException) { return Unavailable("Gemini Apps returned no usage data. Its private interface may have changed."); }
        using (document)
        {
            // Decode the RPC envelope before interpreting the nested, string-encoded quota payload.
            var rpc = FindRpc(document.RootElement);
            if (rpc.ValueKind == JsonValueKind.Undefined)
                return Unavailable("Gemini Apps returned no usage data. Its private interface may have changed.");
            if (HasAuthRejection(rpc))
                return Unavailable("Gemini Apps rejected AgentMeter's local cookie request. The Gemini app may still be signed in. Open Gemini Apps to check the account, then refresh.");
            if (rpc.ValueKind != JsonValueKind.Array || rpc.GetArrayLength() < 3 || rpc[2].ValueKind != JsonValueKind.String)
                return Unavailable("Gemini Apps usage response is unsupported. The private interface may have changed.");

            using var payload = ParseFirstJsonValue(rpc[2].GetString()!);
            var metrics = new Dictionary<int, (double Used, DateTimeOffset? Reset)>();
            FindMetrics(payload.RootElement, metrics);
            var quotas = new List<UsageQuota>();
            if (metrics.TryGetValue(1, out var fiveHour))
                quotas.Add(new("Gemini Apps 5-hour used", fiveHour.Used * 100, true, fiveHour.Reset));
            if (metrics.TryGetValue(2, out var weekly))
                quotas.Add(new("Gemini Apps weekly used", weekly.Used * 100, true, weekly.Reset));
            if (quotas.Count == 0)
                return Unavailable("Gemini Apps returned no recognized usage limits. Its private interface may have changed.");
            return new("gemini", "Gemini Apps", quotas, null,
                ["Source: Gemini Apps web account, read from a local Gemini app or Edge session.",
                 "These are consumer Gemini Apps limits, separate from Gemini CLI / Workspace and AI Studio API quotas.",
                 "The usage request uses Google's private, undocumented web interface and may stop working if Google changes it.",
                 "AgentMeter reads the session in memory only and sends it only to gemini.google.com."], DateTimeOffset.Now)
            { SignedIn = true };
        }
    }

    private static JsonDocument ParseFirstJsonValue(string text)
    {
        var start = text.IndexOfAny(['[', '{']);
        if (start < 0) throw new JsonException();
        var bytes = Encoding.UTF8.GetBytes(text[start..]);
        var reader = new Utf8JsonReader(bytes);
        return JsonDocument.ParseValue(ref reader);
    }

    private static JsonElement FindRpc(JsonElement value)
    {
        if (value.ValueKind == JsonValueKind.Array && value.GetArrayLength() >= 3 &&
            value[0].ValueKind == JsonValueKind.String && value[0].GetString() == "wrb.fr" &&
            value[1].ValueKind == JsonValueKind.String && value[1].GetString() == RpcId) return value;
        if (value.ValueKind is JsonValueKind.Array or JsonValueKind.Object)
            foreach (var child in value.EnumerateArrayOrObject())
            {
                var match = FindRpc(child);
                if (match.ValueKind != JsonValueKind.Undefined) return match;
            }
        return default;
    }

    private static void FindMetrics(JsonElement value, Dictionary<int, (double Used, DateTimeOffset? Reset)> output)
    {
        if (value.ValueKind == JsonValueKind.Array)
        {
            if (value.GetArrayLength() >= 4 && TryNumber(value[1], out var used) &&
                TryNumber(value[2], out var periodNumber) && periodNumber is 1 or 2 && used is >= 0 and <= 1)
            {
                var period = (int)periodNumber;
                output[period] = (used, FindReset(value[3]));
            }
            foreach (var child in value.EnumerateArray()) FindMetrics(child, output);
        }
        else if (value.ValueKind == JsonValueKind.Object)
            foreach (var property in value.EnumerateObject()) FindMetrics(property.Value, output);
    }

    private static DateTimeOffset? FindReset(JsonElement value)
    {
        if (value.ValueKind == JsonValueKind.Array)
        {
            if (value.GetArrayLength() >= 2 && TryNumber(value[0], out var seconds) && seconds is >= 1_000_000_000 and <= 9_999_999_999)
            {
                try { return DateTimeOffset.FromUnixTimeSeconds((long)seconds); }
                catch (ArgumentOutOfRangeException) { return null; }
            }
            foreach (var child in value.EnumerateArray())
                if (FindReset(child) is { } reset) return reset;
        }
        return null;
    }

    private static bool HasAuthRejection(JsonElement rpc) => rpc.ValueKind == JsonValueKind.Array && rpc.GetArrayLength() > 5 &&
        ContainsErrorCode(rpc[5]);

    private static bool ContainsErrorCode(JsonElement value)
    {
        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out var number)) return number == 7;
        if (value.ValueKind == JsonValueKind.String) return value.GetString() == "7";
        if (value.ValueKind == JsonValueKind.Array)
            foreach (var child in value.EnumerateArray()) if (ContainsErrorCode(child)) return true;
        return false;
    }

    private static bool TryNumber(JsonElement element, out double value)
    {
        if (element.ValueKind == JsonValueKind.Number) return element.TryGetDouble(out value);
        if (element.ValueKind == JsonValueKind.String && double.TryParse(element.GetString(), NumberStyles.Float,
                CultureInfo.InvariantCulture, out value)) return true;
        value = 0;
        return false;
    }

    private static UsageSnapshot Unavailable(string message, bool? signedIn = null) =>
        UsageSnapshot.Unavailable("gemini", message) with { SignedIn = signedIn };

    public void Dispose()
    {
        disposed = true;
        lastReported = null;
        client.Dispose();
    }
}

internal static class JsonElementExtensions
{
    public static IEnumerable<JsonElement> EnumerateArrayOrObject(this JsonElement element) =>
        element.ValueKind == JsonValueKind.Array ? element.EnumerateArray() : element.EnumerateObject().Select(property => property.Value);
}
