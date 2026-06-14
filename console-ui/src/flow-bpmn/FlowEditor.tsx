import {Component, createRef} from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import RuleForgePaletteModule from './palette';
import RuleForgeRendererModule from './render';
import RuleForgePropertiesPanel from './properties/RuleForgePropertiesPanel';
import ruleforgeModdle from './moddle/ruleforge.json';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import './palette/ruleforge-palette.css';
import './flow-theme.css';

/**
 * Dirty 通知接口(由 {@link EditorRoute} 传入)。FlowEditor 在 bpmn-js
 * {@code commandStack.changed} 事件里调用 {@code setDirty} 标记编辑器有
 * 未保存改动;{@code loadXML} / {@code createNewDiagram} 完成后调 {@code clearDirty}
 * (新加载 = 未保存状态)。
 *
 * <p>可省略 — 省略时 FlowEditor 不通知 dirty(读侧静默 no-op,保持向后兼容)。
 */
interface DirtyApi {
    setDirty: () => void;
    clearDirty: () => void;
}

interface FlowEditorProps {
    xml?: string;
    onReady?: (editor: FlowEditor) => void;
    dirtyApi?: DirtyApi;
}

interface FlowEditorState {
    modelerReady: boolean;
}

export default class FlowEditor extends Component<FlowEditorProps, FlowEditorState> {
    containerRef = createRef<HTMLDivElement>();
    private modeler: any;
    state: FlowEditorState = {modelerReady: false};

    componentDidMount() {
        this.modeler = new BpmnModeler({
            container: this.containerRef.current,
            keyboard: {bindTo: document},
            additionalModules: [RuleForgePaletteModule, RuleForgeRendererModule],
            moddleExtensions: {
                ruleforge: ruleforgeModdle
            }
        });

        // Subscribe to bpmn-js commandStack — any user action (move element, edit property,
        // add shape, etc.) fires this event. We forward to dirtyApi.setDirty() so the
        // parent EditorRoute can show the *保存 indicator.
        const eventBus = this.modeler.get('eventBus');
        eventBus.on('commandStack.changed', () => {
            if (this.props.dirtyApi) {
                this.props.dirtyApi.setDirty();
            }
        });

        if (this.props.xml) {
            this.loadXML(this.props.xml);
        } else {
            this.createNewDiagram();
        }
    }

    componentDidUpdate(prevProps: FlowEditorProps) {
        if (this.props.xml && this.props.xml !== prevProps.xml) {
            this.loadXML(this.props.xml);
        }
    }

    async createNewDiagram() {
        try {
            await this.modeler.createDiagram();
            // Fresh diagram = no unsaved changes. clearDirty() if we have an api.
            if (this.props.dirtyApi) {
                this.props.dirtyApi.clearDirty();
            }
            this.setState({modelerReady: true});
            if (this.props.onReady) this.props.onReady(this);
        } catch (err) {
            console.error('Error creating diagram:', err);
        }
    }

    async loadXML(xml: string) {
        try {
            await this.modeler.importXML(xml);
            const canvas = this.modeler.get('canvas');
            canvas.zoom('fit-viewport');
            // Newly loaded file = no unsaved changes. clearDirty() if we have an api.
            if (this.props.dirtyApi) {
                this.props.dirtyApi.clearDirty();
            }
            this.setState({modelerReady: true});
            if (this.props.onReady) this.props.onReady(this);
        } catch (err) {
            console.error('Error importing XML:', err);
        }
    }

    async saveXML(): Promise<string | null> {
        try {
            const {xml} = await this.modeler.saveXML({format: true});
            return xml;
        } catch (err) {
            console.error('Error saving XML:', err);
            return null;
        }
    }

    async saveSVG(): Promise<string | null> {
        try {
            const {svg} = await this.modeler.saveSVG();
            return svg;
        } catch (err) {
            console.error('Error saving SVG:', err);
            return null;
        }
    }

    addImport(type: string, path: string) {
        const canvas = this.modeler.get('canvas');
        const rootElement = canvas.getRootElement();
        const bo = rootElement.businessObject;
        const modeling = this.modeler.get('modeling');

        let imports: Array<{type: string; path: string}> = [];
        try {
            const raw = bo.imports || bo.$attrs['ruleforge:imports'] || '';
            if (typeof raw === 'string') {
                imports = raw ? JSON.parse(raw) : [];
            } else if (Array.isArray(raw)) {
                imports = raw;
            }
        } catch (_e) { /* ignore */ }

        if (!imports.find(imp => imp.type === type && imp.path === path)) {
            imports.push({type, path});
            modeling.updateProperties(rootElement, {'ruleforge:imports': JSON.stringify(imports)});
        }
    }

    getImports(): any[] {
        try {
            const canvas = this.modeler.get('canvas');
            const rootElement = canvas.getRootElement();
            const bo = rootElement.businessObject;
            const raw = bo.$attrs['ruleforge:imports'] || '';
            return raw ? JSON.parse(raw) : [];
        } catch (_e) {
            return [];
        }
    }

    componentWillUnmount() {
        if (this.modeler) {
            this.modeler.destroy();
        }
    }

    render() {
        const modeler = this.modeler;
        const services = (modeler && this.state.modelerReady) ? {
            eventBus: modeler.get('eventBus'),
            modeling: modeler.get('modeling'),
            elementRegistry: modeler.get('elementRegistry'),
            commandStack: modeler.get('commandStack'),
            moddle: modeler.get('moddle'),
            canvas: modeler.get('canvas')
        } : {};

        return (
            <div style={{width: '100%', height: '100%', minHeight: 500, position: 'relative'}}>
                <div ref={this.containerRef} style={{width: '100%', height: '100%'}}/>
                {this.state.modelerReady && modeler && (
                    <RuleForgePropertiesPanel {...services} />
                )}
            </div>
        );
    }
}