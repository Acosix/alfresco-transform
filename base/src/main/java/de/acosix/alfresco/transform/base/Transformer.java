/*
 * Copyright 2021 Acosix GmbH
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.alfresco.transform.client.model.config.SupportedSourceAndTarget;

/**
 * Instances of this interface represent specific content transformation workers, transforming the content of a file between different
 * mimetypes or between different formats in the same mimetype. Contrary to Alfresco's framework for transformer applications, a transformer
 * is not responsible for handling metadata extraction - this resposnibility resides with {@link MetadataExtracter} instances.
 *
 * @author Axel Faust
 */
public interface Transformer
{

    /**
     * Retrieves the name of this instance
     *
     * @return the name of this instance
     */
    String getName();

    /**
     * Retrieves the set references to transform options this transformer supports.
     *
     * @return the names of supported transform options
     */
    Set<String> getTransformOptions();

    /**
     * Retrieves the collection of transformations this instance supports
     *
     * @return the supported transformations
     */
    Set<SupportedSourceAndTarget> getSupportedTransformations();

    /**
     * Transforms a content file into the specified target mimetype.
     *
     * @param sourceFile
     *            the path to the source file
     * @param sourceMimetype
     *            the mimetype of the source file
     * @param targetFile
     *            the path to the target file which is expected to hold the result of the transformation after successful conclusion
     * @param targetMimetype
     *            the mimetype of the format into which the content file should be transformed
     * @param timeout
     *            the time allowed for the operation to complete in milliseconds - the operation must ensure it does not block noticeably
     *            longer than the specified amount of time, even if underlying transformations processes cannot technically be cancelled /
     *            interrupted
     */
    default void transform(final Path sourceFile, final String sourceMimetype, final Path targetFile, final String targetMimetype,
            final long timeout)
    {
        this.transform(sourceFile, sourceMimetype, targetFile, targetMimetype, timeout, Collections.emptyMap());
    }

    /**
     * Transforms a content file into the specified target mimetype.
     *
     * @param sourceFile
     *            the path to the source file
     * @param sourceMimetype
     *            the mimetype of the source file
     * @param targetFile
     *            the path to the target file which is expected to hold the result of the transformation after successfull conclusion
     * @param targetMimetype
     *            the mimetype of the format into which the content file should be transformed
     * @param timeout
     *            the time allowed for the operation to complete in milliseconds - the operation must ensure it does not block noticeably
     *            longer than the specified amount of time, even if underlying transformations processes cannot technically be cancelled /
     *            interrupted
     * @param options
     *            the client-provided options for the transformation
     */
    void transform(Path sourceFile, String sourceMimetype, Path targetFile, String targetMimetype, long timeout,
            Map<String, String> options);
}
