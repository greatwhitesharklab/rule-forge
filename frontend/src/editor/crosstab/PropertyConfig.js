/**
 * PropertyConfig - Wrapper around ruleforge.RuleProperty for property configuration.
 *
 * Provides a UI for adding rule properties like salience, effective date,
 * expiration date, enabled flag, and debug flag.
 *
 * Extracted from the crosstab webpack bundle (module 458).
 */

export default class PropertyConfig {
    /**
     * @param {jQuery} container - The container element to render into
     */
    constructor(container) {
        this.container = container;
        this.properties = [];
        this.init();
    }

    /**
     * Initialize the property configuration UI with an "Add Property" button
     * and its dropdown menu.
     */
    init() {
        const self = this;

        this.propertyContainer = $('<span>');
        this.propertyContainer.css({
            padding: '10px'
        });

        const addButton = $("<button type='button' class='rule-add-property btn btn-link'>添加属性</button>");
        this.container.append(addButton);
        this.container.append(this.propertyContainer);

        const onPropertyClick = function (menuItem) {
            const prop = new ruleforge.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.addProperty(prop);
        };

        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
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
            }]
        });

        addButton.click(function (e) {
            self.menu.show(e);
        });
    }

    /**
     * Initialize properties from server data.
     *
     * @param {Object} data - Rule data containing property values
     * @param {string} [data.salience] - Priority value
     * @param {boolean} [data.loop] - Loop flag
     * @param {boolean} [data.debug] - Debug flag
     * @param {string} [data.effectiveDate] - Effective date
     * @param {string} [data.expiresDate] - Expiration date
     * @param {boolean} [data.enabled] - Enabled flag
     */
    initData(data) {
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
     * @param {RuleProperty} prop - The property to add
     */
    addProperty(prop) {
        this.propertyContainer.append(prop.getContainer());
        this.properties.push(prop);
        window._setDirty();
    }

    /**
     * Serialize all properties to XML attributes.
     * @returns {string} XML attribute string
     */
    toXml() {
        let xml = '';
        for (let i = 0; i < this.properties.length; i++) {
            xml += ' ' + this.properties[i].toXml();
        }
        return xml;
    }
}
