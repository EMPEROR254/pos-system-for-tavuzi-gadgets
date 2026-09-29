package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.Product;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductUnit;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.SerialType;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.VatCategory;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.Payment;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.PaymentMethod;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.PriceTier;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.Sale;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleCalculator;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.TaxProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.Vat;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EtimsMapperTest {

  private final Instant soldAt = Instant.parse("2026-09-28T07:15:00Z"); // 10:15 in Nairobi

  private Product product(long id, String name, VatCategory vat, String price) {
    Product p = new Product(soldAt);
    p.update(name, null, null, vat, SerialType.NONE, 0, BigDecimal.ONE, true, soldAt);
    ReflectionTestUtils.setField(p, "id", id);
    ProductUnit unit = new ProductUnit(p);
    unit.update("Piece", 1, null, new BigDecimal(price), null, 0);
    ReflectionTestUtils.setField(unit, "id", id * 10);
    p.getUnits().add(unit);
    return p;
  }

  /** A charger (16% VAT, 100 off) and an exempt cable, paid mostly by M-Pesa. */
  private Sale sale() {
    Vat vat = new Vat(new TaxProperties(new BigDecimal("16")));
    var cart =
        SaleCalculator.price(
            List.of(
                new LineRequest(1L, 10L, 1, new BigDecimal("100"), null),
                new LineRequest(2L, 20L, 2, null, null)),
            Map.of(
                1L, product(1, "Charger 20W", VatCategory.STANDARD, "1260"),
                2L, product(2, "USB cable", VatCategory.EXEMPT, "200")),
            PriceTier.RETAIL,
            vat,
            soldAt);
    User cashier = new User("mary", "Mary Wanjiru", "x", Role.CASHIER, soldAt);
    Sale sale =
        new Sale("TG000042", "ref", cashier, PriceTier.RETAIL, "Grace", "254712345678", soldAt);
    int no = 1;
    for (var priced : cart.lines()) {
      sale.addLine(new SaleLine(sale, no++, priced));
    }
    sale.addPayment(
        new Payment(sale, PaymentMethod.MPESA_TILL, new BigDecimal("1300"), "SJK", soldAt));
    sale.addPayment(new Payment(sale, PaymentMethod.CASH, new BigDecimal("260"), null, soldAt));
    return sale;
  }

  @Test
  void saleBecomesAnInvoiceWithTaxPerType() {
    Map<String, Object> inv = EtimsMapper.sale(sale(), 7, "4321150000");

    assertThat(inv)
        .containsEntry("invcNo", 7L)
        .containsEntry("rcptTyCd", "S")
        .containsEntry("orgInvcNo", 0L);
    assertThat(inv).containsEntry("pmtTyCd", "06"); // mobile money paid the most
    assertThat(inv).containsEntry("salesDt", "20260928").containsEntry("cfmDt", "20260928101500");
    assertThat((BigDecimal) inv.get("taxblAmtB")).isEqualByComparingTo("1160.00");
    assertThat((BigDecimal) inv.get("taxAmtB")).isEqualByComparingTo("160.00");
    assertThat((BigDecimal) inv.get("taxRtB")).isEqualByComparingTo("16");
    assertThat((BigDecimal) inv.get("taxblAmtA")).isEqualByComparingTo("400.00");
    assertThat((BigDecimal) inv.get("taxAmtA")).isEqualByComparingTo("0");
    assertThat((BigDecimal) inv.get("totAmt")).isEqualByComparingTo("1560.00");
    assertThat(inv).containsEntry("totItemCnt", 2);

    @SuppressWarnings("unchecked")
    var item = ((List<Map<String, Object>>) inv.get("itemList")).getFirst();
    assertThat(item).containsEntry("itemCd", "KE2NTU0000001").containsEntry("taxTyCd", "B");
    assertThat((BigDecimal) item.get("splyAmt")).isEqualByComparingTo("1260");
    assertThat((BigDecimal) item.get("dcAmt")).isEqualByComparingTo("100");
    assertThat((BigDecimal) item.get("taxblAmt")).isEqualByComparingTo("1160");
  }

  @Test
  void returnBecomesACreditNotePointingAtTheSale() {
    Sale sale = sale();
    SaleLine cable = sale.getLines().get(1);
    Map<String, Object> credit =
        EtimsMapper.refund(
            sale,
            9,
            7,
            List.of(
                new EtimsMapper.Item(
                    cable, 1, new BigDecimal("200.00"), BigDecimal.ZERO.setScale(2))),
            soldAt,
            "4321150000");
    assertThat(credit).containsEntry("rcptTyCd", "R").containsEntry("orgInvcNo", 7L);
    assertThat((BigDecimal) credit.get("totAmt")).isEqualByComparingTo("200.00");
    assertThat(credit).containsEntry("totItemCnt", 1);
  }

  @Test
  void itemCodesFollowKraFormat() {
    assertThat(EtimsMapper.itemCode(123)).isEqualTo("KE2NTU0000123");
  }
}
