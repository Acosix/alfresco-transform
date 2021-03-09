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
package de.acosix.alfresco.transform.base.handler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class VersionHandler extends AbstractHandler
{

    private String version;

    public VersionHandler(final String defaultVersion)
    {
        super();
        this.version = defaultVersion;
    }

    /**
     * Updates the version information by replacing the currently set info, e.g. after a dynamic runtime check has been performed on
     * underlying software packages
     *
     * @param version
     *            the updated version information
     */
    public void setVersion(final String version)
    {
        this.version = version;
    }

    /**
     * Retrieves the currently set version information, which unless {@link #setVersion(String) changed} will be the version of the
     * application as set in its configuration file(s)
     *
     * @return the current version information
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (target.equals("/version"))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                response.setStatus(HttpStatus.OK_200);
                response.addHeader("Content-Type", MimeTypes.Type.TEXT_PLAIN_UTF_8.asString());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                try (PrintWriter writer = response.getWriter())
                {
                    writer.write(this.version);
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
