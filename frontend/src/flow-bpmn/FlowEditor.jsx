import {Component, createRef} from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import RuleForgePaletteModule from './palette';
import RuleForgePropertiesPanel from './properties/RuleForgePropertiesPanel';
import ruleforgeModdle from './moddle/ruleforge.json';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import './palette/ruleforge-palette.css';

export default class FlowEditor extends Component {
    containerRef = createRef();
    modeler = null;

    componentDidMount() {
        this.modeler = new BpmnModeler({
            container: this.containerRef.current,
            keyboard: {bindTo: document},
            additionalModules: [RuleForgePaletteModule],
            moddleExtensions: {
                ruleforge: ruleforgeModdle
            }
        });

        if (this.props.xml) {
            this.loadXML(this.props.xml);
        } else {
            this.createNewDiagram();
        }
    }

    componentDidUpdate(prevProps) {
        if (this.props.xml && this.props.xml !== prevProps.xml) {
            this.loadXML(this.props.xml);
        }
    }

    async createNewDiagram() {
        try {
            await this.modeler.createDiagram();
            if (this.props.onReady) this.props.onReady(this);
        } catch (err) {
            console.error('Error creating diagram:', err);
        }
    }

    async loadXML(xml) {
        try {
            await this.modeler.importXML(xml);
            const canvas = this.modeler.get('canvas');
            canvas.zoom('fit-viewport');
            if (this.props.onReady) this.props.onReady(this);
        } catch (err) {
            console.error('Error importing XML:', err);
        }
    }

    async saveXML() {
        try {
            const {xml} = await this.modeler.saveXML({format: true});
            return xml;
        } catch (err) {
            console.error('Error saving XML:', err);
            return null;
        }
    }

    async saveSVG() {
        try {
            const {svg} = await this.modeler.saveSVG();
            return svg;
        } catch (err) {
            console.error('Error saving SVG:', err);
            return null;
        }
    }

    componentWillUnmount() {
        if (this.modeler) {
            this.modeler.destroy();
        }
    }

    render() {
        return (
            <div style={{width: '100%', height: '100%', minHeight: 500, position: 'relative'}}>
                <div ref={this.containerRef} style={{width: '100%', height: '100%'}}/>
                {this.modeler && (
                    <RuleForgePropertiesPanel
                        eventBus={this.modeler.get('eventBus')}
                        modeling={this.modeler.get('modeling')}
                    />
                )}
            </div>
        );
    }
}
