import {ajaxSave, formatDate} from '../Utils.js';
import * as event from "../frame/event";

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


export function save(newVersion, project, associatedFiles, versionComment, packageId, callback) {
    return {newVersion, project, associatedFiles, versionComment, callback, packageId, type: SAVE};
}

// 发布测试
export function refreshKnowledgeCache(project,packageConfig, currentPackage) {
    console.log('发布测试', currentPackage)
    var url = window._server + "/frame/fileVersions";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            path: '/' + project + '/___res__package__file__',
            project: project,
            packageId: currentPackage.id,
            page: -1
        }).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        const files = res ? res.files || [] : []
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
        let options = {
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
                        const testPassVersion = document.getElementById('test-pass-version').value;
                        const testContrastVersion = document.getElementById('test-contrast-version').value;
                        const testProportion = document.getElementById('test-proportion').value;
                        const startTime = document.getElementById('startTime').value;
                        const endTime = document.getElementById('endTime').value;
                        const title = document.getElementById('test-title').value;
                        const remark = document.getElementById('test-remark').value;

                        const testPassVersionError = document.getElementById('test-pass-version-error');
                        const testContrastVersionError = document.getElementById('test-contrast-version-error');
                        const testProportionError = document.getElementById('test-proportion-error');
                        const testProportionError1 = document.getElementById('test-proportion-error1');
                        const startTimeError = document.getElementById('start-time-error');
                        const endTimeError = document.getElementById('end-time-error');

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
            document.getElementById('test-pass-version').onblur = function () {
                changeTestPassVersion(project)
            };
            document.getElementById('test-contrast-version').onblur = function () {
                changeTestPassVersion(project)
            };
        }, 1000)
    }).catch(function (response) {
        if (response && response.status === 401) {
            window.bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
}

// 发布测试 选择版本
function changeTestPassVersion(project) {
    document.getElementById('testDiffContent').innerHTML = ''
    const testPassVersion = document.getElementById('test-pass-version').value;
    const testContrastVersion = document.getElementById('test-contrast-version').value;
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
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (diffRes) {
        diffRes = diffRes.data || ''
        let diffContent = ''
        if (diffRes) {
            diffContent = `
                <div>
                    <pre>${escapeHtml(diffRes)}</pre>
                </div>
                `
        }
        document.getElementById('testDiffContent').innerHTML = diffContent
    }).catch(function (response) {
        if (response && response.status === 401) {
            alert('权限不足，不能进行此操作.');
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

function pushTest(project, rate, targetVersion, originVersion, startTime, endTime, title, remark, packageId) {
    fetch(window._server + '/packageeditor/refreshKnowledgeCache', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({project, rate, originVersion, targetVersion, startTime, endTime, title, remark, packageId}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window.bootbox.alert(data);
    }).catch(function () {
        alert('发布知识包失败！');
    });

}

function escapeHtml(unsafe) {
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

export function apply(project, packageConfig, currentPackage) {
    return {project, type: APPLY, packageConfig, currentPackage};
}

// 发起审批
export function applyNewVersion(data, project, packageConfig, currentPackage) {
    const projectArray = project.split(':');
    var url = window._server + "/frame/fileVersions";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            path: '/' + project + '/___res__package__file__',
            project: project,
            packageId: currentPackage.id,
            page: -1
        }).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        const files = res ? res.files || [] : []
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

        let options = {
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

                        const passVersion = document.getElementById('pass-version').value;
                        const contrastVersion = document.getElementById('contrast-version').value;
                        const passRateEffect = document.getElementById('pass-rate-effect').value;
                        const passRateRange = document.getElementById('pass-rate-range').value;
                        const badDebtRateEffect = document.getElementById('bad-debt-rate-effect').value;
                        const badDebtRateRange = document.getElementById('bad-debt-rate-range').value;

                        const passVersionError = document.getElementById('pass-version-error');
                        const contrastVersionError = document.getElementById('contrast-version-error');
                        const passRateEffectError = document.getElementById('pass-rate-effect-error');
                        const passRateRangeError = document.getElementById('pass-rate-range-error');
                        const badDebtRateEffectError = document.getElementById('bad-debt-rate-effect-error');
                        const badDebtRateRangeError = document.getElementById('bad-debt-rate-range-error');

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
                        formData.append('remark', document.getElementById('version-remark').value);
                        formData.append('title', document.getElementById('version-title').value);
                        formData.append('file', document.getElementById('version-file').files[0]);
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
            document.getElementById('pass-version').onblur = function () {
                console.log(this.value)
                changePassVersion(project)
            };
            document.getElementById('contrast-version').onblur = function () {
                console.log(this.value)
                changePassVersion(project)
            };
        }, 1000)
    }).catch(function (response) {
        if (response && response.status === 401) {
            window.bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
    return {type: APPLY_COMPLETED};
}

// 发起审批 选择版本
function changePassVersion(project) {
    document.getElementById('diffContent').innerHTML = ''
    const passVersion = document.getElementById('pass-version').value;
    const contrastVersion = document.getElementById('contrast-version').value;
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
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (diffRes) {
        diffRes = diffRes.data || ''
        let diffContent = ''
        if (diffRes) {
            diffContent = `
                <div>
                    <pre>${escapeHtml(diffRes)}</pre>
                </div>
                `
        }
        document.getElementById('diffContent').innerHTML = diffContent
    }).catch(function (response) {
        if (response && response.status === 401) {
            alert('权限不足，不能进行此操作.');
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

function pushNewVersion(formData) {
    const ce = window.parent.componentEvent;
    ce.eventEmitter.emit(ce.SHOW_LOADING);
    startApprovalProcess(formData, function (result) {
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        window.bootbox.alert('发起审批成功');
    });
}

export function saveData(data, newVersion, project, associatedFiles, versionComment, packageId, callback) {

    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<res-packages>';
    let errorInfo = '';
    console.log('保存', data)
    data.forEach((p, index) => {
        xml += "<res-package id='" + p.id + "' name='" + p.name + "' create_date='" + formatDate(p.createDate, 'yyyy-MM-dd HH:mm:ss') + "'>";
        var resourceItems = p.resourceItems;
        resourceItems.forEach((item, i) => {
            xml += "<res-package-item  name='" + item.name + "' path='" + item.path + "' version='" + item.version + "'/>";
        });
        xml += '</res-package>';
    });
    xml += '</res-packages>';
    xml = encodeURIComponent(xml);

    const url = window._server + '/packageeditor/saveResourcePackages';
    let postData = {xml, project, newVersion};
    if(newVersion) {
        postData = {xml, project, newVersion, associatedFiles, versionComment, packageId};
    }
    console.log(postData)
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(postData)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        window.bootbox.alert('保存成功!')
        if(callback){
            callback()
        }
    }).catch(function (response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });

    return {type: SAVE_COMPLETED};
}

export function addMaster(data) {
    return {type: ADD_MASTER, data};
}

export function updateMaster(data) {
    return {type: UPDATE_MASTER, data};
}

export function deleteMaster(rowIndex) {
    return {rowIndex, type: DEL_MASTER};
}

export function deleteSlave(rowIndex) {
    return {rowIndex, type: DEL_SLAVE};
}

export function addSlave(data) {
    return {type: ADD_SLAVE, data};
}

export function updateSlave(data) {
    return {type: UPDATE_SLAVE, data};
}

export function loadMasterData(project) {
    return function (dispatch) {
        var url = window._server + "/packageeditor/loadPackages";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data});
        }).catch(function () {
            alert("加载数据失败.");
        });
    }
}

export function loadPackageConfig(project) {
    return function (dispatch) {
        var url = window._server + "/packageeditor/loadPackageConfig";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type: LOAD_PACKAGE_CONFIG_COMPLETE, config: data});
        }).catch(function () {
            alert("加载数据失败.");
        });
    }
}

export function startApprovalProcess(formData, callback) {
    var url = window._server + "/common/startApprovalProcess";
    const ce = window.parent.componentEvent;

    fetch(url, {
        method: 'POST',
        body: formData
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result) {
        if (result.status) {
            callback(result);
        } else {
            ce.eventEmitter.emit(ce.HIDE_LOADING);
            window.bootbox.alert(result.message);
        }
    }).catch(function () {
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        window.bootbox.alert('发起审批流程失败!');
    });
}

export function loadSimulatorCategoryData(files, callback) {
    var url = window._server + "/packageeditor/loadForTestVariableCategories";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        buildSimulatorVariableEditorType(data);
        callback(data);
    }).catch(function (response) {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        if (response && response.text) {
            response.text().then(function(text) {
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

export function loadFlows(files, callback) {
    var url = window._server + "/packageeditor/loadFlows";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        callback(data);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('加载决策流信息失败.');
    });
}

export function loadSlaveData(masterData) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}

export function doTest(data, callback) {
    var url = window._server + "/packageeditor/doTest";
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result) {
        callback(result);
    }).catch(function (response) {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        if (response && response.text) {
            response.text().then(function(text) {
                window.bootbox.alert("<span style='color: red'>服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
}

export function doBatchTest(data, callback) {
    var url = window._server + '/packageeditor/doBatchTest';
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result) {
        callback(result);
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('批量测试操作失败.');
    });
}

export function testFlow(data, callback) {
    var url = window._server + "/packageeditor/doTest";
    fetch(url, {
        method: 'POST',
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(data)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result) {
        callback(result);
    }).catch(function () {
        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.HIDE_LOADING);
        alert('仿真测试操作失败!');
    });
}

export function exportExcelTemplate(files) {
    var url = window._server + "/packageeditor/exportExcelTemplate";
    window.open(url, '_self');
}

export function buildSimulatorVariableEditorType(data) {
    data.forEach((category, index) => {
        var variables = category.variables;
        if (variables) {
            variables.forEach((v, i) => {
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
export function getPackageDiffList(project, path, callback) {
    const diffUrl = window._server + '/packageeditor/getPackageDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({project, path}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        callback(res.data || []);
    }).catch(function (response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}

// 获取文件版本差异
export function getFileDiff(data) {
    const diffUrl = window._server + '/packageeditor/getFileDiff';
    fetch(diffUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(data).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        if(res.status) {
            const diffRes = res.data || ''
            const diffContent = `
                <div>
                    <pre>${escapeHtml(diffRes)}</pre>
                </div>
                `
            let options = {
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
    }).catch(function (response) {
        if (response && response.status === 401) {
            alert("权限不足，不能进行此操作.");
        } else {
            alert('服务端错误，操作失败!');
        }
    });
}
