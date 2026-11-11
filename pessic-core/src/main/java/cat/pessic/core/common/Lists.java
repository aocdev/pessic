package cat.pessic.core.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Small helpers for the immutable lists kept in a profile. */
public final class Lists {
    private Lists() {}

    /** A copy of {@code list} with {@code item} replacing the element with the same id, or added at the end. */
    public static <T> List<T> upsert(List<T> list, T item, Function<T, String> id) {
        List<T> out = new ArrayList<>(list);
        for (int i = 0; i < out.size(); i++) {
            if (id.apply(out.get(i)).equals(id.apply(item))) {
                out.set(i, item);
                return out;
            }
        }
        out.add(item);
        return out;
    }
}
