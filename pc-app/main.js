const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const http = require('http');
const os = require('os');
const dgram = require('dgram');

let mainWindow;
let server = null;
let serverPort = 8090;
const DISCOVERY_PORT = 49371;
let discoverySocket = null;


function getLocalIPv4() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return '127.0.0.1';
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 900,
    height: 640,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile('renderer.html');
}

function startServer(port) {
  if (server) return { ok: true, port: serverPort };
  serverPort = port || serverPort;

  server = http.createServer((req, res) => {
    if (req.method === 'POST' && req.url === '/upload') {
      const filename = req.headers['x-filename'] || `upload_${Date.now()}`;
      const fs = require('fs');
      const savePath = path.join(app.getPath('downloads'), filename);

      const ws = fs.createWriteStream(savePath);
      req.on('error', (err) => {
        console.error('req error', err);
        res.statusCode = 500;
        res.end('Request error');
      });
      ws.on('error', (err) => {
        console.error('write error', err);
        res.statusCode = 500;
        res.end('Write error');
      });
      ws.on('finish', () => {
        res.statusCode = 200;
        res.end('OK');
        mainWindow?.webContents.send('server:received', { filename, savePath });
      });
      req.pipe(ws);
      return;
    }

    if (req.method === 'GET' && req.url === '/health') {
      res.statusCode = 200;
      res.end('OK');
      return;
    }

    res.statusCode = 404;
    res.end('Not Found');
  });

  server.listen(serverPort, '0.0.0.0', () => {
    const info = { port: serverPort, ip: getLocalIPv4(), url: `http://${getLocalIPv4()}:${serverPort}/upload` };
    mainWindow?.webContents.send('server:started', info);
  });

  server.on('error', (err) => {
    console.error('Server error', err);
    mainWindow?.webContents.send('server:error', err?.message || String(err));
  });

  return { ok: true, port: serverPort };
}

function stopServer() {
  if (!server) return { ok: true };
  server.close(() => {
    mainWindow?.webContents.send('server:stopped');
  });
  server = null;
  return { ok: true };
}

function startDiscovery() {
  if (discoverySocket) return;
  discoverySocket = dgram.createSocket('udp4');
  discoverySocket.on('message', (msg, rinfo) => {
    try {
      const text = msg.toString();
      if (text === 'DISCOVERY:WHO_IS_PC') {
        const started = !!server;
        const replyObj = {
          type: 'DISCOVERY:PC_INFO',
          ip: getLocalIPv4(),
          port: serverPort,
          started,
          url: started ? `http://${getLocalIPv4()}:${serverPort}/upload` : ''
        };
        const reply = Buffer.from(JSON.stringify(replyObj));
        discoverySocket.send(reply, rinfo.port, rinfo.address);
      }
    } catch (e) {
      console.error('Discovery message error', e);
    }
  });
  discoverySocket.on('error', (err) => {
    console.error('Discovery socket error', err);
  });
  discoverySocket.bind(DISCOVERY_PORT, '0.0.0.0', () => {
    console.log('Discovery listening on UDP', DISCOVERY_PORT);
  });
}

function stopDiscovery() {
  if (!discoverySocket) return;
  try { discoverySocket.close(); } catch {}
  discoverySocket = null;
}





app.whenReady().then(() => {
  createWindow();
  // 同一内网场景：默认自动启动接收服务，省去手动点击
  startServer(serverPort);


  ipcMain.handle('server:start', (event, port) => startServer(port));
ipcMain.handle('discovery:discover', async () => {
  try {
    return { type: 'DISCOVERY:PC_INFO', ip: getLocalIPv4(), port: serverPort, started: !!server, url: `http://${getLocalIPv4()}:${serverPort}/upload` };
  } catch (e) { return null; }
});

  ipcMain.handle('server:stop', () => stopServer());
  startDiscovery();

  // 广播查找 Android 并返回第一个应答
  ipcMain.handle('discovery:android', async () => {
    return await new Promise((resolve) => {
      try {
        const socket = dgram.createSocket('udp4');
        const payload = Buffer.from('DISCOVERY:WHO_IS_ANDROID');
        const port = DISCOVERY_PORT;
        let resolved = false;
        socket.bind(() => {
          socket.setBroadcast(true);
          socket.send(payload, 0, payload.length, port, '255.255.255.255');
        });
        socket.on('message', (msg, rinfo) => {
          try {
            const obj = JSON.parse(msg.toString());
            if (obj.type === 'DISCOVERY:ANDROID_INFO') {
              resolved = true;
              socket.close();
              resolve({ ip: obj.ip || rinfo.address, port: obj.port, url: obj.url || `http://${rinfo.address}:${obj.port}/upload` });
            }
          } catch {}
        });
        setTimeout(() => { if (!resolved) { try { socket.close(); } catch{}; resolve(null); } }, 1500);
      } catch { resolve(null); }
    });
  });

  ipcMain.handle('server:info', () => ({ port: serverPort, ip: getLocalIPv4(), url: `http://${getLocalIPv4()}:${serverPort}/upload` }));
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

