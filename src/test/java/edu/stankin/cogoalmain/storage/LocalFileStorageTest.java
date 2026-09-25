package edu.stankin.cogoalmain.storage;

import edu.stankin.cogoalmain.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path dir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalFileStorage(new AppProperties(
                new AppProperties.Coins(100),
                new AppProperties.Deadlines(false, Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofHours(1)),
                new AppProperties.Storage(dir, DataSize.ofMegabytes(10)),
                new AppProperties.Internal("test-internal-token-123456")));
    }

    @Test
    void storesUnderGeneratedNameKeepingExtension() throws IOException {
        String key = storage.store(stream("photo"), "My Photo.JPG");

        assertThat(key).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(Files.readString(dir.resolve(key))).isEqualTo("photo");
    }

    @Test
    void clientFilenameCannotEscapeStorageDirectory() throws IOException {
        String key = storage.store(stream("x"), "../../etc/passwd");

        assertThat(dir.resolve(key)).exists();
        assertThat(key).doesNotContain("/", "..");
    }

    @Test
    void dropsSuspiciousExtension() throws IOException {
        assertThat(storage.store(stream("x"), "file.tar/../../x")).matches("[0-9a-f-]{36}");
        assertThat(storage.store(stream("x"), "no-extension")).matches("[0-9a-f-]{36}");
    }

    @Test
    void deletesStoredFile() throws IOException {
        String key = storage.store(stream("x"), "a.pdf");

        storage.delete(key);
        storage.delete(key); // second delete is a no-op

        assertThat(dir.resolve(key)).doesNotExist();
    }

    @Test
    void rejectsKeysThatWereNotIssuedByStorage() {
        assertThatThrownBy(() -> storage.delete("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
