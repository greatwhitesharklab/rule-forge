import {formatDate} from '../Utils.js';

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const LOAD_PACKAGE_CONFIG_COMPLETE = 'load_package_config_completed';
export const ADD_MASTER = 'add_master';
export const UPDATE_MASTER = 'update_master';
export const DEL_MASTER = 'del_master';
export const ADD_SLAVE = 'add_slave';
export const UPDATE_SLAVE = 'update_slave';
export const DEL_SLAVE = 'del_slave';
export const SAVE = 'save';
export const SAVE_COMPLETED = 'save_completed';
export const APPLY = 'apply';
export const APPLY_COMPLETED = 'apply_completed';

// --- Type definitions ---

export interface ResourcePackage {
    id: string;
    name: string;
    createDate: Date;
    version?: string;
    testVersion?: string;
    project?: string;
    resourceItems: ResourceItem[];
}

export interface ResourceItem {
    name: string;
    path: string;
    version: string;
}

export interface PackageConfig {
    version?: string;
    testVersion?: string;
    [key: string]: unknown;
}

export interface SimulatorCategory {
    name: string;
    variables?: SimulatorVariable[];
    [key: string]: unknown;
}

export interface SimulatorVariable {
    name: string;
    label: string;
    type: string;
    defaultValue?: string;
    _editorType?: 'number' | 'boolean' | 'date' | 'list' | 'string';
    [key: string]: unknown;
}

export interface FileVersion {
    name: string;
    [key: string]: unknown;
}

export interface ImportExcelResult {
    status: boolean;
    sessionId?: string;
    totalRows?: number;
    data?: ImportErrorItem[];
    msg?: string;
}

export interface ImportErrorItem {
    sheetName: string;
    sheetRowId: number;
    sheetFieldName: string;
    errorMsg: string;
}

export interface BatchTestProgress {
    status: string;
    progress: number;
    totalRows: number;
    errorCount: number;
    successCount: number;
    sessionId?: string;
}

export interface FlowInfo {
    id: string;
    [key: string]: unknown;
}

export interface DiffItem {
    type: string;
    path: string;
    name: string;
    version: string;
    targetVersion?: string;
    [key: string]: unknown;
}

// --- Action types ---

interface SaveAction {
    type: typeof SAVE;
    newVersion: boolean;
    project: string;
    associatedFiles?: string[];
    versionComment?: string;
    packageId?: string;
    callback?: () => void;
}

interface ApplyAction {
    type: typeof APPLY;
    project: string;
    packageConfig: PackageConfig;
    currentPackage: ResourcePackage;
}

interface LoadMasterCompletedAction {
    type: typeof LOAD_MASTER_COMPLETED;
    masterData: ResourcePackage[];
}

interface LoadSlaveCompleteAction {
    type: typeof LOAD_SLAVE_COMPLETE;
    masterRowData: ResourcePackage;
}

interface LoadPackageConfigCompleteAction {
    type: typeof LOAD_PACKAGE_CONFIG_COMPLETE;
    config: PackageConfig;
}

interface AddMasterAction {
    type: typeof ADD_MASTER;
    data: ResourcePackage;
}

interface UpdateMasterAction {
    type: typeof UPDATE_MASTER;
    data: { rowIndex: number; packageName: string };
}

interface DelMasterAction {
    type: typeof DEL_MASTER;
    rowIndex: number;
}

interface AddSlaveAction {
    type: typeof ADD_SLAVE;
    data: ResourceItem;
}

interface UpdateSlaveAction {
    type: typeof UPDATE_SLAVE;
    data: ResourceItem & { rowIndex: number };
}

interface DelSlaveAction {
    type: typeof DEL_SLAVE;
    rowIndex: number;
}

interface SaveCompletedAction {
    type: typeof SAVE_COMPLETED;
}

interface ApplyCompletedAction {
    type: typeof APPLY_COMPLETED;
}

export type PackageAction =
    | SaveAction
    | ApplyAction
    | LoadMasterCompletedAction
    | LoadSlaveCompleteAction
    | LoadPackageConfigCompleteAction
    | AddMasterAction
    | UpdateMasterAction
    | DelMasterAction
    | AddSlaveAction
    | UpdateSlaveAction
    | DelSlaveAction
    | SaveCompletedAction
    | ApplyCompletedAction
    | { type: string; [key: string]: unknown };

// --- Action creators ---

export function save(newVersion: boolean, project: string, associatedFiles?: string[], versionComment?: string, packageId?: string, callback?: () => void): SaveAction {
    return {newVersion, project, associatedFiles, versionComment, callback, packageId, type: SAVE};
}

// 发布测试
export function refreshKnowledgeCache(project: string, packageConfig: PackageConfig, currentPackage: ResourcePackage): void {
    console.log('发布测试', currentPackage)
    const url = window._server + "/frame/fileVersions";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            path: '/' + project + '/___res__package__file__',
            project: project,
            packageId: currentPackage.id,
            page: '-1'
        }).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res: { files?: FileVersion[] }) {
        const files = res ? res.files || [] : [];
        let pushVersionOption = '<option value="">请选择</option>'
        let contrastVersionOption = '<option value="">请选择</option>'
        if(files.length === 1) {
            pushVersionOption += `<option value="${files[0].name}" selected>${files[0].name}</option>`
            contrastVersionOption += `<option value="${files[0].name}">${files[0].name}</option>`
        } else if (files.length > 1) {
            for (let i = 0; i < files.length; i++) {
                if (files[i].name) {
                    pushVersionOption += `<option value="${files[i].name}">${files[i].name}</option>`
                    if (files[i].name === currentPackage.version) {
                        // 默认：测试版本（没有测试版本时，默认为运行版本）
                        contrastVersionOption += `<option value="${files[i].name}" selected>${files[i].name}</option>`
                    } else {
                        contrastVersionOption += `<option value="${files[i].name}">${files[i].name}</option>`
                    }
                }
            }
        }
        const options: Record<string, unknown> = {
            title: '发布测试',
            message: `
                    <form class='bootbox-form'>
                    <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>标题</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="test-title" class="form-control" name="remark"/>
                            </div>
                        </div>
                    </div>
                    <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>版本</label>
                            </div>
                            <div class="col-xs-10">
                                 <div class="row">
                                    <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">发布版本</label>
                                             <select id="test-pass-version" class="form-control" required style="flex: 1;">
                                                ${pushVersionOption}
                                             </select>
                                        </div>
                                        <span id="test-pass-version-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                     <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">比对版本</label>
                                             <select id="test-contrast-version" class="form-control" required style="flex: 1;">
                                                ${contrastVersionOption}
                                             </select>
                                        </div>
                                        <span id="test-contrast-version-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                 </div>
                            </div>
                        </div>
                    </div>
                         <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>测试时间</label>
                            </div>
                            <div class="col-xs-10">
                                 <div class="row">
                                    <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">开始时间</label>
                                             <input type="datetime-local" id="startTime" class="form-control" name="startTime" style="flex: 1;"/>
                                        </div>
                                        <span id="start-time-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                     <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">结束时间</label>
                                             <input type="datetime-local" id="endTime" class="form-control" name="endTime" style="flex: 1;"/>
                                        </div>
                                        <span id="end-time-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                 </div>
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>测试比例</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="test-proportion" class="form-control" name="proportion"/>
                                <span id="test-proportion-error" class="text-danger" style="display: none;">该字段为必填项</span>
                                <span id="test-proportion-error1" class="text-danger" style="display: none;">测试比例范围【0,100】</span>
                            </div>
                        </div>
                    </div>
                    <div class="row" style="margin-top: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>备注</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="test-remark" class="form-control" name="remark"/>
                            </div>
                        </div>
                    </div>
                    <div class="row" style="margin-top: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right;padding: 0;">
                                <label>版本差异</label>
                            </div>
                            <div class="col-xs-10" id="testDiffContent"></div>
                        </div>
                    </div>
                `,
            buttons: {
                save: {
                    label: '发起',
                    className: 'btn-primary',
                    callback: function () {
                        const testPassVersion = (document.getElementById('test-pass-version') as HTMLSelectElement).value;
                        const testContrastVersion = (document.getElementById('test-contrast-version') as HTMLSelectElement).value;
                        const testProportion = (document.getElementById('test-proportion') as HTMLInputElement).value;
                        const startTime = (document.getElementById('startTime') as HTMLInputElement).value;
                        const endTime = (document.getElementById('endTime') as HTMLInputElement).value;
                        const title = (document.getElementById('test-title') as HTMLInputElement).value;
                        const remark = (document.getElementById('test-remark') as HTMLInputElement).value;

                        const testPassVersionError = document.getElementById('test-pass-version-error')!;
                        const testContrastVersionError = document.getElementById('test-contrast-version-error')!;
                        const testProportionError = document.getElementById('test-proportion-error')!;
                        const testProportionError1 = document.getElementById('test-proportion-error1')!;
                        const startTimeError = document.getElementById('start-time-error')!;
                        const endTimeError = document.getElementById('end-time-error')!;

                        testPassVersionError.style.display = testPassVersion ? 'none' : 'inline';
                        testContrastVersionError.style.display = (testContrastVersion || files.length === 1) ? 'none' : 'inline';
                        testProportionError.style.display = testProportion ? 'none' : 'inline';
                        testProportionError1.style.display = !testProportion || +testProportion < 0 || +testProportion > 100 || !Number.isInteger(+testProportion) ? 'inline' : 'none';
                        startTimeError.style.display = startTime ? 'none' : 'inline';
                        endTimeError.style.display = endTime ? 'none' : 'inline';
                        console.log((!testContrastVersion && files.length > 1))
                        if (!testProportion || !testPassVersion || (!testContrastVersion && files.length > 1) || !startTime || !endTime) {
                            return false;
                        }
                        if (!testProportion || +testProportion < 0 || +testProportion > 100 || !Number.isInteger(+testProportion)) {
                            return false;
                        }
                        const packageId = currentPackage.id
                        pushTest(project, testProportion, testPassVersion, testContrastVersion, startTime, endTime, title, remark, packageId)
                    }
                }
            }
        };
        window.bootbox.dialog(options);
        setTimeout(function () {
            (document.getElementById('test-pass-version') as HTMLSelectElement).onblur = function () {
                changeTestPassVersion(project)
            };
            (document.getElementById('test-contrast-version') as HTMLSelectElement).onblur = function () {
                changeTestPassVersion(project)
            };
        }, 1000)
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            window.bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text: string) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
}

// 发布测试 选择版本
function changeTestPassVersion(project: string): void {
    document.getElementById('testDiffContent')!.innerHTML = ''
    const testPassVersion = (document.getElementById('test-pass-version') as HTMLSelectElement).value;
    const testContrastVersion = (document.getElementById('test-contrast-version') as HTMLSelectElement).value;
    if (!testPassVersion || !testContrastVersion) return
    const diffUrl = window._server + '/packageeditor/getPackageDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            project,
            originVersion: testContrastVersion,
            targetVersion: testPassVersion
        }).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (diffRes: { data?: string }) {
        const diffData = diffRes.data || ''
        let diffContent = ''
        if (diffData) {
            diffContent = `
                <div>
                    <pre>${escapeHtml(diffData)}</pre>
                </div>
                `
        }
        document.getElementById('testDiffContent')!.innerHTML = diffContent
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            alert('权限不足，不能进行此操作.');
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

function pushTest(project: string, rate: string, targetVersion: string, originVersion: string, startTime: string, endTime: string, title: string, remark: string, packageId: string): void {
    fetch(window._server + '/packageeditor/refreshKnowledgeCache', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({project, rate, originVersion, targetVersion, startTime, endTime, title, remark, packageId}).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data: unknown) {
        window.bootbox.alert(data as string);
    }).catch(function () {
        alert('发布知识包失败！');
    });

}

function escapeHtml(unsafe: string): string {
    if (!unsafe) {
        return '';
    }
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

export function apply(project: string, packageConfig: PackageConfig, currentPackage: ResourcePackage): ApplyAction {
    return {project, type: APPLY, packageConfig, currentPackage};
}

// 发起审批
export function applyNewVersion(data: ResourcePackage[], project: string, packageConfig: PackageConfig, currentPackage: ResourcePackage): ApplyCompletedAction {
    const projectArray = project.split(':');
    const url = window._server + "/frame/fileVersions";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            path: '/' + project + '/___res__package__file__',
            project: project,
            packageId: currentPackage.id,
            page: '-1'
        }).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res: { files?: FileVersion[] }) {
        const files = res ? res.files || [] : [];
        let pushVersionOption = '<option value="">请选择</option>'
        let contrastVersionOption = '<option value="">请选择</option>'
        if(files.length === 1) {
            pushVersionOption += `<option value="${files[0].name}" selected>${files[0].name}</option>`
            contrastVersionOption += `<option value="${files[0].name}">${files[0].name}</option>`
        } else if (files.length > 1) {
            for (let i = 0; i < files.length; i++) {
                if (files[i].name) {
                    pushVersionOption += `<option value="${files[i].name}">${files[i].name}</option>`
                    if (files[i].name === currentPackage.version) {
                        // 默认：生产运行版本
                        contrastVersionOption += `<option value="${files[i].name}" selected>${files[i].name}</option>`
                    } else {
                        contrastVersionOption += `<option value="${files[i].name}">${files[i].name}</option>`
                    }
                }
            }
        }

        const options: Record<string, unknown> = {
            title: '发起审批',
            message: `
                    <form class='bootbox-form'>
                    <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>标题</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="version-title" class="form-control" name="remark"/>
                            </div>
                        </div>
                    </div>
                     <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>版本</label>
                            </div>
                            <div class="col-xs-10">
                                 <div class="row">
                                    <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">发布版本</label>
                                             <select id="pass-version" class="form-control" required style="flex: 1;">
                                                ${pushVersionOption}
                                             </select>
                                        </div>
                                        <span id="pass-version-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                     <div class="col-xs-6" style="display: flex;flex-flow: column;">
                                        <div style="flex: 1;display: flex;align-items: center;">
                                             <label style="margin-right: 5px;width: 65px;">比对版本</label>
                                             <select id="contrast-version" class="form-control" required style="flex: 1;">
                                                ${contrastVersionOption}
                                             </select>
                                        </div>
                                        <span id="contrast-version-error" class="text-danger" style="display: none;margin-left: 70px;">该字段为必填项</span>
                                    </div>
                                 </div>
                            </div>
                        </div>
                    </div>
                    <div class="row" style="margin-bottom: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>备注</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="version-remark" class="form-control" name="remark"/>
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="form-group">
                           <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>影响范围</label>
                            </div>
                            <div class="col-xs-10">
                                <div class="row">
                                    <div class="col-xs-6">
                                        <div class="row">
                                            <div class="col-xs-12" style="text-align: center;">通过率影响</div>
                                            <div class="col-xs-12 " style="display: flex;margin-bottom: 5px;flex-flow: column;">
                                                <div style="flex: 1;display: flex;align-items: center;">
                                                   <label style="margin-right: 5px;width: 65px;">效果:</label>
                                                   <select id="pass-rate-effect" class="form-control" required style="flex:1;">
                                                           <option value="">请选择</option>
                                                           <option value="提高">提高</option>
                                                           <option value="不变">不变</option>
                                                           <option value="降低">降低</option>
                                                   </select>
                                                </div>
                                                <div id="pass-rate-effect-error" class="text-danger" style="display: none;padding-left: 70px;">该字段为必填项</div>
                                             </div>
                                            <div class="col-xs-12" style="display: flex;flex-flow: column;">
                                                <div style="flex: 1;display: flex;align-items: center;">
                                                   <label style="margin-right: 5px;width: 65px;">预计幅度:</label>
                                                   <input id="pass-rate-range" class="form-control"
                                                          name="passRateRange" type="number" step="0.01"
                                                          style="flex: 1;"
                                                          required />
                                                </div>
                                                <div id="pass-rate-range-error" class="text-danger" style="display: none;padding-left: 70px;">该字段为必填项</div>
                                             </div>
                                        </div>
                                    </div>
                                    <div class="col-xs-6">
                                        <div class="row">
                                            <div class="col-xs-12" style="text-align: center;">坏账率影响</div>
                                            <div class="col-xs-12 " style="display: flex;margin-bottom: 5px;flex-flow: column;">
                                                <div style="flex: 1;display: flex;align-items: center;">
                                                   <label style="margin-right: 5px;width: 65px;">效果:</label>
                                                   <select id="bad-debt-rate-effect" class="form-control" required style="flex:1;">
                                                           <option value="">请选择</option>
                                                           <option value="提高">提高</option>
                                                           <option value="不变">不变</option>
                                                           <option value="降低">降低</option>
                                                   </select>
                                                </div>
                                                <div id="bad-debt-rate-effect-error" class="text-danger" style="display: none;padding-left: 70px;">该字段为必填项</div>
                                             </div>
                                            <div class="col-xs-12" style="display: flex;flex-flow: column;">
                                                <div style="flex: 1;display: flex;align-items: center;">
                                                   <label style="margin-right: 5px;width: 65px;">预计幅度:</label>
                                                   <input id="bad-debt-rate-range" class="form-control"
                                                          style="flex: 1;"
                                                          name="badDebtRateRange" type="number" step="0.01" required />
                                                </div>
                                                <div id="bad-debt-rate-range-error" class="text-danger" style="display: none;padding-left: 70px;">该字段为必填项</div>
                                             </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div>
                    <div class="row" style="margin-top: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right; padding: 0">
                                <label>附件</label>
                            </div>
                            <div class="col-xs-10">
                                <input id="version-file" name="file" style="width: 100%" type="file"/>
                            </div>
                        </div>
                    </div>
                    <div class="row" style="margin-top: 10px;">
                        <div class="form-group">
                            <div class="col-xs-2" style="text-align: right;padding: 0;">
                                <label>版本差异</label>
                            </div>
                            <div class="col-xs-10" id="diffContent"></div>
                        </div>
                    </div>
                </form>
                `,
            buttons: {
                save: {
                    label: '发起',
                    className: 'btn-primary',
                    callback: function () {

                        const passVersion = (document.getElementById('pass-version') as HTMLSelectElement).value;
                        const contrastVersion = (document.getElementById('contrast-version') as HTMLSelectElement).value;
                        const passRateEffect = (document.getElementById('pass-rate-effect') as HTMLSelectElement).value;
                        const passRateRange = (document.getElementById('pass-rate-range') as HTMLInputElement).value;
                        const badDebtRateEffect = (document.getElementById('bad-debt-rate-effect') as HTMLSelectElement).value;
                        const badDebtRateRange = (document.getElementById('bad-debt-rate-range') as HTMLInputElement).value;

                        const passVersionError = document.getElementById('pass-version-error')!;
                        const contrastVersionError = document.getElementById('contrast-version-error')!;
                        const passRateEffectError = document.getElementById('pass-rate-effect-error')!;
                        const passRateRangeError = document.getElementById('pass-rate-range-error')!;
                        const badDebtRateEffectError = document.getElementById('bad-debt-rate-effect-error')!;
                        const badDebtRateRangeError = document.getElementById('bad-debt-rate-range-error')!;

                        passVersionError.style.display = passVersion ? 'none' : 'inline';
                        contrastVersionError.style.display = (contrastVersion || files.length === 1) ? 'none' : 'inline';
                        passRateEffectError.style.display = passRateEffect ? 'none' : 'inline';
                        passRateRangeError.style.display = passRateRange ? 'none' : 'inline';
                        badDebtRateEffectError.style.display = badDebtRateEffect ? 'none' : 'inline';
                        badDebtRateRangeError.style.display = badDebtRateRange ? 'none' : 'inline';
                        if (!passVersion || (!contrastVersion && files.length>1) ||!passRateEffect || !passRateRange || !badDebtRateEffect || !badDebtRateRange) {
                            return false;
                        }
                        const formData = new FormData();
                        formData.append('project', projectArray[0]);
                        formData.append('version', typeof (projectArray[1]) === 'undefined' ? '' : projectArray[1]);
                        formData.append('remark', (document.getElementById('version-remark') as HTMLInputElement).value);
                        formData.append('title', (document.getElementById('version-title') as HTMLInputElement).value);
                        formData.append('file', (document.getElementById('version-file') as HTMLInputElement).files![0]);
                        formData.append('passRateEffect', passRateEffect);
                        formData.append('passRateRange', passRateRange);
                        formData.append('badDebtRateEffect', badDebtRateEffect);
                        formData.append('badDebtRateRange', badDebtRateRange);
                        formData.append('originVersion', contrastVersion);
                        formData.append('targetVersion', passVersion);
                        formData.append('packageId', currentPackage.id)

                        pushNewVersion(formData)
                    }
                }
            }
        };
        window.bootbox.dialog(options);
        setTimeout(function () {
            const passEl = document.getElementById('pass-version') as HTMLSelectElement;
            passEl.onblur = function (this: HTMLSelectElement) {
                console.log(this.value)
                changePassVersion(project)
            };
            const contrastEl = document.getElementById('contrast-version') as HTMLSelectElement;
            contrastEl.onblur = function (this: HTMLSelectElement) {
                console.log(this.value)
                changePassVersion(project)
            };
        }, 1000)
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            window.bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text: string) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
    return {type: APPLY_COMPLETED};
}

// 发起审批 选择版本
function changePassVersion(project: string): void {
    document.getElementById('diffContent')!.innerHTML = ''
    const passVersion = (document.getElementById('pass-version') as HTMLSelectElement).value;
    const contrastVersion = (document.getElementById('contrast-version') as HTMLSelectElement).value;
    if (!passVersion || !contrastVersion) return
    const diffUrl = window._server + '/packageeditor/getPackageDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            project,
            originVersion: contrastVersion,
            targetVersion: passVersion
        }).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (diffRes: { data?: string }) {
        const diffData = diffRes.data || ''
        let diffContent = ''
        if (diffData) {
            diffContent = `
                <div>
                    <pre>${escapeHtml(diffData)}</pre>
                </div>
                `
        }
        document.getElementById('diffContent')!.innerHTML = diffContent
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            alert('权限不足，不能进行此操作.');
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

function pushNewVersion(formData: FormData): void {
    const ce = window.parent.componentEvent;
    ce.eventEmitter.emit(ce.SHOW_LOADING);
    startApprovalProcess(formData, function (result: { status: boolean; message?: string }) {
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        window.bootbox.alert('发起审批成功');
    });
}

export function saveData(data: ResourcePackage[], newVersion: boolean, project: string, associatedFiles: string[] | undefined, versionComment: string | undefined, packageId: string | undefined, callback?: (() => void) | null): SaveCompletedAction {

    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<res-packages>';
    console.log('保存', data)
    data.forEach((p) => {
        xml += "<res-package id='" + p.id + "' name='" + p.name + "' create_date='" + formatDate(p.createDate, 'yyyy-MM-dd HH:mm:ss') + "'>";
        const resourceItems = p.resourceItems;
        resourceItems.forEach((item) => {
            xml += "<res-package-item  name='" + item.name + "' path='" + item.path + "' version='" + item.version + "'/>";
        });
        xml += '</res-package>';
    });
    xml += '</res-packages>';
    xml = encodeURIComponent(xml);

    const url = window._server + '/packageeditor/saveResourcePackages';
    let postData: Record<string, unknown> = {xml, project, newVersion};
    if(newVersion) {
        postData = {xml, project, newVersion, associatedFiles, versionComment, packageId};
    }
    console.log(postData)
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(postData)
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        window.bootbox.alert('保存成功!')
        if(callback){
            callback()
        }
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });

    return {type: SAVE_COMPLETED};
}

export function addMaster(data: ResourcePackage): AddMasterAction {
    return {type: ADD_MASTER, data};
}

export function updateMaster(data: { rowIndex: number; packageName: string }): UpdateMasterAction {
    return {type: UPDATE_MASTER, data};
}

export function deleteMaster(rowIndex: number): DelMasterAction {
    return {rowIndex, type: DEL_MASTER};
}

export function deleteSlave(rowIndex: number): DelSlaveAction {
    return {rowIndex, type: DEL_SLAVE};
}

export function addSlave(data: ResourceItem): AddSlaveAction {
    return {type: ADD_SLAVE, data};
}

export function updateSlave(data: ResourceItem & { rowIndex: number }): UpdateSlaveAction {
    return {type: UPDATE_SLAVE, data};
}

export function loadMasterData(project: string) {
    return function (dispatch: (action: unknown) => void) {
        const url = window._server + "/packageeditor/loadPackages";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(function(response: Response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: ResourcePackage[]) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data});
        }).catch(function () {
            alert("加载数据失败.");
        });
    }
}

export function loadPackageConfig(project: string) {
    return function (dispatch: (action: unknown) => void) {
        const url = window._server + "/packageeditor/loadPackageConfig";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(function(response: Response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: PackageConfig) {
            dispatch({type: LOAD_PACKAGE_CONFIG_COMPLETE, config: data});
        }).catch(function () {
            alert("加载数据失败.");
        });
    }
}

export function startApprovalProcess(formData: FormData, callback: (result: { status: boolean; message?: string }) => void): void {
    const url = window._server + "/common/startApprovalProcess";
    const ce = window.parent.componentEvent;

    fetch(url, {
        method: 'POST',
        body: formData
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: { status: boolean; message?: string }) {
        if (result.status) {
            callback(result);
        } else {
            ce.eventEmitter.emit(ce.HIDE_LOADING);
            window.bootbox.alert(result.message || '');
        }
    }).catch(function () {
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        window.bootbox.alert('发起审批流程失败!');
    });
}

export function loadSimulatorCategoryData(files: string, callback: (data: SimulatorCategory[]) => void): void {
    const url = window._server + "/packageeditor/loadForTestVariableCategories";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files}).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data: SimulatorCategory[]) {
        buildSimulatorVariableEditorType(data);
        callback(data);
    }).catch(function (response: Response) {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        if (response && response.text) {
            response.text().then(function(text: string) {
                try {
                    const jsonResp = JSON.parse(text);
                    window.bootbox.alert("加载文件[" + files + "]失败原因:" + jsonResp.message);
                } catch(e) {
                    window.bootbox.alert("加载文件[" + files + "]失败.");
                }
            });
        } else {
            window.bootbox.alert("加载文件[" + files + "]失败.");
        }
    });
}

export function loadFlows(files: string, callback: (data: FlowInfo[]) => void): void {
    const url = window._server + "/packageeditor/loadFlows";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files}).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data: FlowInfo[]) {
        callback(data);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('加载决策流信息失败.');
    });
}

export function loadSlaveData(masterData: ResourcePackage): LoadSlaveCompleteAction {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}

export function doTest(data: Record<string, unknown>, callback: (result: Record<string, unknown>) => void): void {
    const url = window._server + "/packageeditor/doTest";
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: Record<string, unknown>) {
        callback(result);
    }).catch(function (response: Response) {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        if (response && response.text) {
            response.text().then(function(text: string) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
}

export function doBatchTest(data: Record<string, unknown>, callback: (result: Record<string, unknown>) => void): void {
    const url = window._server + '/packageeditor/doBatchTest';
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: Record<string, unknown>) {
        callback(result);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('批量测试操作失败.');
    });
}

/**
 * 上传 Excel 文件，返回 { sessionId, totalRows }
 * 替代原有的 iframe + form 提交方式
 */
export function importExcelData(files: string, file: File, callback: (result: ImportExcelResult) => void): void {
    const url = window._server + '/packageeditor/importExcelTemplate';
    const formData = new FormData();
    formData.append('file', file);
    formData.append('targetFiles', files);

    fetch(url, {
        method: 'POST',
        body: formData
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: ImportExcelResult) {
        callback(result);
    }).catch(function (response: Response) {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        if (response && response.text) {
            response.text().then(function(text: string) {
                window.bootbox.alert("<span style='color: red'>导入Excel失败：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>导入Excel失败</span>");
        }
    });
}

/**
 * 发起异步批量测试（使用 sessionId）
 */
export function startBatchTest(sessionId: string, params: { files: string | null; flowId?: string; project?: string; packageId?: string }, callback: (result: BatchTestProgress) => void): void {
    const url = window._server + '/packageeditor/doBatchTest';
    const data: Record<string, unknown> = {
        sessionId: sessionId,
        files: params.files
    };
    if (params.flowId) {
        data.flowId = params.flowId;
    }
    if (params.project) {
        data.project = params.project;
    }
    if (params.packageId) {
        data.packageId = params.packageId;
    }

    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: BatchTestProgress) {
        callback(result);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        window.bootbox.alert('批量测试操作失败.');
    });
}

/**
 * 轮询批量测试进度
 */
export function getBatchTestProgress(sessionId: string, callback: (result: BatchTestProgress) => void): void {
    const url = window._server + '/packageeditor/batchTestProgress?sessionId=' + sessionId;
    fetch(url, {
        method: 'GET'
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: BatchTestProgress) {
        callback(result);
    }).catch(function () {
        // 静默失败，下次轮询重试
    });
}

export function testFlow(data: Record<string, unknown>, callback: (result: Record<string, unknown>) => void): void {
    const url = window._server + "/packageeditor/doTest";
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result: Record<string, unknown>) {
        callback(result);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('仿真测试操作失败!');
    });
}

export function exportExcelTemplate(files: string): void {
    const url = window._server + "/packageeditor/exportExcelTemplate";
    window.open(url, '_self');
}

export function buildSimulatorVariableEditorType(data: SimulatorCategory[]): void {
    data.forEach((category) => {
        const variables = category.variables;
        if (variables) {
            variables.forEach((v) => {
                if (v.type === 'Integer' || v.type === 'Double' || v.type === 'Long' || v.type === 'Float' || v.type === 'BigDecimal') {
                    v._editorType = 'number';
                } else if (v.type === 'Boolean') {
                    v._editorType = 'boolean';
                } else if (v.type === 'Date') {
                    v._editorType = 'date';
                } else if (v.type === 'List' || v.type === 'Set') {
                    v._editorType = 'list';
                } else {
                    v._editorType = 'string';
                }
            });
        }
    });
}

export function getPackageDiffList(project: string, path: string, callback: (list: DiffItem[]) => void): void {
    const diffUrl = window._server + '/packageeditor/getPackageDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({project, path}).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res: { data?: DiffItem[] }) {
        callback(res.data || []);
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

// 获取文件版本差异
export function getFileDiff(data: Record<string, string>): void {
    const diffUrl = window._server + '/packageeditor/getFileDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(data).toString()
    }).then(function(response: Response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res: { status?: boolean; data?: string }) {
        if(res.status) {
            const diffRes = res.data || ''
            const diffContent = `
                <div>
                    <pre>${escapeHtml(diffRes)}</pre>
                </div>
                `
            const options: Record<string, unknown> = {
                title: '差异',
                message: `
                    <form class='bootbox-form'>
                        <div style="margin-bottom: 15px;">
                        ${diffContent}
                        </div>
                    </form>
                `
            };
            window.bootbox.dialog(options);
        }
    }).catch(function (response: Response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}
