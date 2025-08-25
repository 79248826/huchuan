const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  startServer: (port) => ipcRenderer.invoke('server:start', port),
  stopServer: () => ipcRenderer.invoke('server:stop'),
  info: () => ipcRenderer.invoke('server:info'),
  onServerStarted: (cb) => ipcRenderer.on('server:started', (_, data) => cb(data)),
  onServerStopped: (cb) => ipcRenderer.on('server:stopped', () => cb()),
  onServerReceived: (cb) => ipcRenderer.on('server:received', (_, data) => cb(data)),
  onServerError: (cb) => ipcRenderer.on('server:error', (_, msg) => cb(msg)),
  discover: () => ipcRenderer.invoke('discovery:discover'),
  discoverAndroid: () => ipcRenderer.invoke('discovery:android'),
});

