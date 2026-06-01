export function createVariableCategory(overrides = {}) {
    return {
        name: 'TestCategory',
        type: 'Custom',
        clazz: 'com.example.Test',
        variables: [
            { name: 'testVar', label: '测试变量', type: 'String', defaultValue: '' },
        ],
        ...overrides,
    };
}

export function createConstantCategory(overrides = {}) {
    return {
        name: 'TestConstants',
        label: '测试常量',
        constants: [
            { name: 'TEST_CONST', label: '测试常量', type: 'String', value: 'test' },
        ],
        ...overrides,
    };
}

export function createActionCategory(overrides = {}) {
    return {
        name: 'TestActions',
        label: '测试动作',
        springBeans: [
            {
                id: 'testBean',
                name: 'TestBean',
                methods: [
                    { name: 'doSomething', methodName: 'doSomething', parameters: [] },
                ],
            },
        ],
        ...overrides,
    };
}

export function createPackageItem(overrides = {}) {
    return {
        id: 'test-package',
        name: '测试包',
        version: '1',
        items: [],
        ...overrides,
    };
}

export function createParameter(overrides = {}) {
    return {
        name: 'testParam',
        label: '测试参数',
        type: 'String',
        ...overrides,
    };
}
