using AgentMeter.Windows.Core;

namespace AgentMeter.Windows.Settings;

internal sealed record UsageSettings
{
    public string[] EnabledProviders { get; init; } = [.. ProviderIds.All];
    public int RefreshSeconds { get; init; } = 300;
    public string CodexPath { get; init; } = "";
    public string CopilotPath { get; init; } = "";
    public string ClaudePath { get; init; } = "";
    public string CursorPath { get; init; } = "";
    public string GeminiPath { get; init; } = "";
    public string KiloPath { get; init; } = "";
    public string ClinePath { get; init; } = "";
    public string OpenCodePath { get; init; } = "";
    public string JuniePath { get; init; } = "";
    public string JunieProjectPath { get; init; } = "";
    public string GeminiProjectId { get; init; } = "";
    public bool ShowDesktopWidget { get; init; }
    public bool StartWithWindows { get; init; }
    public bool StartInWidgetOnlyMode { get; init; }
    public bool WidgetOnly { get; init; }
    public bool WidgetAlwaysOnTop { get; init; } = true;
    public double WidgetOpacity { get; init; } = 1;
    public string WidgetColorStyle { get; init; } = "midnight";
    public string WidgetStyle { get; init; } = "classic";
    public bool WidgetSnapToEdges { get; init; } = true;
    public bool WidgetPositionLocked { get; init; }
    public bool WidgetShowResets { get; init; } = true;
    public bool WidgetShowFreshness { get; init; } = true;
    public bool WidgetAutoCollapse { get; init; }
    public string[] WidgetProviderOrder { get; init; } = [.. ProviderIds.All];
    // Null inherits enabled providers for older settings; an empty list deliberately hides all bar segments.
    public string[]? WidgetVisibleProviders { get; init; }
    public double? WidgetLeft { get; init; }
    public double? WidgetTop { get; init; }

    // This is a persisted choice lookup only; resolving or launching an agent belongs to application services.
    public string GetExecutablePath(string provider) => provider switch
    {
        "codex" => CodexPath,
        "copilot" => CopilotPath,
        "claude" => ClaudePath,
        "cursor" => CursorPath,
        "gemini" => GeminiPath,
        "kilo" => KiloPath,
        "cline" => ClinePath,
        "opencode" => OpenCodePath,
        "junie" => JuniePath,
        _ => ""
    };
}
