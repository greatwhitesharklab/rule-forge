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
    // V5.74.6:SPA 模式 — 单一入口 index.html(走 /src/main.tsx → BrowserRouter),
    // 已删 frame.html/login.html MPA 入口(rollupOptions.input 只需根 index.html)。
    build: {
        rollupOptions: {
            input: {
                index: r('./index.html'),
            },
            output: {
                entryFileNames: 'bundle/[name].bundle.js',
                chunkFileNames: 'bundle/[name]-[hash].js',
                assetFileNames: 'bundle/assets/[name]-[hash][extname]',
                // V7.24:重型依赖拆成独立 vendor chunk — 路由级 lazy chunk 不再重复打包
                // antd/ag-grid/xyflow/monaco/echarts,且 vendor 内容稳定、可长期缓存。
                manualChunks(id: string) {
                    if (!id.includes('node_modules')) {
                        return undefined;
                    }
                    if (id.includes('monaco-editor')) return 'vendor-monaco';
                    if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts';
                    if (id.includes('ag-grid')) return 'vendor-ag-grid';
                    if (id.includes('@xyflow') || id.includes('dagre')) return 'vendor-flow';
                    if (id.includes('antd') || id.includes('@ant-design') || id.includes('rc-')) return 'vendor-antd';
                    if (id.includes('react') || id.includes('redux')) return 'vendor-react';
                    return undefined;
                },
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
