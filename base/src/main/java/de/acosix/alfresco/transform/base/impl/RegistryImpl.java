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
package de.acosix.alfresco.transform.base.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.alfresco.transform.client.model.config.SupportedSourceAndTarget;
import org.alfresco.transform.client.model.config.TransformConfig;
import org.alfresco.transform.client.model.config.TransformOption;
import org.alfresco.transform.client.model.config.TransformOptionGroup;
import org.alfresco.transform.client.model.config.TransformOptionValue;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.MetadataExtracter;
import de.acosix.alfresco.transform.base.Registry;
import de.acosix.alfresco.transform.base.Transformer;
import de.acosix.alfresco.transform.base.TransformerConfigState;
import de.acosix.alfresco.transform.base.TransformerFailoverConfig;
import de.acosix.alfresco.transform.base.TransformerPipelineConfig;

/**
 *
 * @author Axel Faust
 */
public class RegistryImpl implements Registry
{

    // pseudo mimetype used in Alfresco's transform framework to pipe extraction through transformation focused API
    // awful design, but have to support it
    private static final String ALFRESCO_METADATA_EXTRACT = "alfresco-metadata-extract";

    private final Context context;

    private final Map<String, Set<TransformOption>> rootTransformOptions = new HashMap<>();

    private final Map<SourceTargetMimetypePair, Set<TransformerSupportedTransformation>> transformationsBySourceTarget = new HashMap<>();

    private final Map<String, Transformer> registeredTransformers = new HashMap<>();

    private final Map<String, TransformerConfigState> knownTransformerConfigs = new HashMap<>();

    private final Map<String, MetadataExtracter> registeredExtracters = new HashMap<>();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RegistryImpl(final Context context)
    {
        this.context = context;
        this.readTransformerOptionProfiles();
        this.readComplexTransformerConfig();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerTransformer(final Transformer transformer)
    {
        final Collection<String> transformOptions = transformer.getTransformOptions();
        final boolean allOptionsSupported = transformOptions.stream().allMatch(this.rootTransformOptions::containsKey);
        if (!allOptionsSupported)
        {
            throw new IllegalStateException("Transformer options contain unsupported references: " + transformOptions);
        }

        this.registeredTransformers.put(transformer.getName(), transformer);
        this.knownTransformerConfigs.put(transformer.getName(), transformer);

        transformer.getSupportedTransformations().forEach(transformation -> {
            final SourceTargetMimetypePair sourceTargetPair = new SourceTargetMimetypePair(transformation.getSourceMediaType(),
                    transformation.getTargetMediaType());
            this.transformationsBySourceTarget.computeIfAbsent(sourceTargetPair, k -> new TreeSet<>())
                    .add(new TransformerSupportedTransformation(transformer, transformation));
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerMetadataExtracter(final MetadataExtracter metadataExtracter)
    {
        final Collection<String> transformOptions = metadataExtracter.getTransformOptions();
        final boolean allOptionsSupported = transformOptions.stream().allMatch(this.rootTransformOptions::containsKey);
        if (!allOptionsSupported)
        {
            throw new IllegalStateException("Extracter options contain unsupported references: " + transformOptions);
        }

        this.registeredExtracters.put(metadataExtracter.getName(), metadataExtracter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transformer getTransformer(final String name)
    {
        final Transformer transformer = this.registeredTransformers.get(name);
        if (transformer == null)
        {
            throw new IllegalArgumentException("No transformer was registered with the name " + name);
        }
        return transformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataExtracter getMetadataExtractor(final String name)
    {
        final MetadataExtracter extracter = this.registeredExtracters.get(name);
        if (extracter == null)
        {
            throw new IllegalArgumentException("No metadata extracter was registered with the name " + name);
        }
        return extracter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> findTransformer(final String sourceMimetype, final long sourceSizeBytes, final String targetMimetype,
            final Map<String, String> options)
    {
        final Map<String, Boolean> transformerOptionCheckResult = new HashMap<>();

        // transformations are inherently sorted by priority (only)
        final Set<TransformerSupportedTransformation> transformations = this.transformationsBySourceTarget
                .getOrDefault(new SourceTargetMimetypePair(sourceMimetype, targetMimetype), Collections.emptySet());

        return transformations.stream()
                // filter by source size limits if present
                .filter(transformation -> {
                    final long maxSourceBytes = transformation.getSupportedTransformation().getMaxSourceSizeBytes();
                    return maxSourceBytes < 0 || maxSourceBytes >= sourceSizeBytes;
                })
                // filter by option support
                .filter(transformation -> {
                    final Transformer transformer = transformation.getTransformer();
                    final String transformerName = transformer.getName();
                    final Boolean checkResult = transformerOptionCheckResult.computeIfAbsent(transformerName,
                            t -> this.supportsOptions(transformer, options));
                    return Boolean.TRUE.equals(checkResult);
                }).findFirst().map(transformation -> transformation.getTransformer().getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Set<TransformOption>> getAllRootTransformOptions()
    {
        return new HashMap<>(this.rootTransformOptions);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getDefaultOptions(final String transformerName)
    {
        final Transformer transformer = this.getTransformer(transformerName);
        final Collection<String> transformerOptionProfiles = transformer.getTransformOptions();
        final Map<String, Boolean> transformOptions = new HashMap<>();
        transformerOptionProfiles.forEach(profile -> this.rootTransformOptions.get(profile)
                .forEach(option -> this.collectOptionFields(option, true, false, transformOptions, Collections.emptyMap())));

        final Map<String, String> defaultOptions = new HashMap<>();
        for (final String option : transformOptions.keySet())
        {
            final String value = this.getDefaultOption(transformerName, option);
            if (value != null)
            {
                defaultOptions.put(option, value);
            }
        }

        return defaultOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTransformConfigJSON(final Writer writer) throws IOException
    {
        final TransformConfig config = new TransformConfig();
        config.setTransformOptions(this.rootTransformOptions);

        config.setTransformers(
                this.knownTransformerConfigs.entrySet().stream().filter(entry -> isSupported(entry.getValue())).map(entry -> {
                    final String name = entry.getKey();
                    final TransformerConfigState transformerConfig = entry.getValue();
                    final org.alfresco.transform.client.model.config.Transformer t = new org.alfresco.transform.client.model.config.Transformer(
                            name, transformerConfig.getTransformOptions(), transformerConfig.getSupportedTransformations());

                    if (this.registeredExtracters.containsKey(name))
                    {
                        final MetadataExtracter extracter = this.registeredExtracters.get(name);
                        t.getTransformOptions().addAll(extracter.getTransformOptions());

                        final Set<SupportedSourceAndTarget> supported = t.getSupportedSourceAndTargetList();
                        for (final String sourceMimetype : extracter.getSupportedSourceMimetypes())
                        {
                            supported.add(new SupportedSourceAndTarget(sourceMimetype, ALFRESCO_METADATA_EXTRACT, -1));
                        }
                    }
                    else if (transformerConfig instanceof TransformerPipelineConfig)
                    {
                        t.setTransformerPipeline(((TransformerPipelineConfig) transformerConfig).getPipelineSteps());
                    }
                    else if (transformerConfig instanceof TransformerFailoverConfig)
                    {
                        t.setTransformerFailover(((TransformerFailoverConfig) transformerConfig).getFailoverTransformers());
                    }

                    return t;
                }).collect(Collectors.toList()));

        this.registeredExtracters.entrySet().stream().filter(e -> !this.registeredTransformers.containsKey(e.getKey())).map(entry -> {
            final String name = entry.getKey();
            final MetadataExtracter extracter = entry.getValue();
            return new org.alfresco.transform.client.model.config.Transformer(name, extracter.getTransformOptions(),
                    extracter.getSupportedSourceMimetypes().stream()
                            .map(mimetype -> new SupportedSourceAndTarget(mimetype, ALFRESCO_METADATA_EXTRACT, -1))
                            .collect(Collectors.toSet()));
        }).collect(Collectors.toCollection(config::getTransformers));

        this.jsonMapper.writeValue(writer, config);
    }

    private boolean isSupported(final TransformerConfigState transformerConfig)
    {
        boolean result = true;

        // only pipeline transformers currently have potential restrictions to be checked
        if (transformerConfig instanceof TransformerPipelineConfig)
        {
            List<String> localDependencies = ((TransformerPipelineConfig) transformerConfig).getLocalDependencies();
            result = localDependencies.isEmpty() || localDependencies.stream()
                    .allMatch(s -> this.knownTransformerConfigs.containsKey(s) && this.isSupported(this.knownTransformerConfigs.get(s)));
        }

        return result;
    }

    private boolean supportsOptions(final Transformer transformer, final Map<String, String> options)
    {
        final Collection<String> transformerOptionProfiles = transformer.getTransformOptions();
        final Map<String, Boolean> transformOptions = new HashMap<>();
        transformerOptionProfiles.forEach(profile -> this.rootTransformOptions.get(profile)
                .forEach(option -> this.collectOptionFields(option, true, false, transformOptions, options)));

        final List<String> nonSelectorParameterNames = this.context.getMultiValuedProperty("application.nonSelectorParameterNames");

        final boolean allRequiredOptionsProvided = transformOptions.entrySet().stream().filter(Entry::getValue).map(Entry::getKey)
                .allMatch(k -> (options.containsKey(k) && options.get(k) != null && !options.get(k).isBlank())
                        || this.getDefaultOption(transformer.getName(), k) != null);
        final boolean containsOnlySupportedOptions = options.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank()).map(Entry::getKey)
                .filter(Predicate.not(nonSelectorParameterNames::contains)).allMatch(transformOptions::containsKey);

        return allRequiredOptionsProvided && containsOnlySupportedOptions;
    }

    private void collectOptionFields(final TransformOption currentElement, final boolean isRoot, final boolean triggerRequirement,
            final Map<String, Boolean> requiredFlagByOption, final Map<String, String> providedOptions)
    {
        if (currentElement instanceof TransformOptionGroup)
        {
            // due to Alfresco's engine.json structure, root groups can never be trigger sub-elements' required state by being flagged
            // required themselves
            final boolean triggerSubElementRequirement = (!isRoot && currentElement.isRequired())
                    || this.isPresent(currentElement, providedOptions);
            ((TransformOptionGroup) currentElement).getTransformOptions().stream().forEach(
                    se -> this.collectOptionFields(se, false, triggerSubElementRequirement, requiredFlagByOption, providedOptions));
        }
        else if (currentElement instanceof TransformOptionValue)
        {
            requiredFlagByOption.put(((TransformOptionValue) currentElement).getName(), currentElement.isRequired() && triggerRequirement);
        }
    }

    /**
     * Checks whether a particular supported option (group) is present / provided via the client-provided option map.
     *
     * @param currentElement
     *     the element to process
     * @param providedOptions
     *     the options provided by the client
     * @return {@code true} if the element (or any of its sub-elements) is present, {@code false} otherwise
     */
    private boolean isPresent(final TransformOption currentElement, final Map<String, String> providedOptions)
    {
        boolean present = false;
        if (currentElement instanceof TransformOptionGroup)
        {
            present = ((TransformOptionGroup) currentElement).getTransformOptions().stream()
                    .anyMatch(se -> this.isPresent(se, providedOptions));
        }
        else if (currentElement instanceof TransformOptionValue)
        {
            final String name = ((TransformOptionValue) currentElement).getName();
            final String value = providedOptions.get(name);
            present = value != null && !value.isBlank();
        }
        return present;
    }

    private String getDefaultOption(final String transformerName, final String optionName)
    {
        final String property = "transformerDefaultOptions." + transformerName + "." + optionName;
        final String optionValue = this.context.getStringProperty(property);
        return optionValue != null && !optionValue.isBlank() ? optionValue : null;
    }

    private void readComplexTransformerConfig()
    {
        final List<String> failoverTransformerNames = this.context.getMultiValuedProperty("failoverTransformers");
        failoverTransformerNames.forEach(n -> {
            final TransformerFailoverConfigImpl config = new TransformerFailoverConfigImpl(n, this.context);
            this.knownTransformerConfigs.put(config.getName(), config);
        });

        final List<String> pipelineTransformerNames = this.context.getMultiValuedProperty("pipelineTransformers");
        pipelineTransformerNames.forEach(n -> {
            final TransformerPipelineConfigImpl config = new TransformerPipelineConfigImpl(n, this.context);
            this.knownTransformerConfigs.put(config.getName(), config);
        });
    }

    private void readTransformerOptionProfiles()
    {
        final String prefix = "transformerOptions.";
        final Collection<String> transformerOptionRootGroups = this.context.getMultiValuedProperty(prefix + "rootGroups");
        transformerOptionRootGroups.forEach(group -> {
            final TransformOption element = this.readElement(group, new HashSet<>());
            if (!(element instanceof TransformOptionGroup))
            {
                throw new IllegalStateException(
                        "Transformer option root group " + group + " has not been configured as a proper group with constituent elements");
            }
            this.rootTransformOptions.put(group, ((TransformOptionGroup) element).getTransformOptions());
        });
    }

    private TransformOption readElement(final String elementName, final Collection<String> elementsOnPath)
    {
        final boolean required = this.context.getBooleanProperty("transformerOptions.element." + elementName + ".required", false);
        final Collection<String> subElements = this.context
                .getMultiValuedProperty("transformerOptions.element." + elementName + ".elements");

        TransformOption result;
        if (subElements.isEmpty())
        {
            result = new TransformOptionValue(required, elementName);
        }
        else
        {
            elementsOnPath.add(elementName);
            try
            {
                final List<String> conflicting = subElements.stream().filter(elementsOnPath::contains).collect(Collectors.toList());
                if (!conflicting.isEmpty())
                {
                    throw new IllegalStateException(
                            "Transformer option group element " + elementName + " is part of cyclic graph with elements " + conflicting);
                }

                final Set<TransformOption> constituentElements = subElements.stream().map(e -> this.readElement(e, elementsOnPath))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                result = new TransformOptionGroup(required, constituentElements);
            }
            finally
            {
                elementsOnPath.remove(elementName);
            }
        }
        return result;
    }

    private static class SourceTargetMimetypePair
    {

        private final String sourceMimetype;

        private final String targetMimetype;

        public SourceTargetMimetypePair(final String sourceMimetype, final String targetMimetype)
        {
            this.sourceMimetype = sourceMimetype;
            this.targetMimetype = targetMimetype;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            return Objects.hash(this.sourceMimetype, this.targetMimetype);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final SourceTargetMimetypePair other = (SourceTargetMimetypePair) obj;
            if (this.sourceMimetype == null)
            {
                if (other.sourceMimetype != null)
                {
                    return false;
                }
            }
            else if (!this.sourceMimetype.equals(other.sourceMimetype))
            {
                return false;
            }
            if (this.targetMimetype == null)
            {
                if (other.targetMimetype != null)
                {
                    return false;
                }
            }
            else if (!this.targetMimetype.equals(other.targetMimetype))
            {
                return false;
            }
            return true;
        }
    }

    private static class TransformerSupportedTransformation implements Comparable<TransformerSupportedTransformation>
    {

        private final Transformer transformer;

        private final SupportedSourceAndTarget supportedTransformation;

        private TransformerSupportedTransformation(final Transformer transformer, final SupportedSourceAndTarget supportedTransformation)
        {
            this.transformer = transformer;
            this.supportedTransformation = supportedTransformation;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(final TransformerSupportedTransformation o)
        {
            final int ourPriority = this.supportedTransformation.getPriority();
            final int otherPriority = o.getSupportedTransformation().getPriority();
            int result = 0;
            if (ourPriority < otherPriority)
            {
                result = -1;
            }
            else if (ourPriority > otherPriority)
            {
                result = 1;
            }
            return result;
        }

        /**
         * @return the transformer
         */
        public Transformer getTransformer()
        {
            return this.transformer;
        }

        public SupportedSourceAndTarget getSupportedTransformation()
        {
            return this.supportedTransformation;
        }
    }
}
