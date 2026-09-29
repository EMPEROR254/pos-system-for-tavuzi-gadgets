package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerBrief;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerView;
import ke.bscodes.possystemfortavuzigadgetsbackend.mpesa.Phones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer records. The till adds them as it sells (a phone number is enough), so the shop builds
 * its customer list without extra work; one phone number = one customer.
 */
@Service
public class CustomerService {

  private final CustomerRepository customers;
  private final Clock clock;

  public CustomerService(CustomerRepository customers, Clock clock) {
    this.customers = customers;
    this.clock = clock;
  }

  /** "0712 345 678" → "254712345678"; a clear message when it isn't a Kenyan mobile number. */
  public static String phone(String input) {
    try {
      return Phones.normalize(input);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("The phone number doesn't look right (07XX XXX XXX).");
    }
  }

  /**
   * The customer on a sale: an existing one by id, or found/created by phone. A name typed at the
   * till fills in a missing name; consent to promotions is only ever switched on here, never off.
   */
  @Transactional
  public Customer forSale(Long customerId, String name, String rawPhone, boolean optIn) {
    Instant now = clock.instant();
    Customer customer;
    if (customerId != null) {
      customer =
          customers
              .findById(customerId)
              .orElseThrow(() -> ApiException.notFound("No such customer."));
    } else if (rawPhone != null && !rawPhone.isBlank()) {
      String phone = phone(rawPhone);
      String cleanName =
          name == null || name.isBlank() ? "Customer " + phone.substring(8) : name.trim();
      customer =
          customers
              .findByPhone(phone)
              .orElseGet(() -> customers.save(new Customer(cleanName, phone, now)));
    } else {
      return null;
    }
    if (optIn && !customer.isMarketingOptIn()) {
      customer.setMarketingOptIn(true, now);
    }
    return customer;
  }

  @Transactional(readOnly = true)
  public List<CustomerBrief> quickSearch(String query) {
    String q = query == null ? "" : query.trim();
    if (q.length() < 2) {
      return List.of();
    }
    return customers.quickSearch(namePattern(q), phonePattern(q), PageRequest.of(0, 10)).stream()
        .map(CustomerBrief::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<CustomerView> search(String query, Boolean optedIn, Pageable pageable) {
    String q = query == null ? "" : query.trim();
    return customers.search(
        q.isEmpty() ? null : namePattern(q), phonePattern(q), optedIn, pageable);
  }

  @Transactional(readOnly = true)
  public CustomerView get(long id) {
    return customers.findView(id).orElseThrow(() -> ApiException.notFound("No such customer."));
  }

  @Transactional
  public CustomerView create(CustomerRequest r) {
    String phone = phone(r.phone());
    if (customers.findByPhone(phone).isPresent()) {
      throw ApiException.conflict("A customer with that phone number already exists.");
    }
    Instant now = clock.instant();
    Customer c = customers.save(new Customer(r.name().trim(), phone, now));
    apply(c, r, phone, now);
    customers.flush();
    return get(c.getId());
  }

  @Transactional
  public CustomerView update(long id, CustomerRequest r) {
    Customer c =
        customers.findById(id).orElseThrow(() -> ApiException.notFound("No such customer."));
    String phone = phone(r.phone());
    if (customers.existsByPhoneAndIdNot(phone, id)) {
      throw ApiException.conflict("Another customer has that phone number.");
    }
    apply(c, r, phone, clock.instant());
    customers.flush();
    return get(id);
  }

  private static void apply(Customer c, CustomerRequest r, String phone, Instant now) {
    c.update(r.name().trim(), phone, blankToNull(r.email()), blankToNull(r.notes()), now);
    c.setMarketingOptIn(r.marketingOptIn(), now);
  }

  private static String namePattern(String q) {
    return "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "\\_") + "%";
  }

  /** Typed digits without the leading 0, so "0712" and "712" both match 254712... */
  private static String phonePattern(String q) {
    String digits = q.replaceAll("\\D", "").replaceFirst("^0", "");
    return digits.length() < 3 ? "__no_match__" : "%" + digits + "%";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
