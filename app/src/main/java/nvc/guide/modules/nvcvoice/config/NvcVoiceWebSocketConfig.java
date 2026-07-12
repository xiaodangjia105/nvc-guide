package nvc.guide.modules.nvcvoice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import nvc.guide.common.config.CorsProperties;
import nvc.guide.modules.nvcvoice.handler.NvcVoiceWebSocketHandler;

/**
 * NVC 语音练习 WebSocket 配置
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NvcVoiceWebSocketConfig implements WebSocketConfigurer {

  private final NvcVoiceWebSocketHandler nvcVoiceWebSocketHandler;
  private final CorsProperties corsProperties;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(nvcVoiceWebSocketHandler, "/ws/nvc-voice/{sessionId}")
        .addInterceptors(new HttpSessionHandshakeInterceptor())
        .setAllowedOrigins(corsProperties.getAllowedOrigins().split(","));
  }

  @Bean
  public ServletServerContainerFactoryBean createNvcVoiceWebSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
    container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
    return container;
  }
}
