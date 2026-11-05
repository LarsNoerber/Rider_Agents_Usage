using System;
using System.Collections.Generic;

namespace AgentsUsage.VisualStudio.Core
{
    internal sealed class ProviderUsage
    {
        public string Name, Plan, Error;
        public DateTimeOffset UpdatedAt = DateTimeOffset.Now;
        public List<Quota> Quotas = new List<Quota>();
    }

    internal sealed class Quota
    {
        public string Title;
        public int PercentUsed;
        public DateTimeOffset? Reset;
    }
}
