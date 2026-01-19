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

import java.util.List;

import org.alfresco.transform.config.TransformStep;

/**
 * Instances of this interface encapsulate the configuration of a pipeline transformer. Pipeline transformers are configuration-only
 * instances, meant to be sent as part of the overall transformer application configuration to Alfresco Content Services for instantiation
 * in that layer, which is expected to have access to any and all transformers that may potentially be referenced in a pipeline without
 * being part of the specific transformer application in which instances of this configuration state interface are present.
 *
 * @author Axel Faust
 */
public interface TransformerPipelineConfig extends TransformerConfigState
{

    /**
     * Retrieves the transformation steps which must be executed in order to affect the overall transformation.
     *
     * @return the transformation pipeline steps
     */
    List<TransformStep> getPipelineSteps();

    /**
     * Retrieves the local transformer configurations that must be available / active in order to expose this transformation.
     * 
     * @return the required local transformations by name
     */
    List<String> getLocalDependencies();
}
