import {alert, confirm} from '@/utils/modal';
/**
 * Centralized HTTP client for the RuleForge frontend.
 *
 * All functions auto-prepend `window._server` to relative paths.
 * Error handling is centralized — 401 shows permission alert, other
 * errors show the response text via alert.
 */

// ---- Types ----

/** The standard envelope the Spring Boot backend returns for save operations. */
export interface ApiResponse<T = unknown> {
    status: boolean;
    message?: string;
    data?: T;
}

/** Per-request options. */
export interface RequestOptions {
    /** Prefix for error messages shown to the user. */
    errorPrefix?: string;
    /** If true, suppress the automatic error dialog. */
    silent?: boolean;
}

// ---- Internal helpers ----

function baseUrl(path: string): string {
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    return window._server + path;
}

function handleError(response: Response, opts: RequestOptions = {}): Promise<never> {
    if (opts.silent) return Promise.reject(response);

    if (response.status === 401) {
        alert('权限不足，不能进行此操作.');
        return Promise.reject(response);
    }

    return response.text().then(function (text: string) {
        const msg = text
            ? (opts.errorPrefix || '服务端错误：') + text
            : (opts.errorPrefix || '服务端出错');
        alert("<span style='color: red'>" + msg + "</span>");
        return Promise.reject(response);
    });
}

function networkError(err: unknown, opts: RequestOptions = {}): never {
    if (!opts.silent) {
        alert("<span style='color: red'>服务端出错</span>");
    }
    throw err;
}

// ---- Public API ----

/**
 * Form-encoded POST. The most common pattern in the codebase.
 * Automatically serializes params with URLSearchParams.
 */
export function formPost<T = unknown>(
    path: string,
    params: Record<string, string>,
    opts?: RequestOptions,
): Promise<T> {
    return fetch(baseUrl(path), {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params).toString(),
    })
        .then(function (response: Response) {
            if (!response.ok) return handleError(response, opts);
            return response.json() as Promise<T>;
        })
        .catch(function (err: unknown) {
            if (err instanceof TypeError) networkError(err, opts);
            throw err;
        });
}

/**
 * JSON POST. Used by monitoring, datasource, agent, simulation, etc.
 */
export function jsonPost<T = unknown>(
    path: string,
    body: unknown,
    opts?: RequestOptions,
): Promise<T> {
    return fetch(baseUrl(path), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(function (response: Response) {
            if (!response.ok) return handleError(response, opts);
            return response.json() as Promise<T>;
        })
        .catch(function (err: unknown) {
            if (err instanceof TypeError) networkError(err, opts);
            throw err;
        });
}

/**
 * JSON PUT. Used by datasource, release (shadow/gray toggle).
 */
export function jsonPut<T = unknown>(
    path: string,
    body: unknown,
    opts?: RequestOptions,
): Promise<T> {
    return fetch(baseUrl(path), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(function (response: Response) {
            if (!response.ok) return handleError(response, opts);
            return response.json() as Promise<T>;
        })
        .catch(function (err: unknown) {
            if (err instanceof TypeError) networkError(err, opts);
            throw err;
        });
}

/**
 * GET with automatic JSON parsing.
 */
export function httpGet<T = unknown>(
    path: string,
    opts?: RequestOptions,
): Promise<T> {
    return fetch(baseUrl(path))
        .then(function (response: Response) {
            if (!response.ok) return handleError(response, opts);
            return response.json() as Promise<T>;
        })
        .catch(function (err: unknown) {
            if (err instanceof TypeError) networkError(err, opts);
            throw err;
        });
}

/**
 * DELETE with no body parsing.
 */
export function httpDelete(
    path: string,
    opts?: RequestOptions,
): Promise<void> {
    return fetch(baseUrl(path), { method: 'DELETE' })
        .then(function (response: Response) {
            if (!response.ok) return handleError(response, opts);
        })
        .catch(function (err: unknown) {
            if (err instanceof TypeError) networkError(err, opts);
            throw err;
        });
}

/**
 * Save with status check — the Promise-based replacement for `ajaxSave`.
 *
 * Posts form-encoded data, then checks `result.status`. If the server
 * returns `{ status: false, message: "..." }`, shows the message via
 * the modal helper and rejects. If `status: true`, resolves with the full response.
 */
export function save<T = unknown>(
    path: string,
    params: Record<string, string>,
    opts?: RequestOptions,
): Promise<ApiResponse<T>> {
    return formPost<ApiResponse<T>>(path, params, opts).then(function (result) {
        if (!result.status) {
            if (!opts?.silent) {
                alert(result.message || '保存失败');
            }
            return Promise.reject(result);
        }
        return result;
    });
}

/**
 * Check if file is dirty, then prompt for new version and save.
 * The Promise-based replacement for the old `saveNewVersion`.
 *
 * Rejects if the file has no diff or the user cancels the confirm dialog.
 */
export function saveNewVersion(
    path: string,
    postData: { file: string; content: string; [key: string]: unknown },
    opts?: RequestOptions,
): Promise<void> {
    return formPost<{ status: boolean }>(
        '/common/checkFileDirty',
        { filePath: postData.file, content: postData.content as string },
        { silent: true },
    )
        .then(function (result) {
            if (!result.status) {
                // File is not dirty — no diff
                if (!opts?.silent) {
                    alert('与最新版本无差异，无需生成新版本');
                }
                return Promise.reject(new Error('no_diff'));
            }

            // File has changes — ask user to confirm
            let decodedFileName = decodeURIComponent(postData.file);
            if (decodedFileName.includes('%')) {
                decodedFileName = decodeURIComponent(decodedFileName);
            }
            return new Promise<void>(function (resolve, reject) {
                confirm(
                    '是否对【' + decodedFileName + '】生成新版本?',
                    function (confirmed: boolean) {
                        if (!confirmed) {
                            return reject(new Error('cancelled'));
                        }
                        // User confirmed — save
                        save(path, postData as Record<string, string>, opts)
                            .then(function () { resolve(); })
                            .catch(reject);
                    },
                );
            });
        });
}

// ════════════════════════════════════════════════════════════════════════
// BatchTest V5.8.0 批量测试 API(Subject × InputSource 多态)
// ════════════════════════════════════════════════════════════════════════

/** Subject type 常量 */
export const BATCH_TEST_SUBJECT_FLOW = 'FLOW';
export const BATCH_TEST_SUBJECT_DATASOURCE = 'DATASOURCE';

/** Input source type 常量 */
export const BATCH_TEST_INPUT_FILE = 'FILE';
export const BATCH_TEST_INPUT_DATASOURCE = 'DATASOURCE';

/** 行状态常量(后端 BatchTestRowEntity.STATUS_*) */
export const ROW_STATUS_PENDING = 'PENDING';
export const ROW_STATUS_SUCCESS = 'SUCCESS';
export const ROW_STATUS_ERROR = 'ERROR';

/** 会话状态常量(后端 BatchTestSessionEntity.STATUS_*) */
export const SESSION_STATUS_UPLOADED = 'UPLOADED';
export const SESSION_STATUS_RUNNING = 'RUNNING';
export const SESSION_STATUS_COMPLETED = 'COMPLETED';
export const SESSION_STATUS_FAILED = 'FAILED';

/** Start 请求体 */
export interface StartBatchTestRequest {
    subjectType: string;       // FLOW | DATASOURCE
    subjectId: number | null;  // flowId 或 datasourceId
    inputSourceType: string;   // FILE | DATASOURCE
    inputSourceId: number | null;
    inputConfig: Record<string, unknown>;
    project: string | null;
    packageId: string | null;
    flowId: string | null;
}

/** Start 响应 */
export interface StartBatchTestResponse {
    sessionId: number;
    status: string;
    subjectType: string;
    inputSourceType: string;
}

/** 进度响应 */
export interface BatchTestProgress {
    sessionId: number;
    status: string;
    totalRows: number;
    progress: number;
    errorCount: number;
    subjectType: string;
    inputSourceType: string;
}

/** 行结果(V5.8.0 起多 latency / httpStatus / errorCode 字段) */
export interface BatchTestRow {
    id: number;
    sessionId: number;
    rowIndex: number;
    inputData: string | null;
    outputData: string | null;
    errorMessage: string | null;
    status: string;
    latencyMs: number | null;
    httpStatus: number | null;
    errorCode: string | null;
}

/** 启动一次批量测试 */
export function startBatchTest(
    req: StartBatchTestRequest,
    opts?: RequestOptions,
): Promise<StartBatchTestResponse> {
    return jsonPost<StartBatchTestResponse>('/batchtest/start', req, opts);
}

/**
 * v5.8.4:multipart 启动批量测试,带 Excel 文件。
 * 走 POST /batchtest/start-with-file,file 必填,config 是 StartBatchTestRequest JSON。
 */
export function startBatchTestWithFile(
    req: StartBatchTestRequest,
    file: File,
    _opts?: RequestOptions,
): Promise<StartBatchTestResponse> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('config', JSON.stringify(req));
    const base = (window as any)._server || '';
    const url = base + '/batchtest/start-with-file';
    return fetch(url, { method: 'POST', body: fd }).then(
        (resp) => {
            if (!resp.ok) {
                return resp.text().then((text) => {
                    throw new Error(
                        'startBatchTestWithFile failed: ' + resp.status + ' ' + text,
                    );
                });
            }
            return resp.json() as Promise<StartBatchTestResponse>;
        },
    );
}

/** 轮询进度 */
export function getBatchTestProgress(
    sessionId: number,
    opts?: RequestOptions,
): Promise<BatchTestProgress> {
    return httpGet<BatchTestProgress>(
        '/batchtest/sessions/' + sessionId + '/progress',
        opts,
    );
}

/** 拉行结果(分页) */
export function getBatchTestResults(
    sessionId: number,
    page: number,
    size: number,
    opts?: RequestOptions,
): Promise<{ rows: BatchTestRow[]; page: number; size: number; total: number }> {
    return httpGet<{ rows: BatchTestRow[]; page: number; size: number; total: number }>(
        '/batchtest/sessions/' + sessionId + '/results?page=' + page + '&size=' + size,
        opts,
    );
}

/** 列历史 session(给 dashboard 用) */
export function listBatchTestSessions(
    subjectType?: string,
    limit = 20,
    opts?: RequestOptions,
): Promise<BatchTestSession[]> {
    let path = '/batchtest/sessions?limit=' + limit;
    if (subjectType) path += '&subjectType=' + subjectType;
    return httpGet<BatchTestSession[]>(path, opts);
}

/** 简化版:Session 列表响应 */
export interface BatchTestSession {
    id: number;
    project: string | null;
    packageId: string | null;
    flowId: string | null;
    status: string;
    totalRows: number | null;
    errorCount: number | null;
    progress: number | null;
    subjectType: string;
    subjectId: number | null;
    inputSourceType: string;
    inputSourceId: number | null;
}

// ---- 5.10-D: Git 状态面板 (admin) ----

/** /ruleforge/git/observability/summary 响应 */
export interface GitStatusSummary {
    totalFailures: number;
    last1h: number;
    last24h: number;
    counters: Record<string, number>;
}

/** /ruleforge/git/observability/recent 列表行 */
export interface GitStatusFailure {
    id: number;
    filePath: string;
    projectId: number | null;
    fileId: number | null;
    errorType: string;
    errorMessage: string | null;
    branch: string | null;
    occurredAt: string;   // ISO 8601
}

/** 抓取 Git dualWrite 健康 summary (admin 门控). */
export function getGitStatusSummary(opts?: RequestOptions): Promise<GitStatusSummary> {
    return httpGet<GitStatusSummary>('/git/observability/summary', opts);
}

/** 抓取最近 N 条 dualWrite 失败 (admin 门控,默认 50,后端上限 500). */
export function getGitStatusRecent(
    limit = 50,
    opts?: RequestOptions,
): Promise<GitStatusFailure[]> {
    return httpGet<GitStatusFailure[]>(
        '/git/observability/recent?limit=' + limit,
        opts,
    );
}

// ---- V5.15 用户管理 API ----

/** 用户列表行 */
export interface UserItem {
    id: number;
    username: string;
    companyId: string;
    isAdmin: boolean;
    isEnabled: boolean;
    canImport: boolean;
    canExport: boolean;
    createdAt: string;
    updatedAt: string;
}

/** 用户项目权限行 */
export interface UserProjectPermission {
    id: number;
    userId: number;
    project: string;
    readProject: boolean;
    readPackage: boolean;
    writePackage: boolean;
    readVariableFile: boolean;
    writeVariableFile: boolean;
    readParameterFile: boolean;
    writeParameterFile: boolean;
    readConstantFile: boolean;
    writeConstantFile: boolean;
    readActionFile: boolean;
    writeActionFile: boolean;
    readRuleFile: boolean;
    writeRuleFile: boolean;
    readDecisionTableFile: boolean;
    writeDecisionTableFile: boolean;
    readDecisionTreeFile: boolean;
    writeDecisionTreeFile: boolean;
    readScorecardFile: boolean;
    writeScorecardFile: boolean;
    readFlowFile: boolean;
    writeFlowFile: boolean;
}

/** 列出所有用户 (admin-only) */
export function listUsers(opts?: RequestOptions): Promise<UserItem[]> {
    return httpGet<UserItem[]>('/permission/users', opts);
}

/** 创建用户 (admin-only) */
export function createUser(
    username: string,
    password: string,
    isAdmin: boolean,
    canExport: boolean,
    opts?: RequestOptions,
): Promise<{ status: boolean; id: number; username: string; error?: string }> {
    return formPost('/permission/users', {
        username,
        password,
        isAdmin: String(isAdmin),
        canExport: String(canExport),
    }, opts);
}

/** 修改用户 (admin-only) */
export function updateUser(
    id: number,
    params: Record<string, string>,
    opts?: RequestOptions,
): Promise<{ status: boolean; username: string; error?: string }> {
    const base = (window as any)._server || '';
    const url = base + '/permission/users/' + id;
    const body = new URLSearchParams(params).toString();
    return fetch(url, {
        method: 'PUT',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body,
        credentials: 'same-origin',
    }).then(function (response: Response) {
        if (!response.ok) return handleError(response, opts);
        return response.json();
    });
}

/** 启用/禁用用户 (admin-only) */
export function toggleUserEnabled(
    id: number,
    enabled: boolean,
    opts?: RequestOptions,
): Promise<{ status: boolean }> {
    const base = (window as any)._server || '';
    const url = base + '/permission/users/' + id + '/enabled?enabled=' + enabled;
    return fetch(url, {
        method: 'PATCH',
        credentials: 'same-origin',
    }).then(function (response: Response) {
        if (!response.ok) return handleError(response, opts);
        return response.json();
    });
}

/** 重置密码 (admin-only) */
export function resetPassword(
    id: number,
    newPassword: string,
    opts?: RequestOptions,
): Promise<{ status: boolean }> {
    return formPost('/permission/users/' + id + '/reset-password', {
        newPassword,
    }, opts);
}

/** 获取某用户的项目权限 (admin-only) */
export function getUserPermissions(
    userId: number,
    opts?: RequestOptions,
): Promise<UserProjectPermission[]> {
    return httpGet<UserProjectPermission[]>('/permission/users/' + userId + '/permissions', opts);
}

/** 保存某用户的项目权限 (admin-only) */
export function saveUserPermissions(
    userId: number,
    permissions: UserProjectPermission[],
    opts?: RequestOptions,
): Promise<{ status: boolean; count: number }> {
    return jsonPut<{ status: boolean; count: number }>(
        '/permission/users/' + userId + '/permissions',
        permissions,
        opts,
    );
}

// ---- 5.17: 用户/权限操作审计日志 (admin 门控) ----

/** /ruleforge/permission/audit 列表行 */
export interface AuditLogRow {
    id: number;
    occurredAt: string;        // ISO 8601 (DATETIME(3) → "2026-06-07T12:00:00.000")
    actor: string;
    action: string;            // CREATE_USER / UPDATE_USER / TOGGLE_ENABLED / RESET_PASSWORD / SAVE_PERMISSIONS / LOGIN_SUCCESS / LOGIN_FAIL
    targetUserId: number | null;
    targetUsername: string | null;
    fieldName: string | null;
    oldValue: string | null;
    newValue: string | null;
    project: string | null;
    note: string | null;
}

/**
 * 抓取 audit log 列表 (admin 门控,后端上限 500)。
 *  - actor: 可选,过滤执行人
 *  - action: 可选,过滤动作类型
 *  - size: 默认 50,后端 clamp 到 [1, 500]
 *
 * 注意:路径不带 /ruleforge 前缀 — nginx 的 /api/ 代理会自动加上
 * (见 nginx.conf: proxy_pass http://console-app:8180/ruleforge/;)
 */
export function getAuditLogs(
    actor: string | null = null,
    action: string | null = null,
    size = 50,
    opts?: RequestOptions,
): Promise<AuditLogRow[]> {
    const params = new URLSearchParams();
    if (actor) params.set('actor', actor);
    if (action) params.set('action', action);
    params.set('size', String(size));
    return httpGet<AuditLogRow[]>(
        '/permission/audit?' + params.toString(),
        opts,
    );
}
