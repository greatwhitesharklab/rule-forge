window.iframe_id_ = 1;

export function nextIFrameId() {
    window.iframe_id_++;
    return '_iframe' + window.iframe_id_;
}

export function getParameter(name) {
    var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    var r = window.location.search.substr(1).match(reg);
    if (r != null) return r[2];
    return null;
}

export function buildProjectNameFromFile(file) {
    if (file.startsWith('/')) {
        file = file.substring(1);
        const pos = file.indexOf("/");
        return file.substring(0, pos);
    }
}

export function handleResponseError(response, prefix) {
    if (response.status === 401) {
        window.bootbox.alert("权限不足，不能进行此操作.");
    } else if (response.text) {
        return response.text().then(function (text) {
            var msg = text ? (prefix || "服务端错误：") + text : (prefix || "服务端出错");
            window.bootbox.alert("<span style='color: red'>" + msg + "</span>");
        });
    } else {
        window.bootbox.alert("<span style='color: red'>" + (prefix || "服务端出错") + "</span>");
    }
}

export function ajaxSave(url, parameters, callback) {
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(parameters).toString()
    }).then(function (response) {
        if (!response.ok) {
            handleResponseError(response);
            return;
        }
        return response.json();
    }).then(function (result) {
        if (!result) return;
        if (result.status) {
            callback(result);
        } else {
            window.bootbox.alert(result.message || '保存失败');
        }
    }).catch(function (err) {
        window.bootbox.alert("<span style='color: red'>服务端出错</span>");
    });
}

export function formatDate(date, format) {
    if (typeof date === 'number') {
        date = new Date(date);
    }
    if (typeof date === 'string') {
        return date;
    }
    var o = {
        "M+": date.getMonth() + 1,
        "d+": date.getDate(),
        "H+": date.getHours(),
        "m+": date.getMinutes(),
        "s+": date.getSeconds()
    };
    if (/(y+)/.test(format))
        format = format.replace(RegExp.$1, (date.getFullYear() + "").substr(4 - RegExp.$1.length));
    for (var k in o)
        if (new RegExp("(" + k + ")").test(format))
            format = format.replace(RegExp.$1, (RegExp.$1.length === 1) ? (o[k]) : (("00" + o[k]).substr(("" + o[k]).length)));
    return format;
}

export function saveNewVersion(url, postData, cb) {
    fetch(window._server + '/common/checkFileDirty', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            filePath: postData.file,
            content: postData.content
        }).toString()
    }).then(function (response) {
        if (!response.ok) {
            handleResponseError(response);
            return;
        }
        return response.json();
    }).then(function (res) {
        if (!res) return;
        if (res.status) {
            if (res.data) {
                let decodedFileName = decodeURIComponent(postData.file);
                if (decodedFileName.includes('%')) {
                    decodedFileName = decodeURIComponent(decodedFileName);
                }
                bootbox.confirm(`是否对【${decodedFileName}】生成新版本？`, function (result) {
                    if (result) {
                        ajaxSave(url, postData, function () {
                            cb();
                        })
                    }
                })
            } else {
                window.bootbox.alert("与最新版本无差异，无需生成新版本.");
            }
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    }).catch(function () {
        window.bootbox.alert("<span style='color: red'>服务端出错</span>");
    });
}

export function loadLibraries(libraries) {
    if (!libraries) return;
    for (var i = 0; i < libraries.length; i++) {
        var lib = libraries[i];
        switch (lib.type) {
            case 'Constant': constantLibraries.push(lib.path); break;
            case 'Action': actionLibraries.push(lib.path); break;
            case 'Variable': variableLibraries.push(lib.path); break;
            case 'Parameter': parameterLibraries.push(lib.path); break;
        }
    }
    refreshActionLibraries();
    refreshConstantLibraries();
    refreshVariableLibraries();
    refreshParameterLibraries();
    refreshFunctionLibraries();
}

export function loadEditorData(file, extraParams) {
    var url = window._server + '/common/loadXml';
    var params = {files: file};
    if (extraParams) {
        Object.assign(params, extraParams);
    }
    return fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(params).toString()
    }).then(function (response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        var editorData = data[0];
        if (editorData.libraries) {
            loadLibraries(editorData.libraries);
        }
        return editorData;
    });
}
