package com.beep.jobportal.fetch;

import com.beep.jobportal.tag.TaggingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SourcesProperties.class, TaggingProperties.class})
public class HttpConfig {

    /** One shared HTTP client for all fetchers, with timeouts so a slow API can't hang a run. */
    @Bean
    RestClient jobsRestClient(SourcesProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.httpTimeoutSeconds()));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "beep-job-portal/0.1 (student demo project)")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
