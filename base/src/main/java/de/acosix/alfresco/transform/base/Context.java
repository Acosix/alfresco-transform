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
package de.acosix.alfresco.transform.base;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 *
 * @author Axel Faust
 */
public interface Context
{

    /**
     * Creates a temporary file using the provided prefix and suffix.
     *
     * @param prefix
     *            the file prefix to use
     * @param suffix
     *            the file suffix to use
     * @return the path handle for the created temporary file
     */
    Path createTempFile(String prefix, String suffix);

    /**
     * Creates a dedicated sub-directory within this applications temporary file location.
     *
     * @param directoryName
     *            the name of the sub-directory to create
     * @return the path handle for the created sub directory
     */
    Path createTempFileSubDirectory(String directoryName);

    /**
     * Discards and performs the appropriate clean up for a temporary file
     *
     * @param tempFile
     *            the temporary file to discard
     */
    void discardTempFile(Path tempFile);

    /**
     * Returns the (unmodifiable) set of all known configuration property names, even those whose value is {@link String#isBlank() blank}.
     *
     * @return the set of all known configuration property names
     */
    Set<String> getPropertyNames();

    /**
     * Retrieves the textual value of a configuration property
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @return the value of the property or {@code null} if the property has not been defined
     */
    String getStringProperty(String propertyName);

    /**
     * Retrieves the textual value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param defaultValue
     *            the default value to apply if the property has not been defined or is {@link String#isBlank() blank}
     * @return the configured value of the property or the specified default
     */
    default String getStringProperty(final String propertyName, final String defaultValue)
    {
        String property = this.getStringProperty(propertyName);
        if (property == null || property.isBlank())
        {
            property = defaultValue;
        }
        return property;
    }

    /**
     * Retrieves the boolean value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param defaultValue
     *            the default value to apply if the property has not been defined or is {@link String#isBlank() blank}
     * @return the value of the property
     */
    default boolean getBooleanProperty(final String propertyName, final boolean defaultValue)
    {
        final String property = this.getStringProperty(propertyName, String.valueOf(defaultValue));
        return Boolean.parseBoolean(property);
    }

    /**
     * Retrieves the integer value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param defaultValue
     *            the default value to apply if the property has not been defined or is {@link String#isBlank() blank}
     * @param minValue
     *            the lower inclusive bound of valid values
     * @param maxValue
     *            the upper inclusive bound of valid values
     * @return the value of the property
     * @throws IllegalStateException
     *             if the configuration property is not valid integer, either because it does not define an integer number at all or falls
     *             outside the range of allowed values
     */
    default int getIntegerProperty(final String propertyName, final int defaultValue, final int minValue, final int maxValue)
    {
        final String property = this.getStringProperty(propertyName, String.valueOf(defaultValue));
        if (!property.matches("^-?\\d+$"))
        {
            throw new IllegalStateException("Property " + propertyName + " has not been set as a numeric value (integer expected)");
        }

        final int value = Integer.parseInt(property);

        if (value < minValue)
        {
            throw new IllegalStateException("Value of integer property " + propertyName + " must not be lower than " + minValue);
        }

        if (value > maxValue)
        {
            throw new IllegalStateException("Value of integer property " + propertyName + " must not be larger than " + maxValue);
        }

        return value;
    }

    /**
     * Retrieves the integer value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param minValue
     *            the lower inclusive bound of valid values
     * @param maxValue
     *            the upper inclusive bound of valid values
     * @return the value of the property or {@code null} if the property has not been defined
     * @throws IllegalStateException
     *             if the configuration property is not valid integer, either because it does not define an integer number at all or falls
     *             outside the range of allowed values
     */
    default Integer getIntegerProperty(final String propertyName, final int minValue, final int maxValue)
    {
        final String property = this.getStringProperty(propertyName);
        if (property != null && !property.isBlank() && !property.matches("^-?\\d+$"))
        {
            throw new IllegalStateException("Property " + propertyName + " has not been set as a numeric value (integer expected)");
        }

        final Integer value = property != null && !property.isBlank() ? Integer.parseInt(property) : null;
        if (value != null)
        {
            if (value < minValue)
            {
                throw new IllegalStateException("Value of integer property " + propertyName + " must not be lower than " + minValue);
            }

            if (value > maxValue)
            {
                throw new IllegalStateException("Value of integer property " + propertyName + " must not be larger than " + maxValue);
            }
        }

        return value;
    }

    /**
     * Retrieves the long value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param defaultValue
     *            the default value to apply if the property has not been defined or is {@link String#isBlank() blank}
     * @param minValue
     *            the lower inclusive bound of valid values
     * @param maxValue
     *            the upper inclusive bound of valid values
     * @return the value of the property
     * @throws IllegalStateException
     *             if the configuration property is not valid long, either because it does not define an long number at all or falls
     *             outside the range of allowed values
     */
    default long getLongProperty(final String propertyName, final long defaultValue, final long minValue, final long maxValue)
    {
        final String property = this.getStringProperty(propertyName, String.valueOf(defaultValue));
        if (!property.matches("^-?\\d+$"))
        {
            throw new IllegalStateException("Property " + propertyName + " has not been set as a numeric value (long expected)");
        }

        final long value = Long.parseLong(property);

        if (value < minValue)
        {
            throw new IllegalStateException("Value of integer property " + propertyName + " must not be lower than " + minValue);
        }

        if (value > maxValue)
        {
            throw new IllegalStateException("Value of integer property " + propertyName + " must not be larger than " + maxValue);
        }

        return value;
    }

    /**
     * Retrieves the long value of a configuration property.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @param minValue
     *            the lower inclusive bound of valid values
     * @param maxValue
     *            the upper inclusive bound of valid values
     * @return the value of the property or {@code null} if the property has not been defined
     * @throws IllegalStateException
     *             if the configuration property is not valid long, either because it does not define an long number at all or falls
     *             outside the range of allowed values
     */
    default Long getLongProperty(final String propertyName, final long minValue, final long maxValue)
    {
        final String property = this.getStringProperty(propertyName);
        if (property != null && !property.isBlank() && !property.matches("^-?\\d+$"))
        {
            throw new IllegalStateException("Property " + propertyName + " has not been set as a numeric value (long expected)");
        }

        final Long value = property != null && !property.isBlank() ? Long.parseLong(property) : null;
        if (value != null)
        {
            if (value < minValue)
            {
                throw new IllegalStateException("Value of integer property " + propertyName + " must not be lower than " + minValue);
            }

            if (value > maxValue)
            {
                throw new IllegalStateException("Value of integer property " + propertyName + " must not be larger than " + maxValue);
            }
        }

        return value;
    }

    /**
     * Retrieves a list of values from textual configuration property, using comma-separation logic to split the underlying raw value into
     * the list values.
     *
     * @param propertyName
     *            the name of the property to retrieve
     * @return the list of values, excluding any {@link String#isBlank() blank} values
     */
    default List<String> getMultiValuedProperty(final String propertyName)
    {
        final String property = this.getStringProperty(propertyName);
        final List<String> values = new ArrayList<>();
        if (property != null && !property.isBlank())
        {
            values.addAll(Arrays.asList(property.split(",")));
            values.removeIf(String::isBlank);
        }
        return values;
    }

    /**
     * Initialises a SSL context factory. The provided property name base is used to retrieve detailed configuration properties.
     *
     * @param <T>
     *            the type of SSL context factory required by the caller
     * @param sslPropertyNameBase
     *            the base property name for SSL configuration properties, e.g. {@code application.ssl}
     * @param factoryProvider
     *            a provider to instantiate the required specialsied type of SSL context factory the caller requires
     * @return the configured SSL context factory
     */
    <T extends SslContextFactory> T getSslContextFactory(String sslPropertyNameBase, Supplier<T> factoryProvider);

    /**
     * Initialises a SSL context factory if SSL support has been enabled. SSL enablement is checked via checking the boolean value of a
     * configuration property equal to the provided property name base. The base is also used to retrieve detailed configuration properties.
     *
     * @param <T>
     *            the type of SSL context factory required by the caller
     * @param sslPropertyNameBase
     *            the base property name for SSL configuration properties, e.g. {@code application.ssl}
     * @param factoryProvider
     *            a provider to instantiate the required specialsied type of SSL context factory the caller requires
     * @return the configured SSL context factory or {@code null} if SSL support was configured as not enabled
     */
    <T extends SslContextFactory> T getSslContextFactoryIfEnabled(String sslPropertyNameBase, Supplier<T> factoryProvider);
}
