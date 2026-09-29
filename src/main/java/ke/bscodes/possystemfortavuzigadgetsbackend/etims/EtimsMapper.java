package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.Payment;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.PaymentMethod;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.Sale;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleLine;

/**
 * Turns a sale (or a return) into the eTIMS OSCU "saveSales" request, and a product into
 * "saveItems". All KRA field names live here, so they can be checked against KRA's OSCU
 * specification in one place during sandbox certification.
 *
 * <p>Amounts are VAT-inclusive, like our prices. Tax types: A exempt, B 16%, C zero-rated (D
 * non-VAT and E 8% are not used by this shop yet).
 */
public final class EtimsMapper {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(NAIROBI);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(NAIROBI);
  private static final List<String> TAX_TYPES = List.of("A", "B", "C", "D", "E");

  /** One invoice line: from a sale line, or the returned part of it. */
  public record Item(SaleLine line, int quantity, BigDecimal total, BigDecimal vat) {}

  private EtimsMapper() {}

  /**
   * KRA item code: country (KE) + type (2 = finished goods) + packaging (NT) + quantity unit (U) +
   * a 7-digit number (our product id).
   */
  public static String itemCode(long productId) {
    return "KE2NTU%07d".formatted(productId);
  }

  public static Map<String, Object> item(Product p, String itemClass, String taxCode) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("itemCd", itemCode(p.getId()));
    m.put("itemClsCd", itemClass);
    m.put("itemTyCd", "2");
    m.put("itemNm", p.getName());
    m.put("orgnNatCd", "KE");
    m.put("pkgUnitCd", "NT");
    m.put("qtyUnitCd", "U");
    m.put("taxTyCd", taxCode);
    m.put(
        "dftPrc",
        p.getUnits().isEmpty() ? BigDecimal.ZERO : p.getUnits().getFirst().getRetailPrice());
    m.put("isrcAplcbYn", "N");
    m.put("useYn", p.isActive() ? "Y" : "N");
    m.put("regrId", "pos");
    m.put("regrNm", "Tavuzi POS");
    m.put("modrId", "pos");
    m.put("modrNm", "Tavuzi POS");
    return m;
  }

  /** The whole sale as an invoice (receipt type S). */
  public static Map<String, Object> sale(Sale sale, long invoiceNo, String itemClass) {
    List<Item> items =
        sale.getLines().stream()
            .map(l -> new Item(l, l.getQuantity(), l.getLineTotal(), l.getVatAmount()))
            .toList();
    return invoice(sale, invoiceNo, 0L, "S", items, sale.getCreatedAt(), itemClass);
  }

  /** A return as a credit note (receipt type R) pointing at the sale's invoice. */
  public static Map<String, Object> refund(
      Sale sale,
      long invoiceNo,
      long originalInvoiceNo,
      List<Item> items,
      Instant when,
      String itemClass) {
    return invoice(sale, invoiceNo, originalInvoiceNo, "R", items, when, itemClass);
  }

  private static Map<String, Object> invoice(
      Sale sale,
      long invoiceNo,
      long originalInvoiceNo,
      String receiptType,
      List<Item> items,
      Instant when,
      String itemClass) {
    Map<String, BigDecimal> taxable = new LinkedHashMap<>();
    Map<String, BigDecimal> tax = new LinkedHashMap<>();
    Map<String, BigDecimal> rates = new LinkedHashMap<>();
    TAX_TYPES.forEach(
        t -> {
          taxable.put(t, zero());
          tax.put(t, zero());
          rates.put(t, zero());
        });
    List<Map<String, Object>> itemList = new ArrayList<>();
    int seq = 1;
    for (Item it : items) {
      SaleLine l = it.line();
      String code = l.getVatCategory().etimsCode();
      taxable.merge(code, it.total(), BigDecimal::add);
      tax.merge(code, it.vat(), BigDecimal::add);
      rates.put(code, l.getVatRate());
      BigDecimal supply = l.getUnitPrice().multiply(BigDecimal.valueOf(it.quantity()));
      BigDecimal discount = supply.subtract(it.total()).max(zero());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("itemSeq", seq++);
      m.put("itemCd", itemCode(l.getProductId()));
      m.put("itemClsCd", itemClass);
      m.put("itemNm", l.getProductName());
      m.put("pkgUnitCd", "NT");
      m.put("pkg", it.quantity());
      m.put("qtyUnitCd", "U");
      m.put("qty", it.quantity() * l.getUnitFactor());
      m.put("prc", l.getUnitPrice());
      m.put("splyAmt", supply);
      m.put(
          "dcRt",
          supply.signum() == 0
              ? zero()
              : discount.multiply(BigDecimal.valueOf(100)).divide(supply, 2, RoundingMode.HALF_UP));
      m.put("dcAmt", discount);
      m.put("taxTyCd", code);
      m.put("taxblAmt", it.total());
      m.put("taxAmt", it.vat());
      m.put("totAmt", it.total());
      itemList.add(m);
    }
    BigDecimal total = taxable.values().stream().reduce(zero(), BigDecimal::add);
    BigDecimal totalTax = tax.values().stream().reduce(zero(), BigDecimal::add);

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("invcNo", invoiceNo);
    m.put("orgInvcNo", originalInvoiceNo);
    m.put("custNm", sale.getCustomerName());
    m.put("salesTyCd", "N");
    m.put("rcptTyCd", receiptType);
    m.put("pmtTyCd", paymentType(sale.getPayments()));
    m.put("salesSttsCd", "02");
    m.put("cfmDt", DATE_TIME.format(when));
    m.put("salesDt", DATE.format(when));
    m.put("totItemCnt", itemList.size());
    for (String t : TAX_TYPES) {
      m.put("taxblAmt" + t, taxable.get(t));
      m.put("taxRt" + t, rates.get(t));
      m.put("taxAmt" + t, tax.get(t));
    }
    m.put("totTaxblAmt", total);
    m.put("totTaxAmt", totalTax);
    m.put("totAmt", total);
    m.put("prchrAcptcYn", "N");
    m.put("regrId", "pos");
    m.put("regrNm", sale.getCashier().getFullName());
    m.put("modrId", "pos");
    m.put("modrNm", sale.getCashier().getFullName());
    Map<String, Object> receipt = new LinkedHashMap<>();
    receipt.put("custMblNo", sale.getCustomerPhone());
    receipt.put("rptNo", sale.getReceiptNo());
    receipt.put("prchrAcptcYn", "N");
    m.put("receipt", receipt);
    m.put("itemList", itemList);
    return m;
  }

  /**
   * eTIMS takes one payment type per invoice: the method that paid the most. 01 cash, 05 card, 06
   * mobile money.
   */
  static String paymentType(List<Payment> payments) {
    PaymentMethod main =
        payments.stream()
            .max(Comparator.comparing(Payment::getAmount))
            .map(Payment::getMethod)
            .orElse(PaymentMethod.CASH);
    return switch (main) {
      case CASH -> "01";
      case CARD -> "05";
      case MPESA_TILL, MPESA_PAYBILL -> "06";
    };
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(2);
  }
}
