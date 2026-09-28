package ke.bscodes.possystemfortavuzigadgetsbackend.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** A stable JSON shape for paginated lists (Spring's Page JSON may change between versions). */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
