/**
 * Ant Design 5 theme configuration for RuleForge.
 *
 * Matches the existing --rf-primary design token (#1677ff) so Ant Design
 * components blend with the current Tailwind-based design system.
 */

import type { ThemeConfig } from 'antd';

const antdTheme: ThemeConfig = {
    token: {
        colorPrimary: '#1677ff',
        borderRadius: 6,
        fontFamily:
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    },
    components: {
        Button: {
            controlHeight: 32,
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
