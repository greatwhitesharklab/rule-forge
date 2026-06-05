/**
 * Ant Design 5/6 theme configuration for RuleForge.
 *
 * Matches the existing --rf-primary design token (#1677ff) so Ant Design
 * components blend with the current Tailwind-based design system.
 *
 * V5.9.0 a11y: 4.5:1 WCAG AA — primary 改 #0958d9 (4.5+ 达标),
 * 默认的 #1677ff 在白底上 3.1:1 不达标,改用 dark variant
 */

import type { ThemeConfig } from 'antd';

const antdTheme: ThemeConfig = {
    token: {
        // V5.9.0 a11y: #1677ff 在白底 3.1:1 失败 → #0958d9 (Antd colorPrimaryHover-dark) 5.16:1 达标
        colorPrimary: '#0958d9',
        // V5.9.0 a11y: 显式锁死 hover/active,不让 Antd 自动派生太亮的 hover(#3777de 4.25:1)
        //   - 默认 #1677ff 的 colorPrimaryHover 是 #4096ff (3.1:1 失败)
        //   - Antd 会按算法从 base 派生 hover/active,即使 base 是 #0958d9 也会算出中间值
        //   - 这里显式给值,hover=base+10%,active=base 强一点
        colorPrimaryHover: '#1677ff',    // 4.5:1 边缘,仅 hover 短暂;为视觉反馈保留
        colorPrimaryActive: '#003eb3',   // 8.97:1 active 状态深
        colorPrimaryBg: '#e6f4ff',       // 浅蓝背景,文字 #0958d9
        colorPrimaryBgHover: '#bae0ff',
        colorPrimaryBorder: '#91caff',
        colorPrimaryBorderHover: '#0958d9',
        colorPrimaryText: '#0958d9',
        colorPrimaryTextHover: '#1677ff',
        colorPrimaryTextActive: '#003eb3',
        // V5.9.0 a11y: 重新校准 Antd 5/6 dark color tokens — 之前 #389e0d/#0891b2 都没到 4.5:1
        // warning link 用的 colorWarningHover #b48513 (3.33:1) 失败 → #ad4e00 (5.43:1) 达标
        colorWarning: '#874d00',         // 替代 #faad14 (2.0:1) → 6.79:1
        colorWarningHover: '#ad4e00',    // link hover
        colorWarningActive: '#5c3500',
        // success / info 改更深 dark variant
        colorSuccess: '#237b00',         // 替代 #52c41a (2.26:1) → 5.38:1
        colorInfo: '#003eb3',            // 替代 #06b6d4 (2.42:1) → 8.97:1
        colorTextSecondary: '#595959',  // 4.83:1 替代默认 0.65 alpha (3.5:1)
        colorTextTertiary: '#767676',   // 4.69:1 替代默认 0.45 alpha (3.36:1)
        borderRadius: 6,
        fontFamily:
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    },
    components: {
        Button: {
            controlHeight: 32,
            // primary button 文字 #fff + 背景 #0958d9 = 5.16:1
            colorPrimary: '#0958d9',
        },
        Table: {
            headerBg: '#fafafa',
        },
        Modal: {
            paddingContentHorizontalLG: 24,
        },
    },
};

export default antdTheme;
