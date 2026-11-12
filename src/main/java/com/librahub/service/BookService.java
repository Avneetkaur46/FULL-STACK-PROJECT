
package com.librahub.service;

import com.librahub.entity.Book;
import com.librahub.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Book not found with ID: " + id));
    }
    public List<Book> searchByTitle(String title) {
        return bookRepository.findByTitleContainingIgnoreCase(title);
    }

    @Transactional
    public Book addBook(Book book) {
        book.setAvailableCopies(book.getTotalCopies());
        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBook(Long id, Book updatedBook) {
        Book existingBook = getBookById(id);

        if (updatedBook.getTotalCopies() == null ||
                updatedBook.getTotalCopies() < 0) {
            throw new IllegalArgumentException(
                    "Total copies cannot be negative");
        }

        int currentlyBorrowed =
                existingBook.getTotalCopies()
                        - existingBook.getAvailableCopies();

        if (updatedBook.getTotalCopies() < currentlyBorrowed) {
            throw new IllegalArgumentException(
                    "Total copies cannot be less than copies currently borrowed");
        }

        existingBook.setTitle(updatedBook.getTitle());
        existingBook.setAuthor(updatedBook.getAuthor());
        existingBook.setCategory(updatedBook.getCategory());
        existingBook.setIsbn(updatedBook.getIsbn());

        existingBook.setTotalCopies(updatedBook.getTotalCopies());
        existingBook.setAvailableCopies(
                updatedBook.getTotalCopies() - currentlyBorrowed);

        return bookRepository.save(existingBook);
    }


    public void deleteBook(Long id) {
        Book book = getBookById(id);
        bookRepository.delete(book);
    }
    public List<Book> getBooksByCategory(String category) {
        return bookRepository.findByCategoryIgnoreCase(category);
    }
}
