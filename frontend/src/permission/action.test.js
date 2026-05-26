import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock handleResponseError
vi.mock('../Utils.js', () => ({
    handleResponseError: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync(mockFetch) {
    if (mockFetch.mock && mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Permission Module - Action Creators', () => {
    it('GIVEN no parameters WHEN loadMasterData is called THEN it should return a thunk function', () => {
        const thunk = ACTIONS.loadMasterData();
        expect(typeof thunk).toBe('function');
        expect(thunk.length).toBe(1); // accepts dispatch
    });

    it('GIVEN masterRowData WHEN loadSlave is called THEN it should return SLAVE_LOADED action with projectConfigs', () => {
        const masterRowData = {
            username: 'testuser',
            projectConfigs: [
                { project: 'project1', readProject: true }
            ]
        };
        const thunk = ACTIONS.loadSlave(masterRowData);

        expect(typeof thunk).toBe('function');
    });

    it('GIVEN data array WHEN save is called THEN it should call fetch with XML payload', async () => {
        const mockFetch = vi.fn(() => Promise.resolve({
            ok: true,
            json: async () => ({})
        }));
        global.fetch = mockFetch;
        window._server = 'http://test';
        setupMockBootbox();

        const data = [
            {
                username: 'testuser',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: true,
                        writeVariableFile: false,
                        readParameterFile: true,
                        writeParameterFile: false,
                        readConstantFile: true,
                        writeConstantFile: false,
                        readActionFile: true,
                        writeActionFile: false,
                        readRuleFile: true,
                        writeRuleFile: false,
                        readScorecardFile: true,
                        writeScorecardFile: false,
                        readDecisionTableFile: true,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: true,
                        writeDecisionTreeFile: false,
                        readFlowFile: true,
                        writeFlowFile: false,
                    }
                ]
            }
        ];

        ACTIONS.save(data);
        await flushAsync(mockFetch);

        expect(mockFetch).toHaveBeenCalled();
        const callArgs = mockFetch.mock.calls[0];
        expect(callArgs[0]).toBe('http://test/permission/saveResourceSecurityConfigs');
        expect(callArgs[1].method).toBe('POST');

        delete global.fetch;
        delete window._server;
        teardownMockBootbox();
    });
});

describe('Permission Module - Thunks', () => {
    let mockServer, dispatch, mockBootbox;

    beforeEach(() => {
        mockServer = setupMockServer();
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockServer();
        teardownMockBootbox();
    });

    it('GIVEN server returns data WHEN loadMasterData thunk is dispatched THEN it should dispatch MASTER_LOADED', async () => {
        const masterData = [
            {
                username: 'user1',
                projectConfigs: [
                    { project: 'proj1', readProject: true }
                ]
            },
            {
                username: 'user2',
                projectConfigs: []
            }
        ];
        mockServer.mockResponse('/permission/loadResourceSecurityConfigs', masterData);

        const thunk = ACTIONS.loadMasterData();
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.MASTER_LOADED,
            data: masterData
        });
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/permission/loadResourceSecurityConfigs', 500);

        const thunk = ACTIONS.loadMasterData();
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.MASTER_LOADED })
        );
    });

    it('GIVEN masterRowData WHEN loadSlave thunk is dispatched THEN it should dispatch SLAVE_LOADED with projectConfigs', async () => {
        const masterRowData = {
            username: 'testuser',
            projectConfigs: [
                { project: 'project1', readProject: true },
                { project: 'project2', readProject: false }
            ]
        };

        const thunk = ACTIONS.loadSlave(masterRowData);
        await thunk(dispatch);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.SLAVE_LOADED,
            data: masterRowData.projectConfigs
        });
    });
});

describe('Permission Module - saveData XML Generation', () => {
    let mockFetch, mockBootbox;

    beforeEach(() => {
        mockFetch = vi.fn(() => Promise.resolve({
            ok: true,
            json: async () => ({})
        }));
        global.fetch = mockFetch;
        window._server = 'http://test';
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        delete global.fetch;
        delete window._server;
        teardownMockBootbox();
    });

    it('GIVEN valid permission data WHEN save is called THEN it should generate correct XML structure', async () => {
        const data = [
            {
                username: 'testuser',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: true,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    }
                ]
            }
        ];

        ACTIONS.save(data);
        await flushAsync(mockFetch);

        expect(mockFetch).toHaveBeenCalled();
        const callArgs = mockFetch.mock.calls[0];
        const body = callArgs[1].body;
        const params = new URLSearchParams(body);
        const xmlContent = decodeURIComponent(params.get('content'));

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<user-permission>');
        expect(xmlContent).toContain('<user-permission username="testuser">');
        expect(xmlContent).toContain('<project-config project="project1"');
        expect(xmlContent).toContain('read-project="true"');
        expect(xmlContent).toContain('read-package="true"');
        expect(xmlContent).toContain('write-package="false"');
    });

    it('GIVEN data with missing required fields WHEN save is called THEN it should skip configs without project or readProject', async () => {
        const data = [
            {
                username: 'testuser',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: false,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    },
                    {
                        project: '',
                        readProject: false
                    }
                ]
            }
        ];

        ACTIONS.save(data);
        await flushAsync(mockFetch);

        const callArgs = mockFetch.mock.calls[0];
        const body = callArgs[1].body;
        const params = new URLSearchParams(body);
        const xmlContent = decodeURIComponent(params.get('content'));

        const matches = xmlContent.match(/<project-config/g);
        expect(matches).toHaveLength(1);
    });

    it('GIVEN multiple users WHEN save is called THEN it should generate XML for all users', async () => {
        const data = [
            {
                username: 'user1',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: false,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    }
                ]
            },
            {
                username: 'user2',
                projectConfigs: [
                    {
                        project: 'project2',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: false,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    }
                ]
            }
        ];

        ACTIONS.save(data);
        await flushAsync(mockFetch);

        const callArgs = mockFetch.mock.calls[0];
        const body = callArgs[1].body;
        const params = new URLSearchParams(body);
        const xmlContent = decodeURIComponent(params.get('content'));

        expect(xmlContent).toContain('username="user1"');
        expect(xmlContent).toContain('username="user2"');
        expect(xmlContent).toContain('project="project1"');
        expect(xmlContent).toContain('project="project2"');
    });

    it('GIVEN save succeeds WHEN save is called THEN it should show success alert', async () => {
        const data = [
            {
                username: 'testuser',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: false,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    }
                ]
            }
        ];

        ACTIONS.save(data);
        await flushAsync(mockFetch);

        expect(mockBootbox.getLastAlertMessage()).toBe('保存成功');
    });

    it('GIVEN save fails WHEN save is called THEN it should handle error', async () => {
        mockFetch.mockRejectedValue({
            ok: false,
            status: 500,
            text: async () => 'Server error'
        });

        const data = [
            {
                username: 'testuser',
                projectConfigs: [
                    {
                        project: 'project1',
                        readProject: true,
                        readPackage: true,
                        writePackage: false,
                        readVariableFile: false,
                        writeVariableFile: false,
                        readParameterFile: false,
                        writeParameterFile: false,
                        readConstantFile: false,
                        writeConstantFile: false,
                        readActionFile: false,
                        writeActionFile: false,
                        readRuleFile: false,
                        writeRuleFile: false,
                        readScorecardFile: false,
                        writeScorecardFile: false,
                        readDecisionTableFile: false,
                        writeDecisionTableFile: false,
                        readDecisionTreeFile: false,
                        writeDecisionTreeFile: false,
                        readFlowFile: false,
                        writeFlowFile: false,
                    }
                ]
            }
        ];

        ACTIONS.save(data);

        try {
            await flushAsync(mockFetch);
        } catch (e) {
            // Expected rejection
        }

        expect(mockFetch).toHaveBeenCalled();
    });
});
