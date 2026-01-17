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
package de.acosix.alfresco.transform.base;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 *
 * @author Axel Faust
 */
public interface SharedFileAccessor
{

    /**
     * Retrieves a file from the shared file storage for use as a local temporary file.
     *
     * @param fileReference
     *            the reference to the file to retrieve
     * @return the path the temporary file holding the content of the shared file
     */
    default Path retrieveAsTemporyFile(final String fileReference)
    {
        // just ignore the content type
        return retrieveAsTemporyFile(fileReference, mimetype -> {
            return;
        });
    }

    /**
     * Retrieves a file from the shared file storage for use as a local temporary file.
     *
     * @param fileReference
     *            the reference to the file to retrieve
     * @param contentTypeConsumer
     *            a consumer to capture the content type of the file as recorded in the shared file stores
     * @return the path the temporary file holding the content of the shared file
     */
    Path retrieveAsTemporyFile(String fileReference, Consumer<String> contentTypeConsumer);

    /**
     * Stores the contents of a file in the shared file storage.
     *
     * @param file
     *            the path to the file to store
     * @param contentType
     *            the content type of the file to store
     * @return the file reference within the shared file store
     */
    String saveFile(Path file, String contentType);

    /**
     * Deletes a file from the shared file storage.
     *
     * @param fileReference
     *            the reference to the file to delete
     */
    void deleteFile(String fileReference);
}
