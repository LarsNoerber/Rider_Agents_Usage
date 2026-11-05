using System;
using System.Collections.Generic;
using System.Linq;

namespace AgentsUsage.VisualStudio.Core
{
    // Session-local numeric observations. Called only on the coordinator's UI publication path.
    internal sealed class UsageHistory
    {
        private readonly Dictionary<string, List<Tuple<DateTimeOffset, int>>> series = new Dictionary<string, List<Tuple<DateTimeOffset, int>>>();
        public void Record(string key, int? used)
        {
            if (!used.HasValue) return;
            if (!series.TryGetValue(key, out var values)) series[key] = values = new List<Tuple<DateTimeOffset, int>>();
            values.RemoveAll(value => value.Item1 < DateTimeOffset.Now.AddDays(-7));
            values.Add(Tuple.Create(DateTimeOffset.Now, Math.Max(0, Math.Min(100, used.Value))));
            if (values.Count > 10080) values.RemoveAt(0);
        }
        public IEnumerable<string> Summaries() => series.Select(pair => pair.Key + ": " + pair.Value.Min(value => value.Item2) + "–" + pair.Value.Max(value => value.Item2) + "% used · " + pair.Value.Count + " observations");
    }
}
