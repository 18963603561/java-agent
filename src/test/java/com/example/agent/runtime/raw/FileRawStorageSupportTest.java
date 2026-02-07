package com.example.agent.runtime.raw;

import java.nio.file.Files;
import java.nio.file.Path;

import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileRawStorageSupport 文件读写测试。
 */
class FileRawStorageSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndReadShouldWork() {
        FileRawStorageSupport support = new FileRawStorageSupport();

        String filename = support.writeText(tempDir.toString(), "model:planner", "hello world");
        assertNotNull(filename);
        assertTrue(filename.endsWith(".txt"));
        assertTrue(Files.exists(tempDir.resolve(filename)));

        String content = support.readText(tempDir.toString(), filename);
        assertEquals("hello world", content);

        String url = support.buildAccessUrl("http://127.0.0.1:8080/api/v1/raw/files", filename);
        assertEquals("http://127.0.0.1:8080/api/v1/raw/files/" + filename, url);
    }
}

