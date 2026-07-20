// V7.24:从 datasource/index.tsx 拆分 — 批量测试弹窗流程(模式选择/FLOW/DATASOURCE/Excel 上传)与启动请求
import React from 'react';
import {Form, Input, Modal, Radio, Tag, Upload, message} from 'antd';
import {FolderOpenOutlined, ThunderboltOutlined} from '@ant-design/icons';
import {alert} from '@/utils/modal';
import {DatasourceItem} from '../action';
import {startBatchTestWithFile, apiBase} from '../../api/client';
import * as batchTestEvent from '../../package/event';

/**
 * V5.8.4: 批量测试入口 — Antd Modal 支持 3 种模式:
 *   - "测决策流" (FLOW + DATASOURCE):用三方数据调 Flow,验证集成(手填 entityIds)
 *   - "裸数据源测试" (DATASOURCE + FILE):只调数据源,测 SLA(手填 entityIds)
 *   - "Excel 上传" (v5.8.4):上传 .xlsx,3 种 mode 都能走
 */
export const handleBatchTest = async (ds: DatasourceItem) => {
    const testMode = await new Promise<'FLOW' | 'DATASOURCE' | 'EXCEL' | null>((resolve) => {
        let selectedMode: 'FLOW' | 'DATASOURCE' | 'EXCEL' = 'FLOW';
        Modal.confirm({
            title: (
                <span>
                    <ThunderboltOutlined style={{marginRight: 8}} />
                    批量测试 - {ds.name}
                </span>
            ),
            icon: null,
            width: 580,
            content: (
                <div>
                    <div style={{marginBottom: 16, color: '#666', fontSize: 13}}>
                        选一种测试模式。Excel 模式支持 1000+ 行(手填只适合小批量)。
                    </div>
                    <Radio.Group
                        defaultValue="FLOW"
                        onChange={(e) => { selectedMode = e.target.value; }}
                        style={{display: 'flex', flexDirection: 'column', gap: 12}}
                    >
                        <Radio value="FLOW" style={{alignItems: 'flex-start'}}>
                            <div>
                                <div style={{fontWeight: 500}}>
                                    <Tag color="blue">FLOW + DATASOURCE</Tag>
                                    {' '}测决策流(用三方真实数据)
                                </div>
                                <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                    调数据源拿真实响应,跑决策流,验证集成链路
                                </div>
                            </div>
                        </Radio>
                        <Radio value="DATASOURCE" style={{alignItems: 'flex-start'}}>
                            <div>
                                <div style={{fontWeight: 500}}>
                                    <Tag color="orange">DATASOURCE + FILE</Tag>
                                    {' '}裸数据源测试(SLA / 字段映射)
                                </div>
                                <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                    Excel 喂 {`{entityId, fieldName}`},直接调数据源 connector
                                </div>
                            </div>
                        </Radio>
                        <Radio value="EXCEL" style={{alignItems: 'flex-start'}}>
                            <div>
                                <div style={{fontWeight: 500}}>
                                    <Tag color="green">上传 Excel (v5.8.4)</Tag>
                                    {' '}三种模式都支持
                                </div>
                                <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                    上传 .xlsx,固定列 schema:entityId / fieldName(可选 clazz)
                                </div>
                            </div>
                        </Radio>
                    </Radio.Group>
                </div>
            ),
            okText: '下一步',
            cancelText: '取消',
            onOk: () => { resolve(selectedMode); },
            onCancel: () => { resolve(null); },
        });
    });
    if (!testMode) return;

    if (testMode === 'FLOW') {
        showFlowBatchModal(ds);
    } else if (testMode === 'DATASOURCE') {
        showDatasourceOnlyBatchModal(ds);
    } else {
        showExcelUploadModal(ds);
    }
};

/**
 * FLOW + DATASOURCE 模式:用三方数据测决策流
 */
const showFlowBatchModal = (ds: DatasourceItem) => {
    let entityIds = '';
    let idField = 'id';
    let flowId = '';

    Modal.confirm({
        title: (
            <span>
                <Tag color="blue">FLOW + DATASOURCE</Tag>
                {' '}用 {ds.name} 的真实数据测决策流
            </span>
        ),
        icon: null,
        width: 540,
        content: (
            <Form layout="vertical" style={{marginTop: 8}}>
                <Form.Item
                    label="Entity 主键值(逗号或空格分隔)"
                    required
                    help={'比如 "1,2,3,100,200" — 100 个值就要拉 100 次数据源'}
                >
                    <Input.TextArea
                        rows={3}
                        defaultValue={entityIds}
                        placeholder="e.g. 1,2,3,100,200"
                        onChange={(e) => { entityIds = e.target.value; }}
                    />
                </Form.Item>
                <Form.Item label="主键字段名" help="数据源 connector 认的主键字段,默认 id">
                    <Input
                        defaultValue={idField}
                        onChange={(e) => { idField = e.target.value || 'id'; }}
                    />
                </Form.Item>
                <Form.Item
                    label="待测决策流 ID"
                    required
                    help="跑哪个 Flow,比如 loan-approval / risk-score"
                >
                    <Input
                        defaultValue={flowId}
                        placeholder="e.g. loan-approval"
                        onChange={(e) => { flowId = e.target.value; }}
                    />
                </Form.Item>
            </Form>
        ),
        okText: '开始测试',
        cancelText: '取消',
        onOk: () => {
            const valueList = entityIds.split(/[,\s]+/).filter((s: string) => s.length > 0);
            if (valueList.length === 0) {
                alert('请输入 entityIds');
                return Promise.reject();
            }
            if (!flowId) {
                alert('请输入 Flow ID');
                return Promise.reject();
            }
            startBatchTestDs(ds, 'FLOW', idField, valueList, flowId);
            return Promise.resolve();
        },
    });
};

/**
 * DATASOURCE + FILE 模式:裸数据源 SLA / 字段映射验证
 * 简化版:用 Antd prompt 拿 entityIds + fieldName,直接调数据源
 * (实际生产里应该 Excel 导入,但 MVP 简化)
 */
const showDatasourceOnlyBatchModal = (ds: DatasourceItem) => {
    let entityIds = '';
    let fieldName = '';
    let idField = 'id';

    Modal.confirm({
        title: (
            <span>
                <Tag color="orange">DATASOURCE + FILE</Tag>
                {' '}测 {ds.name} 的数据源连接
            </span>
        ),
        icon: null,
        width: 540,
        content: (
            <Form layout="vertical" style={{marginTop: 8}}>
                <Form.Item
                    label="要拉的字段名"
                    required
                    help="数据源 connector 认的字段,比如 score / name"
                >
                    <Input
                        defaultValue={fieldName}
                        placeholder="e.g. score"
                        onChange={(e) => { fieldName = e.target.value; }}
                    />
                </Form.Item>
                <Form.Item
                    label="Entity 主键值(逗号或空格分隔)"
                    required
                    help="N 个值就会拉 N 次数据源"
                >
                    <Input.TextArea
                        rows={3}
                        defaultValue={entityIds}
                        placeholder="e.g. 1,2,3,100,200"
                        onChange={(e) => { entityIds = e.target.value; }}
                    />
                </Form.Item>
                <Form.Item label="主键字段名" help="数据源主键字段,默认 id">
                    <Input
                        defaultValue={idField}
                        onChange={(e) => { idField = e.target.value || 'id'; }}
                    />
                </Form.Item>
                <div style={{background: '#fffbe6', border: '1px solid #ffe58f',
                            padding: 8, borderRadius: 4, marginTop: 8, fontSize: 12, color: '#874d00'}}>
                    <strong>说明:</strong> V5.8.2 简化为 Modal 填 entityIds;
                    后续接 Excel 导入可以走老路径或加新 FILE 上传端点。
                </div>
            </Form>
        ),
        okText: '开始测试',
        cancelText: '取消',
        onOk: () => {
            const valueList = entityIds.split(/[,\s]+/).filter((s: string) => s.length > 0);
            if (!fieldName) {
                alert('请输入 fieldName');
                return Promise.reject();
            }
            if (valueList.length === 0) {
                alert('请输入 entityIds');
                return Promise.reject();
            }
            // 构造 Excel 风格的 rows:[{entityId, fieldName, clazz: ''}]
            // 然后调 startBatchTest(subjectType=DATASOURCE, inputSourceType=FILE, inputConfig={rows: [...]})
            // V5.8.2 简化:用 inline inputConfig,后端 DatasourceInputSource 暂未实现
            // FILE 模式 — 走老 BatchTestService 异步执行
            startBatchTestDs(ds, 'DATASOURCE', idField, valueList, fieldName);
            return Promise.resolve();
        },
    });
};

/**
 * 调后端 startBatchTest + emit dialog 事件
 * subjectType: 'FLOW' (调决策流) | 'DATASOURCE' (只测数据源)
 */
const startBatchTestDs = async (
    ds: DatasourceItem,
    subjectType: 'FLOW' | 'DATASOURCE',
    idField: string,
    valueList: string[],
    extra: string,  // flowId or fieldName
) => {
    const ce = (window as any).parent?.componentEvent || (window as any).componentEvent;

    try {
        let body: Record<string, unknown>;
        if (subjectType === 'FLOW') {
            body = {
                subjectType: 'FLOW',
                subjectId: null,
                inputSourceType: 'DATASOURCE',
                inputSourceId: ds.id,
                inputConfig: {
                    datasourceId: ds.id,
                    clazz: '',
                    valueList,
                    inputField: idField
                },
                project: '',
                packageId: '',
                flowId: extra  // extra is flowId
            };
        } else {
            // DATASOURCE subject:inputConfig.rows 是 [{entityId, fieldName}]
            // (V5.8.2 简化:用 inline inputConfig,FileInputSource 暂未实现 FILE 模式 fetch)
            body = {
                subjectType: 'DATASOURCE',
                subjectId: ds.id,  // subjectId = datasourceId in this mode
                inputSourceType: 'FILE',  // V5.8.2 暂用 FILE + inline rows(后续会走 Excel)
                inputSourceId: null,
                inputConfig: {
                    // For V5.8.2 简化的 DATASOURCE+FILE 路径,FileInputSource
                    // 会校验 session 有 rows。这里我们靠 BatchTestServiceImpl.executeBatchAsync
                    // 复用老路径(测数据源走的代码还没接好 — 留 V5.8.3+)
                    datasourceId: ds.id,
                    rows: valueList.map((id) => ({ entityId: id, fieldName: extra }))
                },
                project: '',
                packageId: '',
                flowId: ''  // not used for DATASOURCE subject
            };
        }
        const resp = await fetch(apiBase() + '/batchtest/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const result = await resp.json();

        if (result.sessionId) {
            batchTestEvent.eventEmitter.emit(batchTestEvent.OPEN_BATCH_TEST_DIALOG, {
                sessionId: result.sessionId,
                data: { subjectType, inputSourceType: 'DATASOURCE', skipStart: true }
            });
            if (ce) ce.eventEmitter.emit(ce.SHOW_LOADING);
        } else {
            alert('启动失败: ' + (result.error || JSON.stringify(result)));
        }
    } catch (e: any) {
        alert('请求失败: ' + e.message);
    }
};

/**
 * v5.8.4 新:Excel 上传批量测试。
 * 弹一个 Antd Modal,让用户选 subject(FLOW / DATASOURCE) + 拖 .xlsx。
 * Excel schema:
 *   - DATASOURCE+FILE:列 entityId, fieldName, clazz(可选)
 *   - FLOW+DATASOURCE:列 entityId(主键)+ 其他要 fetch 的列
 */
const showExcelUploadModal = (ds: DatasourceItem) => {
    let subjectType: 'FLOW' | 'DATASOURCE' = 'DATASOURCE';
    let idField = 'entityId';
    let flowId = '';
    let uploadedFile: File | null = null;

    Modal.confirm({
        title: (
            <span>
                <Tag color="green">上传 Excel (v5.8.4)</Tag>
                {' '}测 {ds.name}
            </span>
        ),
        icon: null,
        width: 560,
        content: (
            <Form layout="vertical" style={{marginTop: 8}}>
                <Form.Item label="测试模式" required>
                    <Radio.Group
                        defaultValue="DATASOURCE"
                        onChange={(e) => { subjectType = e.target.value; }}
                    >
                        <Radio value="DATASOURCE">DATASOURCE + FILE</Radio>
                        <Radio value="FLOW">FLOW + DATASOURCE</Radio>
                    </Radio.Group>
                </Form.Item>
                <Form.Item label="Excel 文件" required
                           help="DATASOURCE+FILE:entityId/fieldName/clazz 列;FLOW+DATASOURCE:entityId + 其他列">
                    <Upload.Dragger
                        accept=".xlsx,.xls"
                        maxCount={1}
                        beforeUpload={(file) => {
                            uploadedFile = file;
                            return false;  // 不自动上传
                        }}
                        onRemove={() => { uploadedFile = null; return true; }}
                        fileList={uploadedFile ? [{
                            uid: '1', name: uploadedFile.name, status: 'done',
                        }] : []}
                    >
                        <p className="ant-upload-drag-icon">
                            <FolderOpenOutlined />
                        </p>
                        <p className="ant-upload-text">点击或拖拽 .xlsx 到这里</p>
                        <p className="ant-upload-hint">单文件,最大 10000 行</p>
                    </Upload.Dragger>
                </Form.Item>
                <Form.Item label="主键字段名" help="Excel 里 entityId 那一列的名字,默认 entityId">
                    <Input
                        defaultValue={idField}
                        onChange={(e) => { idField = e.target.value || 'entityId'; }}
                    />
                </Form.Item>
                <Form.Item label="待测决策流 ID(仅 FLOW 模式)" help="FLOW 模式必填,DATASOURCE 模式忽略">
                    <Input
                        defaultValue={flowId}
                        placeholder="e.g. loan-approval"
                        onChange={(e) => { flowId = e.target.value; }}
                    />
                </Form.Item>
            </Form>
        ),
        okText: '启动测试',
        cancelText: '取消',
        onOk: () => {
            if (!uploadedFile) {
                message.error('请先选择 Excel 文件');
                return Promise.reject();
            }
            if (subjectType === 'FLOW' && !flowId) {
                message.error('FLOW 模式需要决策流 ID');
                return Promise.reject();
            }
            startBatchTestWithExcel(ds, subjectType, idField, flowId, uploadedFile);
            return Promise.resolve();
        },
    });
};

/**
 * v5.8.4 新:走 multipart /batchtest/start-with-file
 */
const startBatchTestWithExcel = async (
    ds: DatasourceItem,
    subjectType: 'FLOW' | 'DATASOURCE',
    idField: string,
    flowId: string,
    file: File,
) => {
    const ce = (window as any).parent?.componentEvent || (window as any).componentEvent;

    try {
        let req: Record<string, unknown>;
        if (subjectType === 'FLOW') {
            req = {
                subjectType: 'FLOW',
                subjectId: null,
                inputSourceType: 'DATASOURCE',
                inputSourceId: ds.id,
                inputConfig: {
                    datasourceId: ds.id,
                    clazz: '',
                    inputField: idField,
                },
                project: '',
                packageId: '',
                flowId,
            };
        } else {
            req = {
                subjectType: 'DATASOURCE',
                subjectId: ds.id,
                inputSourceType: 'FILE',
                inputSourceId: null,
                inputConfig: {
                    datasourceId: ds.id,
                    // fieldName 暂存到 inputConfig,Excel 每行都得含 fieldName 列
                    // (后端 parser 不读 inputConfig 里的 fieldName,以列名为准)
                },
                project: '',
                packageId: '',
                flowId: '',
            };
        }
        const result = await startBatchTestWithFile(req as any, file);

        if (result.sessionId) {
            batchTestEvent.eventEmitter.emit(batchTestEvent.OPEN_BATCH_TEST_DIALOG, {
                sessionId: result.sessionId,
                data: { subjectType, inputSourceType: req.inputSourceType, skipStart: true }
            });
            if (ce) ce.eventEmitter.emit(ce.SHOW_LOADING);
        } else {
            message.error('启动失败: ' + JSON.stringify(result));
        }
    } catch (e: any) {
        message.error('Excel 上传失败: ' + (e.message || e));
    }
};
