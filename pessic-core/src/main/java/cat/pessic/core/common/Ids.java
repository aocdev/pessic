package cat.pessic.core.common;

import java.util.UUID;

/** Short random ids with a type prefix: "p" profile, "m" movement, "f" fixed payment, "a" annual item. */
public final class Ids {
    private Ids() {}

    public static String next(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
