/*
 * Copyright 2021 - 2022 Acosix GmbH
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

import java.util.HashMap;
import java.util.Map;

import de.acosix.alfresco.transform.base.TransformationLog.Entry;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;

/**
 *
 * @author Axel Faust
 */
public class LocalMutableTransformationLogEntry extends LocalTransformationLogEntry implements MutableEntry
{

    /**
     * Creates a new instance of this class.
     *
     * @param host
     *            the name of the host for this entry
     * @param sequenceNumber
     *            the sequence number of this entry
     */
    public LocalMutableTransformationLogEntry(final String host, final int sequenceNumber)
    {
        this.host = host;
        this.sequenceNumber = sequenceNumber;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(final int statusCode)
    {
        this.statusCode = statusCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(final int statusCode, final String statusMessage)
    {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markStartOfTransformation()
    {
        this.requestHandlingDuration = System.currentTimeMillis() - this.startTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markEndOfTransformation()
    {
        if (this.requestHandlingDuration == -1)
        {
            throw new IllegalStateException("Cannot mark end of transaction when start has not been marked yet");
        }
        this.transformationDuration = System.currentTimeMillis() - (this.startTime + this.requestHandlingDuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordRequestValues(final String sourceMimetype, final long sourceSize, final String targetMimetype,
            final Map<String, String> options)
    {
        this.sourceMimetype = sourceMimetype;
        this.sourceSize = sourceSize;
        this.targetMimetype = targetMimetype;
        this.options = options != null ? new HashMap<>(options) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordSelectedTransformer(final String transformerName)
    {
        this.transformerName = transformerName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordResultSize(final long resultSize)
    {
        this.resultSize = resultSize;
    }

    protected Entry closeEntry()
    {
        final LocalTransformationLogEntry logEntry = new LocalTransformationLogEntry();
        logEntry.host = this.host;
        logEntry.sequenceNumber = this.sequenceNumber;
        logEntry.startTime = this.startTime;
        logEntry.endTime = System.currentTimeMillis();
        logEntry.statusCode = this.statusCode;
        logEntry.statusMessage = this.statusMessage;
        logEntry.sourceMimetype = this.sourceMimetype;
        logEntry.sourceSize = this.sourceSize;
        logEntry.targetMimetype = this.targetMimetype;
        logEntry.resultSize = this.resultSize;
        logEntry.transformerName = this.transformerName;
        logEntry.requestHandlingDuration = this.requestHandlingDuration;
        logEntry.transformationDuration = this.transformationDuration;
        if (logEntry.transformationDuration != -1)
        {
            logEntry.responseHandlingDuration = logEntry.endTime
                    - (logEntry.startTime + logEntry.requestHandlingDuration + logEntry.transformationDuration);
        }
        logEntry.options = this.options != null ? new HashMap<>(this.options) : null;

        return logEntry;
    }
}
