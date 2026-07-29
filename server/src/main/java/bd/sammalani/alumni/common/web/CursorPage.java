package bd.sammalani.alumni.common.web;

import java.util.List;

/**
 * One page of a keyset-paginated read.
 * <p>
 * {@code nextCursor} is opaque by contract: clients hand it back unread. That is
 * what lets the encoding change — to a different sort key, or a compound one —
 * without a client release.
 *
 * @param total rows matching the filter, not rows in this page, so a UI can say
 *              "10 / 143" rather than implying that ten is all there is
 */
public record CursorPage<T>(List<T> items, String nextCursor, long total) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, long total) {
        return new CursorPage<>(items, nextCursor, total);
    }

    public <R> CursorPage<R> map(java.util.function.Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor, total);
    }
}
