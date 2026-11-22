package com.example.books;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final Map<Long, BookResponse> books = new ConcurrentHashMap<>(
        Map.of(1L, new BookResponse(1, "Domain-Driven Design", "Eric Evans", 560)));
    private final AtomicLong ids = new AtomicLong(1);

    @GetMapping
    public List<BookResponse> list() {
        return books.values()
            .stream()
            .sorted((a, b) -> Long.compare(a.id(), b.id()))
            .toList();
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable long id) {
        BookResponse book = books.get(id);

        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No book " + id);
        }

        return book;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody CreateBookRequest request) {
        BookResponse book = new BookResponse(ids.incrementAndGet(), request.title(), request.author(),
            request.pages());

        books.put(book.id(), book);

        return book;
    }
}
