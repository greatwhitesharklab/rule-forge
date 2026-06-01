/**
 * Centralized HTTP client for the RuleForge frontend.
 *
 * All functions auto-prepend `window._server` to relative paths.
 * Error handling is centralized — 401 shows permission alert, other
 * errors show the response text via bootbox.alert.
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
    /** If true, suppress the automatic bootbox error dialog. */
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
        window.bootbox.alert('权限不足，不能进行此操作.');
        return Promise.reject(response);
    }

    return response.text().then(function (text: string) {
        const msg = text
            ? (opts.errorPrefix || '服务端错误：') + text
            : (opts.errorPrefix || '服务端出错');
        window.bootbox.alert("<span style='color: red'>" + msg + "</span>");
        return Promise.reject(response);
    });
}

function networkError(err: unknown, opts: RequestOptions = {}): never {
    if (!opts.silent) {
        window.bootbox.alert("<span style='color: red'>服务端出错</span>");
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
 * bootbox and rejects. If `status: true`, resolves with the full response.
 */
export function save<T = unknown>(
    path: string,
    params: Record<string, string>,
    opts?: RequestOptions,
): Promise<ApiResponse<T>> {
    return formPost<ApiResponse<T>>(path, params, opts).then(function (result) {
        if (!result.status) {
            if (!opts?.silent) {
                window.bootbox.alert(result.message || '保存失败');
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
                    window.bootbox.alert('与最新版本无差异，无需生成新版本');
                }
                return Promise.reject(new Error('no_diff'));
            }

            // File has changes — ask user to confirm
            let decodedFileName = decodeURIComponent(postData.file);
            if (decodedFileName.includes('%')) {
                decodedFileName = decodeURIComponent(decodedFileName);
            }
            return new Promise<void>(function (resolve, reject) {
                window.bootbox.confirm(
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
