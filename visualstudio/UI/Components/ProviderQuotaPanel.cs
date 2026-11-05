using System.Windows.Controls;
using AgentsUsage.VisualStudio.Core;
namespace AgentsUsage.VisualStudio.UI.Components
{
    internal static class ProviderQuotaPanel
    {
        public static Border Create(string name, ProviderUsage usage, bool remaining)
        {
            var panel = new StackPanel();
            panel.Children.Add(QuotaElements.Label(name, 15, true));
            panel.Children.Add(QuotaElements.Label("Plan: " + (usage?.Plan ?? "Unknown")));
            if (usage == null) panel.Children.Add(QuotaElements.Label("Waiting for quota..."));
            else
            {
                foreach (var quota in usage.Quotas) QuotaElements.AddQuota(panel, quota.Title, remaining ? 100 - quota.PercentUsed : quota.PercentUsed, quota.Reset, remaining);
                if (usage.Error != null) panel.Children.Add(QuotaElements.Label(usage.Error));
                panel.Children.Add(QuotaElements.Label("Updated: " + usage.UpdatedAt.ToLocalTime().ToString("g"), 11));
            }
            return QuotaElements.Card(panel);
        }
    }
}
