import * as componentEvent from '../../components/componentEvent.js';
import { OPEN_CONFIG_LIBRARY_DIALOG } from '../../components/dialog/component/ConfigLibraryDialog.jsx';

export class ConfigParameterDialog {
    parent: any;

    constructor(parent: any) {
        this.parent = parent;
    }

    open(): void {
        componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, 'parameter');
    }
}

// Backward-compatible global registration
declare const ruleforge: any;
(ruleforge as any).ConfigParameterDialog = ConfigParameterDialog;
