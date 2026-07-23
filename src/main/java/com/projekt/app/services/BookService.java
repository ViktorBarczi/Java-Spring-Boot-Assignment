package com.projekt.app.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.projekt.app.dtos.BookCopyAvaliability;
import com.projekt.app.dtos.BookRequest;
import com.projekt.app.dtos.BookResponse;
import com.projekt.app.dtos.BookUpdate;
import com.projekt.app.entities.Book;
import com.projekt.app.entities.BookCopy;
import com.projekt.app.exceptions.BadRequestException;
import com.projekt.app.exceptions.NotFoundException;
import com.projekt.app.repositories.IBookCopyRepository;
import com.projekt.app.repositories.IBookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Service class for managing book-related operations.
 * Provides methods to retrieve, save, update, and delete books and their copies.
 * Implements the IBookService interface.
 */
@Service
public class BookService implements IBookService {

  @Autowired
  private IBookRepository bookRepository;

  @Autowired
  private IBookCopyRepository bookCopyRepository;

  /**
   * Retrieve all books from the database and map them to BookResponse DTOs.
   */
  @Cacheable(value = "books", key = "'all'")
  public List<BookResponse> getAllBooks() {
    List<Book> list = bookRepository.findAll();
    if (list.isEmpty()) {
      return new ArrayList<>();
    }
    return list.stream().map(book -> {
      BookResponse bookResponse = new BookResponse();
      bookResponse.setId(book.getId());
      bookResponse.setTitle(book.getTitle());
      bookResponse.setAuthor(book.getAuthor());
      bookResponse.setIsbn(book.getIsbn());
      bookResponse.setPublishedYear(book.getPublishedYear());
      return bookResponse;
    }).toList();
  }

  /**
   * Find a book by its ID or throw NotFoundException if not found.
   */
  @Cacheable(value = "books", key = "#id")
  public Book getBookById(Long id) {
    Book book = bookRepository.findById(id).orElse(null);
    if (book == null) {
      throw new NotFoundException("Book with id '" + id + "' does not exist.");
    }
    return book;
  }

  /**
   * Save a new book to the database after validating it doesn't already exist by title.
   */
  @CacheEvict(value = "books", key = "'all'")
  public Book saveBook(BookRequest BookRequest) throws BadRequestException {
    if (!bookRepository.findByTitle(BookRequest.getTitle()).isEmpty()) {
      throw new BadRequestException(
          "Book with title '" + BookRequest.getTitle() + "' already exists.");
    }
    Book book = new Book();
    book.setTitle(BookRequest.getTitle());
    book.setAuthor(BookRequest.getAuthor());
    book.setIsbn(BookRequest.getIsbn());
    book.setPublishedYear(BookRequest.getPublishedYear());
    return bookRepository.save(book);
  }

  /**
   * Delete a book and all its associated copies from the database.
   */
  @Caching(evict = {
      @CacheEvict(value = "books", key = "#id"),
      @CacheEvict(value = "books", key = "'all'")
  })
  public void deleteBook(Long id) {
    Optional<Book> book = bookRepository.findById(id);
    if (book != null) {
      for (BookCopy bookcopy : book.get().getCopies()) {
        bookCopyRepository.delete(bookcopy);
      }
      bookRepository.delete(book.get());
    } else {
      throw new NotFoundException("Book with id '" + id + "' does not exist.");
    }
  }

  /**
   * Update an existing book's title and/or published year.
   */
  @Caching(evict = {
      @CacheEvict(value = "books", key = "#id"),
      @CacheEvict(value = "books", key = "'all'")
  })
  public Book updateBook(Long id, BookUpdate bookUpdate) {
    Book existingBook = bookRepository.findById(id).orElse(null);
    if (existingBook != null) {
      existingBook.setTitle(bookUpdate.getTitle());
      existingBook.setPublishedYear(bookUpdate.getPublishedYear());
      return bookRepository.save(existingBook);
    } else {
      throw new NotFoundException("Book with id '" + id + "' does not exist.");
    }
  }

  /**
   * Add a new copy to the specified book.
   */
  public BookCopy postBookCopy(Long bookId) {
    BookCopy bookCopy = new BookCopy();
    Book book = bookRepository.findById(bookId).orElse(null);
    if (book == null) {
      throw new NotFoundException("Book with id '" + bookId + "' does not exist.");
    }
    bookCopy.setBookId(book);
    book.getCopies().add(bookCopy);
    bookRepository.save(book);
    return bookCopyRepository.save(bookCopy);
  }

  /**
   * Get all copies of a specific book.
   */
  public List<BookCopy> getBookCopies(Long bookId) {
    Book book = bookRepository.findById(bookId).orElse(null);
    if (book == null) {
      throw new NotFoundException("Book with id '" + bookId + "' does not exist.");
    }
    return book.getCopies();
  }

  /**
   * Update the availability status of a specific book copy.
   */
  public BookCopy updateBookCopy(Long bookId, Long BookCopyId,
      BookCopyAvaliability bookCopyAvaliability) {

    Book book = bookRepository.findById(bookId).orElse(null);
    if (book == null) {
      throw new NotFoundException("Book with id '" + bookId + "' does not exist.");
    }

    List<BookCopy> bookCopies = book.getCopies();
    for (BookCopy bookCopy : bookCopies) {
      if (bookCopy.getId().equals(BookCopyId)) {
        bookCopy.setAvailable(bookCopyAvaliability.isAvailable());
        bookRepository.save(book); // Save changes in parent
        return bookCopyRepository.save(bookCopy); // Save updated copy
      }
    }

    throw new NotFoundException("Book copy with id '" + BookCopyId + "' does not exist.");
  }

}
