package com.sky.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务端
 * <p>
 * 端点：/ws/{type}
 * type=1 来单提醒（商家端连接）；type=2 客户催单（商家端连接）
 * <p>
 * 为什么用 WebSocket 而不是轮询：来单提醒需要「实时」，轮询要么延迟高、要么空耗带宽。
 * WebSocket 是长连接，服务端能主动推。
 */
@Component
@ServerEndpoint("/ws/{type}")
public class WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    /** 按 type 分组的会话池（一个 type 可多个连接，如多台商家设备） */
    private static final ConcurrentHashMap<String, Set<Session>> SESSION_POOL = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("type") String type) {
        SESSION_POOL.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 新连接，type={}，当前连接数={}", type, SESSION_POOL.get(type).size());
    }

    @OnClose
    public void onClose(@PathParam("type") String type, Session session) {
        Set<Session> sessions = SESSION_POOL.get(type);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    @OnMessage
    public void onMessage(String message, @PathParam("type") String type) {
        log.debug("收到客户端消息 type={}: {}", type, message);
    }

    /**
     * 向指定 type 的所有连接广播消息
     */
    public void sendToType(String type, String message) {
        Set<Session> sessions = SESSION_POOL.get(type);
        if (sessions == null || sessions.isEmpty()) {
            log.info("无 type={} 的在线连接，跳过推送", type);
            return;
        }
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket 推送失败", e);
            }
        }
    }
}
