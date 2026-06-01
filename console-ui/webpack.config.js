const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = {
    mode: 'development',
    resolve: {
        extensions: ['*', '.js', '.jsx', '.json'],
        alias: {
            '@': path.resolve(__dirname, 'src')
        }
    },
    entry: {
        frame: './src/frame/index.jsx',
        variableEditor: './src/variable/index.jsx',
        constantEditor: './src/constant/index.jsx',
        parameterEditor: './src/parameter/index.jsx',
        actionEditor: './src/action/index.jsx',
        packageEditor: './src/package/index.jsx',
        flowBpmnEditor: './src/flow-bpmn/index.jsx',
        ruleSetEditor: './src/editor/ruleforge/index.jsx',
        decisionTableEditor: './src/editor/decisiontable/index.jsx',
        scriptDecisionTableEditor: './src/editor/scriptdecisiontable/index.jsx',
        decisionTreeEditor: './src/editor/decisiontree/index.jsx',
        clientConfigEditor: './src/client/index.jsx',
        ulEditor: './src/editor/ul/index.jsx',
        scoreCardTable: './src/scorecard/index.jsx',
        permissionConfigEditor: './src/permission/index.jsx',
        resourceEditor: './src/resource/index.jsx',
        crosstabEditor: './src/editor/crosstab/index.jsx',
        complexScoreCardEditor: './src/editor/complexscorecard/index.jsx',
        login: './src/login/index.jsx',
        monitoringDashboard: './src/monitoring/index.jsx',
        analysisDashboard: './src/analysis/index.jsx'
    },
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'bundle/[name].bundle.js',
        clean: true
    },
    plugins: [
        new CopyWebpackPlugin({
            patterns: [
                { from: path.resolve(__dirname, 'lib'), to: 'lib' },
                { from: path.resolve(__dirname, 'fonts'), to: 'fonts' },
            ]
        }),
        new HtmlWebpackPlugin({ filename: 'index.html', template: 'html/frame.html', chunks: ["frame"] }),
        new HtmlWebpackPlugin({ filename: 'html/variable-editor.html', template: 'html/variable-editor.html', chunks: ["variableEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/ruleset-editor.html', template: 'html/ruleset-editor.html', chunks: ["ruleSetEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/decision-table-editor.html', template: 'html/decision-table-editor.html', chunks: ["decisionTableEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/decision-tree-editor.html', template: 'html/decision-tree-editor.html', chunks: ["decisionTreeEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/score-card-editor.html', template: 'html/score-card-editor.html', chunks: ["scoreCardTable"] }),
        new HtmlWebpackPlugin({ filename: 'html/flow-bpmn-editor.html', template: 'html/flow-bpmn-editor.html', chunks: ["flowBpmnEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/package-editor.html', template: 'html/package-editor.html', chunks: ["packageEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/constant-editor.html', template: 'html/constant-editor.html', chunks: ["constantEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/action-editor.html', template: 'html/action-editor.html', chunks: ["actionEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/parameter-editor.html', template: 'html/parameter-editor.html', chunks: ["parameterEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/script-decision-table-editor.html', template: 'html/script-decision-table-editor.html', chunks: ["scriptDecisionTableEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/ul-editor.html', template: 'html/ul-editor.html', chunks: ["ulEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/permission-config-editor.html', template: 'html/permission-config-editor.html', chunks: ["permissionConfigEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/client-config-editor.html', template: 'html/client-config-editor.html', chunks: ["clientConfigEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/resource-editor.html', template: 'html/resource-editor.html', chunks: ["resourceEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/crosstab-editor.html', template: 'html/crosstab-editor.html', chunks: ["crosstabEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/complexscorecard-editor.html', template: 'html/complexscorecard-editor.html', chunks: ["complexScoreCardEditor"] }),
        new HtmlWebpackPlugin({ filename: 'html/login.html', template: 'html/login.html', chunks: ["login"] }),
        new HtmlWebpackPlugin({ filename: 'html/monitoring-dashboard.html', template: 'html/monitoring-dashboard.html', chunks: ["monitoringDashboard"] }),
        new HtmlWebpackPlugin({ filename: 'html/analysis-dashboard.html', template: 'html/analysis-dashboard.html', chunks: ["analysisDashboard"] }),
    ],
    module: {
        rules: [
            {
                test: /\.(jsx|js)?$/,
                exclude: /node_modules/,
                loader: "babel-loader",
                options: {
                    presets: ["@babel/preset-react", "@babel/preset-env"]
                }
            },
            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader', 'postcss-loader']
            },
            {
                test: /\.(eot|woff|woff2|ttf|svg|png|jpg)$/,
                type: 'asset',
                parser: {
                    dataUrlCondition: {
                        maxSize: 10 * 1024 * 1024
                    }
                }
            }
        ]
    },
    devServer: {
        static: path.join(__dirname, 'dist'),
        compress: true,
        port: 3000,
        host: "0.0.0.0",
        open: false,
        client: { overlay: false },
        proxy: [
            {
                context: ['/api/'],
                target: 'http://127.0.0.1:8180/',
                changeOrigin: true,
                pathRewrite: {
                    '^/api': '/ruleforgeV2'
                }
            }
        ]
    }
};
