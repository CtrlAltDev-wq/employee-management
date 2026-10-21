package com.wangqi.employee.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds the {@link RestClient} used to call the third-party HR system.
 * A finite connect/read timeout is mandatory here: without it a slow partner
 * can pin request threads indefinitely and take the API down with it.
 *
 * @author wangqi
 */
@Configuration
@EnableConfigurationProperties(ExternalHrProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient externalHrRestClient(ExternalHrProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();

        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
