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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.alfresco.transform.client.model.config.TransformOption;

/**
 *
 * @author Axel Faust
 */
public interface Registry
{

    /**
     * Registers a transformer.
     *
     * @param transformer
     *            the transformer to register
     */
    void registerTransformer(Transformer transformer);

    /**
     * Registers a metadata extracter.
     *
     * @param metadataExtracter
     *            the metadata extracter to register
     */
    void registerMetadataExtracter(MetadataExtracter metadataExtracter);

    /**
     * Looks up a particular registered transformer by name.
     *
     * @param name
     *            the name of the transformer to look up
     * @return the transformer
     */
    Transformer getTransformer(String name);

    /**
     * Looks up a particular registered metadata extracter.
     *
     * @param name
     *            the name of the metadata extracter to look up
     * @return the metadata extracter
     */
    MetadataExtracter getMetadataExtractor(String name);

    /**
     * Finds the highest prioritised transformer which supports a specific requested transformation
     *
     * @param sourceMimetype
     *            the mimetype from which a transformation is to take place
     * @param sourceSizeBytes
     *            the size of the source content in bytes
     * @param targetMimetype
     *            the mimetype to which a transformation is to take place
     * @param options
     *            the options provided for the transformation
     * @return the name of the highest prioritised transformer
     */
    Optional<String> findTransformer(String sourceMimetype, long sourceSizeBytes, String targetMimetype, Map<String, String> options);

    /**
     * Retrieves all supported root transform options keyed by the global definition name, which may be referenced by transformers and
     * extracters.
     *
     * @return the root transform options
     */
    Map<String, Set<TransformOption>> getAllRootTransformOptions();

    /**
     * Retrieves the default options for a specific transformer.
     *
     * @param transformerName
     *            the name of the transformer for which to retrieve the default options
     * @return the map of non-{@link String#isBlank() blank} default options
     */
    Map<String, String> getDefaultOptions(String transformerName);

    /**
     * Writes the internal transformer registrations / configuration into a JSON representation. This operation must produce a structure
     * 100% consistent / serialisation compatible with the Alfresco transformer framework's {@code engine.json} configuration files.
     *
     * @param writer
     *            the writer to use for outputting the JSON representation
     * @throws IOException
     *             if an error occurs during writing of the JSON representation
     */
    void writeTransformConfigJSON(Writer writer) throws IOException;
}
