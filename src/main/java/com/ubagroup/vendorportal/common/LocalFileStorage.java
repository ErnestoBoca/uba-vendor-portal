package com.ubagroup.vendorportal.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(String originalName, byte[] content) throws IOException {
        String safe = originalName == null ? "file" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        LocalDate d = LocalDate.now();
        String key = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + UUID.randomUUID() + "-" + safe;
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key;
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        Path p = resolve(storageKey);
        if (!Files.exists(p)) {
            throw new IOException("Ficheiro nao encontrado: " + storageKey);
        }
        return new FileSystemResource(p);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("storage_key invalida");
        }
        return p;
    }
}
