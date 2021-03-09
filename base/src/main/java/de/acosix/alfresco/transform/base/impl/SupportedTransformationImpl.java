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

import de.acosix.alfresco.transform.base.SupportedTransformation;

/**
 *
 * @author Axel Faust
 */
public class SupportedTransformationImpl implements SupportedTransformation
{

    private final String sourceMimetype;

    private final String targetMimetype;

    private int priority = 50;

    private long maxSourceSizeBytes = -1;

    public SupportedTransformationImpl(final String sourceMimetype, final String targetMimetype)
    {
        this.sourceMimetype = sourceMimetype;
        this.targetMimetype = targetMimetype;
    }

    public SupportedTransformationImpl(final String sourceMimetype, final String targetMimetype, final Integer priority,
            final Long maxSourceSizeBytes)
    {
        this(sourceMimetype, targetMimetype);
        if (priority != null)
        {
            this.priority = priority.intValue();
        }
        if (maxSourceSizeBytes != null)
        {
            this.maxSourceSizeBytes = maxSourceSizeBytes.longValue();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getSourceMimetype()
    {
        return this.sourceMimetype;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getTargetMimetype()
    {
        return this.targetMimetype;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public int getPriority()
    {
        return this.priority;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Long getMaxSourceBytes()
    {
        return this.maxSourceSizeBytes;
    }

}
