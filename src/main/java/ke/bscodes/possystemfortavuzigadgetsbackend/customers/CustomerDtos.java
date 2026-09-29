package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Request and response bodies for /api/customers. */
public final class CustomerDtos {

  private CustomerDtos() {}

  public record CustomerRequest(
      @NotBlank(message = "Enter the customer's name.")
          @Size(max = 100, message = "At most 100 characters.")
          String name,
      @NotBlank(message = "Enter a phone number.")
          @Size(max = 20, message = "Too long for a phone number.")
          String phone,
      @Email(message = "That email doesn't look right.") @Size(max = 120) String email,
      @Size(max = 300, message = "At most 300 characters.") String notes,
      boolean marketingOptIn) {}

  /** For the till's customer search: just enough to pick the right person. */
  public record CustomerBrief(long id, String name, String phone, boolean marketingOptIn) {

    static CustomerBrief from(Customer c) {
      return new CustomerBrief(c.getId(), c.getName(), c.getPhone(), c.isMarketingOptIn());
    }
  }

  /** For the customer list: with purchase totals worked out by the database. */
  public record CustomerView(
      long id,
      String name,
      String phone,
      String email,
      String notes,
      boolean marketingOptIn,
      long visits,
      BigDecimal totalSpent,
      Instant lastPurchaseAt) {}
}
