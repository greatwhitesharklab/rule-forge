import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
// V5.53:用 `new URL('./xxx', import.meta.url).pathname` 替换 `path` + `__dirname` 写法,
//   避开 console-ui 没装 @types/node 时的 TS 诊断(Cannot find name 'path' / '__dirname')。
//   `new URL(...)` 是 Web 标准 API,`import.meta.url` 由 Vite/esbuild 在 ESM 模式下注入 —
//   无 Node 类型依赖,纯 Web/ESM。
//   注意:URL.pathname 在 Windows 上是 `/C:/...`,Vite/Rollup 都吃这种 POSIX-ish 路径,无问题。
const r = (p: string) => new URL(p, import.meta.url).pathname;

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': r('./src'),
        },
    },
    // Multi-page app: each HTML template is an entry point
    build: {
        rollupOptions: {
            input: {
                frame: r('./html/frame.html'),
                login: r('./html/login.html'),
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
        // V5.53:本机 LAN 访问 — Vite dev server 绑 5173(host=0.0.0.0 已有),
        // proxy target 指向 console 8180(本会话 LAN 启动 port,跟 docker compose 8180 一致)。
        // 任何 LAN 客户端:http://<本机 IP>:5173 → Vite proxy → 8180 console REST。
        port: 5173,
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
