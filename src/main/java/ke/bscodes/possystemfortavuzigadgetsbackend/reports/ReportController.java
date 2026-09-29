package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ShopProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.DashboardDtos.Dashboard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * /api/reports (owner and manager - VIEW_REPORTS): the dashboard, each report as JSON, and each
 * report as a PDF or CSV download. Dates are Nairobi days (?from=2026-09-01&to=2026-09-28).
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAuthority('VIEW_REPORTS')")
public class ReportController {

  private final ReportService service;
  private final ShopProperties shop;
  private final Clock clock;

  public ReportController(ReportService service, ShopProperties shop, Clock clock) {
    this.service = service;
    this.shop = shop;
    this.clock = clock;
  }

  @GetMapping("/dashboard")
  public Dashboard dashboard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.dashboard(ReportRange.of(from, to, clock));
  }

  @GetMapping("/{name}")
  public ReportTable report(
      @PathVariable String name,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.table(reportName(name), ReportRange.of(from, to, clock));
  }

  /** ?format=pdf (the default). */
  @GetMapping("/{name}/export")
  public ResponseEntity<byte[]> exportPdf(
      @PathVariable String name,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "pdf") String format) {
    if (!format.equals("pdf")) {
      throw ApiException.badRequest("Choose PDF or CSV.");
    }
    ReportRange range = ReportRange.of(from, to, clock);
    ReportTable table = service.table(reportName(name), range);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName(table, range, "pdf")))
        .body(PdfReport.render(table, shop, clock.instant()));
  }

  /** ?format=csv: streamed to the browser for Excel or Google Sheets. */
  @GetMapping(value = "/{name}/export", params = "format=csv")
  public ResponseEntity<StreamingResponseBody> exportCsv(
      @PathVariable String name,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    ReportRange range = ReportRange.of(from, to, clock);
    ReportTable table = service.table(reportName(name), range);
    StreamingResponseBody body =
        out -> CsvReport.write(table, new OutputStreamWriter(out, StandardCharsets.UTF_8));
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName(table, range, "csv")))
        .body(body);
  }

  private static String fileName(ReportTable table, ReportRange range, String extension) {
    return "tavuzi-" + table.name() + "-" + range.from() + "-to-" + range.to() + "." + extension;
  }

  private static ReportName reportName(String slug) {
    return ReportName.fromSlug(slug).orElseThrow(() -> ApiException.notFound("No such report."));
  }

  private static String attachment(String filename) {
    return ContentDisposition.attachment().filename(filename).build().toString();
  }
}
