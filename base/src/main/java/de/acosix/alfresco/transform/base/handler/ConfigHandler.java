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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import de.acosix.alfresco.transform.base.Registry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class ConfigHandler extends AbstractHandler
{

    private final Registry registry;

    public ConfigHandler(final Registry registry)
    {
        super();
        this.registry = registry;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (target.equals("/transform/config"))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                response.setStatus(HttpStatus.OK_200);
                response.addHeader("Content-Type", MimeTypes.Type.APPLICATION_JSON_UTF_8.asString());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                try (final PrintWriter writer = response.getWriter())
                {
                    this.registry.writeTransformConfigJSON(writer);
                }
            }
            else
            {
                response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                response.flushBuffer();
            }
            baseRequest.setHandled(true);
        }
    }

}
