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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author Axel Faust
 */
public final class RequestConstants
{

    public static final long DEFAULT_TRANSFORM_TIMEOUT = 15 * 60 * 1000;

    // timeout defined in Alfresco transformation apps, but not yet used there at all
    public static final String TIMEOUT = "timeout";

    public static final String SOURCE_ENCODING = "sourceEncoding";

    public static final String SOURCE_EXTENSION = "sourceExtension";

    public static final String TARGET_EXTENSION = "targetExtension";

    public static final String SOURCE_MIMETYPE = "sourceMimetype";

    public static final String TARGET_MIMETYPE = "targetMimetype";

    public static final Collection<String> NON_TRANSFORMATION_PARAMETER_NAMES = Collections.unmodifiableList(
            Arrays.asList(TIMEOUT, SOURCE_EXTENSION, TARGET_EXTENSION, SOURCE_MIMETYPE, TARGET_MIMETYPE, "testDelay", "transformName"));

    public static final Collection<String> NON_TRANSFORMATION_SELECTOR_PARAMETER_NAMES = Collections
            .unmodifiableList(Arrays.asList(SOURCE_ENCODING, "transformName", "alfresco.transform-name-parameter"));

    private RequestConstants()
    {
        // NO-OP
    }
}
