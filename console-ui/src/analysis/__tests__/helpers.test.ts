import {getStartTime, getGranularity} from '../helpers.js';
import {describe, it, expect} from 'vitest';

describe('analysis helpers', () => {
    describe('getStartTime', () => {
        it('should return ~1h ago for 1h range', () => {
            const now = Date.now();
            const result = getStartTime('1h').getTime();
            expect(Math.abs(result - (now - 3600000))).toBeLessThan(50);
        });

        it('should return ~6h ago for 6h range', () => {
            const now = Date.now();
            const result = getStartTime('6h').getTime();
            expect(Math.abs(result - (now - 6 * 3600000))).toBeLessThan(50);
        });

        it('should return ~24h ago for 24h range', () => {
            const now = Date.now();
            const result = getStartTime('24h').getTime();
            expect(Math.abs(result - (now - 24 * 3600000))).toBeLessThan(50);
        });

        it('should return ~7d ago for 7d range', () => {
            const now = Date.now();
            const result = getStartTime('7d').getTime();
            expect(Math.abs(result - (now - 7 * 86400000))).toBeLessThan(50);
        });

        it('should return ~30d ago for 30d range', () => {
            const now = Date.now();
            const result = getStartTime('30d').getTime();
            expect(Math.abs(result - (now - 30 * 86400000))).toBeLessThan(50);
        });

        it('should default to 24h ago for unknown range', () => {
            const now = Date.now();
            const result = getStartTime('unknown').getTime();
            expect(Math.abs(result - (now - 24 * 3600000))).toBeLessThan(50);
        });
    });

    describe('getGranularity', () => {
        it('should return daily for 30d', () => {
            expect(getGranularity('30d')).toBe('daily');
        });

        it('should return daily for 7d', () => {
            expect(getGranularity('7d')).toBe('daily');
        });

        it('should return hourly for 24h', () => {
            expect(getGranularity('24h')).toBe('hourly');
        });

        it('should return hourly for 1h', () => {
            expect(getGranularity('1h')).toBe('hourly');
        });

        it('should return hourly for 6h', () => {
            expect(getGranularity('6h')).toBe('hourly');
        });
    });
});
