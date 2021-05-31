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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.alfresco.transform.client.model.config.TransformOption;
import org.alfresco.transform.client.model.config.TransformOptionGroup;
import org.alfresco.transform.client.model.config.TransformOptionValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.owasp.encoder.Encode;

import de.acosix.alfresco.transform.base.Registry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class TestFormHandler extends AbstractHandler
{

    private final String applicationName;

    private final Registry registry;

    public TestFormHandler(final String applicationName, final Registry registry)
    {
        super();
        this.applicationName = applicationName;
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
        if (target.equals("/"))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                response.setStatus(HttpStatus.OK_200);
                response.addHeader("Content-Type", MimeTypes.Type.TEXT_HTML_UTF_8.asString());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                try (final PrintWriter writer = response.getWriter())
                {
                    this.writeTestForm(writer);
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

    private void writeTestForm(final Writer writer) throws IOException
    {
        writer.write("<html>\n");
        writer.write("<head>\n");
        writer.write("\t<style>\n");
        writer.write("\t\ttd.label { text-align: right; }\n");
        writer.write("\t\tinput[type=\"text\"] { width: 20em; }\n");
        writer.write("\t</style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n\t<div>\n\t\t<h2>");
        Encode.forHtml(writer, this.applicationName);
        writer.write(" Test Transformations</h2>\n");

        writer.write("\t\t<form method=\"POST\" enctype=\"multipart/form-data\" action=\"/transform\">\n");

        writer.write("\t\t\t<table>\n");
        writer.write("\t\t\t\t<tbody>\n");
        this.writeFormControl(writer, "file *", "file", "file");
        this.writeFormControl(writer, "sourceMimetype *", "sourceMimetype", "text");
        this.writeFormControl(writer, "targetExtension *", "targetExtension", "text");
        this.writeFormControl(writer, "targetMimetype *", "targetMimetype", "text");
        this.writeFormControl(writer, "timeout", "timeout", "text");
        writer.write("\t\t\t\t</tbody>\n");

        final Map<String, Set<TransformOption>> rootTransformOptions = this.registry.getAllRootTransformOptions();
        for (final Set<TransformOption> rootSet : rootTransformOptions.values())
        {
            this.writeOptionSet(writer, rootSet);
        }

        writer.write("\t\t\t\t<tbody>\n");
        writer.write("\t\t\t\t\t<tr><td></td><td><input type=\"submit\" value=\"Transform\" /></td></tr>\n");
        writer.write("\t\t\t\t</tbody>\n");

        writer.write("\t\t\t</table>\n");
        writer.write("\t\t</form>\n");
        writer.write("\t</div>\n\t<div>\n\t\t<a href=\"/log\">Log entries</a>\n\t</div>\n</body>\n</html>");
    }

    private void writeOptionSet(final Writer writer, final Set<TransformOption> options) throws IOException
    {
        writer.write("\t\t\t\t<tbody>\n");
        writer.write("\t\t\t\t\t<tr><td class=\"optionGroupStart\" colspan=\"2\">Start option group</td>");
        for (final TransformOption option : options)
        {
            this.writeOption(writer, option);
        }
        writer.write("\t\t\t\t\t<tr><td class=\"optionGroupEnd\" colspan=\"2\">End option group</td>");
        writer.write("\t\t\t\t</tbody>\n");
    }

    private void writeOption(final Writer writer, final TransformOption option) throws IOException
    {
        if (option instanceof TransformOptionValue)
        {
            final String name = ((TransformOptionValue) option).getName();
            final boolean isRequired = option.isRequired();

            final String label = name + (isRequired ? " *" : "");
            this.writeFormControl(writer, label, name, "text");
        }
        else if (option instanceof TransformOptionGroup)
        {
            final Set<TransformOption> subOptions = ((TransformOptionGroup) option).getTransformOptions();

            writer.write("\t\t\t\t\t<tr><td class=\"optionGroupStart\" colspan=\"2\">Start nested option group</td>");
            for (final TransformOption subOption : subOptions)
            {
                this.writeOption(writer, subOption);
            }
            writer.write("\t\t\t\t\t<tr><td class=\"optionGroupEnd\" colspan=\"2\">End nested option group</td>");
        }
    }

    private void writeFormControl(final Writer writer, final String label, final String fieldName, final String type) throws IOException
    {
        writer.write("\t\t\t\t\t<tr><td class=\"label\">");
        Encode.forHtml(writer, label);
        writer.write("</td><td><input type=\"");
        writer.write(type);
        writer.write("\" name=\"");
        Encode.forHtml(writer, fieldName);
        writer.write("\"");

        switch (type)
        {
            case "checkbox":
                writer.write(" value=\"true\"");
                break;
            case "text":
                writer.write(" value=\"\"");
                break;
            default: // NO-OP
        }

        writer.write(" /></td></tr>\n");
    }
}
