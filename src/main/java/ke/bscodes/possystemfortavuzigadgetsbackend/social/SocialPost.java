package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A post: title, description and one picture (kept as JPEG). Lists never load this entity - they
 * use projections - so the pictures are only read when a network needs them.
 */
@Entity
@Table(name = "social_posts")
public class SocialPost {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;
  private String description;
  private String caption;
  private byte[] image;
  private String imageToken;
  private int imageWidth;
  private int imageHeight;
  private Long createdBy;
  private Instant createdAt;

  protected SocialPost() {}

  public SocialPost(
      String title,
      String description,
      String caption,
      ImageProcessor.Prepared image,
      String imageToken,
      long createdBy,
      Instant now) {
    this.title = title;
    this.description = description;
    this.caption = caption;
    this.image = image.jpeg();
    this.imageWidth = image.width();
    this.imageHeight = image.height();
    this.imageToken = imageToken;
    this.createdBy = createdBy;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getCaption() {
    return caption;
  }

  public byte[] getImage() {
    return image;
  }

  public String getImageToken() {
    return imageToken;
  }
}
