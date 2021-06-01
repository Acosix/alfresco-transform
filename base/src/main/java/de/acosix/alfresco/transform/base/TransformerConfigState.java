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

import java.util.Set;

import org.alfresco.transform.client.model.config.SupportedSourceAndTarget;

/**
 * Instances of this interface encapsulate the configuration state of a specific transformer
 *
 * @author Axel Faust
 */
public interface TransformerConfigState
{

    /**
     * Retrieves the name of this instance
     *
     * @return the name of this instance
     */
    String getName();

    /**
     * Retrieves the set references to transform options this transformer supports.
     *
     * @return the names of supported transform options
     */
    Set<String> getTransformOptions();

    /**
     * Retrieves the collection of transformations this instance supports
     *
     * @return the supported transformations
     */
    Set<SupportedSourceAndTarget> getSupportedTransformations();
}
