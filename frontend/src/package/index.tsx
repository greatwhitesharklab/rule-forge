import '../bootbox.js';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/tailwind-base.css';
import { createRoot } from 'react-dom/client';
import {applyMiddleware, createStore, Store} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import PackageEditor from './components/PackageEditor.jsx';
import * as action from './action.js';

document.addEventListener('DOMContentLoaded', function () {
    const store: Store = createStore(reducer, applyMiddleware(thunk));
    const project = _getParameter("file").replace('.rp', '');
    (store.dispatch as Function)(action.loadMasterData(project));
    (store.dispatch as Function)(action.loadPackageConfig(project));
    createRoot(document.getElementById("container")!).render(
        <Provider store={store}>
            <PackageEditor project={project}/>
        </Provider>,
    );
});

function _getParameter(name: string): string {
    const reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    const r = window.location.search.substr(1).match(reg);
    if (r != null) return unescape(r[2]);
    return null;
}
