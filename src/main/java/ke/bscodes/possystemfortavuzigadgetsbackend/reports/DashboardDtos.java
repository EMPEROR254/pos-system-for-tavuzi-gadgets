package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The owner's dashboard: key numbers and the data behind its charts. */
public final class DashboardDtos {

  private DashboardDtos() {}

  public record Dashboard(
      LocalDate from,
      LocalDate to,
      Kpis kpis,
      List<DayPoint> days,
      List<Slice> payments,
      List<Slice> topProducts,
      List<Slice> cashiers) {}

  /** Sales figures are for the chosen dates; stock and open shifts are as of now. */
  public record Kpis(
      BigDecimal netSales,
      long salesCount,
      BigDecimal averageSale,
      BigDecimal profit,
      BigDecimal vat,
      BigDecimal refunds,
      BigDecimal cashDifference,
      long lowStock,
      BigDecimal stockValue,
      long openShifts) {}

  public record DayPoint(LocalDate day, BigDecimal sales, BigDecimal profit, long salesCount) {}

  public record Slice(String label, BigDecimal amount) {}
}
