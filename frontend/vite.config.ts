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
                variableEditor: resolve(__dirname, 'html/variable-editor.html'),
                constantEditor: resolve(__dirname, 'html/constant-editor.html'),
                parameterEditor: resolve(__dirname, 'html/parameter-editor.html'),
                actionEditor: resolve(__dirname, 'html/action-editor.html'),
                packageEditor: resolve(__dirname, 'html/package-editor.html'),
                flowBpmnEditor: resolve(__dirname, 'html/flow-bpmn-editor.html'),
                ruleSetEditor: resolve(__dirname, 'html/ruleset-editor.html'),
                decisionTableEditor: resolve(__dirname, 'html/decision-table-editor.html'),
                scriptDecisionTableEditor: resolve(__dirname, 'html/script-decision-table-editor.html'),
                decisionTreeEditor: resolve(__dirname, 'html/decision-tree-editor.html'),
                clientConfigEditor: resolve(__dirname, 'html/client-config-editor.html'),
                ulEditor: resolve(__dirname, 'html/ul-editor.html'),
                scoreCardTable: resolve(__dirname, 'html/score-card-editor.html'),
                permissionConfigEditor: resolve(__dirname, 'html/permission-config-editor.html'),
                resourceEditor: resolve(__dirname, 'html/resource-editor.html'),
                crosstabEditor: resolve(__dirname, 'html/crosstab-editor.html'),
                complexScoreCardEditor: resolve(__dirname, 'html/complexscorecard-editor.html'),
                login: resolve(__dirname, 'html/login.html'),
                monitoringDashboard: resolve(__dirname, 'html/monitoring-dashboard.html'),
                analysisDashboard: resolve(__dirname, 'html/analysis-dashboard.html'),
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
                rewrite: (path) => path.replace(/^\/api/, '/ruleforgeV2'),
            },
        },
    },
    css: {
        postcss: './postcss.config.js',
    },
});
