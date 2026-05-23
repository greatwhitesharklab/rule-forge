import React, {Component} from 'react';
import {createRoot} from 'react-dom/client';
import '../css/theme.css';

class LoginPage extends Component {
    constructor(props) {
        super(props);
        this.state = {username: '', password: '', error: '', loading: false};
    }

    handleSubmit = (e) => {
        e.preventDefault();
        this.setState({loading: true, error: ''});
        const {username, password} = this.state;
        $.ajax({
            url: window._server + '/frame/login',
            type: 'POST',
            data: {username, password},
            success: (result) => {
                this.setState({loading: false});
                if (result.status) {
                    const redirect = new URLSearchParams(window.location.search).get('redirect') || 'index.html';
                    window.location.href = redirect;
                } else {
                    this.setState({error: '登录失败'});
                }
            },
            error: () => {
                this.setState({loading: false, error: '登录失败，请检查用户名和密码'});
            }
        });
    };

    render() {
        const {username, password, error, loading} = this.state;
        return (
            <div style={{
                display: 'flex', justifyContent: 'center', alignItems: 'center',
                minHeight: '100vh', background: '#f5f5f5'
            }}>
                <div style={{
                    background: '#fff', borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    padding: 40, width: 360
                }}>
                    <h2 style={{textAlign: 'center', marginBottom: 30, color: '#333'}}>RuleForge</h2>
                    <form onSubmit={this.handleSubmit}>
                        <div className="form-group">
                            <input type="text" className="form-control" placeholder="用户名"
                                   value={username}
                                   onChange={(e) => this.setState({username: e.target.value})}/>
                        </div>
                        <div className="form-group">
                            <input type="password" className="form-control" placeholder="密码"
                                   value={password}
                                   onChange={(e) => this.setState({password: e.target.value})}/>
                        </div>
                        {error && <div className="alert alert-danger" style={{padding: '6px 12px'}}>{error}</div>}
                        <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
                            {loading ? '登录中...' : '登 录'}
                        </button>
                    </form>
                </div>
            </div>
        );
    }
}

const container = document.getElementById('root');
if (container) {
    createRoot(container).render(<LoginPage/>);
}
