using System.Diagnostics;
using System.IO;
using AgentsUsage.VisualStudio.Core.Processes;

namespace AgentMeter.Windows.Core.Processes;

internal static class ReadOnlyAgentCommand
{
    internal sealed record Result(int ExitCode, string Output, string Error);

    internal static async Task<Result> RunAsync(string executable, string arguments, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(25));
        using var job = new ProcessJob();
        using var process = new Process { StartInfo = AgentExecutables.StartInfo(executable, arguments) };
        process.Start();
        using var registration = timeout.Token.Register(() =>
        {
            try { if (!process.HasExited) process.Kill(entireProcessTree: true); }
            catch (Exception) { }
        });
        try
        {
            job.Add(process);
            process.StandardInput.Close();
            // Drain both pipes concurrently so a full stderr pipe cannot block process exit.
            var output = ReadBoundedAsync(process.StandardOutput, timeout.Token);
            var error = ReadBoundedAsync(process.StandardError, timeout.Token);
            await process.WaitForExitAsync(timeout.Token);
            return new(process.ExitCode, await output, await error);
        }
        finally
        {
            try { if (!process.HasExited) process.Kill(entireProcessTree: true); }
            catch (Exception) { }
        }
    }

    private static async Task<string> ReadBoundedAsync(StreamReader reader, CancellationToken token)
    {
        // Bound retained output as it arrives, including commands without a known response length.
        var result = new System.Text.StringBuilder();
        var buffer = new char[4096];
        int count;
        while ((count = await reader.ReadAsync(buffer.AsMemory(), token)) > 0)
        {
            if (result.Length + count > 1024 * 1024) throw new IOException("Agent response exceeds the supported size.");
            result.Append(buffer, 0, count);
        }
        return result.ToString();
    }
}
