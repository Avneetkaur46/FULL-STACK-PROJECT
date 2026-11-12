
package com.librahub.service;

import com.librahub.entity.Book;
import com.librahub.repository.BookRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

@Component
public class CsvBookImporter implements ApplicationRunner {

    private final BookRepository bookRepository;
    private final ResourceLoader resourceLoader;

    public CsvBookImporter(
            BookRepository bookRepository,
            ResourceLoader resourceLoader) {
        this.bookRepository = bookRepository;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        Resource resource =
                resourceLoader.getResource("classpath:books.csv");

        if (!resource.exists()) {
            System.out.println("books.csv not found. Import skipped.");
            return;
        }

        int imported = 0;
        int skipped = 0;

        try (
                Reader reader = new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                );
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
                        .parse(reader)
        ) {
            for (CSVRecord row : parser) {
                try {
                    String title = row.get("title").trim();
                    String author = row.get("author").trim();
                    String category = row.get("category").trim();
                    String isbn = row.get("isbn").trim();
                    int totalCopies = Integer.parseInt(
                            row.get("totalCopies").trim()
                    );

                    if (title.isBlank()
                            || author.isBlank()
                            || category.isBlank()
                            || isbn.isBlank()
                            || totalCopies < 0) {
                        skipped++;
                        continue;
                    }

                    if (bookRepository.findByIsbn(isbn).isPresent()) {
                        skipped++;
                        continue;
                    }

                    Book book = new Book(
                            title,
                            author,
                            category,
                            isbn,
                            totalCopies,
                            totalCopies
                    );

                    bookRepository.save(book);
                    imported++;

                } catch (Exception e) {
                    skipped++;
                    System.err.println(
                            "Skipping invalid CSV row: " + row.getRecordNumber()
                    );
                }
            }
        }

        System.out.println(
                "CSV import finished. Imported: " + imported
                        + ", skipped: " + skipped
        );
    }
}
