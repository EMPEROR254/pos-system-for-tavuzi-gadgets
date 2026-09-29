package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** Picks the SMS sender: Africa's Talking when AT_API_KEY is set, otherwise log only. */
@Configuration
@EnableScheduling
public class SmsConfig {

  @Bean
  SmsSender smsSender(SmsProperties props, RestClient.Builder builder) {
    return props.live() ? new AfricasTalkingSmsSender(builder, props) : new LoggingSmsSender();
  }
}
