/*
 * Copyright 2021 - 2026 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.transform.base.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Consumer;

import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.SharedFileAccessException;
import de.acosix.alfresco.transform.base.SharedFileAccessor;

/**
 * @author Axel Faust
 */
public class LocalSharedFileAccessorImpl implements SharedFileAccessor
{

    private static final String TYPE_PSEUDO_EXTENSION = ".type";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSharedFileAccessorImpl.class);

    private final Path storageDir;

    private final Path downloadDir;

    public LocalSharedFileAccessorImpl(final Context context)
    {
        this.storageDir = context.createTempFileSubDirectory("sfsStorage");
        this.downloadDir = context.createTempFileSubDirectory("sfsDownloads");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path retrieveAsTemporyFile(final String fileReference, final Consumer<String> contentTypeConsumer)
    {
        LOGGER.debug("Retrieving shared file for reference {}", fileReference);

        if (fileReference == null || fileReference.contains("/") || fileReference.startsWith("."))
        {
            throw new SharedFileAccessException(HttpStatus.BAD_REQUEST_400, "Invalid file reference: " + fileReference);
        }

        final Path file = this.storageDir.resolve(fileReference);
        if (Files.isRegularFile(file) && !fileReference.endsWith(TYPE_PSEUDO_EXTENSION))
        {
            long size = -1;
            Path downloadFile = null;
            String contentType = "application/octet-stream";

            try
            {
                final Path contentTypeFile = this.storageDir.resolve(fileReference + TYPE_PSEUDO_EXTENSION);
                if (Files.isRegularFile(contentTypeFile))
                {
                    contentType = Files.readString(contentTypeFile, StandardCharsets.UTF_8);
                }
                contentTypeConsumer.accept(contentType);

                size = Files.size(file);
                downloadFile = Files.createFile(this.downloadDir.resolve(fileReference));
                Files.copy(file, downloadFile, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final IOException ioex)
            {
                final long usableSpace = this.downloadDir.toFile().getUsableSpace();
                if (size != -1 && downloadFile != null && usableSpace <= size)
                {
                    LOGGER.error("Not enough space available to store {} bytes in {}", size, downloadFile);
                    throw new SharedFileAccessException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the shared file",
                            ioex);
                }
                LOGGER.error("Failed to retrieve shared file {}", fileReference);
                throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve file from Shared File Store",
                        ioex);
            }

            LOGGER.debug("Read shared file {} to {} with {} bytes and {} as content type", fileReference, downloadFile, size, contentType);

            return downloadFile;
        }
        else
        {
            throw new SharedFileAccessException(HttpStatus.NOT_FOUND_404, "Failed to retrieve file from Shared File Store");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String saveFile(final Path file, final String contentType)
    {
        LOGGER.debug("Storing {} as shared file", file);
        try
        {
            final String fileReference = UUID.randomUUID().toString();

            final long size = Files.size(file);

            final Path contentTypeFile = this.storageDir.resolve(fileReference + TYPE_PSEUDO_EXTENSION);
            Files.writeString(contentTypeFile, contentType, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

            final Path storageFile = this.storageDir.resolve(fileReference);
            try
            {
                Files.copy(file, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final IOException ioex)
            {
                final long usableSpace = storageFile.toFile().getUsableSpace();
                if (usableSpace <= size)
                {
                    LOGGER.error("Not enough spasce available to store {} bytes in {}", size, storageFile);
                    throw new SharedFileAccessException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the shared file",
                            ioex);
                }
                throw ioex;
            }

            return fileReference;
        }
        catch (final IOException ioex)
        {
            LOGGER.error("Failed to store {} as shared file", file);
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to store file in Shared File Store", ioex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(final String fileReference)
    {
        LOGGER.debug("Deleting shared file {}", fileReference);

        if (fileReference == null || fileReference.contains("/") || fileReference.startsWith("."))
        {
            throw new SharedFileAccessException(HttpStatus.BAD_REQUEST_400, "Invalid file reference: " + fileReference);
        }

        try
        {
            final Path file = this.storageDir.resolve(fileReference);
            final boolean deletedFile = Files.deleteIfExists(file);

            final Path contentTypeFile = this.storageDir.resolve(fileReference + TYPE_PSEUDO_EXTENSION);
            final boolean deletedTypeMarker = Files.deleteIfExists(contentTypeFile);

            if (deletedFile || deletedTypeMarker)
            {
                LOGGER.debug("Deleted shared file {}", fileReference);
            }
            else
            {
                LOGGER.debug("Shared file {} never existed or has already been deleted", fileReference);
            }
        }
        catch (final IOException ioex)
        {
            LOGGER.error("Failed to delete shared file {}", fileReference);
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to delete file in Shared File Store", ioex);
        }
    }
}
