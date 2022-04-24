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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import de.acosix.alfresco.transform.base.TransformationLog.Entry;

/**
 *
 * @author Axel Faust
 */
public class LocalTransformationLogEntry implements Entry
{

    protected String host;

    protected int sequenceNumber;

    protected long startTime;

    protected long endTime;

    protected int statusCode;

    protected String statusMessage;

    protected long requestHandlingDuration = -1;

    protected long transformationDuration = -1;

    protected long responseHandlingDuration = -1;

    protected String sourceMimetype;

    protected long sourceSize;

    protected String targetMimetype;

    protected long resultSize;

    protected String transformerName;

    protected Map<String, String> options;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHost()
    {
        return this.host;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSequenceNumber()
    {
        return this.sequenceNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStartTime()
    {
        return this.startTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEndTime()
    {
        return this.endTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStatusCode()
    {
        return this.statusCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStatusMessage()
    {
        return this.statusMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRequestHandlingDuration()
    {
        return this.requestHandlingDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTransformationDuration()
    {
        return this.transformationDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getResponseHandlingDuration()
    {
        return this.responseHandlingDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSourceMimetype()
    {
        return this.sourceMimetype;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSourceSize()
    {
        return this.sourceSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTargetMimetype()
    {
        return this.targetMimetype;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getResultSize()
    {
        return this.resultSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTransformerName()
    {
        return this.transformerName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getOptions()
    {
        return this.options != null ? new HashMap<>(this.options) : Collections.emptyMap();
    }

}