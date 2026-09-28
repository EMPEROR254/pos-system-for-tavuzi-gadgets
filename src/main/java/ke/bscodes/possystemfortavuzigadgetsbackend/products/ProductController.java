package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import jakarta.validation.Valid;
import java.util.List;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.ProductRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.ProductResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.SellableProduct;
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
 * /api/products: the till's quick lookup (anyone who sells) and catalogue management (owner and
 * manager - MANAGE_PRODUCTS).
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  /** GET /api/products/lookup?q=samsung or a scanned barcode. */
  @GetMapping("/lookup")
  @PreAuthorize("hasAuthority('MAKE_SALE')")
  public List<SellableProduct> lookup(@RequestParam(defaultValue = "") String q) {
    return service.lookup(q);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('MANAGE_PRODUCTS')")
  public PageResponse<ProductResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "asc") String dir) {
    Sort sort =
        Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, "name")
            .and(Sort.by("id"));
    PageRequest pageable =
        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
    String cat = category == null || category.isBlank() ? null : category;
    return PageResponse.of(service.search(q, cat, active, pageable));
  }

  @GetMapping("/categories")
  @PreAuthorize("hasAuthority('MANAGE_PRODUCTS')")
  public List<String> categories() {
    return service.categories();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('MANAGE_PRODUCTS')")
  public ProductResponse get(@PathVariable long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('MANAGE_PRODUCTS')")
  public ProductResponse create(@Valid @RequestBody ProductRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('MANAGE_PRODUCTS')")
  public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
    return service.update(id, request);
  }
}
