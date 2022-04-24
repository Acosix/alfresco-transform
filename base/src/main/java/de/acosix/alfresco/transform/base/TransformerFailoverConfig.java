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
package de.acosix.alfresco.transform.base;

import java.util.List;

/**
 * Instances of this interface encapsulate the configuration of a failover transformer. Failover transformers are configuration-only
 * instances, meant to be sent as part of the overall transformer application configuration to Alfresco Content Services for instantiation
 * in that layer, which is expected to have access to any and all transformers that may potentially be referenced in a pipeline without
 * being part of the specific transformer application in which instances of this configuration state interface are present.
 *
 * @author Axel Faust
 */
public interface TransformerFailoverConfig extends TransformerConfigState
{

    /**
     * Retrieves the list of transformers which should be attempted in order until the first transformer supports and succeeds to perform
     * the requested transformation.
     *
     * @return the names of transformers in the failover configuration
     */
    List<String> getFailoverTransformers();
}
