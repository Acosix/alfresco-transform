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

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.StatusException;

/**
 *
 * @author Axel Faust
 */
public class ContextImpl implements Context
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextImpl.class);

    protected final Map<String, String> properties;

    protected final Path tempDir;

    public ContextImpl()
    {
        try
        {
            this.tempDir = Files.createTempDirectory("TransformerApplication");
        }
        catch (final IOException ioex)
        {
            throw new IllegalStateException("Failed to set up temporary directory", ioex);
        }

        final Properties tempProperties = new Properties();

        // load properties from default to most specific override
        this.loadPropertiesFromClasspathResource(tempProperties, "defaultTransformer.properties");
        this.loadPropertiesFromClasspathResource(tempProperties, "transformer.properties");

        // allow overrides/additions via config file mounted in Docker
        this.loadPropertiesFromFile(tempProperties, "transformer.properties");

        final HashSet<String> propertyNames = new HashSet<>(tempProperties.stringPropertyNames());
        // allow overrides via environment properties
        for (final String propertyName : propertyNames)
        {
            // use prefix to differentiate from other envs / avoid accidental overlap
            final String effectivePropertyName = "TENV_" + propertyName;
            final String value = System.getenv(effectivePropertyName);
            if (value != null)
            {
                tempProperties.setProperty(propertyName, value);
            }
        }

        // allow override via explicitly set system properties
        for (final String propertyName : propertyNames)
        {
            final String value = System.getProperty(propertyName);
            if (value != null)
            {
                tempProperties.setProperty(propertyName, value);
            }
        }

        // to map, so we can make static state unmodifiable
        final Map<String, String> mapified = new HashMap<>();
        for (final String propertyName : propertyNames)
        {
            mapified.put(propertyName, tempProperties.getProperty(propertyName));
        }
        this.properties = Collections.unmodifiableMap(mapified);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Path createTempFile(final String prefix, final String suffix)
    {
        try
        {
            return Files.createTempFile(this.tempDir, prefix, suffix);
        }
        catch (final IOException ioex)
        {
            throw new StatusException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                    "Failed to create temporary file with prefix " + prefix + " and suffix " + suffix);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Path createTempFileSubDirectory(final String directoryName)
    {
        try
        {
            return Files.createDirectory(this.tempDir.resolve(directoryName));
        }
        catch (final IOException ioex)
        {
            throw new StatusException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                    "Failed to create temporary file sub-directory with name " + directoryName);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void discardTempFile(final Path tempFile)
    {
        try
        {
            Files.deleteIfExists(tempFile);
        }
        catch (final IOException ignore)
        {
            // ignore
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Set<String> getPropertyNames()
    {
        return Collections.unmodifiableSet(this.properties.keySet());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <T extends SslContextFactory> T getSslContextFactoryIfEnabled(final String sslPropertyNameBase,
            final Supplier<T> factoryProvider)
    {
        final boolean enabled = this.getBooleanProperty(sslPropertyNameBase, false);
        final T result;
        if (enabled)
        {
            result = this.getSslContextFactory(sslPropertyNameBase, factoryProvider);
        }
        else
        {
            result = null;
        }
        return result;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <T extends SslContextFactory> T getSslContextFactory(final String sslPropertyNameBase, final Supplier<T> factoryProvider)
    {
        final T result = factoryProvider.get();

        this.processStringPropertyIfSet(sslPropertyNameBase + ".secureRandomAlgorithm", result::setSecureRandomAlgorithm);

        this.processMultiValuedStringPropertyIfSet(sslPropertyNameBase + ".includeProtocols", result::setIncludeProtocols);
        this.processMultiValuedStringPropertyIfSet(sslPropertyNameBase + ".excludeProtocols", result::setExcludeProtocols);

        this.processMultiValuedStringPropertyIfSet(sslPropertyNameBase + ".includeCipherSuites", result::setIncludeCipherSuites);
        this.processMultiValuedStringPropertyIfSet(sslPropertyNameBase + ".excludeCipherSuites", result::setExcludeCipherSuites);
        result.setUseCipherSuitesOrder(this.getBooleanProperty(sslPropertyNameBase + ".useCiperSuitesOrder", true));

        result.setValidateCerts(this.getBooleanProperty(sslPropertyNameBase + ".validateCerts", true));
        result.setValidatePeerCerts(this.getBooleanProperty(sslPropertyNameBase + ".validatePeerCerts", true));

        result.setEnableCRLDP(this.getBooleanProperty(sslPropertyNameBase + ".crldpEnabled", false));
        result.setEnableOCSP(this.getBooleanProperty(sslPropertyNameBase + ".ocspEnabled", false));
        this.processStringPropertyIfSet(sslPropertyNameBase + ".ocspResponderUrl", result::setOcspResponderURL);

        final String keyStorePath = this.getStringProperty(sslPropertyNameBase + ".keystore.path");
        if (keyStorePath != null && !keyStorePath.isBlank())
        {
            result.setKeyStorePath(keyStorePath);

            this.processStringPropertyIfSet(sslPropertyNameBase + ".keystore.password", result::setKeyStorePassword);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".keystore.provider", result::setKeyStoreProvider);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".keystore.type", result::setKeyStoreType);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".keymanager.factoryAlgorithm", result::setKeyManagerFactoryAlgorithm);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".keymanager.passwordAlgorithm", result::setKeyManagerPassword);

            this.processStringPropertyIfSet(sslPropertyNameBase + ".certAlias", result::setCertAlias);
        }

        final String trustStorePath = this.getStringProperty(sslPropertyNameBase + ".truststore.path");
        if (trustStorePath != null && !trustStorePath.isBlank())
        {
            result.setTrustStorePath(trustStorePath);

            this.processStringPropertyIfSet(sslPropertyNameBase + ".truststore.password", result::setTrustStorePassword);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".truststore.provider", result::setTrustStoreProvider);
            this.processStringPropertyIfSet(sslPropertyNameBase + ".truststore.type", result::setTrustStoreType);

            this.processStringPropertyIfSet(sslPropertyNameBase + ".trustmanager.factoryAlgorithm",
                    result::setTrustManagerFactoryAlgorithm);
        }

        result.setTrustAll(this.getBooleanProperty(sslPropertyNameBase + ".trustAll", false));

        if (result instanceof SslContextFactory.Server)
        {
            ((SslContextFactory.Server) result).setSniRequired(this.getBooleanProperty(sslPropertyNameBase + ".sniRequired", false));
        }

        return result;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getStringProperty(final String propertyName)
    {
        String property = this.properties.get(propertyName);

        if (property != null && property.matches("(^|[^\\\\]([\\\\]{2})*+)\\$\\{.*"))
        {
            property = this.resolvePlaceholders(propertyName, property);
        }

        return property;
    }

    protected String resolvePlaceholders(final String propertyName, final String originalValue)
    {
        // simple placeholder resolution without default value capability
        final StringBuilder resolutionBuilder = new StringBuilder(originalValue);

        int startIdx = 0;
        boolean hasSkippedStarts = false;
        while (startIdx < resolutionBuilder.length())
        {
            final int placeholderStart = this.findNextValidPlaceholderFragment(resolutionBuilder, "${", startIdx);
            if (placeholderStart == -1)
            {
                startIdx = resolutionBuilder.length();
            }
            else
            {
                final int nextPlaceholderStart = this.findNextValidPlaceholderFragment(resolutionBuilder, "${", placeholderStart + 2);
                final int nextPlaceholderEnd = this.findNextValidPlaceholderFragment(resolutionBuilder, "}", placeholderStart + 2);

                if (nextPlaceholderEnd == -1)
                {
                    // we have an unclosed placeholder - will be treated as literal value
                    startIdx = resolutionBuilder.length();
                }
                else if (nextPlaceholderStart == -1 || nextPlaceholderEnd < nextPlaceholderStart)
                {
                    final String placeholderProperty = resolutionBuilder.substring(placeholderStart + 2, nextPlaceholderEnd);
                    final String resolvedValue = this.properties.get(placeholderProperty);
                    if (resolvedValue == null)
                    {
                        throw new IllegalStateException("Unable to resolve placeholder ${" + placeholderProperty + "}");
                    }

                    resolutionBuilder.replace(placeholderStart, nextPlaceholderEnd + 1, resolvedValue);
                    if (hasSkippedStarts)
                    {
                        startIdx = 0;
                        hasSkippedStarts = false;
                    }
                    else
                    {
                        startIdx = placeholderStart;
                    }
                }
                else
                {
                    // next iteration resolves nested placeholder, and then comes around to try outer placeholder again
                    hasSkippedStarts = true;
                    startIdx = nextPlaceholderStart;
                }
            }
        }

        LOGGER.trace("Resolved value of property {} from {} to {}", propertyName, originalValue, resolutionBuilder);
        return resolutionBuilder.toString();
    }

    protected int findNextValidPlaceholderFragment(final StringBuilder value, final String fragment, final int startIdx)
    {
        int idx = -1;
        int offset = startIdx;
        while (offset < value.length())
        {
            final int placeholderStart = value.indexOf(fragment, offset);
            if (placeholderStart != -1)
            {
                if (placeholderStart != 0 && value.substring(startIdx, placeholderStart).matches("\\\\([\\\\]{2})*+$"))
                {
                    // continue looking for valid start without escape
                    offset = placeholderStart + fragment.length();
                }
                else
                {
                    idx = placeholderStart;
                    offset = value.length();
                }
            }
            else
            {
                offset = value.length();
            }
        }

        return idx;
    }

    protected void loadPropertiesFromClasspathResource(final Properties properties, final String resourceName)
    {
        final ClassLoader classLoader = ContextImpl.class.getClassLoader();
        final InputStream resourceAsStream = classLoader.getResourceAsStream(resourceName);
        if (resourceAsStream != null)
        {
            try (Reader propertiesReader = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
            {
                properties.load(propertiesReader);
            }
            catch (final IOException ioex)
            {
                throw new IllegalStateException("Failed to load transformer properties from classpath resource " + resourceName, ioex);
            }
        }
        else
        {
            LOGGER.info("Did not find transformer config resource {} on classpath", resourceName);
        }
    }

    protected void loadPropertiesFromFile(final Properties properties, final String fileName)
    {
        final Path path = Paths.get(fileName);
        if (Files.isReadable(path) && Files.isRegularFile(path))
        {
            try (Reader propertiesReader = new FileReader(path.toFile(), StandardCharsets.UTF_8))
            {
                properties.load(propertiesReader);
            }
            catch (final IOException ioex)
            {
                throw new IllegalStateException("Failed to load transformer properties from file " + path.toAbsolutePath(), ioex);
            }
        }
        else
        {
            LOGGER.info("Did not find transformer config resource at {}", path.toAbsolutePath());
        }
    }

    protected void processStringPropertyIfSet(final String propertyName, final Consumer<String> processor)
    {
        final String value = this.getStringProperty(propertyName);
        if (value != null && !value.isBlank())
        {
            processor.accept(value);
        }
    }

    protected void processMultiValuedStringPropertyIfSet(final String propertyName, final Consumer<String[]> processor)
    {
        final List<String> values = this.getMultiValuedProperty(propertyName);
        if (values != null && !values.isEmpty())
        {
            processor.accept(values.toArray(new String[0]));
        }
    }
}
