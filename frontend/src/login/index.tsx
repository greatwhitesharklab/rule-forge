import '../bootbox.js';
import '../css/tailwind-base.css';
import React, {Component, ChangeEvent, FormEvent} from 'react';
import {createRoot} from 'react-dom/client';

interface LoginState {
    username: string;
    password: string;
    error: string;
    loading: boolean;
}

class LoginPage extends Component<object, LoginState> {
    state: LoginState = {username: '', password: '', error: '', loading: false};

    handleSubmit = (e: FormEvent<HTMLFormElement>): void => {
        e.preventDefault();
        this.setState({loading: true, error: ''});
        const {username, password} = this.state;
        fetch(window._server + '/frame/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({username, password}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then((result: { status: boolean }) => {
            this.setState({loading: false});
            if (result.status) {
                const redirect = new URLSearchParams(window.location.search).get('redirect') || '/';
                window.location.href = redirect;
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

const container = document.getElementById('root');
if (container) {
    createRoot(container).render(<LoginPage/>);
}
