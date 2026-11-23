package com.ubagroup.vendorportal.common;

import java.io.IOException;

import org.springframework.core.io.Resource;

/**
 * Abstraccao do armazenamento de ficheiros. Hoje: disco local (LocalFileStorage).
 * Amanha: partilha de rede / blob storage, sem mexer nos servicos (a BD guarda so a storage_key).
 */
public interface FileStorage {
    /** Guarda os bytes e devolve a storage_key. */
    String store(String originalName, byte[] content) throws IOException;

    Resource load(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
