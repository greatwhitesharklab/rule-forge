import {Component} from 'react';
import {Drawer, Table, Button, message, Switch} from 'antd';
import {getUserPermissions, saveUserPermissions, UserItem, UserProjectPermission} from '@/api/client';

interface UserPermissionDrawerProps {
    visible: boolean;
    user: UserItem;
    onClose: () => void;
}

interface UserPermissionDrawerState {
    permissions: UserProjectPermission[];
    loading: boolean;
    saving: boolean;
}

/**
 * 用户项目权限编辑 Drawer — V5.15
 *
 * 打开后加载某用户的所有项目权限,支持批量编辑保存。
 */
export default class UserPermissionDrawer extends Component<UserPermissionDrawerProps, UserPermissionDrawerState> {
    state: UserPermissionDrawerState = {
        permissions: [],
        loading: false,
        saving: false,
    };

    componentDidUpdate(prevProps: UserPermissionDrawerProps) {
        if (this.props.visible && !prevProps.visible) {
            this.loadPermissions();
        }
    }

    loadPermissions = async () => {
        this.setState({loading: true});
        try {
            const permissions = await getUserPermissions(this.props.user.id);
            this.setState({permissions});
        } catch (e) {
            // handled by client.ts
        } finally {
            this.setState({loading: false});
        }
    };

    handleSave = async () => {
        this.setState({saving: true});
        try {
            await saveUserPermissions(this.props.user.id, this.state.permissions);
            message.success('权限已保存');
            this.props.onClose();
        } catch (e) {
            // handled by client.ts
        } finally {
            this.setState({saving: false});
        }
    };

    togglePermission = (index: number, field: keyof UserProjectPermission) => {
        const permissions = [...this.state.permissions];
        permissions[index] = {
            ...permissions[index],
            [field]: !permissions[index][field],
        };
        this.setState({permissions});
    };

    // ── 列定义 ──

    getFileTypeColumns = () => {
        const types = [
            {key: 'Package', label: '包'},
            {key: 'VariableFile', label: '变量'},
            {key: 'ParameterFile', label: '参数'},
            {key: 'ConstantFile', label: '常量'},
            {key: 'ActionFile', label: '动作库'},
            {key: 'RuleFile', label: '规则'},
            {key: 'DecisionTableFile', label: '决策表'},
            {key: 'DecisionTreeFile', label: '决策树'},
            {key: 'ScorecardFile', label: '评分卡'},
            {key: 'FlowFile', label: '决策流'},
        ];

        return types.map(t => ({
            title: t.label,
            key: t.key,
            width: 80,
            align: 'center' as const,
            render: (_: unknown, __: UserProjectPermission, index: number) => {
                const readField = ('read' + t.key) as keyof UserProjectPermission;
                const writeField = ('write' + t.key) as keyof UserProjectPermission;
                // Package only has read/write, no separate read/write for project
                const hasWrite = writeField in this.state.permissions[index];
                return (
                    <div style={{display: 'flex', flexDirection: 'column', gap: 2, alignItems: 'center'}}>
                        <Switch
                            size="small"
                            checked={!!this.state.permissions[index][readField]}
                            onChange={() => this.togglePermission(index, readField)}
                            title="读取"
                        />
                        {hasWrite && (
                            <Switch
                                size="small"
                                checked={!!this.state.permissions[index][writeField]}
                                onChange={() => this.togglePermission(index, writeField)}
                                title="写入"
                            />
                        )}
                    </div>
                );
            },
        }));
    };

    render() {
        const {visible, user, onClose} = this.props;
        const {permissions, loading, saving} = this.state;

        const columns = [
            {
                title: '项目',
                dataIndex: 'project',
                key: 'project',
                width: 120,
                fixed: 'left' as const,
            },
            ...this.getFileTypeColumns(),
        ];

        return (
            <Drawer
                title={`权限配置 — ${user.username}`}
                open={visible}
                onClose={onClose}
                size={900}
                extra={
                    <Button type="primary" loading={saving} onClick={this.handleSave}>
                        保存
                    </Button>
                }
            >
                <Table<UserProjectPermission>
                    rowKey="id"
                    dataSource={permissions}
                    columns={columns}
                    loading={loading}
                    pagination={false}
                    size="small"
                    scroll={{x: 920}}
                />
            </Drawer>
        );
    }
}
