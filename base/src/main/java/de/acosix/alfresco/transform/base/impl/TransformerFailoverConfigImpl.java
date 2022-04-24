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

import java.util.ArrayList;
import java.util.List;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformerFailoverConfig;

/**
 * @author Axel Faust
 */
public class TransformerFailoverConfigImpl extends AbstractTransformerConfigState implements TransformerFailoverConfig
{

    private final List<String> failoverTransformers = new ArrayList<>();

    protected TransformerFailoverConfigImpl(final String name, final Context context)
    {
        super(name, context);
        this.readConfig();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public List<String> getFailoverTransformers()
    {
        return new ArrayList<>(this.failoverTransformers);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String getConfigKeyPrefix()
    {
        return "failoverTransformer";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readExtendedConfig(final String prefix)
    {
        super.readExtendedConfig(prefix);

        final List<String> transformers = this.context.getMultiValuedProperty(prefix + "transformers");
        if (transformers == null || transformers.isEmpty())
        {
            throw new IllegalStateException("Missing transformer names configuration for failover transformer " + this.name);
        }
        this.failoverTransformers.addAll(transformers);
    }

}
