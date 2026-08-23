package com.projekt.app.dtos;

import java.util.List;
import java.util.Map;

public class BookReport {
  /** Transformed, sorted entries that passed every filter stage. */
  public List<String> entries;
  /** Single string produced by folding all entries with the {@code accumulator}. */
  public String aggregate;
  /** Number of entries that passed the filter, grouped by author. */
  public Map<String, Long> countByAuthor;

  public BookReport(List<String> entries, String aggregate, Map<String, Long> countByAuthor) {
    this.entries = entries;
    this.aggregate = aggregate;
    this.countByAuthor = countByAuthor;
  }

  @Override
  public String toString() {
    return "BookReport{entries=" + entries.size() + ", aggregate='" + aggregate + '\''
        + ", countByAuthor=" + countByAuthor + '}';
  }
}
