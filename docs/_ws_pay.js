// 只连 type=1（商家端「来单提醒」），验证支付成功后的实时推送
const t0 = Date.now();
const stamp = () => `+${((Date.now() - t0) / 1000).toFixed(2)}s`;
const ws = new WebSocket('ws://localhost:8080/ws/1');
ws.onopen = () => console.log(`[${stamp()}] 商家端(type=1) 已连接`);
ws.onmessage = (e) => console.log(`[${stamp()}] 商家端收到来单提醒 -> ${e.data}`);
ws.onerror = (e) => console.log(`[${stamp()}] 错误 ${e.message || ''}`);
setTimeout(() => { ws.close(); process.exit(0); }, 15000);
