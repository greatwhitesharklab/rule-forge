import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

interface PackageDialogProps {
    dispatch: (a: unknown) => void;
}

interface PackageDialogState {
    visible: boolean;
    title: string;
    packageId: string;
    packageName: string;
    disabled: boolean;
    errors: Record<string, string | undefined>;
    create?: boolean;
    rowIndex?: number;
}

export default class PackageDialog extends Component<PackageDialogProps, PackageDialogState> {
    constructor(props: PackageDialogProps) {
        super(props);
        this.state = {visible: false, title: '', packageId: '', packageName: '', disabled: false, errors: {}};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_CREATE_PACKAGE_DIALOG, (data: { create: boolean; title: string; rowIndex: number; rowData?: { id: string; name: string } }) => {
            this.setState({visible: true, errors: {}});
            const create = data.create;
            const title = data.title;
            const rowIndex = data.rowIndex;
            if (create) {
                this.setState({create, title, rowIndex, packageId: '', packageName: '', disabled: false});
            } else {
                this.setState({create, title, rowIndex, packageId: data.rowData!.id, packageName: data.rowData!.name, disabled: true});
            }
        });
        event.eventEmitter.on(event.HIDE_CREATE_PACKAGE_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_PACKAGE_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_CREATE_PACKAGE_DIALOG);
    }

    _validate(): { valid: boolean; errors: Record<string, string | undefined> } {
        const errors: Record<string, string | undefined> = {};
        const {packageId, packageName, create} = this.state;
        if (create) {
            if (!packageId || !packageId.trim()) {
                errors.packageId = '知识包编码不能为空';
            }
        }
        if (!packageName || !packageName.trim()) {
            errors.packageName = '知识包名称不能为空';
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch} = this.props;
        const body = (
            <div>
                <div className="form-group">
                    <label>包ID:</label>
                    <input type="text" className="form-control" name="packageId"
                        value={this.state.packageId}
                        disabled={this.state.disabled}
                        onChange={(e) => this.setState({packageId: e.target.value, errors: {...this.state.errors, packageId: undefined}})}/>
                    {this.state.errors.packageId && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.packageId}</div>}
                </div>
                <div className="form-group">
                    <label>包名称:</label>
                    <input type="text" className="form-control" name="packageName"
                        value={this.state.packageName}
                        onChange={(e) => this.setState({packageName: e.target.value, errors: {...this.state.errors, packageName: undefined}})}/>
                    {this.state.errors.packageName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.packageName}</div>}
                </div>
            </div>
        );
        const buttons = [
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: function (this: PackageDialog) {
                    const {valid, errors} = this._validate();
                    if (!valid) {
                        this.setState({errors});
                        return;
                    }
                    const {packageId, packageName, rowIndex, create} = this.state;
                    if (create) {
                        dispatch(action.addMaster({id: packageId, name: packageName, createDate: new Date(), resourceItems: []}));
                    } else {
                        dispatch(action.updateMaster({packageName, rowIndex: rowIndex!}));
                    }
                    event.eventEmitter.emit(event.HIDE_CREATE_PACKAGE_DIALOG);
                }.bind(this)
            }
        ];
        return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>);
    };
}
