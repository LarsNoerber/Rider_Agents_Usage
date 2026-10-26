using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;
using AgentMeter.Windows.Core.Processes;
using AgentsUsage.VisualStudio.Core.Processes;

namespace AgentMeter.Windows.Providers.Junie;

// Keep one ACP session for repeated built-in statistics calls. Never send an ordinary model prompt.
internal sealed class JunieConnection : IDisposable
{
    internal sealed record Report(bool? Connected, string Text, string? Notice = null);
    private Process? process;
    private ProcessJob? job;
    private string? executable, directory, session;
    private bool statisticsAvailable;
    private int requestId;
    private readonly CancellationTokenSource lifetime = new();

    public async Task<Report> ReadAsync(string path, string project, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token, lifetime.Token);
        timeout.CancelAfter(TimeSpan.FromSeconds(30));
        var ct = timeout.Token;
        try
        {
            if (process == null || process.HasExited || executable != path || directory != project)
            {
                Stop();
                executable = path;
                directory = project;
                job = new();
                process = new() { StartInfo = AgentExecutables.StartInfo(path, "--acp=true --skip-update-check") };
                process.StartInfo.WorkingDirectory = project;
                process.Start();
                job.Add(process);
                _ = DrainErrorsAsync(process.StandardError);
                var init = await RequestAsync("initialize", new { protocolVersion = 1,
                    clientCapabilities = new { }, clientInfo = new { name = "AgentMeter", version = "1.2.2" } }, null, ct);
                if (init.TryGetProperty("error", out _)) throw new IOException("ACP initialization failed.");
                var created = await RequestAsync("session/new", new { cwd = project, mcpServers = Array.Empty<object>() }, null, ct);
                if (created.TryGetProperty("error", out var error))
                {
                    var message = error.TryGetProperty("message", out var m) ? m.GetString() ?? "" : "";
                    var authRequired = message.Contains("Authentication is required", StringComparison.OrdinalIgnoreCase);
                    Stop();
                    return new(authRequired ? false : null, "", authRequired
                        ? "Sign in through the official Junie CLI, then refresh. Rider sign-in alone may not provide a CLI session."
                        : "Junie could not open a statistics session. Check its connection and project directory.");
                }
                session = created.GetProperty("result").GetProperty("sessionId").GetString();
            }
            // Advertised built-in command and description are required before submitting anything.
            while (!statisticsAvailable) Handle(await ReceiveAsync(ct), null);
            var text = new StringBuilder();
            var response = await RequestAsync("session/prompt", new { sessionId = session,
                prompt = new[] { new { type = "text", text = "/stats" } } }, text, ct);
            return response.TryGetProperty("error", out _) ? new(true, "", "Junie statistics are temporarily unavailable.")
                : new(true, text.ToString());
        }
        catch (OperationCanceledException) when (!token.IsCancellationRequested && !lifetime.IsCancellationRequested)
        {
            var connected = session != null ? true : (bool?)null;
            Stop();
            return new(connected, "", "Junie did not return built-in statistics in time. AgentMeter retries automatically.");
        }
        catch
        {
            Stop();
            throw;
        }
    }

    private async Task<JsonElement> RequestAsync(string method, object parameters, StringBuilder? text, CancellationToken ct)
    {
        var id = ++requestId;
        await SendAsync(new { jsonrpc = "2.0", id, method, @params = parameters }, ct);
        while (true)
        {
            var value = await ReceiveAsync(ct);
            // Command advertisements and streamed statistics may arrive before the matching reply.
            Handle(value, text);
            if (!value.TryGetProperty("method", out _) && value.TryGetProperty("id", out var responseId)
                && responseId.ValueKind == JsonValueKind.Number && responseId.GetInt32() == id) return value;
            if (value.TryGetProperty("method", out _) && value.TryGetProperty("id", out var clientId))
                // Never grant file/terminal/permission operations during quota polling.
                await SendAsync(new { jsonrpc = "2.0", id = clientId,
                    error = new { code = -32601, message = "Unsupported client operation" } }, ct);
        }
    }

    private void Handle(JsonElement value, StringBuilder? text)
    {
        if (!value.TryGetProperty("params", out var p) || !p.TryGetProperty("update", out var update)
            || !update.TryGetProperty("sessionUpdate", out var type)) return;
        if (type.GetString() == "available_commands_update" && update.TryGetProperty("availableCommands", out var commands))
        {
            statisticsAvailable = commands.EnumerateArray().Any(command =>
                command.TryGetProperty("name", out var name) && name.GetString() == "stats"
                && command.TryGetProperty("description", out var d)
                && (d.GetString() ?? "").Contains("Junie usage statistics", StringComparison.OrdinalIgnoreCase));
        }
        if (text != null && type.GetString() == "agent_message_chunk"
            && update.TryGetProperty("content", out var content) && content.TryGetProperty("text", out var chunk))
        {
            if (text.Length + (chunk.GetString()?.Length ?? 0) > 1024 * 1024) throw new IOException("Statistics too large.");
            text.Append(chunk.GetString());
        }
    }

    private async Task<JsonElement> ReceiveAsync(CancellationToken ct)
    {
        var line = await process!.StandardOutput.ReadLineAsync(ct);
        if (line == null || line.Length > 1024 * 1024) throw new IOException("Junie ACP response unavailable.");
        using var doc = JsonDocument.Parse(line);
        return doc.RootElement.Clone();
    }

    private Task SendAsync(object value, CancellationToken ct) => process!.StandardInput.WriteLineAsync(
        JsonSerializer.Serialize(value).AsMemory(), ct);

    private static async Task DrainErrorsAsync(StreamReader reader)
    {
        try
        {
            // Drain without retaining stderr, which may contain provider/session details.
            var buffer = new char[4096];
            while (await reader.ReadAsync(buffer) > 0) { }
        }
        catch (Exception e) when (e is IOException or ObjectDisposedException) { }
    }

    private void Stop()
    {
        try { if (process is { HasExited: false }) process.Kill(entireProcessTree: true); }
        catch (Exception) { }
        job?.Dispose();
        process?.Dispose();
        job = null;
        process = null;
        session = null;
        statisticsAvailable = false;
    }

    internal void Pause() => Stop();

    public void Dispose()
    {
        lifetime.Cancel();
        Stop();
        lifetime.Dispose();
    }
}
