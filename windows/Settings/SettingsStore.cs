using System.IO;
using System.Text.Json;
using AgentMeter.Windows.Core;

namespace AgentMeter.Windows.Settings;

// Settings contain user choices only; provider credentials belong to the official stores or the explicit key vault.
internal static class SettingsStore
{
    private static readonly string DirectoryPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "AgentMeter");
    private static readonly string FilePath = Path.Combine(DirectoryPath, "windows-settings.json");

    public static UsageSettings Load()
    {
        try
        {
            // Normalize persisted choices without enabling newly added providers on an existing installation.
            var settings = JsonSerializer.Deserialize<UsageSettings>(File.ReadAllText(FilePath)) ?? new();
            return settings with
            {
                RefreshSeconds = Math.Clamp(settings.RefreshSeconds, 30, 3600),
                WidgetOpacity = double.IsFinite(settings.WidgetOpacity) ? Math.Clamp(settings.WidgetOpacity, 0.2, 1) : 1,
                WidgetStyle = settings.WidgetStyle is "classic" or "compact" or "cards" or "circles" or "vertical" or "mini"
                    ? settings.WidgetStyle : "classic",
                WidgetColorStyle = settings.WidgetColorStyle is "midnight" or "ocean" or "forest" or "plum" or "graphite"
                    ? settings.WidgetColorStyle : "midnight",
                WidgetProviderOrder = (settings.WidgetProviderOrder ?? [])
                .Concat(ProviderIds.All)
                .Where(ProviderIds.IsKnown).Distinct().ToArray(),
                EnabledProviders = (settings.EnabledProviders ?? []).Where(ProviderIds.IsKnown).Distinct().ToArray(),
                ShowDesktopWidget = true,
                WidgetVisibleProviders = settings.WidgetVisibleProviders?.Where(ProviderIds.IsKnown).Distinct().ToArray(),
                CodexPath = settings.CodexPath ?? "",
                CopilotPath = settings.CopilotPath ?? "",
                ClaudePath = settings.ClaudePath ?? "",
                CursorPath = settings.CursorPath ?? "",
                KiloPath = settings.KiloPath ?? "",
                ClinePath = settings.ClinePath ?? "",
                OpenCodePath = settings.OpenCodePath ?? "",
                JuniePath = settings.JuniePath ?? "",
                JunieProjectPath = settings.JunieProjectPath ?? "",
                GeminiPath = settings.GeminiPath ?? "",
                GeminiProjectId = settings.GeminiProjectId ?? ""
            };
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or JsonException)
        {
            return new();
        }
    }

    public static bool Save(UsageSettings settings)
    {
        try
        {
            Directory.CreateDirectory(DirectoryPath);
            var temporary = FilePath + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try
            {
                File.WriteAllText(temporary, JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true }));
                File.Move(temporary, FilePath, true);
            }
            finally
            {
                if (File.Exists(temporary)) File.Delete(temporary);
            }
            return true;
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException) { return false; }
    }
}
