package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.ProductRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.ProductResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.SellableProduct;
import ke.bscodes.possystemfortavuzigadgetsbackend.products.ProductDtos.UnitRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.tax.Vat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product catalogue: management (owner/manager) and quick lookup for the till. Rules: every
 * product has exactly one base unit (factor 1); SKUs and barcodes are unique.
 */
@Service
public class ProductService {

  private static final int TILL_RESULTS = 20;

  private final ProductRepository products;
  private final Vat vat;
  private final Clock clock;

  public ProductService(ProductRepository products, Vat vat, Clock clock) {
    this.products = products;
    this.vat = vat;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<ProductResponse> search(
      String query, String category, Boolean active, Pageable pageable) {
    return load(query, category, active, false, pageable).map(ProductResponse::from);
  }

  /** Till search: an exact barcode/SKU match wins; otherwise name matches, active only. */
  @Transactional(readOnly = true)
  public List<SellableProduct> lookup(String query) {
    String q = query == null ? "" : query.trim();
    if (q.isEmpty()) {
      return List.of();
    }
    List<Product> exact = products.findActiveByCode(q);
    if (!exact.isEmpty()) {
      return exact.stream()
          .map(p -> SellableProduct.from(p, vat.ratePercent(p.getVatCategory())))
          .toList();
    }
    return load(q, null, true, false, PageRequest.of(0, TILL_RESULTS, Sort.by("name")))
        .map(p -> SellableProduct.from(p, vat.ratePercent(p.getVatCategory())))
        .getContent();
  }

  @Transactional(readOnly = true)
  public ProductResponse get(long id) {
    return ProductResponse.from(find(id));
  }

  @Transactional(readOnly = true)
  public List<String> categories() {
    return products.categories();
  }

  @Transactional
  public ProductResponse create(ProductRequest request) {
    String sku = blankToNull(request.sku());
    if (sku != null && products.existsBySkuIgnoreCase(sku)) {
      throw ApiException.conflict("Another product already uses that SKU.");
    }
    checkUnits(request.units(), null);
    Product product = new Product(clock.instant());
    apply(product, request);
    return ProductResponse.from(products.save(product));
  }

  @Transactional
  public ProductResponse update(long id, ProductRequest request) {
    Product product = find(id);
    String sku = blankToNull(request.sku());
    if (sku != null && products.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
      throw ApiException.conflict("Another product already uses that SKU.");
    }
    checkUnits(request.units(), id);
    apply(product, request);
    return ProductResponse.from(products.saveAndFlush(product));
  }

  private void apply(Product product, ProductRequest r) {
    product.update(
        r.name().trim(),
        blankToNull(r.sku()),
        blankToNull(r.category()),
        r.vatCategory(),
        r.serialType(),
        r.warrantyMonths(),
        money(r.costPrice()),
        r.active(),
        clock.instant());
    product.setReorderLevel(r.reorderLevel() == null ? 0 : r.reorderLevel());
    syncUnits(product, r.units());
  }

  /**
   * Updates units in place (keeping their ids, so carts on other tills stay valid), adds new ones
   * and removes those no longer listed. Old sales keep a copy of the unit name and factor.
   */
  private void syncUnits(Product product, List<UnitRequest> requested) {
    Map<Long, ProductUnit> existing =
        product.getUnits().stream()
            .filter(u -> u.getId() != null)
            .collect(Collectors.toMap(ProductUnit::getId, Function.identity()));
    List<ProductUnit> result = new ArrayList<>();
    for (int i = 0; i < requested.size(); i++) {
      UnitRequest r = requested.get(i);
      ProductUnit unit = r.id() != null ? existing.get(r.id()) : null;
      if (unit == null) {
        unit = new ProductUnit(product);
      }
      unit.update(
          r.name().trim(),
          r.factor(),
          blankToNull(r.barcode()),
          money(r.retailPrice()),
          r.wholesalePrice() == null ? null : money(r.wholesalePrice()),
          i);
      result.add(unit);
    }
    product.getUnits().retainAll(result);
    result.stream().filter(u -> !product.getUnits().contains(u)).forEach(product.getUnits()::add);
    product.getUnits().sort(Comparator.comparingInt(result::indexOf));
  }

  private void checkUnits(List<UnitRequest> units, Long productId) {
    long baseUnits = units.stream().filter(u -> u.factor() == 1).count();
    if (baseUnits != 1) {
      throw ApiException.badRequest(
          "Exactly one unit must have a factor of 1 (the base unit, e.g. Piece).");
    }
    Set<String> names = new HashSet<>();
    Set<String> barcodes = new HashSet<>();
    for (UnitRequest u : units) {
      if (!names.add(u.name().trim().toLowerCase(Locale.ROOT))) {
        throw ApiException.badRequest("Two units have the same name.");
      }
      String barcode = blankToNull(u.barcode());
      if (barcode != null && !barcodes.add(barcode)) {
        throw ApiException.badRequest("Two units have the same barcode.");
      }
    }
    if (!barcodes.isEmpty() && products.barcodeUsedElsewhere(barcodes, productId)) {
      throw ApiException.conflict("A barcode is already used by another product.");
    }
  }

  /** Products with their units, for other features (e.g. the stock list with lowOnly). */
  public Page<Product> load(
      String query, String category, Boolean active, boolean lowOnly, Pageable pageable) {
    String q = query == null ? "" : query.trim();
    String pattern =
        q.isEmpty()
            ? null
            : "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "\\_") + "%";
    Page<Long> ids =
        products.searchIds(pattern, q.isEmpty() ? null : q, category, active, lowOnly, pageable);
    Map<Long, Product> byId =
        products.findWithUnits(ids.getContent()).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    List<Product> ordered = ids.getContent().stream().map(byId::get).toList();
    return new PageImpl<>(ordered, pageable, ids.getTotalElements());
  }

  private Product find(long id) {
    return products.findById(id).orElseThrow(() -> ApiException.notFound("No such product."));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
