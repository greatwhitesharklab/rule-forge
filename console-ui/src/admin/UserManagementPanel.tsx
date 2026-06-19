import {Component} from 'react';
import {Table, Button, Space, Tag, Modal, message} from 'antd';
import {PlusOutlined, EditOutlined, StopOutlined, CheckOutlined, KeyOutlined, SafetyOutlined} from '@ant-design/icons';
import PageShell from '@/frame/components/PageShell';
import {
    listUsers,
    createUser,
    updateUser,
    toggleUserEnabled,
    resetPassword,
    UserItem,
} from '@/api/client';
import UserFormModal from './UserFormModal';
import UserPermissionDrawer from './UserPermissionDrawer';

interface UserManagementPanelState {
    users: UserItem[];
    loading: boolean;
    formVisible: boolean;
    drawerVisible: boolean;
    editingUser: UserItem | null;
    permissionUser: UserItem | null;
}

/**
 * 用户管理面板 — V5.15 权限改造
 *
 * Antd Table 列出所有用户,支持:新增 / 编辑 / 启用禁用 / 重置密码 / 权限配置。
 * Admin-only (只有 admin 能看到 activity-bar 入口)。
 */
export default class UserManagementPanel extends Component<{}, UserManagementPanelState> {
    state: UserManagementPanelState = {
        users: [],
        loading: false,
        formVisible: false,
        drawerVisible: false,
        editingUser: null,
        permissionUser: null,
    };

    componentDidMount() {
        this.loadUsers();
    }

    loadUsers = async () => {
        this.setState({loading: true});
        try {
            const users = await listUsers();
            this.setState({users});
        } catch (e) {
            // 401 handled by client.ts
        } finally {
            this.setState({loading: false});
        }
    };

    // ── 创建/编辑用户 ──

    showCreateForm = () => {
        this.setState({formVisible: true, editingUser: null});
    };

    showEditForm = (user: UserItem) => {
        this.setState({formVisible: true, editingUser: user});
    };

    handleFormSubmit = async (values: {
        username?: string;
        password?: string;
        isAdmin: boolean;
        canExport: boolean;
    }) => {
        try {
            if (this.state.editingUser) {
                const params: Record<string, string> = {};
                if (values.password) params.password = values.password;
                params.isAdmin = String(values.isAdmin);
                params.canExport = String(values.canExport);
                await updateUser(this.state.editingUser.id, params);
                message.success('用户已更新');
            } else {
                await createUser(
                    values.username || '',
                    values.password || '',
                    values.isAdmin,
                    values.canExport,
                );
                message.success('用户已创建');
            }
            this.setState({formVisible: false, editingUser: null});
            this.loadUsers();
        } catch (e) {
            // error handled by client.ts
        }
    };

    handleFormCancel = () => {
        this.setState({formVisible: false, editingUser: null});
    };

    // ── 启用/禁用 ──

    handleToggleEnabled = (user: UserItem) => {
        const action = user.isEnabled ? '禁用' : '启用';
        Modal.confirm({
            title: `确认${action}用户 "${user.username}"?`,
            onOk: async () => {
                try {
                    await toggleUserEnabled(user.id, !user.isEnabled);
                    message.success(`已${action}`);
                    this.loadUsers();
                } catch (e) {
                    // handled
                }
            },
        });
    };

    // ── 重置密码 ──

    handleResetPassword = (user: UserItem) => {
        let newPassword = '';
        Modal.confirm({
            title: `重置用户 "${user.username}" 的密码`,
            content: (
                <input
                    className="ant-input"
                    placeholder="输入新密码"
                    type="password"
                    onChange={e => { newPassword = e.target.value; }}
                />
            ),
            onOk: async () => {
                if (!newPassword) {
                    message.warning('密码不能为空');
                    return;
                }
                try {
                    await resetPassword(user.id, newPassword);
                    message.success('密码已重置');
                } catch (e) {
                    // handled
                }
            },
        });
    };

    // ── 权限配置 ──

    showPermissions = (user: UserItem) => {
        this.setState({drawerVisible: true, permissionUser: user});
    };

    handleDrawerClose = () => {
        this.setState({drawerVisible: false, permissionUser: null});
    };

    // ── 渲染 ──

    render() {
        const {users, loading, formVisible, editingUser, drawerVisible, permissionUser} = this.state;

        const columns = [
            {
                title: '用户名',
                dataIndex: 'username',
                key: 'username',
            },
            {
                title: '角色',
                dataIndex: 'isAdmin',
                key: 'role',
                render: (isAdmin: boolean) =>
                    isAdmin ? <Tag color="blue">管理员</Tag> : <Tag>普通用户</Tag>,
            },
            {
                title: '状态',
                dataIndex: 'isEnabled',
                key: 'status',
                render: (isEnabled: boolean) =>
                    isEnabled
                        ? <Tag color="green">启用</Tag>
                        : <Tag color="red">禁用</Tag>,
            },
            {
                title: '创建时间',
                dataIndex: 'createdAt',
                key: 'createdAt',
                render: (v: string) => v ? new Date(v).toLocaleString() : '-',
            },
            {
                title: '操作',
                key: 'actions',
                render: (_: unknown, record: UserItem) => (
                    <Space size="small">
                        <Button size="small" icon={<EditOutlined/>}
                                onClick={() => this.showEditForm(record)}>编辑</Button>
                        <Button size="small"
                                icon={record.isEnabled ? <StopOutlined/> : <CheckOutlined/>}
                                danger={record.isEnabled}
                                onClick={() => this.handleToggleEnabled(record)}>
                            {record.isEnabled ? '禁用' : '启用'}
                        </Button>
                        <Button size="small" icon={<KeyOutlined/>}
                                onClick={() => this.handleResetPassword(record)}>重置密码</Button>
                        {!record.isAdmin && (
                            <Button size="small" icon={<SafetyOutlined/>}
                                    onClick={() => this.showPermissions(record)}>权限</Button>
                        )}
                    </Space>
                ),
            },
        ];

        return (
            <PageShell
                title="用户管理"
                actions={<Button type="primary" icon={<PlusOutlined/>} onClick={this.showCreateForm}>新增用户</Button>}
            >
                <Table<UserItem>
                    rowKey="id"
                    dataSource={users}
                    columns={columns}
                    loading={loading}
                    pagination={false}
                    size="middle"
                />

                <UserFormModal
                    visible={formVisible}
                    user={editingUser}
                    onOk={this.handleFormSubmit}
                    onCancel={this.handleFormCancel}
                />

                {permissionUser && (
                    <UserPermissionDrawer
                        visible={drawerVisible}
                        user={permissionUser}
                        onClose={this.handleDrawerClose}
                    />
                )}
            </PageShell>
        );
    }
}
