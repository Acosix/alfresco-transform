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
package de.acosix.alfresco.transform.base.handler;

import org.eclipse.jetty.server.handler.AbstractHandler;

import de.acosix.alfresco.transform.base.Context;

/**
 * @author Axel Faust
 */
public abstract class ContextAwareHandler extends AbstractHandler
{

    protected final Context context;

    protected ContextAwareHandler(final Context context)
    {
        super();
        this.context = context;
    }
}
