import {Component} from 'react';

interface ConfigPanelProps {
    dispatch: (action: unknown) => void;
}

interface ConfigPanelState {
    vendor: string;
    apiKey: string;
    baseUrl: string;
    model: string;
    temperature: string;
    maxTokens: string;
    systemPrompt: string;
    vendors: VendorInfo[];
    testing: boolean;
    testResult: string | null;
}

interface VendorInfo {
    id: string;
    name: string;
    baseUrl: string;
    defaultModel: string;
    description: string;
}

class ConfigPanel extends Component<ConfigPanelProps, ConfigPanelState> {
    state: ConfigPanelState = {
        vendor: '', apiKey: '', baseUrl: '', model: '',
        temperature: '0.7', maxTokens: '4096', systemPrompt: '',
        vendors: [], testing: false, testResult: null
    };

    componentDidMount() {
        this.loadConfig();
        this.loadVendors();
    }

    async loadConfig() {
        try {
            const resp = await fetch(window._server + '/agent/config');
            const config = await resp.json();
            this.setState({
                vendor: config['llm.vendor'] || '',
                apiKey: config['llm.api_key'] || '',
                baseUrl: config['llm.base_url'] || '',
                model: config['llm.model'] || '',
                temperature: config['llm.temperature'] || '0.7',
                maxTokens: config['llm.max_tokens'] || '4096',
                systemPrompt: config['system_prompt'] || '',
            });
        } catch (e) {
            console.error('Failed to load config', e);
        }
    }

    async loadVendors() {
        try {
            const resp = await fetch(window._server + '/agent/vendors');
            const vendors: VendorInfo[] = await resp.json();
            this.setState({vendors});
        } catch (e) {
            console.error('Failed to load vendors', e);
        }
    }

    handleVendorChange = async (vendorId: string) => {
        this.setState({vendor: vendorId});
        try {
            await fetch(window._server + '/agent/vendors/' + vendorId + '/apply', {method: 'POST'});
            this.loadConfig();
        } catch (e) {
            console.error('Failed to apply vendor', e);
        }
    };

    handleSave = async () => {
        const {vendor, apiKey, baseUrl, model, temperature, maxTokens, systemPrompt} = this.state;
        try {
            await fetch(window._server + '/agent/config', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    'llm.vendor': vendor,
                    'llm.api_key': apiKey,
                    'llm.base_url': baseUrl,
                    'llm.model': model,
                    'llm.temperature': temperature,
                    'llm.max_tokens': maxTokens,
                    'system_prompt': systemPrompt,
                })
            });
            window.bootbox.alert('配置已保存');
        } catch (e) {
            window.bootbox.alert('保存失败: ' + (e as Error).message);
        }
    };

    handleTest = async () => {
        this.setState({testing: true, testResult: null});
        try {
            const resp = await fetch(window._server + '/agent/config/test-connection', {method: 'POST'});
            const result = await resp.json();
            this.setState({testResult: result.success ? '✅ 连接成功' : '❌ ' + result.message});
        } catch (e) {
            this.setState({testResult: '❌ 连接失败: ' + (e as Error).message});
        } finally {
            this.setState({testing: false});
        }
    };

    render() {
        const {vendors, vendor, apiKey, baseUrl, model, temperature, maxTokens, systemPrompt, testing, testResult} = this.state;

        return (
            <div style={{padding: '8px 12px', borderBottom: '1px solid #e8e8e8', background: '#fafafa'}}>
                {/* Vendor selector */}
                <div style={{marginBottom: 8}}>
                    <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>LLM 厂商</label>
                    <select className="form-control input-sm" value={vendor}
                            onChange={(e) => this.handleVendorChange(e.target.value)}>
                        <option value="">选择厂商...</option>
                        {vendors.map((v) => (
                            <option key={v.id} value={v.id}>{v.name}</option>
                        ))}
                    </select>
                </div>

                {/* API Key */}
                <div style={{marginBottom: 8}}>
                    <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>API Key</label>
                    <input type="password" className="form-control input-sm" value={apiKey}
                           onChange={(e) => this.setState({apiKey: e.target.value})}
                           placeholder="sk-xxx"/>
                </div>

                {/* Base URL */}
                <div style={{marginBottom: 8}}>
                    <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>Base URL</label>
                    <input type="text" className="form-control input-sm" value={baseUrl}
                           onChange={(e) => this.setState({baseUrl: e.target.value})}/>
                </div>

                {/* Model */}
                <div style={{marginBottom: 8, display: 'flex', gap: 8}}>
                    <div style={{flex: 1}}>
                        <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>模型</label>
                        <input type="text" className="form-control input-sm" value={model}
                               onChange={(e) => this.setState({model: e.target.value})}/>
                    </div>
                    <div style={{width: 70}}>
                        <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>Temperature</label>
                        <input type="text" className="form-control input-sm" value={temperature}
                               onChange={(e) => this.setState({temperature: e.target.value})}/>
                    </div>
                    <div style={{width: 70}}>
                        <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>Max Tokens</label>
                        <input type="text" className="form-control input-sm" value={maxTokens}
                               onChange={(e) => this.setState({maxTokens: e.target.value})}/>
                    </div>
                </div>

                {/* System Prompt */}
                <div style={{marginBottom: 8}}>
                    <label style={{fontSize: 11, color: '#666', display: 'block', marginBottom: 2}}>系统提示词</label>
                    <textarea className="form-control input-sm" rows={3} value={systemPrompt}
                              onChange={(e) => this.setState({systemPrompt: e.target.value})}/>
                </div>

                {/* Actions */}
                <div style={{display: 'flex', gap: 8}}>
                    <button className="btn btn-sm btn-primary" onClick={this.handleSave}>
                        <i className="glyphicon glyphicon-save" style={{marginRight: 4}}/>保存
                    </button>
                    <button className="btn btn-sm btn-default" onClick={this.handleTest} disabled={testing}>
                        {testing ? '测试中...' : '测试连接'}
                    </button>
                    {testResult && (
                        <span style={{fontSize: 12, lineHeight: '30px'}}>{testResult}</span>
                    )}
                </div>
            </div>
        );
    }
}

export default ConfigPanel;
