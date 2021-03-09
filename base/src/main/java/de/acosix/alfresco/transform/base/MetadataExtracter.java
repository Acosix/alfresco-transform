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

import java.util.Collection;
import java.util.Set;

/**
 * Instances of this interface represent specific metadata extraction workers, parsing / processing the content of a file and extracting
 * metadata properties to be applied to an Alfresco node which owns that particular content.
 *
 * @author Axel Faust
 */
public interface MetadataExtracter
{

    /**
     * Retrieves the name of this instance
     *
     * @return the name of this instance
     */
    String getName();

    /**
     * Retrieves the set references to transform options this extracter supports.
     *
     * @return the names of supported transform options
     */
    Set<String> getTransformOptions();

    /**
     * Retrieves the collection of mimetypes from which this extracter is able to extract metadata properties
     *
     * @return the supported collection of mimetypes
     */
    Collection<String> getSupportedSourceMimetypes();

    // TODO flesh out
}
