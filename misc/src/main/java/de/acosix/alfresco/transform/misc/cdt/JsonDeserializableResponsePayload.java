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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Instances of this interface encapsulate the payload of a DevTools Protocol command response that can be deserialised from JSON.
 *
 * @author Axel Faust
 */
public interface JsonDeserializableResponsePayload
{

    /**
     * Deserialise the payload from the provided JSON structure.
     *
     * @param payload
     *     the JSON object node holding the response payload
     */
    void deserialise(JsonNode payload);

}
