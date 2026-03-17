package com.project.catxi.common.config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.project.catxi.chat.service.RedisPubSubService;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Configuration
public class RedisConfig {

	// 리스너 컨테이너들을 추적하기 위한 리스트
	private final List<RedisMessageListenerContainer> listenerContainers = new ArrayList<>();

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port}")
	private int port;

	@Value("${spring.data.redis.password}")
	private String password;

	//연결 기본 객체
	@Bean
	@Qualifier("chatRedisConnectionFactory")
	public RedisConnectionFactory chatPubSubFactory() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
		configuration.setHostName(host);
		configuration.setPort(port);
		//redis pub/sub 에서는 특정 데이터베이스에 의존적이지 않음.
		//configuration.setDatabase(0);
		configuration.setPassword(RedisPassword.of(password));
		return new LettuceConnectionFactory(configuration);
	}

	@Bean("chatPubSubTemplate")
	@Qualifier("chatPubSub")
	public StringRedisTemplate chatPubSubTemplate(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf) {
		StringRedisTemplate tpl = new StringRedisTemplate(cf);
		// StringRedisTemplate 기본도 StringRedisSerializer(UTF-8)이지만 명시해도 ok
		// var s = new StringRedisSerializer(StandardCharsets.UTF_8);
		// tpl.setKeySerializer(s); tpl.setValueSerializer(s);
		// tpl.setHashKeySerializer(s); tpl.setHashValueSerializer(s);
		return tpl;
	}

	@Bean("commonTaskScheduler")
	public ThreadPoolTaskScheduler redisPubSubScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("redis-pubsub-");
		scheduler.initialize();
		return scheduler;
	}

	//publish 객체
	/*@Bean
	@Qualifier("chatPubSub")
	// 일반적으로 RedisTemplate< key 데이터 타입 , value 데이터타입> 을 사용
	public StringRedisTemplate stringRedisTemplate( @Qualifier("chatRedisConnectionFactory")RedisConnectionFactory redisConnectionFactory) {
		return new StringRedisTemplate(redisConnectionFactory);
	}*/

	//subscribe 객체
	@Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf,
            RedisPubSubService listener,
            @Qualifier("commonTaskScheduler")ThreadPoolTaskScheduler redisPubSubScheduler
    ) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(cf);
		container.setTaskExecutor(redisPubSubScheduler);

		// 명시적 구독: 채널 + 패턴
		container.addMessageListener(listener, new ChannelTopic("chat"));
		container.addMessageListener(listener, new ChannelTopic("map"));
		container.addMessageListener(listener, new PatternTopic("ready:*"));
		container.addMessageListener(listener, new PatternTopic("participants:*"));
		container.addMessageListener(listener, new PatternTopic("kick:*"));
		container.addMessageListener(listener, new PatternTopic("roomdeleted:*"));
        container.addMessageListener(listener, new PatternTopic("readyresult:*"));

		// 종료 시 정리를 위해 리스트에 추가
		listenerContainers.add(container);
		return container;
	}
	
	// FCM 전용 리스너 - FCM이 Queue 기반으로 변경되어 불필요
	// @Bean
    // public RedisMessageListenerContainer fcmOnlyListenerContainer(...) { ... }

	/**
	 * Redis 리스너 컨테이너들의 정리 작업
	 */
	@PreDestroy
	public void cleanup() {
		log.info("Redis 리스너 컨테이너 정리 시작 - 총 {} 개", listenerContainers.size());
		
		for (RedisMessageListenerContainer container : listenerContainers) {
			try {
				if (container.isRunning()) {
					container.stop();
					log.debug("Redis 리스너 컨테이너 정지 완료");
				}
				
				// 정리 작업
				container.destroy();
				log.debug("Redis 리스너 컨테이너 정리 완료");
				
			} catch (Exception e) {
				log.warn("Redis 리스너 컨테이너 정리 중 오류 발생", e);
			}
		}
		
		listenerContainers.clear();
		log.info("Redis 리스너 컨테이너 정리 완료");
	}


/*	//redis에서 수신된 메시지를 처리하는 객체 생성
	@Bean
	public MessageListenerAdapter messageListenerAdapter(RedisPubSubService redisPubSubService){
		//RedisPubSubService의 특정 메서드가 수신된 메시지를 처리할 수 있도록 지정
		return new MessageListenerAdapter(redisPubSubService,"onMessage");
	}*/

	// JWT 토큰 저장용 Redis 연결
	@Bean
	@Qualifier("tokenRedisConnectionFactory")
	public RedisConnectionFactory tokenRedisConnectionFactory() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
		configuration.setHostName(host);
		configuration.setPort(port);

		//리프레시 토큰용 DB 분리
		configuration.setDatabase(1);
		configuration.setPassword(RedisPassword.of(password));
		return new LettuceConnectionFactory(configuration);
	}

	// JWT 토큰 저장용 RedisTemplate
	@Bean("tokenRedisTemplate")
	@Qualifier("tokenRedisTemplate")
	public RedisTemplate<String, String> tokenRedisTemplate(
			@Qualifier("tokenRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		template.setDefaultSerializer(new StringRedisSerializer());
		return template;
	}

	@Bean
	@Primary // RedisTemplate 중에서는 이것이 기본
	public RedisTemplate<String, String> redisTemplate(
		@Qualifier("chatRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		template.setDefaultSerializer(new StringRedisSerializer());
		return template;
	}


}
