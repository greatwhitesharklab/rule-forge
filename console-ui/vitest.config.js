import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
    resolve: {
        alias: {
            '@': resolve(__dirname, 'src'),
        },
    },
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./vitest.setup.js'],
        include: ['src/**/*.{test,spec}.{js,jsx,ts,tsx}'],
    },
});
