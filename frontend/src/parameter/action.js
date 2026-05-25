import {ajaxSave} from '../Utils.js';
export const DEL='del';
export const SAVE='save';
export const ADD='add';
export const LOAD_DATA_COMPLETED='load_data_completed';

export function add(){
    return {type:ADD};
};

export function loadData(files){
    return function (dispatch) {
        var url=window._server+"/xml";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({files}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type:LOAD_DATA_COMPLETED,data:data[0]});
        }).catch(function (response) {
            if (response && response.status === 401) {
                window.bootbox.alert("权限不足，不能进行此操作.");
            } else if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
            }
        });
    }
};

export function save(newVersion,file){
    return {newVersion,file,type:SAVE};
};

export function saveData(data,newVersion,file){
    let xml='<?xml version="1.0" encoding="utf-8"?>';
    xml+='<parameter-library>';
    let errorInfo='';
    data.forEach((item,index)=>{
        if(!item.name || item.name.length<1){
            errorInfo='参数名称不能为空.';
            return false;
        }
        if(!item.label || item.label.length<1){
            errorInfo='参数标题不能为空.';
            return false;
        }
        xml+="<parameter name='"+item.name+"' label='"+item.label+"' type='"+item.type+"' act='InOut'/>";
    });
    if(errorInfo.length>1){
        window.bootbox.alert(errorInfo+',不能保存！');
        return;
    }
    xml+='</parameter-library>';
    xml=encodeURIComponent(xml);
    let postData={content:xml,file,newVersion};
    const url=window._server+'/common/saveFile';
    if(newVersion){
        bootbox.prompt("请输入新版本描述.",function (versionComment) {
            if(!versionComment){
                return;
            }
            postData.versionComment=versionComment;
            ajaxSave(url,postData,function () {
                window.bootbox.alert('保存成功!');
            })
        });
    }else{
        ajaxSave(url,postData,function () {
            window.bootbox.alert('保存成功!');
        })
    }
};

export function del(rowIndex){
    return {rowIndex,type:DEL};
};

