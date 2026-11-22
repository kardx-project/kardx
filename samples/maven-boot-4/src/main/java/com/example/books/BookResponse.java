package com.example.books;

/**
 * A book in the catalog.
 *
 * @param id book ID
 * @param title title
 * @param author author name
 * @param pages number of pages
 */
public record BookResponse(long id, String title, String author, int pages) {
}
