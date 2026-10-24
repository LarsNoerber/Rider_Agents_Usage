using System.IO;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace AgentMeter.Windows.Core.History;

// Persist measured values only. Authentication, provider responses and account details never enter this file.
internal sealed class UsageSnapshotStore
{
    private sealed record Entry(DateTimeOffset At, UsageQuota[] Quotas);
    // Provider reads finish concurrently; serialize cache updates and atomic file replacement.
    private readonly object gate = new();
    private readonly string path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "AgentMeter", "saved-usage.json");
    private Dictionary<string, Entry> entries = new();

    public UsageSnapshotStore()
    {
        try
        {
            if (new FileInfo(path) is { Exists: true, Length: <= 1024 * 1024 })
            {
                var loaded = JsonSerializer.Deserialize<Dictionary<string, Entry>>(File.ReadAllText(path)) ?? new();
                entries = loaded.Where(pair => Regex.IsMatch(pair.Key, @"^[a-z]{1,24}$") && pair.Value != null)
                    .Take(32).ToDictionary(pair => pair.Key, pair => new Entry(pair.Value.At,
                        (pair.Value.Quotas ?? []).Select(Sanitize).OfType<UsageQuota>().Take(16).ToArray()));
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
    }

    public UsageSnapshot Apply(UsageSnapshot snapshot)
    {
        lock (gate)
        {
            var measured = snapshot.Quotas.Select(Sanitize).OfType<UsageQuota>().Take(16).ToArray();
            if (measured.Length > 0)
            {
                entries[snapshot.ProviderId] = new(snapshot.UpdatedAt ?? DateTimeOffset.Now, measured);
                Save();
                return snapshot;
            }
            if (!entries.TryGetValue(snapshot.ProviderId, out var saved) || saved.Quotas.Length == 0) return snapshot;
            // Retain the live read's sign-in result and the saved measurement's original time.
            return snapshot with
            {
                Quotas = saved.Quotas,
                UpdatedAt = saved.At,
                IsCached = true,
                Notice = "Last saved usage · " + saved.At.ToLocalTime().ToString("g") + ". " + (snapshot.Notice ?? "Current usage is unavailable.")
            };
        }
    }

    public UsageSnapshot? Get(string provider)
    {
        lock (gate)
        {
            return entries.TryGetValue(provider, out var saved) && saved.Quotas.Length > 0
                ? new(provider, "Saved usage", saved.Quotas,
                    "Last saved usage · " + saved.At.ToLocalTime().ToString("g") + ". Checking current connection and usage…",
                    UpdatedAt: saved.At)
                { IsCached = true } : null;
        }
    }

    // Free-form details, plan names and sign-in state are intentionally excluded from persistence.
    private static UsageQuota? Sanitize(UsageQuota quota)
    {
        if (quota == null || string.IsNullOrWhiteSpace(quota.Title) || quota.Title.Length > 160) return null;
        var percent = quota.Percent is double p && double.IsFinite(p) ? Math.Clamp(p, 0, 100) : (double?)null;
        var display = quota.DisplayValue is string value && value.Length <= 80
            && Regex.IsMatch(value, @"^(?:No limit|(?:\$\s*)?[0-9][0-9.,]*(?:\s*(?:[KMB]|credits?|USD|AI Credits|tokens?))?)$", RegexOptions.IgnoreCase)
            ? value : null;
        if (percent == null && display == null && !quota.Unlimited) return null;
        return new(quota.Title, percent, quota.Consumed, quota.ResetsAt, Unlimited: quota.Unlimited, DisplayValue: display);
    }

    private void Save()
    {
        var temporary = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.WriteAllText(temporary, JsonSerializer.Serialize(entries));
            // Readers must see either the previous complete JSON file or the next complete file.
            File.Move(temporary, path, true);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        finally
        {
            try { if (File.Exists(temporary)) File.Delete(temporary); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
    }
}
