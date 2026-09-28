package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.PaymentView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SerialView;

/** Turns a Sale entity into the SaleView the web app and receipts use. */
final class SaleViews {

  private SaleViews() {}

  static SaleView of(Sale s) {
    return new SaleView(
        s.getId(),
        s.getReceiptNo(),
        s.getCreatedAt(),
        s.getCashier().getFullName(),
        s.getStatus().name(),
        s.getPriceTier().name(),
        s.getCustomerName(),
        s.getCustomerPhone(),
        s.getSubtotal(),
        s.getDiscountTotal(),
        s.getVatTotal(),
        s.getTotal(),
        s.getPaidTotal(),
        s.getChangeDue(),
        s.getLines().stream().map(SaleViews::line).toList(),
        s.getPayments().stream()
            .map(
                p ->
                    new PaymentView(
                        p.getMethod().name(),
                        p.getMethod().label(),
                        p.getAmount(),
                        p.getReference()))
            .toList());
  }

  private static LineView line(SaleLine l) {
    return new LineView(
        l.getId(),
        l.getProductName(),
        l.getUnitName(),
        l.getQuantity(),
        l.getReturnedQuantity(),
        l.getUnitPrice(),
        l.getDiscount(),
        l.getLineTotal(),
        l.getVatCategory().etimsCode(),
        l.getVatRate(),
        l.getVatAmount(),
        l.getWarrantyEndsAt(),
        l.getSerials().stream()
            .map(x -> new SerialView(x.getSerial(), x.getReturnedAt() != null))
            .toList());
  }
}
