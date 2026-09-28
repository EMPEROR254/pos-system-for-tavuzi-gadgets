package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Wires the M-Pesa module: settings and an HTTP client for Daraja. */
@Configuration
@EnableConfigurationProperties(MpesaProperties.class)
public class MpesaConfig {

  @Bean
  RestClient mpesaRestClient(MpesaProperties properties) {
    return RestClient.builder().baseUrl(properties.baseUrl()).build();
  }
}
