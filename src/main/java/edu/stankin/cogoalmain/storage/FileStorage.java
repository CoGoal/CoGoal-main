package edu.stankin.cogoalmain.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Stores uploaded proof files. Callers keep only the returned key (in {@code proof.file_url}).
 */
public interface FileStorage {

    /**
     * @param originalFilename name sent by the client; only its extension is kept
     * @return key identifying the stored file
     */
    String store(InputStream content, String originalFilename) throws IOException;

    /** The stored file, or empty if it no longer exists. */
    Optional<Resource> load(String key);

    /** Deletes the file; does nothing if it does not exist. */
    void delete(String key) throws IOException;
}
