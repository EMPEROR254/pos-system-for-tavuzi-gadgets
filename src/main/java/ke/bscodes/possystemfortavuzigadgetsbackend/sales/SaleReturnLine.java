package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** How many of one sale line came back, and its share of the refund and VAT. */
@Entity
@Table(name = "sale_return_lines")
public class SaleReturnLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "return_id")
  private SaleReturn saleReturn;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_line_id")
  private SaleLine saleLine;

  private int quantity;
  private BigDecimal amount;
  private BigDecimal vatAmount;

  protected SaleReturnLine() {}

  SaleReturnLine(
      SaleReturn saleReturn, SaleLine saleLine, int quantity, BigDecimal amount, BigDecimal vat) {
    this.saleReturn = saleReturn;
    this.saleLine = saleLine;
    this.quantity = quantity;
    this.amount = amount;
    this.vatAmount = vat;
  }
}
