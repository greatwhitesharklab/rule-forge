import '../css/tailwind-base.css';
import {createRoot} from 'react-dom/client';
import LoginPage from './index.tsx';

/**
 * login.html 独立入口(SPA 之前的访问方式 + 回退)。
 *
 * <p>SPA 根入口是 {@code /src/main.tsx}(index.html → BrowserRouter)。
 * 本文件仅服务于直接访问 {@code /html/login.html} 的场景,把 LoginPage 挂到 #root。
 */
createRoot(document.getElementById('root')!).render(<LoginPage/>);
