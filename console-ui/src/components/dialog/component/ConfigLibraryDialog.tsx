import { useEffect, useState } from 'react';
import {Button, Modal, Table} from 'antd';
import * as componentEvent from '../../componentEvent.js';

import {alert} from '@/utils/modal';
import {useDirty, useProject} from '@/editor/EditorContexts';
declare const variableLibraries: string[];
declare const constantLibraries: string[];
declare const actionLibraries: string[];
declare const parameterLibraries: string[];
declare function refreshVariableLibraries(): void;
declare function refreshConstantLibraries(): void;
declare function refreshActionLibraries(): void;
declare function refreshParameterLibraries(): void;

interface LibConfig {
    title: string;
    fileType: string;
    getLibraries: () => string[];
    refresh: () => void;
    existsMsg: string;
}

type LibType = 'variable' | 'constant' | 'action' | 'parameter';

const CONFIGS: Record<string, LibConfig> = {
    variable: {
        title: '变量库配置',
        fileType: 'VariableLibrary',
        getLibraries: () => variableLibraries,
        refresh: () => refreshVariableLibraries(),
        existsMsg: '变量库文件已存在'
    },
    constant: {
        title: '常量库配置',
        fileType: 'ConstantLibrary',
        getLibraries: () => constantLibraries,
        refresh: () => refreshConstantLibraries(),
        existsMsg: '常量库文件已存在'
    },
    action: {
        title: '动作库配置',
        fileType: 'ActionLibrary',
        getLibraries: () => actionLibraries,
        refresh: () => refreshActionLibraries(),
        existsMsg: '动作库文件已存在'
    },
    parameter: {
        title: '参数库配置',
        fileType: 'ParameterLibrary',
        getLibraries: () => parameterLibraries,
        refresh: () => refreshParameterLibraries(),
        existsMsg: '参数库文件已存在'
    }
};

export const OPEN_CONFIG_LIBRARY_DIALOG = 'open_config_library_dialog';
export const CLOSE_CONFIG_LIBRARY_DIALOG = 'close_config_library_dialog';

/**
 * 复用库配置对话框 — 由 editor 路由 mount,open/close 走 event emitter。
 *
 * <p>添加 / 删除库路径后通过 {@link DirtyContext} 通知编辑器变脏,项目名由
 * {@link ProjectContext} 读(替代历史 {@code window._project} / {@code window._setDirty} 全局变量)。
 */
export default function ConfigLibraryDialog() {
    const [visible, setVisible] = useState(false);
    const [type, setType] = useState<LibType | null>(null);
    const [libraries, setLibraries] = useState<string[]>([]);

    const project = useProject();
    const dirty = useDirty();

    useEffect(() => {
        const onOpen = (openType: LibType) => {
            const config = CONFIGS[openType];
            if (!config) return;
            setVisible(true);
            setType(openType);
            setLibraries([...config.getLibraries()]);
        };
        const onClose = () => {
            setVisible(false);
            setType(null);
        };
        componentEvent.eventEmitter.on(OPEN_CONFIG_LIBRARY_DIALOG, onOpen);
        componentEvent.eventEmitter.on(CLOSE_CONFIG_LIBRARY_DIALOG, onClose);
        return () => {
            componentEvent.eventEmitter.removeAllListeners(OPEN_CONFIG_LIBRARY_DIALOG);
            componentEvent.eventEmitter.removeAllListeners(CLOSE_CONFIG_LIBRARY_DIALOG);
        };
    }, []);

    const handleAdd = (): void => {
        if (!type) return;
        const config = CONFIGS[type];
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: project,
            fileType: config.fileType,
            callback: (file: string, version: string) => {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                const libs = config.getLibraries();
                if (libs.indexOf(path) !== -1) {
                    alert(config.existsMsg);
                    return;
                }
                libs.push(path);
                config.refresh();
                dirty.setDirty();
                setLibraries([...libs]);
            }
        });
    };

    const handleDelete = (lib: string): void => {
        if (!type) return;
        const config = CONFIGS[type];
        const libs = config.getLibraries();
        const pos = libs.indexOf(lib);
        if (pos !== -1) {
            libs.splice(pos, 1);
            config.refresh();
            dirty.setDirty();
            setLibraries([...libs]);
        }
    };

    const handleClose = (): void => {
        setVisible(false);
        setType(null);
    };

    if (!visible || !type) return null;
    const config = CONFIGS[type];

    return (
        <Modal open={true} title={config.title} onCancel={handleClose}
            footer={[
                <Button key="add" type="primary" onClick={handleAdd}>添加</Button>,
                <Button key="close" onClick={handleClose}>关闭</Button>,
            ]}>
            <Table rowKey={(r: {name: string}) => r.name}
                dataSource={libraries.map((lib: string) => ({name: lib}))} pagination={false} size="small"
                columns={[
                    {title: config.title.replace('配置', '文件'), dataIndex: 'name', key: 'name'},
                    {title: '操作', key: 'op', width: 70,
                        render: (_: unknown, r: {name: string}) => (
                            <Button type="link" onClick={() => handleDelete(r.name)}>删除</Button>
                        )},
                ]}/>
        </Modal>
    );
}