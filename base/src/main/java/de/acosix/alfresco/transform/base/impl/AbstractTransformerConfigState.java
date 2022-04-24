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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.alfresco.transform.client.model.config.SupportedSourceAndTarget;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformerConfigState;

/**
 *
 * @author Axel Faust
 */
public abstract class AbstractTransformerConfigState implements TransformerConfigState
{

    protected final String name;

    protected final Context context;

    private final Set<String> transformerOptionElements = new HashSet<>();

    private final Set<SupportedSourceAndTarget> supportedTransformations = new HashSet<>();

    protected AbstractTransformerConfigState(final String name, final Context context)
    {
        this.name = name;
        this.context = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getTransformOptions()
    {
        return new HashSet<>(this.transformerOptionElements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<SupportedSourceAndTarget> getSupportedTransformations()
    {
        return new HashSet<>(this.supportedTransformations);
    }

    protected abstract String getConfigKeyPrefix();

    /**
     * Read extended configuration relevant for the specific type of transformer this implementation represents.
     *
     * @param prefix
     *     the key prefix to be used when resolving configuration properties
     */
    protected void readExtendedConfig(final String prefix)
    {
        // NO-OP - for extension purposes
    }

    protected final void readConfig()
    {
        final String prefix = this.getConfigKeyPrefix() + "." + this.name + '.';
        this.transformerOptionElements.addAll(this.context.getMultiValuedProperty(prefix + "transformerOptions"));

        final Map<String, Map<String, SupportedSourceAndTarget>> transformationsBySourceAndTarget = new HashMap<>();

        final int defaultPriority = this.context.getIntegerProperty(prefix + "default.priority", 50, Integer.MIN_VALUE, Integer.MAX_VALUE);
        final long defaultMaxSourceSize = this.context.getLongProperty(prefix + "default.maxSourceSizeBytes", -1, -1, Long.MAX_VALUE);

        final Collection<String> globalSourceMimetypes = this.context.getMultiValuedProperty(prefix + "sourceMimetypes");
        final String globalTargetMimetypesProperty = prefix + "targetMimetypes";
        final Collection<String> globalTargetMimetypes = this.context.getMultiValuedProperty(globalTargetMimetypesProperty);

        final Collection<String> nonGlobalSourceMimetypes = this.context.getPropertyNames().stream()
                .filter(k -> !k.equals(globalTargetMimetypesProperty) && k.startsWith(prefix) && k.endsWith(".targetMimetypes"))
                .map(k -> k.substring(prefix.length(), k.length() - ".targetMimetypes".length()))
                .filter(k -> !globalSourceMimetypes.contains(k)).collect(Collectors.toSet());

        for (final String sourceMimetype : globalSourceMimetypes)
        {
            this.readTransformationsForSourceMimetype(sourceMimetype, globalTargetMimetypes, prefix, defaultPriority, defaultMaxSourceSize,
                    transformationsBySourceAndTarget);
        }

        for (final String sourceMimetype : nonGlobalSourceMimetypes)
        {
            this.readTransformationsForSourceMimetype(sourceMimetype, Collections.emptySet(), prefix, defaultPriority, defaultMaxSourceSize,
                    transformationsBySourceAndTarget);
        }

        transformationsBySourceAndTarget.values().forEach(v -> v.values().forEach(t -> this.supportedTransformations.add(t)));

        this.readExtendedConfig(prefix);
    }

    private void readTransformationsForSourceMimetype(final String sourceMimetype, final Collection<String> targetMimetypes,
            final String prefix, final int defaultPriority, final long defaultMaxSourceSize,
            final Map<String, Map<String, SupportedSourceAndTarget>> transformationsBySourceAndTarget)
    {
        final Map<String, SupportedSourceAndTarget> transformationsForSource = transformationsBySourceAndTarget
                .computeIfAbsent(sourceMimetype, k -> new HashMap<>());

        final String sourcePrefix = prefix + sourceMimetype;
        final String wildcardSourcePrefix = prefix + '*';
        final Collection<String> sourceSpecificTargetMimetypes = this.context.getMultiValuedProperty(sourcePrefix + ".targetMimetypes");
        final Collection<String> effectiveTargetMimetypes = sourceSpecificTargetMimetypes.isEmpty() ? targetMimetypes
                : sourceSpecificTargetMimetypes;

        final Integer sourceSpecificPriority = this.context.getIntegerProperty(sourcePrefix + ".priority", Integer.MIN_VALUE,
                Integer.MAX_VALUE);
        final Integer sourceSpecificMaxSourceSize = this.context.getIntegerProperty(sourcePrefix + ".maxSourceSizeBytes", -1,
                Integer.MAX_VALUE);

        for (final String targetMimetype : effectiveTargetMimetypes)
        {
            final String terminalPrefix = sourcePrefix + '.' + targetMimetype;
            final String wildcardTerminalPrefix = wildcardSourcePrefix + '.' + targetMimetype;
            final boolean wildcardSupported = this.context.getBooleanProperty(wildcardTerminalPrefix + ".supported", true);
            final boolean terminalSupported = this.context.getBooleanProperty(terminalPrefix + ".supported", wildcardSupported);
            if (terminalSupported)
            {
                Integer terminalPriority = this.context.getIntegerProperty(terminalPrefix + ".priority", Integer.MIN_VALUE,
                        Integer.MAX_VALUE);
                if (terminalPriority == null)
                {
                    terminalPriority = this.context.getIntegerProperty(wildcardTerminalPrefix + ".priority", Integer.MIN_VALUE,
                            Integer.MAX_VALUE);
                }
                Integer terminalMaxSourceSize = this.context.getIntegerProperty(terminalPrefix + ".maxSourceSizeBytes", -1,
                        Integer.MAX_VALUE);
                if (terminalMaxSourceSize == null)
                {
                    terminalMaxSourceSize = this.context.getIntegerProperty(wildcardTerminalPrefix + ".maxSourceSizeBytes", -1,
                            Integer.MAX_VALUE);
                }

                final long effectiveMaxSourceSize = terminalMaxSourceSize != null ? terminalMaxSourceSize
                        : (sourceSpecificMaxSourceSize != null ? sourceSpecificMaxSourceSize : defaultMaxSourceSize);
                final int effectivePriority = terminalPriority != null ? terminalPriority
                        : (sourceSpecificPriority != null ? sourceSpecificPriority : defaultPriority);

                final SupportedSourceAndTarget trafo = new SupportedSourceAndTarget(sourceMimetype, targetMimetype, effectiveMaxSourceSize,
                        effectivePriority);
                transformationsForSource.putIfAbsent(targetMimetype, trafo);
            }
        }
    }
}
