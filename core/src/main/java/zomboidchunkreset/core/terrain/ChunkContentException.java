package zomboidchunkreset.core.terrain;

/**
 * Thrown when the content of a chunk file cannot be decoded further: an object type this program
 * does not know how to skip, or data that does not follow the expected layout.
 */
public class ChunkContentException extends Exception {

    private static final long serialVersionUID = 1L;

    public ChunkContentException(String message) {
        super(message);
    }

    public ChunkContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
