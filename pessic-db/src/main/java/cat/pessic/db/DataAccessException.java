package cat.pessic.db;

/** Unchecked wrapper for database errors. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
