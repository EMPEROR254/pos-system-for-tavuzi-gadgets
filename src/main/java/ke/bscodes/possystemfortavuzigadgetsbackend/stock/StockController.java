package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductService;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockCountService.CountEntry;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockCountService.CountView;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.AdjustRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.MovementView;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.StockRow;
import ke.bscodes.possystemfortavuzigadgetsbackend.stock.StockDtos.StockSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
 * /api/stock: levels, low-stock alerts, history, adjustments and stock counts (owner and manager -
 * MANAGE_STOCK). The till reads IMEIs in stock (MAKE_SALE).
 */
@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAuthority('MANAGE_STOCK')")
public class StockController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductService productService;
  private final ProductRepository products;
  private final StockService stock;
  private final StockCountService countService;
  private final StockMovementRepository movements;
  private final StockSerialRepository serials;
  private final StockCountRepository counts;

  public StockController(
      ProductService productService,
      ProductRepository products,
      StockService stock,
      StockCountService countService,
      StockMovementRepository movements,
      StockSerialRepository serials,
      StockCountRepository counts) {
    this.productService = productService;
    this.products = products;
    this.stock = stock;
    this.countService = countService;
    this.movements = movements;
    this.serials = serials;
    this.counts = counts;
  }

  /** GET /api/stock?q=&category=&low=true */
  @GetMapping
  @Transactional(readOnly = true)
  public PageResponse<StockRow> levels(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "false") boolean low,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    PageRequest pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by("name").and(Sort.by("id")));
    String cat = category == null || category.isBlank() ? null : category;
    return PageResponse.of(productService.load(q, cat, true, low, pageable).map(StockRow::from));
  }

  /** Numbers for the stock screen and the home page: low-stock count, value, last count. */
  @GetMapping("/summary")
  @PreAuthorize("hasAnyAuthority('MANAGE_STOCK', 'VIEW_REPORTS')")
  @Transactional(readOnly = true)
  public StockSummary summary() {
    return new StockSummary(
        products.countLowStock(), products.stockValue(), counts.lastFinishedAt());
  }

  @GetMapping("/{productId}/history")
  public PageResponse<MovementView> history(
      @PathVariable long productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        movements.history(
            productId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE))));
  }

  /** IMEIs/serials on the shelf, for the till to pick from. */
  @GetMapping("/{productId}/serials")
  @PreAuthorize("hasAuthority('MAKE_SALE')")
  public List<String> serialsInStock(@PathVariable long productId) {
    return serials.inStock(productId);
  }

  @PostMapping("/adjustments")
  public Map<String, Integer> adjust(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody AdjustRequest request) {
    return Map.of("stockOnHand", stock.adjust(request, me.id()));
  }

  @GetMapping("/counts")
  public List<CountView> counts() {
    return countService.recent();
  }

  @PostMapping("/counts")
  @ResponseStatus(HttpStatus.CREATED)
  public CountView startCount(
      @AuthenticationPrincipal AuthUser me,
      @RequestBody(required = false) Map<String, String> body) {
    return countService.start(body == null ? null : body.get("category"), me.id());
  }

  @GetMapping("/counts/{id}")
  public CountView count(@PathVariable long id) {
    return countService.get(id);
  }

  @PutMapping("/counts/{id}")
  public CountView record(@PathVariable long id, @RequestBody List<CountEntry> entries) {
    return countService.record(id, entries);
  }

  @PostMapping("/counts/{id}/finish")
  public CountView finish(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    return countService.finish(id, me.id());
  }

  @PostMapping("/counts/{id}/cancel")
  public CountView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    return countService.cancel(id, me.id());
  }
}
