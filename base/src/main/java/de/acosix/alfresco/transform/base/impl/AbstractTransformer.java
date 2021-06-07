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
package de.acosix.alfresco.transform.base.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.Transformer;

/**
 *
 * @author Axel Faust
 */
public abstract class AbstractTransformer extends AbstractTransformerConfigState implements Transformer
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransformer.class);

    protected final TransformationLog transformationLog;

    protected AbstractTransformer(final String name, final Context context, final TransformationLog transformationLog)
    {
        this(name, context, transformationLog, true);
    }

    protected AbstractTransformer(final String name, final Context context, final TransformationLog transformationLog,
            final boolean readConfigImmediately)
    {
        super(name, context);
        this.transformationLog = transformationLog;
        if (readConfigImmediately)
        {
            this.readConfig();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public final void transform(final Path sourceFile, final String sourceMimetype, final Path targetFile, final String targetMimetype,
            final long timeout, final Map<String, String> options)
    {
        final MutableEntry logEntry = this.transformationLog.getCurrentEntry().orElseGet(() -> {
            LOGGER.warn(
                    "No transformation log entry has been initialised for transformation request routed to transformer {} - check all callers",
                    this.name);
            final MutableEntry newEntry = this.transformationLog.startNewEntry();
            newEntry.recordSelectedTransformer(this.name);

            return newEntry;
        });

        // lazily record missing information
        // note: options at this point may already not be the original anymore
        if (logEntry.getSourceMimetype() == null || logEntry.getSourceSize() == -1 || logEntry.getTargetMimetype() == null)
        {
            long sourceSize = -1;
            try
            {
                sourceSize = Files.size(sourceFile);
            }
            catch (final IOException ioex)
            {
                LOGGER.warn("Unable to determine size of source file {}", sourceFile, ioex);
            }
            logEntry.recordRequestValues(sourceMimetype, sourceSize, targetMimetype, options);
        }

        this.doTransform(logEntry, sourceFile, sourceMimetype, targetFile, targetMimetype, timeout, options);

        if (logEntry.getResultSize() == -1)
        {
            try
            {
                final long resultSize = Files.size(targetFile);
                logEntry.recordResultSize(resultSize);
            }
            catch (final IOException ioex)
            {
                LOGGER.warn("Unable to determine size of target file {}", targetFile, ioex);
            }
        }
    }

    /**
     * Performs the actual transformation of a content file in the specified target mimetype
     *
     * @param logEntry
     *     the log entry in which to record transformation details
     * @param sourceFile
     *     the path to the source file
     * @param sourceMimetype
     *     the mimetype of the source file
     * @param targetFile
     *     the path to the target file which is expected to hold the result of the transformation after successfull conclusion
     * @param targetMimetype
     *     the mimetype of the format into which the content file should be transformed
     * @param timeout
     *     the time allowed for the operation to complete in milliseconds - the operation must ensure it does not block noticeably
     *     longer than the specified amount of time, even if underlying transformations processes cannot technically be cancelled /
     *     interrupted
     * @param options
     *     the client-provided options for the transformation
     */
    protected abstract void doTransform(MutableEntry logEntry, Path sourceFile, String sourceMimetype, Path targetFile,
            String targetMimetype, long timeout, Map<String, String> options);

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String getConfigKeyPrefix()
    {
        return "transformer";
    }
}
