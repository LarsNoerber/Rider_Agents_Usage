using System;
using System.ComponentModel;
using Microsoft.VisualStudio.Shell;

namespace AgentsUsage.VisualStudio.Settings
{
    public sealed class UsageOptions : DialogPage
    {
        [Category("Agents"), DisplayName("Show OpenAI Codex"), Description("Display Codex quota and refresh it in the background.")]
        public bool CodexEnabled { get; set; } = true;

        [Category("Agents"), DisplayName("Show GitHub Copilot"), Description("Display reported quota from the signed-in Copilot service in Visual Studio.")]
        public bool CopilotEnabled { get; set; } = true;

        [Category("Agents"), DisplayName("Show Claude Code")]
        public bool ClaudeEnabled { get; set; } = true;
        [Category("Agents"), DisplayName("Show Cursor")]
        public bool CursorEnabled { get; set; } = true;

        [Category("Display"), DisplayName("Show status bar"), Description("Show the clickable AI icon and quota percentages. Refreshes continue while this is enabled.")]
        public bool ShowStatusBar { get; set; } = true;

        [Category("Refresh"), DisplayName("Codex CLI path"), Description("Leave empty to discover codex on PATH.")]
        public string CodexPath { get; set; } = "";

        [Category("Refresh"), DisplayName("Refresh interval (seconds)"), Description("Seconds between refreshes, from 10 to 3600.")]
        public int RefreshIntervalSeconds { get; set; } = 60;

        internal event EventHandler Changed;

        protected override void OnApply(PageApplyEventArgs e)
        {
            if (RefreshIntervalSeconds < 10 || RefreshIntervalSeconds > 3600)
            {
                System.Windows.Forms.MessageBox.Show("Choose a refresh interval between 10 and 3600 seconds.", "AgentMeter");
                e.ApplyBehavior = ApplyKind.Cancel;
                return;
            }
            base.OnApply(e);
            Changed?.Invoke(this, EventArgs.Empty);
        }
    }
}
