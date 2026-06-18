import {Component} from 'react';
import {Button, Space} from 'antd';
import Grid from '../../components/grid/component/Grid.jsx';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import {alert} from '@/utils/modal';
import {ClusterOutlined, DownloadOutlined, RetweetOutlined, ThunderboltOutlined, UploadOutlined} from '@ant-design/icons';
import {
    ResourcePackage,
    SimulatorCategory,
    SimulatorVariable,
} from '../action.js';
import {SimulatorCategoryContext} from '../../components/grid/SimulatorCategoryContext.ts';

interface SimulatorPageProps {}

interface SimulatorPageState {
    visible: boolean;
    title: string;
    simulatorCategoryData: SimulatorCategory[];
    simulatorCategoryRow: SimulatorCategory | SimulatorVariable[] | Record<string, unknown>;
    testResultinfo: string;
    project: string;
    packageId: string;
    files: string;
}

export default class SimulatorPage extends Component<SimulatorPageProps, SimulatorPageState> {
    constructor(props: SimulatorPageProps) {
        super(props);
        this.state = {
            visible: false,
            title: '',
            simulatorCategoryData: [],
            simulatorCategoryRow: [],
            testResultinfo: '',
            project: '',
            packageId: '',
            files: ''
        };
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_SIMULATOR_DIALOG, (rowData: ResourcePackage) => {
            const resourceItems = rowData.resourceItems;
            if (!resourceItems || resourceItems.length === 0) {
                alert("知识包[" + rowData.name + "]下未定义具体的文件，不能进行仿真测试!");
                return;
            }
            this.setState({project: rowData.project || '', packageId: rowData.id})
            let files = "";
            resourceItems.forEach((item, index) => {
                if (index > 0) {
                    files += ';';
                }
                files += item.path + "," + item.version;
            });

            action.loadSimulatorCategoryData(files, function (this: SimulatorPage, simulatorCategoryData: SimulatorCategory[]) {
                const ce = window.parent.componentEvent;
                ce.eventEmitter.emit(ce.HIDE_LOADING);
                // V5.74.4:不再写 window.simulatorCategoryData — 改由下面的
                // <SimulatorCategoryContext.Provider> 注入到树内 Cell。
                this.setState({
                    simulatorCategoryData,
                    simulatorCategoryRow: (simulatorCategoryData.length > 0 ? simulatorCategoryData[0] : [])
                })
            }.bind(this));
            this.setState({files, title: "对知识包[" + rowData.name + "]进行仿真测试"});
            this.setState({visible: true});
        });

        event.eventEmitter.on(event.HIDE_SIMULATOR_DIALOG, () => {
            this.setState({visible: false});
        });

        event.eventEmitter.on(event.REFRESH_SIMULATOR_DATA, (result: { info: string; data: SimulatorCategory[] }) => {
            const info = result.info;
            const data = result.data;
            action.buildSimulatorVariableEditorType(data);
            this.setState({
                simulatorCategoryData: data,
                simulatorCategoryRow: (data.length > 0 ? data[0] : {}),
                testResultinfo: '测试结果：' + info
            });
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_SIMULATOR_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_SIMULATOR_DIALOG);
        event.eventEmitter.removeAllListeners(event.REFRESH_SIMULATOR_DATA);
    }

    render() {
        if (this.state.simulatorCategoryData.length > 0) {
            const masterHeaders = [
                {id: 'tm-name', name: 'name', label: '类型名称', filterable: true}
            ];
            const slaveHeaders = [
                {id: 'ts-defaultValue', name: 'defaultValue', label: '值', editable: true, width: '200px'},
                {id: 'ts-label', name: 'label', filterable: true, label: '标题'},
                {id: 'ts-type', name: 'type', label: '数据类型', width: '100px'}
            ];
            // V5.74.4:Provider 包住整个 body(两个 Grid 在内),Cell 双击 list 编辑器时
            // 从 contextType 读 simulatorCategoryData。非仿真器场景下 null,Cell 回退空数组。
            const body = (
                <SimulatorCategoryContext.Provider value={this.state.simulatorCategoryData as any}>
                    <div style={{minHeight: '400px'}}>
                        <div style={{
                            padding: '8px',
                            marginBottom: '5px',
                            border: 'solid 1px rgb(219, 215, 215)',
                            borderRadius: '5px'
                        }}>
                            <Space size="small" style={{margin: '2px'}}>
                                <Button type="primary" htmlType="button" onClick={() => {
                                    const ce = window.parent.componentEvent;
                                    ce.eventEmitter.emit(ce.SHOW_LOADING);
                                    action.doTest({
                                        'project': this.state.project,
                                        'packageId': this.state.packageId,
                                        'files': this.state.files,
                                        'data': [this.state.simulatorCategoryData]
                                    }, function (this: SimulatorPage, result: Record<string, unknown>) {
                                        const info = '测试结果：' + result.info;
                                        alert(info as string);
                                        const data = result.data as SimulatorCategory[];
                                        action.buildSimulatorVariableEditorType(data);
                                        this.setState({
                                            simulatorCategoryData: data,
                                            simulatorCategoryRow: (data.length > 0 ? data[0] : {}),
                                            testResultinfo: info
                                        });
                                        ce.eventEmitter.emit(ce.HIDE_LOADING);
                                    }.bind(this));
                                }}><ThunderboltOutlined /> 测试决策包
                                </Button>
                            </Space>
                            <Space size="small" style={{margin: '2px'}}>
                                <Button color="blue" htmlType="button" onClick={() => {
                                    const ce = window.parent.componentEvent;
                                    ce.eventEmitter.emit(ce.SHOW_LOADING);
                                    event.eventEmitter.emit(event.OPEN_FLOW_DIALOG, {
                                        project: this.state.project,
                                        packageId: this.state.packageId,
                                        files: this.state.files,
                                        data: this.state.simulatorCategoryData
                                    });
                                }}><RetweetOutlined /> 测试决策流
                                </Button>
                            </Space>
                            <Space size="small" style={{margin: '2px'}}>
                                <Button color="green" htmlType="button" onClick={() => {
                                    event.eventEmitter.emit(event.OPEN_RETE_DIAGRAM_DIALOG, this.state.files);
                                }}><ClusterOutlined /> 查看Rete树
                                </Button>
                            </Space>
                            <Space size="small" style={{margin: '2px'}}>
                                <Button color="gold" htmlType="button" onClick={() => {
                                    action.exportExcelTemplate(this.state.files);
                                }}><DownloadOutlined /> 下载Excel测试数据模版
                                </Button>
                                <Button color="danger" htmlType="button" onClick={() => {
                                    event.eventEmitter.emit(event.OPEN_IMPORT_EXCEL_DIALOG, this.state.files);
                                }}><UploadOutlined /> 上传Excel测试数据
                                </Button>
                            </Space>
                        </div>
                        <div className="rf-row" style={{margin: 0}}>
                            <div className="rf-col-xs-3 rf-col-md-3" style={{paddingLeft: 0, paddingRight: '5px'}}>
                                <Grid selectFirst={true} headers={masterHeaders} rows={this.state.simulatorCategoryData}
                                      rowClick={(rowData: SimulatorCategory, rowIndex: number) => {
                                          const data = this.state.simulatorCategoryData;
                                          setTimeout(function (this: SimulatorPage) {
                                              this.setState({simulatorCategoryRow: data[rowIndex]});
                                          }.bind(this), 10);
                                      }}/>
                            </div>
                            <div className="rf-col-xs-9 rf-col-md-9" style={{padding: 0}}>
                                <Grid headers={slaveHeaders} rows={(this.state.simulatorCategoryRow as SimulatorCategory).variables || []}
                                      uniqueKey={true}/>
                            </div>
                        </div>
                    </div>
                </SimulatorCategoryContext.Provider>
            );
            return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} large={true} buttons={[]} onClose={() => this.setState({visible: false})}/>);
        } else {
            return (<CommonDialog visible={this.state.visible} title={this.state.title} body={[]} large={true} buttons={[]} onClose={() => this.setState({visible: false})}/>);
        }
    }
}
