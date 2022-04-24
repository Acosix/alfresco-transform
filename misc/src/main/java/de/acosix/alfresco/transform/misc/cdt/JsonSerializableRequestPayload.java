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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Instances of this interface encapsulate the payload of a DevTools Protocol command that can be serialised to JSON.
 *
 * @author Axel Faust
 */
public interface JsonSerializableRequestPayload
{

    /**
     * Serialise the payload into a JSON structure. This operation must only write the contents of the payload object, which has already
     * been initialised via the provided generator.
     *
     * @param jsonGenerator
     *     the generator used to emit the JSON structure
     *
     * @throws IOException
     *     if an error occurs writing the JSON structure to the generator-backing output
     */
    void serialise(JsonGenerator jsonGenerator) throws IOException;

}
