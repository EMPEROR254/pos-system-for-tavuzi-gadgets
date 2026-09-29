package ke.bscodes.possystemfortavuzigadgetsbackend.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.ApprovalService;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.ReturnLineRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.ReturnRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.ReturnView;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Permission;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockService;
import ke.bscodes.possystemfortavuzigadgetsbackend.warranty.Serials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns and exchanges. Spec: "No exchanges unless supervised by me" - so every return needs the
 * owner (REFUND permission), either signed in or approving with their password. An exchange is a
 * return followed by a new sale.
 */
@Service
public class ReturnService {

  private final SaleRepository sales;
  private final SaleReturnRepository returns;
  private final ApprovalService approvals;
  private final StockService stock;
  private final Clock clock;

  public ReturnService(
      SaleRepository sales,
      SaleReturnRepository returns,
      ApprovalService approvals,
      StockService stock,
      Clock clock) {
    this.sales = sales;
    this.returns = returns;
    this.approvals = approvals;
    this.stock = stock;
    this.clock = clock;
  }

  @Transactional
  public ReturnView process(AuthUser actor, long saleId, ReturnRequest request) {
    Sale sale = sales.findDetail(saleId).orElseThrow(() -> ApiException.notFound("No such sale."));
    long approver =
        approvals.authorise(actor, Permission.REFUND, request.approval(), "A return or exchange");
    Instant now = clock.instant();
    Map<Long, SaleLine> lines =
        sale.getLines().stream().collect(Collectors.toMap(SaleLine::getId, Function.identity()));
    SaleReturn saleReturn =
        new SaleReturn(
            sale, actor.id(), approver, request.reason().trim(), request.refundMethod(), now);
    Set<Long> seen = new HashSet<>();
    List<StockService.Change> stockBack = new ArrayList<>();
    for (ReturnLineRequest r : request.lines()) {
      SaleLine line = lines.get(r.saleLineId());
      if (line == null || !seen.add(r.saleLineId())) {
        throw ApiException.badRequest("A returned item is not on this receipt.");
      }
      if (r.quantity() > line.returnableQuantity()) {
        throw ApiException.badRequest(
            line.getProductName() + ": only " + line.returnableQuantity() + " can be returned.");
      }
      List<String> backOnShelf = returnSerials(line, r, now);
      stockBack.add(
          new StockService.Change(
              line.getProductId(), r.quantity() * line.getUnitFactor(), backOnShelf));
      int before = line.getReturnedQuantity();
      int after = before + r.quantity();
      saleReturn.addLine(
          line,
          r.quantity(),
          share(line.getLineTotal(), after, line.getQuantity())
              .subtract(share(line.getLineTotal(), before, line.getQuantity())),
          share(line.getVatAmount(), after, line.getQuantity())
              .subtract(share(line.getVatAmount(), before, line.getQuantity())));
      line.recordReturn(r.quantity());
    }
    stock.recordReturn(stockBack, sale.getReceiptNo(), actor.id());
    sale.refreshReturnStatus();
    SaleReturn saved = returns.save(saleReturn);
    return new ReturnView(
        saved.getId(),
        sale.getReceiptNo(),
        saved.getRefundAmount(),
        saved.getVatAmount(),
        sale.getStatus().name());
  }

  /**
   * The part of an amount for the first n of total items. Taking differences of these means the
   * pieces of several partial returns always add up to exactly the line total (no lost cents).
   */
  static BigDecimal share(BigDecimal amount, int n, int total) {
    return amount
        .multiply(BigDecimal.valueOf(n))
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  /**
   * Marks which IMEIs/serials came back (they lose their warranty) and returns them, so they go
   * back on the shelf.
   */
  private List<String> returnSerials(SaleLine line, ReturnLineRequest r, Instant now) {
    List<SoldSerial> active =
        line.getSerials().stream().filter(s -> s.getReturnedAt() == null).toList();
    if (active.isEmpty()) {
      return List.of();
    }
    int needed = r.quantity() * line.getUnitFactor();
    List<SoldSerial> chosen;
    if (needed == active.size()) {
      chosen = active;
    } else {
      Set<String> wanted =
          r.serials() == null
              ? Set.of()
              : r.serials().stream().map(Serials::normalise).collect(Collectors.toSet());
      chosen = active.stream().filter(s -> wanted.contains(s.getSerial())).toList();
      if (chosen.size() != needed) {
        throw ApiException.badRequest(
            line.getProductName() + ": choose which " + needed + " IMEI/serial numbers came back.");
      }
    }
    chosen.forEach(s -> s.markReturned(now));
    return chosen.stream().map(SoldSerial::getSerial).toList();
  }
}
