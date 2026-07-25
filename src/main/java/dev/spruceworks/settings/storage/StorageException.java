package dev.spruceworks.settings.storage;

/** Wraps any storage-backend failure so callers never see a raw SQLException. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
