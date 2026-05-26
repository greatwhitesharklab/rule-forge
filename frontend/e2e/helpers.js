export async function login(page) {
    // Authenticate by making a login API call from within the page's browser context.
    // This ensures the JSESSIONID cookie is set in the browser context that the page uses.
    // We use the absolute path /api/frame/login because:
    // - Editor pages (in /html/) use window._server = "../api" which resolves to /api/ (proxied correctly)
    // - The login page uses window._server = "./api" which would resolve to /html/api/ (NOT proxied)
    // By navigating to a neutral page first and calling the API with an absolute path, the cookie is set correctly.
    await page.goto('/html/login.html');
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
