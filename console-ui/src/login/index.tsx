import '../css/tailwind-base.css';
import React, {Component, ChangeEvent, FormEvent} from 'react';
import {formPost} from '../api/client.js';

interface LoginState {
    username: string;
    password: string;
    error: string;
    loading: boolean;
}

export default class LoginPage extends Component<object, LoginState> {
    state: LoginState = {username: '', password: '', error: '', loading: false};

    handleSubmit = (e: FormEvent<HTMLFormElement>): void => {
        e.preventDefault();
        this.setState({loading: true, error: ''});
        const {username, password} = this.state;
        formPost<{ status: boolean }>('/frame/login', {username, password}, { silent: true }).then((result) => {
            this.setState({loading: false});
            if (result.status) {
                const redirect = new URLSearchParams(window.location.search).get('redirect') || '/app';
                window.location.href = redirect;
                // SPA 模式(main.tsx 路由内)优先用 navigate('/app'),避免整页刷新;
                // 但 LoginPage 不直接依赖 router(兼容 login.html 独立访问),阶段 2 在 /app 路由统一处理。
            } else {
                this.setState({error: '登录失败'});
            }
        }).catch(() => {
            this.setState({loading: false, error: '登录失败，请检查用户名和密码'});
        });
    };

    render() {
        const {username, password, error, loading} = this.state;
        return (
            <div className="login-container">
                <div className="login-brand-panel">
                    <div className="login-brand-grid"/>
                    <div className="login-brand-content">
                        <div className="login-logo-icon">
                            <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                                <rect x="4" y="4" width="40" height="40" rx="8" fill="rgba(255,255,255,0.2)"/>
                                <path d="M16 18L24 14L32 18V30L24 34L16 30V18Z" stroke="white" strokeWidth="2" fill="rgba(255,255,255,0.15)"/>
                                <circle cx="24" cy="24" r="4" fill="white"/>
                            </svg>
                        </div>
                        <h1 className="login-brand-title">RuleForge</h1>
                        <p className="login-brand-desc">智能决策管理平台</p>
                    </div>
                </div>
                <div className="login-form-panel">
                    <div className="login-card">
                        <h2 className="login-card-title">欢迎登录</h2>
                        <p className="login-card-subtitle">请输入您的账号信息</p>
                        <form onSubmit={this.handleSubmit}>
                            <div className="login-field">
                                <label className="login-label">用户名</label>
                                <input type="text" className="login-input"
                                       placeholder="请输入用户名"
                                       value={username}
                                       onChange={(e: ChangeEvent<HTMLInputElement>) => this.setState({username: e.target.value})}/>
                            </div>
                            <div className="login-field">
                                <label className="login-label">密码</label>
                                <input type="password" className="login-input"
                                       placeholder="请输入密码"
                                       value={password}
                                       onChange={(e: ChangeEvent<HTMLInputElement>) => this.setState({password: e.target.value})}/>
                            </div>
                            {error && <div className="login-error">{error}</div>}
                            <button type="submit" className="login-submit-btn" disabled={loading}>
                                {loading ? (
                                    <span className="login-btn-loading">
                                        <span className="login-btn-spinner"/>
                                        登录中...
                                    </span>
                                ) : '登 录'}
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        );
    }
}

// 独立挂载(login.html 直接访问)移到 src/login/main.tsx,
// 避免本模块被 SPA 根入口 src/main.tsx import 时重复 createRoot 同一个 #root。
