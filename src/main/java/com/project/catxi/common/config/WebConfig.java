package com.project.catxi.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebConfig {
  //RestTemplate이용 카카오 요구 방식 충족 위해서

  @Bean
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();

    //HttpMessageConverter : HTTP 요청 응답 JAVA 객체 -> HTTP 메시지로 변환
    List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
    //application/x-www-fomr-urlencoded 방식 요청/응답 처리
    messageConverters.add(new FormHttpMessageConverter());
    restTemplate.setMessageConverters(messageConverters);

    return new RestTemplate();
  }


}
