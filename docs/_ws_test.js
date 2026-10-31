// WebSocket 实测脚本：两个客户端分别连 type=1 和 type=2，验证「按 type 分组推送」
const t0 = Date.now();
const stamp = () => `+${((Date.now() - t0) / 1000).toFixed(2)}s`;

function connect(type) {
  const ws = new WebSocket(`ws://localhost:8080/ws/${type}`);
  ws.onopen = () => console.log(`[${stamp()}] 客户端(type=${type}) 已连接`);
  ws.onmessage = (e) => console.log(`[${stamp()}] 客户端(type=${type}) 收到推送 -> ${e.data}`);
  ws.onerror = (e) => console.log(`[${stamp()}] 客户端(type=${type}) 错误 ${e.message || ''}`);
  ws.onclose = () => console.log(`[${stamp()}] 客户端(type=${type}) 已断开`);
  return ws;
}

const a = connect('1');
const b = connect('2');

setTimeout(() => {
  a.close();
  b.close();
  console.log('测试结束');
  process.exit(0);
}, 20000);
