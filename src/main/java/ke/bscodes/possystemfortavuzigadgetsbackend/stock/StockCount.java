package ke.bscodes.possystemfortavuzigadgetsbackend.stock;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A stock count (spec: weekly). Only one can be open at a time. */
@Entity
@Table(name = "stock_counts")
public class StockCount {

  public enum Status {
    OPEN,
    FINISHED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private Status status = Status.OPEN;

  private String category;
  private Long startedBy;
  private Instant startedAt;
  private Long finishedBy;
  private Instant finishedAt;

  @OneToMany(mappedBy = "count", cascade = CascadeType.ALL)
  @OrderBy("id")
  private List<StockCountLine> lines = new ArrayList<>();

  protected StockCount() {}

  public StockCount(String category, long startedBy, Instant now) {
    this.category = category;
    this.startedBy = startedBy;
    this.startedAt = now;
  }

  public void close(Status status, long userId, Instant now) {
    this.status = status;
    this.finishedBy = userId;
    this.finishedAt = now;
  }

  public Long getId() {
    return id;
  }

  public Status getStatus() {
    return status;
  }

  public String getCategory() {
    return category;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public List<StockCountLine> getLines() {
    return lines;
  }
}
