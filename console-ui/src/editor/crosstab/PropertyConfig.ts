/**
 * PropertyConfig - Wrapper around ruleforge.RuleProperty for property configuration.
 *
 * Provides a UI for adding rule properties like salience, effective date,
 * expiration date, enabled flag, and debug flag.
 *
 * Extracted from the crosstab webpack bundle (module 458).
 */

declare const ruleforge: any;

interface PropertyMenuItem {
    label: string;
    name: string;
    defaultValue: string | boolean;
    editorType: number;
    onClick?: (menuItem: PropertyMenuItem) => void;
}

export default class PropertyConfig {
    container: HTMLElement;
    propertyContainer: HTMLSpanElement;
    properties: any[];
    menu: MenuInstance;

    /**
     * @param container - The container element to render into
     */
    constructor(container: HTMLElement) {
        this.container = container;
        this.properties = [];
        this.init();
    }

    /**
     * Initialize the property configuration UI with an "Add Property" button
     * and its dropdown menu.
     */
    init(): void {
        const self = this;

        const propertyContainer = document.createElement('span');
        propertyContainer.style.cssText = 'padding: 10px';
        this.propertyContainer = propertyContainer;

        const addButton = document.createElement('button');
        addButton.type = 'button';
        addButton.className = 'rule-add-property btn btn-link';
        addButton.textContent = '添加属性';
        this.container.appendChild(addButton);
        this.container.appendChild(propertyContainer);

        const onPropertyClick = function (menuItem: PropertyMenuItem) {
            const prop = new ruleforge.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.addProperty(prop);
        };

        const menuItems: PropertyMenuItem[] = [{
                label: '优先级',
                name: 'salience',
                defaultValue: '10',
                editorType: 1,
                onClick: onPropertyClick
            }, {
                label: '生效日期',
                name: 'effective-date',
                defaultValue: '',
                editorType: 2,
                onClick: onPropertyClick
            }, {
                label: '失效日期',
                name: 'expires-date',
                defaultValue: '',
                editorType: 2,
                onClick: onPropertyClick
            }, {
                label: '是否启用',
                name: 'enabled',
                defaultValue: true,
                editorType: 3,
                onClick: onPropertyClick
            }, {
                label: '允许调试信息输出',
                name: 'debug',
                defaultValue: true,
                editorType: 3,
                onClick: onPropertyClick
            }];

        self.menu = new RuleForge.menu.Menu({menuItems: menuItems as any});

        addButton.addEventListener('click', function (e: MouseEvent) {
            self.menu.show(e);
        });
    }

    /**
     * Initialize properties from server data.
     *
     * @param data - Rule data containing property values
     */
    initData(data: any): void {
        const salience = data.salience;
        if (salience) {
            this.addProperty(new ruleforge.RuleProperty(this, 'salience', salience, 1));
        }

        const loop = data.loop;
        if (loop != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'loop', loop, 3));
        }

        const debug = data.debug;
        if (debug != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'debug', debug, 3));
        }

        const effectiveDate = data.effectiveDate;
        if (effectiveDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'effective-date', effectiveDate, 2));
        }

        const expiresDate = data.expiresDate;
        if (expiresDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'expires-date', expiresDate, 2));
        }

        const enabled = data.enabled;
        if (enabled != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'enabled', enabled, 3));
        }
    }

    /**
     * Add a property to the configuration.
     *
     * @param prop - The property to add
     */
    addProperty(prop: any): void {
        this.propertyContainer.appendChild(prop.getContainer());
        this.properties.push(prop);
        window._setDirty?.();
    }

    /**
     * Serialize all properties to XML attributes.
     * @returns XML attribute string
     */
    toXml(): string {
        let xml = '';
        for (let i = 0; i < this.properties.length; i++) {
            xml += ' ' + this.properties[i].toXml();
        }
        return xml;
    }
}
