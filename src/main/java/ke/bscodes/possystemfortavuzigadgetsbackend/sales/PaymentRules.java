package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentRequest;

/**
 * Checks how a sale is paid (one or more methods). Rules:
 *
 * <ul>
 *   <li>together the payments cover the total;
 *   <li>only cash can be more than needed (the difference is the change) - M-Pesa and card amounts
 *       can't exceed what is still owed after them, since no change is given on them;
 *   <li>every M-Pesa payment has a valid 10-character code, used once.
 * </ul>
 */
public final class PaymentRules {

  /** M-Pesa confirmation codes are 10 letters/digits, e.g. SJK4H7XYZ1. */
  private static final Pattern MPESA_CODE = Pattern.compile("^[A-Z0-9]{10}$");

  public record CheckedPayment(PaymentMethod method, BigDecimal amount, String reference) {}

  public record PaymentPlan(List<CheckedPayment> payments, BigDecimal paid, BigDecimal change) {}

  private PaymentRules() {}

  public static String normaliseCode(String code) {
    return code == null ? "" : code.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
  }

  public static PaymentPlan check(List<PaymentRequest> requested, BigDecimal total) {
    List<CheckedPayment> payments = new ArrayList<>();
    Set<String> codes = new HashSet<>();
    BigDecimal paid = BigDecimal.ZERO.setScale(2);
    BigDecimal nonCash = BigDecimal.ZERO.setScale(2);
    for (PaymentRequest r : requested) {
      BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
      String reference = null;
      if (r.method().isMpesa()) {
        reference = normaliseCode(r.reference());
        if (!MPESA_CODE.matcher(reference).matches()) {
          throw ApiException.badRequest(
              "Type the 10-character M-Pesa code from the customer's SMS (e.g. SJK4H7XYZ1).");
        }
        if (!codes.add(reference)) {
          throw ApiException.badRequest("M-Pesa code " + reference + " is entered twice.");
        }
      } else if (r.method() == PaymentMethod.CARD) {
        String code = r.reference() == null ? "" : r.reference().trim();
        reference = code.isEmpty() ? null : code;
      }
      if (r.method() != PaymentMethod.CASH) {
        nonCash = nonCash.add(amount);
      }
      paid = paid.add(amount);
      payments.add(new CheckedPayment(r.method(), amount, reference));
    }
    if (nonCash.compareTo(total) > 0) {
      throw ApiException.badRequest(
          "M-Pesa and card payments are more than the total. Change is only given on cash.");
    }
    if (paid.compareTo(total) < 0) {
      throw ApiException.badRequest("KES " + total.subtract(paid) + " is still to be paid.");
    }
    return new PaymentPlan(payments, paid, paid.subtract(total));
  }
}
