package edu.stankin.cogoalmain.storage;

import edu.stankin.cogoalmain.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps files in a local directory ({@code app.storage.path}) under generated names,
 * so client-supplied names can never point outside that directory.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?");

    private final Path root;

    public LocalFileStorage(AppProperties properties) throws IOException {
        this.root = properties.storage().path().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(InputStream content, String originalFilename) throws IOException {
        String key = UUID.randomUUID() + extensionOf(originalFilename);
        Files.copy(content, root.resolve(key));
        return key;
    }

    @Override
    public Optional<Resource> load(String key) {
        Path file = resolve(key);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid file key: " + key);
        }
        return root.resolve(key);
    }

    private static String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        if (extension == null) {
            return "";
        }
        extension = extension.toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(extension).matches() ? "." + extension : "";
    }
}
