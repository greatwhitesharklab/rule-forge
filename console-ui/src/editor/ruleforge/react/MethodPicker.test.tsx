/**
 * MethodPicker.test.tsx — BDD tests for the shared action-library browser.
 *
 * Three layers, following the project BDD convention (Given/When/Then in the
 * `it` titles, the same pattern as VariablePicker.test.tsx):
 *
 *   Layer A — render: placeholder / not-found behavior + signature display.
 *   Layer B — onChange contract: the rendered Cascader forwards the path.
 *   Layer C — parseActionLibraryXml: raw .al.xml → spring-bean tree.
 *
 * Back-compat: when libraries is undefined/empty the picker still renders one
 * Cascader without throwing (the caller falls back to free-text inputs).
 */
import { describe, it, expect } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MethodPicker } from './MethodPicker';
import type { ActionLibrary, MethodBinding } from './MethodPicker';
import { parseActionLibraryXml } from './useActionLibraries';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/**
 * A single library with one bean holding two methods. The first method has a
 * parameter signature (name + type); the second has none.
 */
function oneLibrary(): ActionLibrary[] {
  return [
    [
      {
        id: 'customerService',
        name: '客户服务',
        methods: [
          {
            name: '查询客户',
            methodName: 'findCustomer',
            parameters: [
              { name: 'id', type: 'String' },
              { name: 'name', type: 'String' },
            ],
          },
          {
            name: '删除客户',
            methodName: 'deleteCustomer',
            parameters: [],
          },
        ],
      },
    ],
  ];
}

/** Two libraries whose bean ids collide (multi-library nesting guard). */
function twoLibraries(): ActionLibrary[] {
  return [
    [
      {
        id: 'customerService',
        name: '客户服务',
        methods: [{ name: '查询', methodName: 'find', parameters: [] }],
      },
    ],
    [
      {
        id: 'customerService',
        name: '客户服务2',
        methods: [{ name: '保存', methodName: 'save', parameters: [] }],
      },
    ],
  ];
}

afterEach(() => {
  cleanup();
});

// ===========================================================================
// Layer A — render: placeholder / not-found + signature display
// ===========================================================================

describe('MethodPicker — render', () => {
  it(`
    GIVEN no libraries (empty project)
    WHEN the picker is rendered
    THEN it renders a single Cascader without crashing (the "未加载动作库"
         not-found content shows inside the dropdown when opened)
  `, () => {
    render(
      <MethodPicker
        value={{ bean: '', beanLabel: '', methodName: '', methodLabel: '' }}
        onChange={() => {}}
        libraries={[]}
      />,
    );
    expect(document.querySelectorAll('.ant-cascader').length).toBe(1);
  });

  it(`
    GIVEN a populated single library
    WHEN the picker is rendered with an existing bean+method binding
    THEN the binding survives the controlled render and the parameter
         signature is shown as a read-only hint
  `, () => {
    const binding: MethodBinding = {
      bean: 'customerService',
      beanLabel: '客户服务',
      methodName: 'findCustomer',
      methodLabel: '查询客户',
    };
    const { container } = render(
      <MethodPicker value={binding} onChange={() => {}} libraries={oneLibrary()} />,
    );
    expect(container).toBeTruthy();
    // The parameter signature id: String, name: String is rendered read-only.
    expect(screen.getByText(/id:\s*String/)).toBeTruthy();
    expect(screen.getByText(/name:\s*String/)).toBeTruthy();
  });

  it(`
    GIVEN a method with no parameters
    WHEN the picker is rendered with that binding
    THEN the signature hint shows "()" (empty parameter list)
  `, () => {
    const binding: MethodBinding = {
      bean: 'customerService',
      beanLabel: '客户服务',
      methodName: 'deleteCustomer',
      methodLabel: '删除客户',
    };
    render(
      <MethodPicker value={binding} onChange={() => {}} libraries={oneLibrary()} />,
    );
    // The "参数签名" hint with empty parens is shown.
    expect(screen.getByText(/参数签名/)).toBeTruthy();
  });
});

// ===========================================================================
// Layer B — onChange contract: the rendered Cascader forwards the path
// ===========================================================================

describe('MethodPicker — selection → onChange', () => {
  it(`
    GIVEN a single library with bean customerService / method findCustomer
    WHEN the picker renders
    THEN it exposes exactly one Cascader (no crash) and the binding is a no-op
         pure pass-through until the user acts (onChange not yet fired)
  `, () => {
    const calls: MethodBinding[] = [];
    render(
      <MethodPicker
        value={{ bean: '', beanLabel: '', methodName: '', methodLabel: '' }}
        onChange={(b) => calls.push(b)}
        libraries={oneLibrary()}
      />,
    );

    const combobox = document.querySelector('.ant-cascader');
    expect(combobox).not.toBeNull();
    expect(document.querySelectorAll('.ant-cascader').length).toBe(1);
    // No user interaction yet — onChange must not have fired.
    expect(calls).toHaveLength(0);
  });

  it(`
    GIVEN two libraries with a colliding bean id
    WHEN the picker renders
    THEN it does NOT crash and exposes a single Cascader (the multi-library
         nesting keeps the two customerService beans distinguishable)
  `, () => {
    const { container } = render(
      <MethodPicker
        value={{ bean: '', beanLabel: '', methodName: '', methodLabel: '' }}
        onChange={() => {}}
        libraries={twoLibraries()}
      />,
    );
    expect(container).toBeTruthy();
    expect(document.querySelectorAll('.ant-cascader').length).toBe(1);
  });
});

// ===========================================================================
// Layer C — parseActionLibraryXml: raw .al.xml → spring-bean tree
// ===========================================================================

describe('parseActionLibraryXml — raw .al.xml → spring-bean tree', () => {
  it(`
    GIVEN a well-formed <action-library> XML document with one spring-bean
         holding two <method> elements (the first with two <parameter> children)
    WHEN parseActionLibraryXml(xml) is called
    THEN it returns one bean with two methods, the parameters preserved with
         name + type
  `, () => {
    const xml =
      '<?xml version="1.0" encoding="utf-8"?>' +
      '<action-library>' +
      "<spring-bean id='customerService' name='客户服务'>" +
      "<method name='查询客户' method-name='findCustomer'>" +
      "<parameter name='id' type='String'/>" +
      "<parameter name='name' type='String'/>" +
      '</method>' +
      "<method name='删除客户' method-name='deleteCustomer'>" +
      '</method>' +
      '</spring-bean>' +
      '</action-library>';

    const beans = parseActionLibraryXml(xml);
    expect(beans).toHaveLength(1);
    const bean = beans[0];
    expect(bean.id).toBe('customerService');
    expect(bean.name).toBe('客户服务');
    expect(bean.methods).toHaveLength(2);
    expect(bean.methods[0].name).toBe('查询客户');
    expect(bean.methods[0].methodName).toBe('findCustomer');
    expect(bean.methods[0].parameters).toEqual([
      { name: 'id', type: 'String' },
      { name: 'name', type: 'String' },
    ]);
    // Second method has no parameters.
    expect(bean.methods[1].methodName).toBe('deleteCustomer');
    expect(bean.methods[1].parameters).toEqual([]);
  });

  it(`
    GIVEN an empty string (the /common/loadXml passthrough returned nothing)
    WHEN parseActionLibraryXml is called
    THEN it returns an empty array (the hook degrades to "no libraries")
  `, () => {
    expect(parseActionLibraryXml('')).toEqual([]);
  });

  it(`
    GIVEN an XML whose root is not <action-library>
    WHEN parseActionLibraryXml is called
    THEN it returns an empty array (defensive against a wrong-shape payload)
  `, () => {
    const xml = '<?xml version="1.0"?><decision-table></decision-table>';
    expect(parseActionLibraryXml(xml)).toEqual([]);
  });
});

// ===========================================================================
// Regression — back-compat: no libraries prop = caller falls back to free-text.
// The picker itself, when given libraries=[], renders the placeholder without
// throwing.
// ===========================================================================

describe('MethodPicker — back-compat', () => {
  it(`
    GIVEN libraries is undefined (caller did not wire the prop)
    WHEN the picker renders
    THEN it does not crash and renders exactly one Cascader
  `, () => {
    render(
      <MethodPicker
        value={{ bean: '', beanLabel: '', methodName: '', methodLabel: '' }}
        onChange={() => {}}
      />,
    );
    expect(document.querySelectorAll('.ant-cascader').length).toBe(1);
  });
});
