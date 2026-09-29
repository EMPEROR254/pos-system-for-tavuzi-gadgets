package ke.bscodes.possystemfortavuzigadgetsbackend.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;

/**
 * Reads the M-Pesa statement CSV downloaded from the M-Pesa business portal (Till or Paybill). The
 * file starts with a few lines about the account; the table starts at the row with "Receipt No" and
 * "Paid In". Only money received (Paid In) on completed transactions is kept.
 */
public final class MpesaStatementParser {

  private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
  private static final List<DateTimeFormatter> TIME_FORMATS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
          DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

  /** One money-in line of the statement. */
  public record StatementRow(
      String receiptNo,
      Instant completedAt,
      BigDecimal amount,
      String otherParty,
      String account,
      String details) {}

  private MpesaStatementParser() {}

  public static List<StatementRow> parse(String csv) {
    List<List<String>> table = csvRows(csv);
    int header = -1;
    for (int i = 0; i < table.size(); i++) {
      String joined = String.join("|", table.get(i)).toLowerCase(Locale.ROOT);
      if (joined.contains("receipt no") && joined.contains("paid in")) {
        header = i;
        break;
      }
    }
    if (header < 0) {
      throw ApiException.badRequest(
          "This doesn't look like an M-Pesa statement: no 'Receipt No.' and 'Paid In' columns.");
    }
    List<String> names = table.get(header).stream().map(MpesaStatementParser::key).toList();
    int receipt = column(names, "receiptno");
    int time = column(names, "completiontime");
    int paidIn = column(names, "paidin");
    int status = names.indexOf("transactionstatus");
    int other = names.indexOf("otherpartyinfo");
    int account = names.indexOf("a/cno");
    int details = names.indexOf("details");

    List<StatementRow> rows = new ArrayList<>();
    for (List<String> cells : table.subList(header + 1, table.size())) {
      String code = cell(cells, receipt).toUpperCase(Locale.ROOT);
      String amountText = cell(cells, paidIn).replace(",", "");
      if (code.isEmpty() || amountText.isEmpty()) {
        continue;
      }
      if (status >= 0
          && !cell(cells, status).isEmpty()
          && !cell(cells, status).equalsIgnoreCase("Completed")) {
        continue;
      }
      BigDecimal amount;
      try {
        amount = new BigDecimal(amountText).setScale(2, RoundingMode.HALF_UP);
      } catch (NumberFormatException e) {
        continue;
      }
      if (amount.signum() <= 0) {
        continue;
      }
      rows.add(
          new StatementRow(
              code,
              time(cell(cells, time)),
              amount,
              blankToNull(cell(cells, other)),
              blankToNull(cell(cells, account)),
              blankToNull(cell(cells, details))));
    }
    return rows;
  }

  private static Instant time(String text) {
    for (DateTimeFormatter f : TIME_FORMATS) {
      try {
        return LocalDateTime.parse(text.trim(), f).atZone(NAIROBI).toInstant();
      } catch (DateTimeParseException e) {
        // try the next format
      }
    }
    throw ApiException.badRequest("Could not read the date '" + text + "' in the statement.");
  }

  /** "Receipt No." → "receiptno", "A/C No." → "a/cno". */
  private static String key(String header) {
    return header.toLowerCase(Locale.ROOT).replaceAll("[\\s.]", "");
  }

  private static int column(List<String> names, String key) {
    int i = names.indexOf(key);
    if (i < 0) {
      throw ApiException.badRequest("The statement has no '" + key + "' column.");
    }
    return i;
  }

  private static String cell(List<String> cells, int i) {
    return i >= 0 && i < cells.size() ? cells.get(i).trim() : "";
  }

  private static String blankToNull(String s) {
    return s.isEmpty() ? null : s;
  }

  /** Minimal CSV: commas, double quotes ("" inside quotes), CRLF or LF line ends. */
  static List<List<String>> csvRows(String csv) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
          i++;
        }
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
      } else {
        cell.append(c);
      }
    }
    if (!cell.isEmpty() || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }
}
