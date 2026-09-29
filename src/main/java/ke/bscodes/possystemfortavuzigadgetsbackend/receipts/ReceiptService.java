package ke.bscodes.possystemfortavuzigadgetsbackend.receipts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ke.bscodes.possystemfortavuzigadgetsbackend.etims.EtimsService;
import ke.bscodes.possystemfortavuzigadgetsbackend.hardware.PrinterService;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ReceiptView.VatLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.LineView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleService;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Builds receipts for a sale and sends them to the receipt printer when one is set up. */
@Service
public class ReceiptService {

  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

  private final SaleService sales;
  private final ShopProperties shop;
  private final PrinterService printer;
  private final EtimsService etims;

  public ReceiptService(
      SaleService sales, ShopProperties shop, PrinterService printer, EtimsService etims) {
    this.sales = sales;
    this.shop = shop;
    this.printer = printer;
    this.etims = etims;
  }

  public ReceiptView receipt(AuthUser actor, long saleId) {
    return build(sales.get(actor, saleId), etims.forReceipt(saleId));
  }

  /** Prints on the shop's receipt printer; opens the cash drawer when cash was taken. */
  public void print(AuthUser actor, long saleId) {
    ReceiptView view = receipt(actor, saleId);
    boolean cash = view.sale().payments().stream().anyMatch(p -> "CASH".equals(p.method()));
    printer.print(EscPosReceipt.render(view, printer.current().width()), cash);
  }

  ReceiptView build(SaleView sale, EtimsService.ReceiptInfo etimsInfo) {
    List<String> details = new ArrayList<>();
    addIfSet(details, shop.location());
    addIfSet(details, isSet(shop.phone()) ? "Tel: " + shop.phone() : null);
    addIfSet(details, isSet(shop.kraPin()) ? "KRA PIN: " + shop.kraPin() : null);
    List<String> pay = new ArrayList<>();
    addIfSet(pay, isSet(shop.tillNumber()) ? "M-Pesa Buy Goods Till: " + shop.tillNumber() : null);
    if (isSet(shop.paybillNumber())) {
      pay.add(
          "M-Pesa Paybill: "
              + shop.paybillNumber()
              + (isSet(shop.paybillAccount()) ? " Acc: " + shop.paybillAccount() : ""));
    }
    return new ReceiptView(
        shop.name(),
        details,
        pay,
        sale,
        vatBreakdown(sale.lines()),
        shop.returnPolicy(),
        shop.footer(),
        etimsInfo);
  }

  /** Groups lines by tax code (A exempt, B 16%, C zero-rated). */
  static List<VatLine> vatBreakdown(List<LineView> lines) {
    Map<String, VatLine> byCode = new TreeMap<>();
    for (LineView l : lines) {
      byCode.merge(
          l.vatCode(),
          new VatLine(
              l.vatCode(),
              l.vatRate(),
              l.lineTotal().subtract(l.vatAmount()),
              l.vatAmount(),
              l.lineTotal()),
          (a, b) ->
              new VatLine(
                  a.code(),
                  a.ratePercent(),
                  a.taxable().add(b.taxable()),
                  a.vat().add(b.vat()),
                  a.gross().add(b.gross())));
    }
    return List.copyOf(byCode.values());
  }

  private static void addIfSet(List<String> list, String value) {
    if (isSet(value)) {
      list.add(value);
    }
  }

  private static boolean isSet(String value) {
    return value != null && !value.isBlank();
  }
}
