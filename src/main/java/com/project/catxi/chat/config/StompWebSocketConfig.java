package com.project.catxi.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompHandler stompHandler;

	public StompWebSocketConfig(StompHandler stompHandler) {
		this.stompHandler = stompHandler;
	}

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/connect")
            .setAllowedOriginPatterns("*")
            .withSockJS()
            .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js")
            .setStreamBytesLimit(512 * 1024)
            .setHttpMessageCacheSize(1000)
            .setDisconnectDelay(30 * 1000)
            .setSuppressCors(true);
    }


    @Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		//  /publish/1 형태로 메시지 발행해야 함을 설정
		// /publish 로 시작하는 url 패턴으로 메시지가 발행되면 @Controller 객체의 @MessageMapping 메서드로 라우팅
		registry.setApplicationDestinationPrefixes("/publish");

		//  /topic/1 형태로 메시지 수신해야 함을 설정
		// /topic 로 시작하는 url 패턴으로 메시지가 발행되면 @Controller 객체의 @MessageMapping 메서드로 라우팅
		registry.enableSimpleBroker("/topic","/queue");
		registry.setUserDestinationPrefix("/user");
	}

	//웹소켓 요청(connect, subscribe, disconnect )등의 요청시에는 http header 등 http 메시지를 넣어올 수 있고 이를 interceptor 를 통해 가로채 토큰등을 검증할 수 있음.
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompHandler);
	}
}
