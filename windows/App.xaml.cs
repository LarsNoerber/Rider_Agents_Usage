using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.IO;
using System.Text.Json;
using AgentMeter.Windows.Providers.Gemini;

namespace AgentMeter.Windows;

public partial class App : System.Windows.Application
{
    public App()
    {
        // Keep custom window chrome and the transparent widget on one reliable rendering path.
        RenderOptions.ProcessRenderMode = RenderMode.SoftwareOnly;
    }

    protected override async void OnStartup(StartupEventArgs e)
    {
        if (!e.Args.Contains("--diagnose-gemini", StringComparer.Ordinal))
        {
            base.OnStartup(e);
            var mainWindow = new UI.MainWindow();
            MainWindow = mainWindow;
            mainWindow.Show();
            if (mainWindow.StartsDashboardHidden) mainWindow.Hide();
            return;
        }
        base.OnStartup(e);
        var report = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "AgentMeter", "gemini-diagnostic.json");
        try
        {
            var result = await Task.Run(async () =>
            {
                using var reader = new GeminiReader();
                var snapshot = await reader.ReadWorkspaceAsync("", CancellationToken.None);
                return new
                {
                    snapshot.SignedIn, snapshot.UpdatedAt, snapshot.Notice,
                    QuotaCount = snapshot.Quotas.Count,
                    Quotas = snapshot.Quotas.Select(q => new { q.Title, q.Percent }).ToArray()
                };
            });
            Directory.CreateDirectory(Path.GetDirectoryName(report)!);
            await File.WriteAllTextAsync(report, JsonSerializer.Serialize(result));
            Shutdown(result.QuotaCount > 0 ? 0 : 1);
        }
        catch (Exception error)
        {
            // Exception types only: never export exception text, bodies, headers or session material.
            Directory.CreateDirectory(Path.GetDirectoryName(report)!);
            await File.WriteAllTextAsync(report, JsonSerializer.Serialize(new
                { ErrorType = error.GetType().Name, InnerErrorType = error.InnerException?.GetType().Name }));
            Shutdown(1);
        }
    }
}
