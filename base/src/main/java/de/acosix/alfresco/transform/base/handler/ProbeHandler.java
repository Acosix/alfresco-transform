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
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.Registry;
import de.acosix.alfresco.transform.base.RequestConstants;
import de.acosix.alfresco.transform.base.StatusException;
import de.acosix.alfresco.transform.base.TransformationException;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.Transformer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class ProbeHandler extends ContextAwareHandler
{

    private static final String READY = "ready";

    private static final String LIVE = "live";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProbeHandler.class);

    private final Registry registry;

    private final TransformationLog transformationLog;

    private final long defaultTransformTimeout;

    public ProbeHandler(final Context configuration, final Registry registry, final TransformationLog transformationLog)
    {
        super(configuration);
        this.registry = registry;
        this.transformationLog = transformationLog;
        this.defaultTransformTimeout = this.context.getLongProperty("application.default.transformTimeout",
                RequestConstants.DEFAULT_TRANSFORM_TIMEOUT, 1, Long.MAX_VALUE);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        final boolean liveProbe = target.equals("/live");
        if (liveProbe || target.equals("/ready"))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                final String propertyPrefix = liveProbe ? "probe.live." : "probe.ready.";
                final String probeTransformers = this.context.getStringProperty(propertyPrefix + "transformerNames");

                response.addHeader("Content-Type", MimeTypes.Type.TEXT_PLAIN_UTF_8.asString());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                if (probeTransformers != null && probeTransformers.matches("^[^,\\s]+(,[^,\\s]+)*$"))
                {
                    this.runProbes(request, response, liveProbe, probeTransformers);
                }
                else
                {
                    LOGGER.warn("Failed to run {} probe as no transformers are configured for this type of probe",
                            liveProbe ? LIVE : READY);
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    try (PrintWriter writer = response.getWriter())
                    {
                        writer.write("Failure - No transformers configured for probe.");
                    }
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

    private void runProbes(final HttpServletRequest request, final HttpServletResponse response, final boolean liveProbe,
            final String probeTransformers) throws IOException
    {
        LOGGER.debug("About to run {} probe with transformers {}", liveProbe ? LIVE : READY, probeTransformers);
        final String[] transformerNames = probeTransformers.split(",");

        final List<String> messages = new ArrayList<>(transformerNames.length);
        for (final String transformerName : transformerNames)
        {
            final String message = this.probe(transformerName, liveProbe);
            messages.add(message);
        }

        final boolean oneSuccess = messages.stream().anyMatch(message -> message.startsWith("Success - "));
        final boolean allSuccess = messages.stream().allMatch(message -> message.startsWith("Success - "));
        final boolean reportAsOverallSuccess = (liveProbe && allSuccess) || (!liveProbe && oneSuccess);

        response.setStatus(reportAsOverallSuccess ? HttpStatus.OK_200 : HttpStatus.INTERNAL_SERVER_ERROR_500);
        try (PrintWriter writer = response.getWriter())
        {
            boolean first = true;
            for (final String message : messages)
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    writer.write('\n');
                }
                writer.write(message);
            }
        }
    }

    private String probe(final String probeTransformer, final boolean liveProbe)
    {
        final String propertyPrefix = (liveProbe ? "probe.live." : "probe.ready.") + probeTransformer + '.';

        final boolean runTransform = this.context.getBooleanProperty(propertyPrefix + "runTransform", liveProbe);

        if (!runTransform)
        {
            LOGGER.debug("Transformer {} set to not run actual transformation for {} probe", probeTransformer, liveProbe ? LIVE : READY);
            return "Success - No transform via transformer " + probeTransformer;
        }

        final MutableEntry logEntry = this.transformationLog.startNewEntry();
        try
        {
            logEntry.recordSelectedTransformer(probeTransformer);
            final String sourceFileName = this.context.getStringProperty(propertyPrefix + "sourceFileName");
            final String targetFileName = this.context.getStringProperty(propertyPrefix + "targetFileName",
                    sourceFileName + ".transformed");
            if (!this.verifyResource(sourceFileName))
            {
                throw new IllegalStateException("Source file " + sourceFileName + " must be set and present");
            }

            final String sourceMimetype = this.context.getStringProperty(propertyPrefix + "sourceMimetype");
            final String targetMimetype = this.context.getStringProperty(propertyPrefix + "targetMimetype");
            if (sourceMimetype == null || sourceMimetype.isBlank())
            {
                throw new IllegalStateException("Source mimetype must be set");
            }
            if (targetMimetype == null || targetMimetype.isBlank())
            {
                throw new IllegalStateException("Target mimetype must be set");
            }

            logEntry.recordRequestValues(sourceMimetype, -1, targetMimetype, Collections.emptyMap());

            final long expectedLength = this.context.getLongProperty(propertyPrefix + "expectedLength", 1, 1, Long.MAX_VALUE);
            final long validLengthDeviation = this.context.getLongProperty(propertyPrefix + "validLengthDeviation", 0, 0,
                    Long.MAX_VALUE - expectedLength);
            final long minLength = Math.max(0, expectedLength - validLengthDeviation);
            final long maxLength = expectedLength + validLengthDeviation;

            final Long transformTimeout = this.context.getLongProperty(propertyPrefix + "transformTimeout", 1, Long.MAX_VALUE);

            return this.doProbe(logEntry, probeTransformer, liveProbe, sourceFileName, sourceMimetype, targetFileName, targetMimetype,
                    minLength, maxLength, transformTimeout);
        }
        finally
        {
            this.transformationLog.closeCurrentEntry();
        }
    }

    private boolean verifyResource(final String resourceName)
    {
        boolean valid = resourceName != null && !resourceName.isBlank();
        if (valid)
        {
            final URL resource = this.getClass().getResource("/" + resourceName);
            final Path path = Paths.get(resourceName);
            valid = resource != null || (Files.isReadable(path) && Files.isRegularFile(path));
        }
        return valid;
    }

    private String doProbe(final MutableEntry logEntry, final String probeTransformer, final boolean liveProbe, final String sourceFileName,
            final String sourceMimetype, final String targetFileName, final String targetMimetype, final long minLength,
            final long maxLength, final Long transformTimeout)
    {
        final Path sourceFile = this.createSourceFile(sourceFileName);

        try
        {
            logEntry.recordRequestValues(sourceMimetype, Files.size(sourceFile), targetMimetype, Collections.emptyMap());
        }
        catch (final IOException ioex)
        {
            LOGGER.warn("Unable to determine size of source file {}", sourceFile, ioex);
        }

        final Path targetFile = this.context.createTempFile("target_", "_" + targetFileName);
        try
        {
            logEntry.markStartOfTransformation();
            final long start = System.currentTimeMillis();
            TransformationException tex = null;
            try
            {
                this.transform(probeTransformer, sourceFile, sourceMimetype, targetFile, targetMimetype,
                        transformTimeout != null ? transformTimeout.longValue() : this.defaultTransformTimeout);
            }
            catch (final TransformationException tex1)
            {
                LOGGER.warn("Failed to run {} probe via transformer {}", liveProbe ? LIVE : READY, probeTransformer, tex1);
                tex = tex1;
            }
            finally
            {
                logEntry.markEndOfTransformation();
            }

            final long end = System.currentTimeMillis();

            final long duration = end - start;

            return this.determineProbeResult(logEntry, probeTransformer, liveProbe, targetFile, tex, minLength, maxLength,
                    transformTimeout != null ? transformTimeout.longValue() : this.defaultTransformTimeout, duration);
        }
        finally
        {
            this.context.discardTempFile(sourceFile);
            this.context.discardTempFile(targetFile);
        }
    }

    private Path createSourceFile(final String resourceName)
    {
        final Path tempFile = this.context.createTempFile("source_", "_" + resourceName);

        final InputStream resourceAsStream = this.getClass().getResourceAsStream("/" + resourceName);
        final Path path = Paths.get(resourceName);
        try
        {
            if (resourceAsStream != null)
            {
                try
                {
                    Files.copy(resourceAsStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                finally
                {
                    resourceAsStream.close();
                }
            }
            else if (Files.isReadable(path) && Files.isRegularFile(path))
            {
                Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (final IOException ioex)
        {
            throw new StatusException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to copy from " + resourceName + " to temporary file");
        }

        return tempFile;
    }

    private String determineProbeResult(final MutableEntry logEntry, final String probeTransformer, final boolean liveProbe,
            final Path targetFile, final TransformationException tex, final long minLength, final long maxLength,
            final long transformTimeout, final long duration)
    {
        String message = "Success - Transformation via " + probeTransformer + " took " + duration + "ms";
        if (tex != null)
        {
            message = "Failed - Transformation via " + probeTransformer + " failed due to " + tex.getMessage();
            logEntry.setStatus(tex.getStatus(), message);
        }
        else if (transformTimeout < duration)
        {
            LOGGER.warn("Transformation duration of {} ms in {} probe via {} exceeded expectation of {} ms", duration,
                    liveProbe ? LIVE : READY, probeTransformer, transformTimeout);
            message = "Failed - Transformation via " + probeTransformer + " took " + duration + "ms, which is more than the allowed "
                    + transformTimeout + "ms";
            logEntry.setStatus(HttpStatus.REQUEST_TIMEOUT_408, message);
        }
        else
        {
            try
            {
                final long targetSize = Files.size(targetFile);
                logEntry.recordResultSize(targetSize);
                if (targetSize < minLength && targetSize > maxLength)
                {
                    message = "Failed - Transformation via " + probeTransformer + " resulted in file of " + targetSize
                            + " bytes, which outside the expected range of " + minLength + " to " + maxLength + " bytes";
                    logEntry.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500, message);
                }
            }
            catch (final IOException ioex)
            {
                LOGGER.warn("Failed to determine file size of {} in {} probe via {}", targetFile, liveProbe ? LIVE : READY,
                        probeTransformer, ioex);
                message = "Failed - Transformation via " + probeTransformer + " resulted in file of indeterminable size";
                logEntry.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500, message);
            }
        }
        return message;
    }

    private void transform(final String transformer, final Path sourceFile, final String sourceMimetype, final Path targetFile,
            final String targetMimetype, final long transformTimeout)
    {
        final Transformer transformerInstance;
        try
        {
            transformerInstance = this.registry.getTransformer(transformer);
        }
        catch (final IllegalArgumentException iae)
        {
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, iae.getMessage(), iae);
        }
        transformerInstance.transform(sourceFile, sourceMimetype, targetFile, targetMimetype, transformTimeout);
    }
}
