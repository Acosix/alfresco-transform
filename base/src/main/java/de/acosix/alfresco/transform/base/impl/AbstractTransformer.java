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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.alfresco.transform.client.model.config.SupportedSourceAndTarget;
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
public abstract class AbstractTransformer implements Transformer
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTransformer.class);

    private final String name;

    protected final Context context;

    protected final TransformationLog transformationLog;

    private final Set<String> transformerOptionElements = new HashSet<>();

    private final Set<SupportedSourceAndTarget> supportedTransformations = new HashSet<>();

    protected AbstractTransformer(final String name, final Context context, final TransformationLog transformationLog)
    {
        this.name = name;
        this.context = context;
        this.transformationLog = transformationLog;
        this.readConfig();
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
     *            the log entry in which to record transformation details
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
    protected abstract void doTransform(MutableEntry logEntry, Path sourceFile, String sourceMimetype, Path targetFile,
            String targetMimetype, long timeout, Map<String, String> options);

    private void readConfig()
    {
        final String prefix = "transformer." + this.name + '.';
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
    }

    private void readTransformationsForSourceMimetype(final String sourceMimetype, final Collection<String> targetMimetypes,
            final String prefix, final int defaultPriority, final long defaultMaxSourceSize,
            final Map<String, Map<String, SupportedSourceAndTarget>> transformationsBySourceAndTarget)
    {
        final Map<String, SupportedSourceAndTarget> transformationsForSource = transformationsBySourceAndTarget
                .computeIfAbsent(sourceMimetype, k -> new HashMap<>());

        final String sourcePrefix = prefix + sourceMimetype;
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
            final boolean supported = this.context.getBooleanProperty(terminalPrefix + ".supported", true);
            if (supported)
            {
                final Integer terminalPriority = this.context.getIntegerProperty(terminalPrefix + ".priority", Integer.MIN_VALUE,
                        Integer.MAX_VALUE);
                final Integer terminalMaxSourceSize = this.context.getIntegerProperty(terminalPrefix + ".maxSourceSizeBytes", -1,
                        Integer.MAX_VALUE);

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
