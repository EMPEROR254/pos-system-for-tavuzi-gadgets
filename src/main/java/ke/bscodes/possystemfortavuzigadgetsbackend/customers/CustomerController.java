package ke.bscodes.possystemfortavuzigadgetsbackend.customers;

import jakarta.validation.Valid;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerBrief;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.customers.CustomerDtos.CustomerView;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleDtos.SaleSummary;
import ke.bscodes.possystemfortavuzigadgetsbackend.sales.SaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/customers: the till can find and add customers (MAKE_SALE); the full list, editing and
 * purchase history are for the owner and manager (MANAGE_CUSTOMERS).
 */
@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAuthority('MANAGE_CUSTOMERS')")
public class CustomerController {

  private static final int MAX_PAGE_SIZE = 100;

  private final CustomerService service;
  private final SaleRepository sales;

  public CustomerController(CustomerService service, SaleRepository sales) {
    this.service = service;
    this.sales = sales;
  }

  /** GET /api/customers/lookup?q=0712 or a name - for the till. */
  @GetMapping("/lookup")
  @PreAuthorize("hasAuthority('MAKE_SALE')")
  public List<CustomerBrief> lookup(@RequestParam(defaultValue = "") String q) {
    return service.quickSearch(q);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('MAKE_SALE')")
  public CustomerView create(@Valid @RequestBody CustomerRequest request) {
    return service.create(request);
  }

  @GetMapping
  public PageResponse<CustomerView> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean optedIn,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        service.search(
            q,
            optedIn,
            PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by("name"))));
  }

  @GetMapping("/{id}")
  public CustomerView get(@PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public CustomerView update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
    return service.update(id, request);
  }

  /** The customer's purchases, newest first. */
  @GetMapping("/{id}/sales")
  public PageResponse<SaleSummary> purchases(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    service.get(id);
    return PageResponse.of(
        sales.forCustomer(
            id,
            PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"))));
  }
}
