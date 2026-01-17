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
package de.acosix.alfresco.transform.base;

/**
 *
 * @author Axel Faust
 */
public interface SupportedTransformation
{

    /**
     * Retrieves the mimetype from which content can be transformed
     *
     * @return the source mimetype
     */
    String getSourceMimetype();

    /**
     * Retrieves the mimetype to which content can be transformed
     *
     * @return the target mimetype
     */
    String getTargetMimetype();

    /**
     * Retrieves the priority of this transformation as a value to put into relation with other transformer's transformations with the same
     * source and target mimetypes.
     *
     * @return the priority
     */
    int getPriority();

    /**
     * Retrieves the limit on the size of source files in bytes.
     *
     * @return the limit for the size of source files in bytes
     */
    Long getMaxSourceBytes();
}
