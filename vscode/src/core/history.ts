/** Session-local numeric observations only: never stores provider responses or account data. */
export class UsageHistory {
    private readonly series = new Map<string, { time: number; used: number }[]>();

    record(key: string, used: number | undefined): void {
        if (used === undefined || !Number.isFinite(used)) return;
        const now = Date.now();
        const values = (this.series.get(key) ?? []).filter(value => now - value.time < 7 * 86400000);
        values.push({time: now, used: Math.max(0, Math.min(100, used))});
        this.series.set(key, values.slice(-10080));
    }

    summaries(): {
        key: string;
        observations: number;
        minimum: number;
        maximum: number;
        first: number;
        last: number
    }[] {
        return [...this.series].map(([key, values]) => ({
            key, observations: values.length,
            minimum: Math.min(...values.map(v => v.used)), maximum: Math.max(...values.map(v => v.used)),
            first: values[0].time, last: values[values.length - 1].time
        }));
    }
}
