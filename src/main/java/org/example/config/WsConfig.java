// src/main/java/org/example/config/WsConfig.java
package org.example.config;

import org.example.security.JwtChannelInterceptor;
import org.example.security.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableWebSocketMessageBroker
public class WsConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired private JwtChannelInterceptor jwtChannelInterceptor;
    @Autowired private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor) // <-- important: handshake stores token in session attributes
                .setAllowedOriginPatterns("http://localhost:4200") // adapte si tu as d'autres origines
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry
                .setMessageSizeLimit(64 * 1024)        // 64KB par frame
                .setSendBufferSizeLimit(512 * 1024)    // 512KB buffer côté serveur
                .setSendTimeLimit(15_000);             // 15s max d’envoi
    }

    private ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1);
        ts.setThreadNamePrefix("ws-heartbeat-");
        ts.initialize();
        return ts;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        var simple = registry.enableSimpleBroker("/topic", "/queue");
        simple.setTaskScheduler(heartbeatScheduler());
        simple.setHeartbeatValue(new long[]{10_000, 10_000}); // server<->client 10s
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
