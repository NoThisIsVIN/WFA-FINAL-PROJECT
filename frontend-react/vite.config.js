import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import http from 'http';
import { handleMockApi, isRealBackendOnline } from './mock-server.js';

function smartBackendPlugin() {
  let backendOnline = false;
  let lastChecked = 0;

  async function checkBackend() {
    const now = Date.now();
    if (now - lastChecked > 8000) {
      lastChecked = now;
      backendOnline = await isRealBackendOnline();
    }
    return backendOnline;
  }

  return {
    name: 'smart-backend-plugin',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = req.url || '';
        if (url.startsWith('/api') || url === '/health') {
          const online = await checkBackend();
          if (online) {
            // Forward directly to the real Spring Boot backend on 8090
            const headers = { ...req.headers, host: '127.0.0.1:8090' };
            const proxyReq = http.request(
              {
                hostname: '127.0.0.1',
                port: 8090,
                path: req.url,
                method: req.method,
                headers: headers
              },
              (proxyRes) => {
                res.writeHead(proxyRes.statusCode || 200, proxyRes.headers);
                proxyRes.pipe(res);
              }
            );
            proxyReq.on('error', () => {
              // If backend connection fails, seamlessly fallback to mock
              handleMockApi(req, res);
            });
            req.pipe(proxyReq);
            return;
          }

          // Real backend is offline -> Serve simulated responses cleanly without terminal error dumps!
          return handleMockApi(req, res);
        }
        next();
      });
    }
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), smartBackendPlugin()],
  server: {
    port: 5173
  }
});
