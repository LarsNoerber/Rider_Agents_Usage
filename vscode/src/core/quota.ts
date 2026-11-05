export interface Quota {
    title: string;
    percentUsed: number;
    reset?: number
}

export interface ProviderUsage {
    name: string;
    plan?: string;
    quotas: Quota[];
    updatedAt: number;
    error?: string
}
