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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.alfresco.transform.client.model.config.TransformStep;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformerPipelineConfig;

/**
 * @author Axel Faust
 */
public class TransformerPipelineConfigImpl extends AbstractTransformerConfigState implements TransformerPipelineConfig
{

    private final List<TransformStep> pipelineSteps = new ArrayList<>();

    protected TransformerPipelineConfigImpl(final String name, final Context context)
    {
        super(name, context);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public List<TransformStep> getPipelineSteps()
    {
        // steps are mutable, so need deep-copy
        return this.pipelineSteps.stream().map(s -> new TransformStep(s.getTransformerName(), s.getTargetMediaType()))
                .collect(Collectors.toList());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String getConfigKeyPrefix()
    {
        return "pipelineTransformer";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readExtendedConfig(final String prefix)
    {
        super.readExtendedConfig(prefix);

        final List<String> transformers = this.context.getMultiValuedProperty(prefix + "transformerNames");
        final List<String> intermediateTypes = this.context.getMultiValuedProperty(prefix + "intermediateTypes");

        if (transformers == null || transformers.isEmpty() || intermediateTypes == null || intermediateTypes.isEmpty()
                || transformers.size() != intermediateTypes.size() + 1)
        {
            throw new IllegalStateException(
                    "Missing / inconsistent transformer names and intermediate types configuration for pipeline transformer " + this.name);
        }

        for (int idx = 0, max = transformers.size(); idx < max; idx++)
        {
            final String transformer = transformers.get(idx);
            final String intermediateType = idx == max - 2 ? null : intermediateTypes.get(idx);

            final TransformStep step = new TransformStep(transformer, intermediateType);
            this.pipelineSteps.add(step);
        }
    }

}
