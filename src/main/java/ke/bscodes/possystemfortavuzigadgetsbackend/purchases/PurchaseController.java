package ke.bscodes.possystemfortavuzigadgetsbackend.purchases;

import jakarta.validation.Valid;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PaymentRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.PurchaseView;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.StatementLine;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.SupplierRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.purchases.PurchaseDtos.SupplierView;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/suppliers and /api/purchases (owner and manager - MANAGE_STOCK). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('MANAGE_STOCK')")
public class PurchaseController {

  private static final int MAX_PAGE_SIZE = 100;

  private final PurchaseService service;

  public PurchaseController(PurchaseService service) {
    this.service = service;
  }

  @GetMapping("/suppliers")
  public PageResponse<SupplierView> suppliers(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        service.searchSuppliers(
            q,
            active,
            PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by("name"))));
  }

  @PostMapping("/suppliers")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierView createSupplier(@Valid @RequestBody SupplierRequest request) {
    return service.createSupplier(request);
  }

  @GetMapping("/suppliers/{id}")
  public SupplierView supplier(@PathVariable long id) {
    return service.supplier(id);
  }

  @PutMapping("/suppliers/{id}")
  public SupplierView updateSupplier(
      @PathVariable long id, @Valid @RequestBody SupplierRequest request) {
    return service.updateSupplier(id, request);
  }

  @GetMapping("/suppliers/{id}/statement")
  public List<StatementLine> statement(@PathVariable long id) {
    return service.statement(id);
  }

  @PostMapping("/suppliers/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierView pay(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable long id,
      @Valid @RequestBody PaymentRequest request) {
    return service.pay(id, request, me.id());
  }

  @GetMapping("/purchases")
  public PageResponse<PurchaseSummary> purchases(
      @RequestParam(required = false) Long supplierId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        service.list(
            supplierId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE))));
  }

  @PostMapping("/purchases")
  @ResponseStatus(HttpStatus.CREATED)
  public PurchaseView receive(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody PurchaseRequest request) {
    return service.receive(request, me.id());
  }

  @GetMapping("/purchases/{id}")
  public PurchaseView purchase(@PathVariable long id) {
    return service.get(id);
  }
}
