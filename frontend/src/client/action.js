import {handleResponseError} from '../Utils.js';

export const ADD='add';
export const DEL='del';
export const LOADED_DATA='loaded_data';

export function loadData(project) {
    return function (dispatch) {
        fetch(window._server+'/clientconfig/loadData?project='+project, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type:LOADED_DATA,data});
        }).catch(function (response) {
            handleResponseError(response, '服务端错误：');
        });
    };
};
export function save(data,project) {
    let xml="<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config>",error=null;
    for(let item of data){
        if(!item.name){
            error='客户端名不能为空';
            break;
        }
        if(!item.client){
            error='客户端地址不能为空';
            break;
        }
        xml+=`<item name="${item.name}" client="${item.client}"/>`;
    }
    if(error){
        bootbox.alert(error);
        return;
    }
    xml+="</client-config>";
    xml=encodeURIComponent(xml);
    fetch(window._server+'/clientconfig/save', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({project,content:xml}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        bootbox.alert('保存成功!');
    }).catch(function (response) {
        handleResponseError(response, '保存失败，服务端错误：');
    });
};
export function del(index) {
    return {type:DEL,index};
};
export function add() {
    return {type: ADD};
};

