package com.projekt.app.utils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import com.projekt.app.dtos.BookReport;
import com.projekt.app.entities.Book;

/**
 * Utility class for common operations.
 * This class provides methods that can be used across the application for various utility functions.
 */
public class Utilities {

  /**
   * Immutable value object returned by {@link #analyseBooks}.
   * Carries the filtered+transformed entries, a single aggregated label,
   * and a per-author breakdown.
   */


  /**
   * Analyses a list of {@link Book} objects through a multi-stage functional pipeline
   * and returns a {@link BookReport}.
   *
   * <p>Pipeline stages:
   * <ol>
   *   <li><b>{@link Predicate} (primary filter)</b> – keeps only books that match
   *       the initial condition, e.g. {@code b -> b.getPublishedYear() > 2000}.</li>
   *   <li><b>{@link Predicate} (secondary filter, composed)</b> – AND-ed on top of the
   *       primary filter via {@link Predicate#and}, e.g. an ISBN validity check.</li>
   *   <li><b>{@link UnaryOperator}</b> – normalises each surviving book in-place before
   *       mapping, e.g. trimming whitespace from fields.</li>
   *   <li><b>{@link Function} (mapper)</b> – converts each normalised {@link Book} to
   *       a display string.</li>
   *   <li><b>{@link UnaryOperator} (string decorator)</b> – post-processes every mapped
   *       string, e.g. wrapping it in brackets or uppercasing it.</li>
   *   <li><b>{@link Comparator}</b> – sorts the decorated strings before aggregation.</li>
   *   <li><b>{@link BiFunction}</b> – combines each entry with its 1-based position index
   *       to produce a numbered label.</li>
   *   <li><b>{@link BinaryOperator}</b> – folds the numbered labels into a single
   *       aggregated string (e.g. joining with a separator).</li>
   *   <li><b>{@link Consumer}</b> – side-effect called once per final entry (e.g. logging).</li>
   *   <li><b>{@link Supplier}</b> – lazily provides a fallback aggregate when nothing
   *       survives the filter stages.</li>
   * </ol>
   *
   * @param books           source list of books (must not be {@code null})
   * @param primaryFilter   first {@link Predicate} applied to the stream
   * @param secondaryFilter second {@link Predicate} AND-composed with the first
   * @param normaliser      {@link UnaryOperator} that may mutate / replace each book before mapping
   * @param mapper          {@link Function} converting a {@link Book} to a raw string
   * @param decorator       {@link UnaryOperator} post-processing each mapped string
   * @param order           {@link Comparator} used to sort decorated strings
   * @param indexer         {@link BiFunction}{@code <String, Integer, String>} that adds a positional label
   * @param accumulator     {@link BinaryOperator} that folds all indexed strings into one aggregate
   * @param entryConsumer   {@link Consumer} invoked for every final entry
   * @param emptyFallback   {@link Supplier} whose value becomes the aggregate when the list is empty
   * @return a populated {@link BookReport}
   */


  public static BookReport analyseBooks(List<Book> books, Predicate<Book> primaryFilter,
      Predicate<Book> secondaryFilter, UnaryOperator<Book> normaliser,
      Function<Book, String> mapper, UnaryOperator<String> decorator, Comparator<String> order,
      BiFunction<String, Integer, String> indexer, BinaryOperator<String> accumulator,
      Consumer<String> entryConsumer, Supplier<String> emptyFallback) {

    // --- Stage 1 & 2: compose both filters, normalise, map, decorate, sort ---
    Predicate<Book> combinedFilter = primaryFilter.and(secondaryFilter);

    List<String> decorated = books.stream().filter(combinedFilter).map(normaliser).map(mapper)
        .map(decorator).sorted(order).collect(Collectors.toList());

    // --- Stage 3: per-author breakdown (computed before indexing changes labels) ---
    // Re-stream filtered+normalised books to count by author
    Map<String, Long> countByAuthor = books.stream().filter(combinedFilter).map(normaliser)
        .collect(Collectors.groupingBy(Book::getAuthor, Collectors.counting()));

    // --- Stage 4: index each entry with its 1-based position via BiFunction ---
    List<String> indexed = new java.util.ArrayList<>();
    for (int i = 0; i < decorated.size(); i++) {
      indexed.add(indexer.apply(decorated.get(i), i + 1));
    }

    // --- Stage 5: side-effect via Consumer, then fold via BinaryOperator ---
    indexed.forEach(entryConsumer);

    Optional<String> aggregate = indexed.stream().reduce(accumulator);

    return new BookReport(indexed, aggregate.orElseGet(emptyFallback), countByAuthor);
  }

  public static boolean isValidIsbn13(String isbn) {
    // Remove hyphens if any
    isbn = replaceChars("-", "",isbn);

    // Must be 13 digits and only digits
    if (isbn.length() != 13 || !isbn.matches("\\d{13}")) {
      return false;
    }

    // Validate check digit
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = Character.getNumericValue(isbn.charAt(i));
      sum += (i % 2 == 0) ? digit : digit * 3;
    }

    int expectedCheckDigit = (10 - (sum % 10)) % 10;
    int actualCheckDigit = Character.getNumericValue(isbn.charAt(12));

    return expectedCheckDigit == actualCheckDigit;
  }

  public static String replaceChars(String toChange, String changeTo, String inputStr) {

    LinkedList<Character> listStr = new LinkedList<>();
    for (char c : inputStr.toCharArray()) {
      listStr.add(c);
    }

    LinkedList<Character> temp = new LinkedList();
    int i = 0;
    int len = listStr.size();
    while (i < len) {
      if (listStr.get(i).equals(toChange.charAt(0))) {
        boolean found = true;
        int remI = i;
        for (int j = 0; j < toChange.length(); j++) {
          if (i > listStr.size() || !listStr.get(i).equals(toChange.charAt(j))) {
            found = false;
            break;
          }
          i++;
        }
        if (found) {
          for (char c : changeTo.toCharArray()) {
            temp.add(c);
          }
          i = remI + (toChange.length()) - 1;
        } else {
          i = remI;
          temp.add(listStr.get(i));
        }
      } else {
        temp.add(listStr.get(i));
      }
      i++;
    }

    return temp.stream().map(String::valueOf).collect(Collectors.joining());
  }
}
