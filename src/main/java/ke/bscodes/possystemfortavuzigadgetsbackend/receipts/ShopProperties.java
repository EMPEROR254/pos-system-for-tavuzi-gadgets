package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the shop prints on receipts (application.yml under "app.shop"). The spec asks for the
 * paybill/till numbers and the return policy. Not secrets - but kept in configuration so the
 * owner's details can change without new code.
 */
@ConfigurationProperties(prefix = "app.shop")
public record ShopProperties(
    String name,
    String location,
    String phone,
    String kraPin,
    String tillNumber,
    String paybillNumber,
    String paybillAccount,
    String returnPolicy,
    List<String> footer) {

  public ShopProperties {
    footer = footer == null ? List.of() : footer;
  }
}
