import {formPost} from '../api/client.js';

import {alert} from '@/utils/modal';
export const ADD = 'add';
export const DEL = 'del';
export const LOADED_DATA = 'loaded_data';
export const LOAD_FAILED = 'load_failed';

export function loadData(project: string | null) {
    return function (dispatch: (a: unknown) => void) {
        // silent:失败不弹全局 alert,只 dispatch 失败态,由 EditorRoute gate
        // 渲染统一错误态(EditorLoadState)——避免 alert + 错误态双重提示。
        formPost('/clientconfig/loadData', {project: project || ''}, {
            silent: true,
        }).then(function (data) {
            dispatch({type: LOADED_DATA, data});
        }).catch(function (err: unknown) {
            dispatch({type: LOAD_FAILED, error: err});
        });
    };
}

export function save(data: Array<{ name?: string; client?: string }>, project: string | null) {
    let xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config>", error: string | null = null;
    for (let item of data) {
        if (!item.name) {
            error = '客户端名不能为空';
            break;
        }
        if (!item.client) {
            error = '客户端地址不能为空';
            break;
        }
        xml += `<item name="${item.name}" client="${item.client}"/>`;
    }
    if (error) {
        alert(error);
        return;
    }
    xml += "</client-config>";
    xml = encodeURIComponent(xml);
    formPost('/clientconfig/save', {project: project || '', content: xml}, {
        errorPrefix: '保存失败，服务端错误：',
    }).then(function () {
        alert('保存成功!');
    }).catch(function () {
        // error already handled by client
    });
}

export function del(index: number) {
    return {type: DEL, index};
}

export function add() {
    return {type: ADD};
}
