/**
 * Reusable Ant Design ConfigProvider wrapper.
 *
 * V7 SPA 走查(B5):iframe 时代已过,SPA 唯一入口 main.tsx 在根上挂本 Provider
 * (覆盖 /login 与 /app 下所有路由,含 RequireAuth),全站统一主题 + zhCN locale。
 * 组件内不再需要局部包 ConfigProvider locale。
 *
 * Ant Design 5 使用 CSS-in-JS,与现有 Bootstrap/Tailwind 样式无全局冲突。
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
