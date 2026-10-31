// Tiny test server for S10. node server.js [port]. Logs every request to requests.log (one JSON per line).
const http = require('http');
const fs = require('fs');
const path = require('path');
const port = Number(process.argv[2] || 18091);
const logFile = path.join(__dirname, 'requests.log');
const send = (res, status, obj) => {
  res.writeHead(status, { 'Content-Type': 'application/json', 'X-Spike': 's10' });
  res.end(JSON.stringify(obj));
};
http.createServer((req, res) => {
  fs.appendFileSync(logFile, JSON.stringify({ t: new Date().toISOString(), method: req.method, url: req.url }) + '\n');
  const u = req.url.split('?')[0];
  if (u === '/ok') return send(res, 200, { applicationId: 'APP-1', status: 'OK', items: [1, 2] });
  if (u === '/notfound') return send(res, 404, { error: 'not_found', detail: 'no such application' });
  if (u === '/fail') return send(res, 500, { error: 'boom', detail: 'internal failure' });
  if (u === '/slow') return setTimeout(() => send(res, 200, { slow: true }), 6000);
  send(res, 400, { error: 'unknown route' });
}).listen(port, '127.0.0.1', () => console.log('s10 server on ' + port));
