/**
 * Test helpers for Playwright e2e tests.
 *
 * Vite serves HTML from html/ directory, main frame at /html/frame.html.
 * API proxy: /api → http://127.0.0.1:8180/ruleforgeV2
 */

export async function login(page) {
    // Navigate to login page first to establish browser context
    await page.goto('/html/login.html');

    // Authenticate by making a login API call from within the page's browser context.
    // This ensures the JSESSIONID cookie is set in the browser context that the page uses.
    // We use the absolute path /api/frame/login because the Vite proxy rewrites /api → backend.
    await page.evaluate(async () => {
        const response = await fetch('/api/frame/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({username: 'admin', password: 'admin'}).toString()
        });
        const result = await response.json();
        if (!result.status) {
            throw new Error('Login failed: ' + JSON.stringify(result));
        }
    });
}

/**
 * Login and navigate to the main frame page.
 */
export async function loginAndGotoFrame(page) {
    await login(page);
    await page.goto('/html/frame.html');
    // Wait for the frame to render (Redux dispatch + tree loading)
    await page.waitForSelector('#container', {timeout: 10000});
}
