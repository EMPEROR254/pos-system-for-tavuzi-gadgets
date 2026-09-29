package ke.bscodes.possystemfortavuzigadgetsbackend.reports;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import ke.bscodes.possystemfortavuzigadgetsbackend.receipts.ShopProperties;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Column;
import ke.bscodes.possystemfortavuzigadgetsbackend.reports.ReportTable.Figure;

/**
 * Renders a report to an A4 PDF (OpenPDF): shop name, title, dates, headline figures, the table
 * with a totals row, and page numbers. Wide reports are printed landscape.
 */
public final class PdfReport {

  private static final Color HEADER_BG = new Color(0xCC, 0xFB, 0xF1);
  private static final Color TOTALS_BG = new Color(0xF1, 0xF5, 0xF9);
  private static final DateTimeFormatter PRINTED =
      DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH).withZone(ReportRange.NAIROBI);

  private PdfReport() {}

  public static byte[] render(ReportTable table, ShopProperties shop, Instant printedAt) {
    boolean wide = table.columns().size() > 6;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document(wide ? PageSize.A4.rotate() : PageSize.A4, 32, 32, 32, 40);
    PdfWriter.getInstance(doc, out);
    HeaderFooter footer =
        new HeaderFooter(
            new Phrase(
                table.title() + " · printed " + PRINTED.format(printedAt) + " · page ", font(7)),
            true);
    footer.setAlignment(Element.ALIGN_RIGHT);
    footer.setBorder(Rectangle.NO_BORDER);
    doc.setFooter(footer);
    doc.open();

    String shopName = shop.name() == null || shop.name().isBlank() ? "Shop" : shop.name();
    doc.add(new Paragraph(shopName, bold(10)));
    doc.add(new Paragraph(table.title(), bold(16)));
    doc.add(new Paragraph(table.period(), font(10)));
    if (!table.summary().isEmpty()) {
      StringBuilder figures = new StringBuilder();
      for (Figure f : table.summary()) {
        if (!figures.isEmpty()) {
          figures.append("    ");
        }
        figures.append(f.label()).append(": ").append(ReportFormat.forPeople(f.value(), f.kind()));
      }
      Paragraph p = new Paragraph(figures.toString(), bold(10));
      p.setSpacingBefore(6);
      doc.add(p);
    }

    PdfPTable grid = new PdfPTable(table.columns().size());
    grid.setWidthPercentage(100);
    grid.setSpacingBefore(10);
    grid.setHeaderRows(1);
    for (Column c : table.columns()) {
      grid.addCell(cell(c.label(), c, bold(8), HEADER_BG));
    }
    if (table.rows().isEmpty()) {
      PdfPCell empty = new PdfPCell(new Phrase("Nothing in these dates.", font(8)));
      empty.setColspan(table.columns().size());
      empty.setPadding(6);
      grid.addCell(empty);
    }
    for (Map<String, Object> row : table.rows()) {
      for (Column c : table.columns()) {
        grid.addCell(cell(ReportFormat.forPeople(row.get(c.key()), c.kind()), c, font(8), null));
      }
    }
    if (table.totals() != null && !table.rows().isEmpty()) {
      for (Column c : table.columns()) {
        grid.addCell(
            cell(
                ReportFormat.forPeople(table.totals().get(c.key()), c.kind()),
                c,
                bold(8),
                TOTALS_BG));
      }
    }
    doc.add(grid);

    if (table.note() != null) {
      Paragraph note = new Paragraph(table.note(), font(8));
      note.setSpacingBefore(8);
      doc.add(note);
    }
    doc.close();
    return out.toByteArray();
  }

  private static PdfPCell cell(String text, Column column, Font font, Color background) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(4);
    cell.setBorderColor(new Color(0xE2, 0xE8, 0xF0));
    cell.setHorizontalAlignment(column.numeric() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
    if (background != null) {
      cell.setBackgroundColor(background);
    }
    return cell;
  }

  private static Font font(float size) {
    return FontFactory.getFont(FontFactory.HELVETICA, size);
  }

  private static Font bold(float size) {
    return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size);
  }
}
