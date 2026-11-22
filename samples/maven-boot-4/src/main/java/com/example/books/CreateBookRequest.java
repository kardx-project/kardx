package com.example.books;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A new book.
 *
 * @param title title
 * @param author author name
 * @param pages number of pages
 */
public record CreateBookRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String author,
    @Positive int pages
) {
}
