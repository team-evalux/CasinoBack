package org.example.config;

import org.example.security.JwtChannelInterceptor;
import org.example.security.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WsConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    // mêmes origines que CORS
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    public WsConfig(JwtChannelInterceptor ch, JwtHandshakeInterceptor hs) {
        this.jwtChannelInterceptor = ch;
        this.jwtHandshakeInterceptor = hs;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = allowedOrigins.split("\\s*,\\s*");
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(patterns)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(15_000);
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
        simple.setHeartbeatValue(new long[]{10_000, 10_000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
