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
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.owasp.encoder.Encode;

import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.Entry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class LogHandler extends AbstractHandler
{

    private final String applicationName;

    private final TransformationLog transformationLog;

    public LogHandler(final String applicationName, final TransformationLog transformationLog)
    {
        super();
        this.applicationName = applicationName;
        this.transformationLog = transformationLog;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (target.equals("/log"))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                // TODO optional JSON rendering variant based on Accept header

                final String countStr = request.getParameter("count");
                final int count = countStr != null && !countStr.isBlank() ? Integer.parseInt(countStr) : 25;

                response.setStatus(HttpStatus.OK_200);
                response.addHeader("Content-Type", MimeTypes.Type.TEXT_HTML_UTF_8.asString());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                try (final PrintWriter writer = response.getWriter())
                {
                    this.writeLogPage(writer, count);
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

    private void writeLogPage(final Writer writer, final int numberOfEntries) throws IOException
    {
        writer.write("<html>\n");
        writer.write("<head>\n");
        writer.write("\t<style>\n");
        writer.write("\t\tdiv { margin-bottom: 2ex; }\n");
        writer.write("\t</style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n\t<div>\n\t\t<h2>");
        Encode.forHtml(writer, this.applicationName);
        writer.write(" Transformation Logs</h2>\n");

        final String cellIndent = "\t\t\t\t\t";
        writer.write("\t\t<table>\n\t\t\t<thead>\n\t\t\t\t<tr>\n");
        writer.append(cellIndent).append("<td>Host</td>\n");
        writer.append(cellIndent).append("<td>Seq. #</td>\n");
        writer.append(cellIndent).append("<td>Status</td>\n");
        writer.append(cellIndent).append("<td>End</td>\n");
        writer.append(cellIndent).append("<td>Duration</td>\n");
        writer.append(cellIndent).append("<td>Source</td>\n");
        writer.append(cellIndent).append("<td>Target</td>\n");
        writer.append(cellIndent).append("<td>Options</td>\n");
        writer.append(cellIndent).append("<td>Message</td>\n");
        writer.write("\t\t\t\t</tr>\n\t\t\t</thead>\n");

        final List<Entry> entries = this.transformationLog.getMostRecentEntries(numberOfEntries);

        if (!entries.isEmpty())
        {
            writer.write("\t\t\t<tbody>\n");

            for (final Entry entry : entries)
            {
                writer.write("\t\t\t\t<tr>\n");
                this.writeEntryTableRowCells(entry, writer, cellIndent);
                writer.write("\t\t\t\t</tr>\n");
            }

            writer.write("\t\t\t</tbody>\n");
        }
        writer.write("\t\t</table>\n\t</div>\n");
        writer.write("\t<div>\n\t\t<a href=\"/\">Test Transformation</a>\n\t</div>\n");
        writer.write("</body>\n</html>");
    }

    private void writeEntryTableRowCells(final Entry entry, final Writer writer, final String cellIndent) throws IOException
    {
        this.writeEntryTableRowCell(writer, entry.getHost(), cellIndent);
        this.writeEntryTableRowCell(writer, String.valueOf(entry.getSequenceNumber()), cellIndent);
        this.writeEntryTableRowCell(writer, String.valueOf(entry.getStatusCode()), cellIndent);
        this.writeEntryTableRowCell(writer, Instant.ofEpochMilli(entry.getEndTime()).toString().replace('T', ' '), cellIndent);
        this.writeEntryTableRowCell(writer, this.getTransformationDurationDisplayValue(entry), cellIndent);
        this.writeEntryTableRowCell(writer, this.getTypeAndSizeDisplayValue(entry.getSourceMimetype(), entry.getSourceSize()), cellIndent);
        this.writeEntryTableRowCell(writer, this.getTypeAndSizeDisplayValue(entry.getTargetMimetype(), entry.getResultSize()), cellIndent);
        this.writeEntryTableRowCell(writer, entry.getOptions().toString(), cellIndent);
        this.writeEntryTableRowCell(writer, entry.getStatusMessage(), cellIndent);
    }

    private void writeEntryTableRowCell(final Writer writer, final String value, final String cellIndent) throws IOException
    {
        writer.write(cellIndent);
        writer.write("<td>");
        if (value != null && !value.isBlank())
        {
            Encode.forHtml(writer, value);
        }
        writer.write("</td>\n");
    }

    private String getTransformationDurationDisplayValue(final Entry entry)
    {
        final StringBuilder sb = new StringBuilder(128);

        final long startTime = entry.getStartTime();
        final long endTime = entry.getEndTime();

        this.addDurationValue(endTime - startTime, sb);

        final long requestHandlingDuration = entry.getRequestHandlingDuration();
        final long transformationDuration = entry.getTransformationDuration();
        final long responseHandlingDuration = entry.getResponseHandlingDuration();

        if (requestHandlingDuration != -1)
        {
            sb.append(" (");
            this.addDurationValue(requestHandlingDuration, sb);

            if (transformationDuration != -1)
            {
                sb.append(", ");
                this.addDurationValue(transformationDuration, sb);
            }

            if (responseHandlingDuration != -1)
            {
                sb.append(", ");
                this.addDurationValue(responseHandlingDuration, sb);
            }
            sb.append(")");
        }

        return sb.toString();
    }

    private void addDurationValue(final long duration, final StringBuilder sb)
    {
        if (duration >= 1000)
        {
            final BigDecimal durationSeconds = BigDecimal.valueOf(duration).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
            sb.append(durationSeconds.toPlainString()).append(" s");
        }
        else
        {
            sb.append(String.valueOf(duration)).append(" ms");
        }
    }

    private String getTypeAndSizeDisplayValue(final String mimetype, final long size)
    {
        String displayValue;

        if (size <= 0)
        {
            displayValue = mimetype;
        }
        else
        {
            BigDecimal bigSize = BigDecimal.valueOf(size);
            int unitPrefixDivisions = 0;
            while (bigSize.intValue() >= 1024)
            {
                bigSize = bigSize.divide(BigDecimal.valueOf(1024), 2, RoundingMode.HALF_UP);
                unitPrefixDivisions++;
            }

            final StringBuilder sb = new StringBuilder(128);
            sb.append(mimetype).append(" (");
            sb.append(bigSize.toPlainString()).append(" ");

            final String unit;
            switch (unitPrefixDivisions)
            {
                case 0:
                    unit = "B";
                    break;
                case 1:
                    unit = "KiB";
                    break;
                case 2:
                    unit = "MiB";
                    break;
                // ridiculous, but possible for extremely large media files
                case 3:
                    unit = "GiB";
                    break;
                // ludicrous
                case 4:
                    unit = "TiB";
                    break;
                // plaid
                case 5:
                    unit = "PiB";
                    break;
                default:
                    throw new IllegalArgumentException("Invalid / unreasonably large file size");
            }
            sb.append(unit);

            sb.append(")");

            displayValue = sb.toString();
        }

        return displayValue;
    }
}
