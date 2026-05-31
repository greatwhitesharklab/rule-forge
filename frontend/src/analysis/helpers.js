/**
 * 时间范围辅助函数
 */
export function getStartTime(range) {
    const now = new Date();
    switch (range) {
        case '1h': return new Date(now.getTime() - 3600000);
        case '6h': return new Date(now.getTime() - 6 * 3600000);
        case '24h': return new Date(now.getTime() - 24 * 3600000);
        case '7d': return new Date(now.getTime() - 7 * 86400000);
        case '30d': return new Date(now.getTime() - 30 * 86400000);
        default: return new Date(now.getTime() - 24 * 3600000);
    }
}

export function getGranularity(range) {
    if (range === '30d' || range === '7d') return 'daily';
    return 'hourly';
}
