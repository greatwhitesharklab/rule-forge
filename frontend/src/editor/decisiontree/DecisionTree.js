import {handleResponseError} from '../../Utils.js';

DecisionTree = function (container) {
    this.container = container;
    this.topNode = new VariableTreeNode();
    this.initToolbar();
    var content = document.createElement("div");
    content.style.textAlign = "center";
    container.appendChild(content);
    content.appendChild(this.topNode.container);
};

DecisionTree.prototype.initToolbar = function () {
    var file = _getRequestParameter("file");
    var version = _getRequestParameter("version") || "";
    if (!file || file.length < 1) {
        RuleForge.alert("未指定具体的决策树文件！");
        return;
    }

    var saveButton = '<div class="btn-group navbar-btn" style="margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
        '<button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="icon-save"/> 保存</button>' +
        '<button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn" style="display: none;"><i class="icon-save"/> 生成版本</button>' +
        '</div>';
    var toolbarHtml = '<nav class="navbar navbar-default" style="margin: 5px">' +
        '<div>' +
        '<div>' +
        '<div class="btn-group navbar-btn" style="margin-left:5px;margin-top:0px;margin-bottom: 0px" role="group" aria-label="...">' +
        '<button id="configVarButton" type="button" class="btn btn-default"><i class="icon-tasks"></i> 导入变量库</button>' +
        '<button id="configConstantsButton" type="button" class="btn btn-default"><i class="icon-th-list"></i> 导入常量库</button>' +
        '<button id="configActionButton" type="button" class="btn btn-default"><i class="icon-bolt"></i> 导入动作库</button>' +
        '<button id="configParameterButton" type="button" class="btn btn-default"><i class="icon-th"></i> 导入参数库</button>' +
        '</div>' +
        saveButton +
        ' </div>' +
        '</div>' +
        '</nav>';
    var toolbar = document.createElement("div");
    toolbar.innerHTML = toolbarHtml;
    toolbar.style.display = "inline-block";
    var toolbarContainer = document.createElement("div");
    toolbarContainer.appendChild(toolbar.firstElementChild || toolbar);
    this.container.appendChild(toolbarContainer);
    var self = this;
    document.getElementById("configVarButton").addEventListener('click', function () {
        if (!self.configVarDialog) {
            self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
        }
        self.configVarDialog.open();
    });

    document.getElementById("configConstantsButton").addEventListener('click', function () {
        if (!self.configConstantDialog) {
            self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
        }
        self.configConstantDialog.open();
    });

    document.getElementById("configActionButton").addEventListener('click', function () {
        if (!self.configActionDialog) {
            self.configActionDialog = new ruleforge.ConfigActionDialog(self);
        }
        self.configActionDialog.open();
    });

    document.getElementById("configParameterButton").addEventListener('click', function () {
        if (!self.configParameterDialog) {
            self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
        }
        self.configParameterDialog.open();
    });

    document.getElementById("saveButton").addEventListener('click', function () {
        _save(false);
    });
    document.getElementById("saveButtonNewVersion").addEventListener('click', function () {
        _save(true);
    });
    document.getElementById("saveButton").classList.add("disabled");
    // document.getElementById("saveButtonNewVersion").classList.add("disabled");
    _loadDecisionTreeFileData();

    function _save(newVersion) {
        if (document.getElementById("saveButton").classList.contains("disabled")) {
            return false;
        }
        var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        xml += "<decision-tree>";
        parameterLibraries.forEach(function (item) {
            xml += "<import-parameter-library path=\"" + item + "\"/>";
        });
        variableLibraries.forEach(function (item) {
            xml += "<import-variable-library path=\"" + item + "\"/>";
        });
        constantLibraries.forEach(function (item) {
            xml += "<import-constant-library path=\"" + item + "\"/>";
        });
        actionLibraries.forEach(function (item) {
            xml += "<import-action-library path=\"" + item + "\"/>";
        });
        try {
            xml += self.topNode.toXml();
        } catch (error) {
            RuleForge.alert(error);
            return;
        }
        xml += "</decision-tree>";
        xml = encodeURI(xml);
        var url = (ruleforgeServer || "") + "ruleforge?action=savexml&file=" + file + "";
        var dialog = document.createElement("div");
        dialog.style.cssText = "width:100px;height:50px";
        dialog.textContent = "文件保存中...";
        dialog.style.position = 'fixed';
        dialog.style.top = '50%';
        dialog.style.left = '50%';
        dialog.style.transform = 'translate(-50%, -50%)';
        dialog.style.zIndex = '10000';
        dialog.style.background = '#fff';
        dialog.style.padding = '20px';
        dialog.style.borderRadius = '5px';
        dialog.style.boxShadow = '0 2px 10px rgba(0,0,0,0.3)';
        document.body.appendChild(dialog);
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({xml: xml, newVersion: newVersion}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            cancelDirty();
            dialog.remove();
        }).catch(function (response) {
            dialog.remove();
            handleResponseError(response, '保存失败：');
        });
    };

    function _loadDecisionTreeFileData() {
        var url = (ruleforgeServer || "") + "ruleforge?action=loadxml&files=" + file + "," + version + "";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
                var treeData = data[0];
                var libraries = treeData["libraries"];
                if (libraries) {
                    for (var i = 0; i < libraries.length; i++) {
                        var lib = libraries[i];
                        var type = lib["type"];
                        var path = lib["path"];
                        switch (type) {
                            case "Constant":
                                constantLibraries.push(path);
                                break;
                            case "Action":
                                actionLibraries.push(path);
                                break;
                            case "Variable":
                                variableLibraries.push(path);
                                break;
                            case "Parameter":
                                parameterLibraries.push(path);
                                break;
                        }
                    }
                }
                refreshActionLibraries();
                refreshConstantLibraries();
                refreshVariableLibraries();
                refreshParameterLibraries();
                refreshFunctionLibraries();
                self.topNode.initData(treeData["variableTreeNode"]);
                cancelDirty();
            }
        }).catch(function (response) {
            handleResponseError(response, '加载文件失败：');
        });
    };

};

function _getRequestParameter(name) {
    var value = null;
    var params = window.location.search.substring(1).split("&");
    for (var i = 0; i < params.length; i++) {
        var param = params[i];
        if (param.indexOf("=") == -1) {
            continue;
        }
        var pair = param.split("=");
        var key = pair[0];
        if (key == name) {
            value = pair[1];
            break;
        }
    }
    return value;
};

window._setDirty = function () {
    if (window._dirty) {
        return;
    }
    window._dirty = true;
    var saveBtn = document.getElementById("saveButton");
    saveBtn.innerHTML = "<i class='icon-save'></i> *保存";
    saveBtn.classList.remove("disabled");
};

function cancelDirty() {
    if (!window._dirty) {
        return;
    }
    window._dirty = false;
    var saveBtn = document.getElementById("saveButton");
    saveBtn.innerHTML = "<i class='icon-save'></i> 保存";
    saveBtn.classList.add("disabled");
};
