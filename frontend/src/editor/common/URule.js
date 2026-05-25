/* bootbox is a global */

window._ConstantValueArray = [];
window._ActionTypeArray = [];
window._VariableValueArray = [];
window._ParameterValueArray = [];
window._FunctionValueArray = [];
window.actionLibraries = [];
window.variableLibraries = [];
window.constantLibraries = [];
window.parameterLibraries = [];
window.ruleforge = {};

window.generateContainer = function () {
    var container = document.createElement("span");
    container.textContent = ".";
    container.style.cssText = "height:20px;cursor:pointer;margin:0px;color:white;border:dashed transparent 1px;";
    container.addEventListener("mouseover", function () {
        container.style.border = "dashed gray 1px";
    });
    container.addEventListener("mouseout", function () {
        container.style.border = "dashed transparent 1px";
    });
    return container;
};

window.refreshParameterLibraries = function () {
    var parameterFiles = "";
    for (var i = 0; i < parameterLibraries.length; i++) {
        var parameter = parameterLibraries[i];
        if (i == 0) {
            parameterFiles = parameter;
        } else {
            parameterFiles += ";" + parameter;
        }
    }
    if (parameterFiles == "" || parameterFiles.length < 2) {
        return;
    }
    var url = window._server + '/common/loadXml';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: parameterFiles}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window._ruleforgeEditorParameterLibraries = data;
        window._ParameterValueArray.forEach(function (item) {
            item.initMenu(data);
        });
    }).catch(function () {
        window.bootbox.alert("加载文件失败！");
    });
};

window.refreshVariableLibraries = function () {
    var variableFiles = "";
    for (var i = 0; i < variableLibraries.length; i++) {
        var variable = variableLibraries[i];
        if (i == 0) {
            variableFiles = variable;
        } else {
            variableFiles += ";" + variable;
        }
    }
    if (variableFiles == "" || variableFiles.length < 2) {
        return;
    }
    var url = window._server + '/common/loadXml';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: variableFiles}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window._ruleforgeEditorVariableLibraries = data;
        window._VariableValueArray.forEach(function (item) {
            item.initMenu(data);
        });
    }).catch(function () {
        window.bootbox.alert("加载文件失败！");
    });
};
window.refreshActionLibraries = function () {
    var actionFiles = "";
    for (var i = 0; i < actionLibraries.length; i++) {
        var action = actionLibraries[i];
        if (i == 0) {
            actionFiles = action;
        } else {
            actionFiles += ";" + action;
        }
    }
    if (actionFiles == "" || actionFiles.length < 2) {
        actionFiles = "builtinactions";
    }
    var url = window._server + '/common/loadXml';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: actionFiles}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window._ruleforgeEditorActionLibraries = data;
        window._ActionTypeArray.forEach(function (item) {
            item.initMenu(data);
        });
    }).catch(function () {
        window.bootbox.alert("加载文件失败！");
    });
};
window.refreshFunctionLibraries = function () {
    var url = window._server + '/common/loadFunctions';
    fetch(url).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window._ruleforgeEditorFunctionLibraries = data;
        window._FunctionValueArray.forEach(function (item) {
            item.initMenu(data);
        });
    }).catch(function () {
        window.bootbox.alert("加载函数失败！");
    });
};

window.refreshConstantLibraries = function () {
    var constantFiles = "";
    for (var i = 0; i < constantLibraries.length; i++) {
        var constant = constantLibraries[i];
        if (i == 0) {
            constantFiles = constant;
        } else {
            constantFiles += ";" + constant;
        }
    }
    if (constantFiles == "" || constantFiles.length < 2) {
        return;
    }
    var url = window._server + '/common/loadXml';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: constantFiles}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        window._ruleforgeEditorConstantLibraries = data;
        window._ConstantValueArray.forEach(function (item) {
            item.initMenu(data);
        });
    }).catch(function () {
        window.bootbox.alert("加载文件失败！");
    });
};

