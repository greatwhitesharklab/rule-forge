/**
 * Reusable Ant Design ConfigProvider wrapper.
 *
 * Each iframe entry point wraps its root component with this provider
 * to get consistent theming. Ant Design 5 uses CSS-in-JS, so there are
 * no global CSS conflicts with existing Bootstrap/Tailwind styles.
 */

import React from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import antdTheme from '@/theme/antd-theme';

interface AntdProviderProps {
    children: React.ReactNode;
}

const AntdProvider: React.FC<AntdProviderProps> = ({ children }) => (
    <ConfigProvider theme={antdTheme} locale={zhCN}>
        {children}
    </ConfigProvider>
);

export default AntdProvider;
