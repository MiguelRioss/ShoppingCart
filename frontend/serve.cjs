// Run with: node frontend/serve.cjs
// Serves only frontend files, never backend settings or repository files.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };
http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const name = pathname === '/' ? 'login-tester.html' : pathname.slice(1);
    if (!['login-tester.html', 'checkout-success.html', 'checkout-error.html', 'checkout-result.css', 'checkout-result.js'].includes(name)) {
        res.writeHead(404).end('Not found');
        return;
    }
    fs.readFile(path.join(__dirname, name), (error, data) => {
        if (error) { res.writeHead(404).end('Not found'); return; }
        res.writeHead(200, { 'Content-Type': types[path.extname(name)], 'Cache-Control': 'no-store' });
        res.end(data);
    });
}).listen(5500, '127.0.0.1', () => console.log('Frontend: http://127.0.0.1:5500/'));
