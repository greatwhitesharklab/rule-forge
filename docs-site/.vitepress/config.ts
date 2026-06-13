import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'RuleForge',
  description: '面向金融场景的智能决策引擎',

  // GitHub Pages 部署基础路径
  base: '/rule-forge/',

  // 忽略 localhost 链接检查
  ignoreDeadLinks: [
    (url) => url.startsWith('http://localhost'),
  ],

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/rule-forge/logo.svg' }],
  ],

  themeConfig: {
    logo: '/logo.svg',

    nav: [
      { text: '指南', link: '/guide/getting-started' },
      { text: '教程', link: '/tutorial/sme-loan-approval' },
      { text: 'API', link: '/api/console-api' },
      {
        text: '更多',
        items: [
          { text: '架构', link: '/architecture/overview' },
          { text: '部署', link: '/deployment/docker-compose' },
          { text: '开发', link: '/development/setup' },
        ],
      },
    ],

    sidebar: {
      '/guide/': [
        {
          text: '使用指南',
          items: [
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '安装部署', link: '/guide/installation' },
            { text: '第一个规则', link: '/guide/quick-start' },
            { text: '规则类型', link: '/guide/rule-types' },
            { text: '决策流设计器', link: '/guide/flow-designer' },
            { text: '评分卡', link: '/guide/scorecard' },
            { text: '规则测试', link: '/guide/testing' },
            { text: 'V5.22 AI 规则创作', link: '/guide/v522-ai-rule-authoring' },
          ],
        },
      ],
      '/tutorial/': [
        {
          text: '场景教程',
          items: [
            { text: '小微信贷审批', link: '/tutorial/sme-loan-approval' },
            { text: '反欺诈交易检测', link: '/tutorial/anti-fraud-detection' },
          ],
        },
      ],
      '/api/': [
        {
          text: 'API 参考',
          items: [
            { text: 'Console API', link: '/api/console-api' },
            { text: 'Executor API', link: '/api/executor-api' },
            { text: 'Model Service API', link: '/api/model-service-api' },
            { text: '决策 API', link: '/api/decision-api' },
          ],
        },
      ],
      '/architecture/': [
        {
          text: '架构文档',
          items: [
            { text: '架构概览', link: '/architecture/overview' },
            { text: 'RETE 引擎', link: '/architecture/rete-engine' },
            { text: '决策流引擎', link: '/architecture/decision-flow' },
            { text: 'AI+规则混合', link: '/architecture/ai-rules-hybrid' },
          ],
        },
      ],
      '/development/': [
        {
          text: '开发文档',
          items: [
            { text: '开发环境搭建', link: '/development/setup' },
            { text: '代码结构', link: '/development/code-structure' },
            { text: '贡献指南', link: '/development/contributing' },
            { text: 'V5.44.3 老 library 资源迁移手册', link: '/post-road-b-migration' },
          ],
        },
      ],
      '/deployment/': [
        {
          text: '部署文档',
          items: [
            { text: 'Docker Compose', link: '/deployment/docker-compose' },
            { text: '生产环境', link: '/deployment/production' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/greatwhitesharklab/rule-forge' },
    ],

    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
              modal: {
                noResultsText: '无法找到相关结果',
                resetButtonTitle: '清除查询条件',
                footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
              },
            },
          },
        },
      },
    },

    footer: {
      message: '基于 Apache 2.0 许可发布',
      copyright: 'Copyright © 2017-present RuleForge Contributors',
    },

    editLink: {
      pattern: 'https://github.com/greatwhitesharklab/rule-forge/edit/main/docs-site/:path',
      text: '在 GitHub 上编辑此页面',
    },

    lastUpdated: {
      text: '最后更新于',
    },
  },
})
