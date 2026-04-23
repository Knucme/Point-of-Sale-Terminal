package com.sos.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the netty-socketio SocketIOServer bean.
 * Runs on a separate port (default 3002) from the Spring Boot HTTP server (3001).
 * The Vite dev proxy routes /socket.io traffic to this port.
 */
@Configuration
public class SocketIOConfig {

    @Value("${socketio.host}")
    private String host;

    @Value("${socketio.port}")
    private int port;

    @Value("${cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config =
                new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);

        // Allow both WebSocket and polling transports (matches Node.js config)
        config.setTransports(Transport.WEBSOCKET, Transport.POLLING);

        // CORS — allow the React dev server
        config.setOrigin(allowedOrigin);
        config.setAllowHeaders("*");

        // Increase ping timeout for reliability
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        return new SocketIOServer(config);
    }
}
