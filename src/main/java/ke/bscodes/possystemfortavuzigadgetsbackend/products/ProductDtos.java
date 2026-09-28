package ke.bscodes.possystemfortavuzigadgetsbackend.products;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request and response bodies for /api/products. */
public final class ProductDtos {

  private ProductDtos() {}

  public record UnitRequest(
      Long id,
      @NotBlank(message = "Enter the unit name.")
          @Size(max = 40, message = "At most 40 characters.")
          String name,
      @Min(value = 1, message = "At least 1.") int factor,
      @Size(max = 50, message = "At most 50 characters.") String barcode,
      @NotNull(message = "Enter the retail price.")
          @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal retailPrice,
      @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal wholesalePrice) {}

  public record ProductRequest(
      @NotBlank(message = "Enter the product name.")
          @Size(max = 150, message = "At most 150 characters.")
          String name,
      @Size(max = 50, message = "At most 50 characters.") String sku,
      @Size(max = 60, message = "At most 60 characters.") String category,
      @NotNull(message = "Choose the VAT type.") VatCategory vatCategory,
      @NotNull(message = "Choose whether to record IMEI/serial.") SerialType serialType,
      @Min(value = 0, message = "Can't be negative.") @Max(value = 120, message = "At most 120.")
          int warrantyMonths,
      @NotNull(message = "Enter the cost price (0 if unknown).")
          @DecimalMin(value = "0", message = "Can't be negative.")
          @Digits(integer = 10, fraction = 2, message = "Up to 2 decimals.")
          BigDecimal costPrice,
      boolean active,
      @NotEmpty(message = "Add at least one selling unit.") @Valid List<UnitRequest> units) {}

  public record UnitResponse(
      long id,
      String name,
      int factor,
      String barcode,
      BigDecimal retailPrice,
      BigDecimal wholesalePrice) {

    static UnitResponse from(ProductUnit u) {
      return new UnitResponse(
          u.getId(),
          u.getName(),
          u.getFactor(),
          u.getBarcode(),
          u.getRetailPrice(),
          u.getWholesalePrice());
    }
  }

  /** Full product for the management screen (includes the cost price). */
  public record ProductResponse(
      long id,
      String name,
      String sku,
      String category,
      VatCategory vatCategory,
      SerialType serialType,
      int warrantyMonths,
      BigDecimal costPrice,
      boolean active,
      List<UnitResponse> units) {

    static ProductResponse from(Product p) {
      return new ProductResponse(
          p.getId(),
          p.getName(),
          p.getSku(),
          p.getCategory(),
          p.getVatCategory(),
          p.getSerialType(),
          p.getWarrantyMonths(),
          p.getCostPrice(),
          p.isActive(),
          p.getUnits().stream().map(UnitResponse::from).toList());
    }
  }

  /** What the till needs: no cost price; vatRate (percent) lets the till show VAT as it sells. */
  public record SellableProduct(
      long id,
      String name,
      String sku,
      VatCategory vatCategory,
      BigDecimal vatRate,
      SerialType serialType,
      int warrantyMonths,
      List<UnitResponse> units) {

    static SellableProduct from(Product p, BigDecimal vatRate) {
      return new SellableProduct(
          p.getId(),
          p.getName(),
          p.getSku(),
          p.getVatCategory(),
          vatRate,
          p.getSerialType(),
          p.getWarrantyMonths(),
          p.getUnits().stream().map(UnitResponse::from).toList());
    }
  }
}
