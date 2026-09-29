package ke.bscodes.possystemfortavuzigadgetsbackend.etims;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * An eTIMS invoice waiting for, or signed by, KRA. PENDING ones are retried with growing gaps (the
 * internet may be down for hours - selling carries on); REJECTED means KRA refused it and a person
 * must look at the reason.
 */
@Entity
@Table(name = "etims_invoices")
public class EtimsInvoice {

  public enum Kind {
    SALE,
    REFUND
  }

  public enum Status {
    PENDING,
    SENT,
    REJECTED
  }

  /** Wait before try 2, 3, 4...; then every 30 minutes. */
  private static final Duration[] BACKOFF = {
    Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(15)
  };

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private long invoiceNo;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  private Long saleId;
  private Long returnId;
  private Long originalInvoiceNo;

  @Enumerated(EnumType.STRING)
  private Status status = Status.PENDING;

  private int attempts;
  private Instant nextAttemptAt;
  private String lastError;
  private Long scuReceiptNo;
  private Long totalReceiptNo;
  private String internalData;
  private String receiptSignature;
  private String scuId;
  private String mrcNo;
  private String scuDate;
  private Instant createdAt;
  private Instant sentAt;

  protected EtimsInvoice() {}

  public EtimsInvoice(
      long invoiceNo, Kind kind, long saleId, Long returnId, Long originalInvoiceNo, Instant now) {
    this.invoiceNo = invoiceNo;
    this.kind = kind;
    this.saleId = saleId;
    this.returnId = returnId;
    this.originalInvoiceNo = originalInvoiceNo;
    this.createdAt = now;
    this.nextAttemptAt = now;
  }

  /** KRA signed it: keep what goes on the receipt. */
  public void markSent(EtimsClient.Signed signed, Instant now) {
    status = Status.SENT;
    attempts++;
    lastError = null;
    scuReceiptNo = signed.receiptNo();
    totalReceiptNo = signed.totalReceiptNo();
    internalData = signed.internalData();
    receiptSignature = signed.signature();
    scuId = signed.scuId();
    mrcNo = signed.mrcNo();
    scuDate = signed.scuDate();
    sentAt = now;
  }

  /** Could not reach KRA: try again later. */
  public void retryLater(String error, Instant now) {
    attempts++;
    lastError = trim(error);
    Duration wait = attempts <= BACKOFF.length ? BACKOFF[attempts - 1] : Duration.ofMinutes(30);
    nextAttemptAt = now.plus(wait);
  }

  /** Waiting for something else first (e.g. the original sale's invoice before a refund). */
  public void postpone(String reason, Instant until) {
    lastError = trim(reason);
    nextAttemptAt = until;
  }

  public void reject(String error) {
    attempts++;
    status = Status.REJECTED;
    lastError = trim(error);
  }

  /** A person fixed the cause (e.g. a product's item class): send again now. */
  public void retryNow(Instant now) {
    status = Status.PENDING;
    nextAttemptAt = now;
  }

  private static String trim(String s) {
    return s == null ? null : s.substring(0, Math.min(300, s.length()));
  }

  public Long getId() {
    return id;
  }

  public long getInvoiceNo() {
    return invoiceNo;
  }

  public Kind getKind() {
    return kind;
  }

  public Long getSaleId() {
    return saleId;
  }

  public Long getReturnId() {
    return returnId;
  }

  public Long getOriginalInvoiceNo() {
    return originalInvoiceNo;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Long getScuReceiptNo() {
    return scuReceiptNo;
  }

  public Long getTotalReceiptNo() {
    return totalReceiptNo;
  }

  public String getInternalData() {
    return internalData;
  }

  public String getReceiptSignature() {
    return receiptSignature;
  }

  public String getScuId() {
    return scuId;
  }

  public String getMrcNo() {
    return mrcNo;
  }

  public String getScuDate() {
    return scuDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
