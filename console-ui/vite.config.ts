import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': resolve(__dirname, 'src'),
        },
    },
    // Multi-page app: each HTML template is an entry point
    build: {
        rollupOptions: {
            input: {
                frame: resolve(__dirname, 'html/frame.html'),
                login: resolve(__dirname, 'html/login.html'),
                editor: resolve(__dirname, 'html/editor.html'),
            },
            output: {
                entryFileNames: 'bundle/[name].bundle.js',
                chunkFileNames: 'bundle/[name]-[hash].js',
                assetFileNames: 'bundle/assets/[name]-[hash][extname]',
            },
        },
        copyPublicDir: true,
    },
    publicDir: 'public',
    server: {
        port: 3000,
        host: '0.0.0.0',
        open: false,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8180',
                changeOrigin: true,
                // /api/* → /ruleforge/* (commit 06c59925 重命名,原 /ruleforgeV2 删了)
                rewrite: (path) => path.replace(/^\/api/, '/ruleforge'),
            },
        },
    },
    css: {
        postcss: './postcss.config.js',
    },
});
