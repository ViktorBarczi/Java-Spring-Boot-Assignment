package com.projekt.app.dtos;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for returning book details.
 * This class is used to encapsulate the data returned when fetching book details.
 */
@Data
public class BookResponse implements Serializable {
  private Long id;
  private String title;
  private String author;
  private String isbn;
  private Integer publishedYear;
}
