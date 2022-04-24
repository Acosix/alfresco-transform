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
package de.acosix.alfresco.transform.base.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.alfresco.transform.client.model.TransformReply;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.Registry;
import de.acosix.alfresco.transform.base.RequestConstants;
import de.acosix.alfresco.transform.base.SharedFileAccessException;
import de.acosix.alfresco.transform.base.SharedFileAccessor;
import de.acosix.alfresco.transform.base.StatusException;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.Transformer;
import de.acosix.alfresco.transform.base.dto.TransformRequest;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

/**
 * @author Axel Faust
 */
public class TransformHandler extends ContextAwareHandler
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformHandler.class);

    private final Registry registry;

    private final TransformationLog transformationLog;

    private final long defaultTransformTimeout;

    private final SharedFileAccessor sharedFileAccessor;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // since we use raw handlers without a full servlet context, we need to set a multipart config on a request before handling multipart
    // request messages
    private final MultipartConfigElement multiPartConfig;

    public TransformHandler(final Context context, final Registry registry, final TransformationLog transformationLog,
            final SharedFileAccessor sharedFileAccessor)
    {
        super(context);
        this.registry = registry;
        this.transformationLog = transformationLog;
        this.sharedFileAccessor = sharedFileAccessor;

        this.defaultTransformTimeout = this.context.getLongProperty("application.default.transformTimeout",
                RequestConstants.DEFAULT_TRANSFORM_TIMEOUT, 1, Long.MAX_VALUE);

        final Path tmpDir = context.createTempFileSubDirectory("multipartRequest");
        final long maxFileSize = context.getLongProperty("application.multipartRequest.maxFileSize", -1, -1, Long.MAX_VALUE);
        final long maxRequestSize = context.getLongProperty("application.multipartRequest.maxRequestSize", -1, -1, Long.MAX_VALUE);

        this.multiPartConfig = new MultipartConfigElement(tmpDir.toString(), maxFileSize, maxRequestSize, 1024 * 100);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (target.equals("/transform"))
        {
            if (!baseRequest.getMethod().equals(HttpMethod.POST.name()))
            {
                throw new StatusException(HttpStatus.METHOD_NOT_ALLOWED_405, "Only POST requests supported on this endpoint");
            }

            final MutableEntry logEntry = this.transformationLog.startNewEntry();

            final String contentType = baseRequest.getContentType();
            final int semiColonIdx = contentType.indexOf(';');
            final String mimetypeOnly = semiColonIdx != -1 ? contentType.substring(0, semiColonIdx) : contentType;

            if (MimeTypes.Type.MULTIPART_FORM_DATA.is(mimetypeOnly))
            {
                baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, this.multiPartConfig);
                this.handleMultiPartRequest(request, response, logEntry);
            }
            else if (MimeTypes.Type.APPLICATION_JSON.is(mimetypeOnly))
            {
                final String charset = MimeTypes.getCharsetFromContentType(contentType);
                final boolean utf8 = !StringUtil.__ISO_8859_1.equals(charset);
                this.handleJSONRequest(utf8, request, response, logEntry);
            }
            else
            {
                LOGGER.debug("Rejecting transformation request with invalid request content type {}", contentType);
                throw new StatusException(HttpStatus.BAD_REQUEST_400,
                        "Only multipart/form-data or application/json requests supported on this endpoint");
            }

            baseRequest.setHandled(true);
        }
    }

    private void handleMultiPartRequest(final HttpServletRequest request, final HttpServletResponse response, final MutableEntry logEntry)
            throws IOException, ServletException
    {
        final Part filePart = this.getPart(request, "file", true);
        final String targetExtension = this.getParameter(request, RequestConstants.TARGET_EXTENSION, true);
        // Alfresco transformer apps have both mimetypes as non-required in API, which is a lie
        // if not provided, transformer registry lookup will report error later on
        // we require target mimetype and try to fall back on content type in provided file for source
        String sourceMimetype = this.getParameter(request, RequestConstants.SOURCE_MIMETYPE, false);
        if (sourceMimetype == null || sourceMimetype.isBlank())
        {
            sourceMimetype = filePart.getContentType();
        }
        final String targetMimetype = this.getParameter(request, RequestConstants.TARGET_MIMETYPE, true);
        final String timeout = this.getParameter(request, RequestConstants.TIMEOUT, false);
        final Long timeoutL = timeout != null && !timeout.isBlank() ? Long.parseLong(timeout) : null;
        final Map<String, String> transformationRequestParameters = this.getTransformationRequestParameters(request);

        logEntry.recordRequestValues(sourceMimetype, -1, targetMimetype, transformationRequestParameters);
        LOGGER.debug(
                "Handling multipart/form-data transformation request from source mimetype {} to target {}, using extension {}, timeout {} and request parameters {}",
                sourceMimetype, targetMimetype, targetExtension, timeout != null ? timeout : "(default)", transformationRequestParameters);

        final String sourceFileName = this.getEffectiveSourceFileName(filePart);

        Path sourceFile = null;
        Path targetFile = null;
        String targetFileName = null;
        try
        {
            boolean failed = false;
            try
            {
                sourceFile = this.prepareSourceFile(filePart, sourceFileName);

                // sourceFile should now be in local temporary files, so there should be no IOException
                final long sourceSize = Files.size(sourceFile);
                // re-record since we now have a reliable source size
                logEntry.recordRequestValues(sourceMimetype, sourceSize, targetMimetype, transformationRequestParameters);

                targetFileName = this.getEffectiveTargetFileName(sourceFileName, targetExtension);
                targetFile = this.context.createTempFile("target_", "_" + targetFileName);

                this.doTransform(logEntry, sourceFile, sourceMimetype, targetFile, targetMimetype, timeoutL,
                        transformationRequestParameters);
            }
            catch (final StatusException stex)
            {
                final String messageWithCause = messageWithCause("Failed to perform transformation", stex);
                logEntry.setStatus(stex.getStatus(), messageWithCause);
                response.sendError(stex.getStatus(), messageWithCause);
                failed = true;
            }
            catch (final Exception ex)
            {
                final String messageWithCause = messageWithCause("Unexpected error during transformation request processing", ex);
                logEntry.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500, messageWithCause);
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, messageWithCause);
                failed = true;
            }

            if (!failed)
            {
                response.setStatus(HttpStatus.OK_200);
                response.setContentType(targetMimetype);
                final long targetSize = Files.size(targetFile);
                logEntry.recordResultSize(targetSize);
                response.setContentLengthLong(targetSize);
                response.setHeader("Content-Disposition",
                        "attachment; filename*= UTF-8''" + UrlEncoded.encodeString(targetFileName, StandardCharsets.UTF_8));
                try (final OutputStream os = response.getOutputStream())
                {
                    Files.copy(targetFile, os);
                }

                logEntry.setStatus(HttpStatus.OK_200);
            }

            this.transformationLog.closeCurrentEntry();
        }
        finally
        {
            this.context.discardTempFile(sourceFile);
            this.context.discardTempFile(targetFile);

        }
    }

    private void handleJSONRequest(final boolean utf8, final HttpServletRequest request, final HttpServletResponse response,
            final MutableEntry logEntry) throws IOException
    {
        LOGGER.debug("Handling JSON transformation request in {}", utf8 ? "UTF-8" : "ISO-8859-1");

        TransformRequest transformRequest;

        try (Reader reader = new InputStreamReader(request.getInputStream(),
                (utf8 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1).name()))
        {
            transformRequest = this.jsonMapper.readValue(reader, TransformRequest.class);
        }
        catch (final JsonProcessingException jsonEx)
        {
            throw new StatusException(HttpStatus.BAD_REQUEST_400, jsonEx.getMessage());
        }

        final TransformReply transformReply = new TransformReply();
        transformReply.setInternalContext(transformRequest.getInternalContext());
        transformReply.setRequestId(transformRequest.getRequestId());
        transformReply.setSourceReference(transformRequest.getSourceReference());
        transformReply.setSchema(transformRequest.getSchema());
        transformReply.setClientData(transformRequest.getClientData());

        // if not provided via request body but via request parameters
        // handle timeout for compatibility with how Alfresco transformers handle it (should really be in JSON there as well)
        final String timeoutParam = this.getParameter(request, "timeout", false);
        if (transformRequest.getTimeout() == null && timeoutParam != null && !timeoutParam.isBlank())
        {
            try
            {
                final Long timeout = Long.parseLong(timeoutParam);
                transformRequest.setTimeout(timeout);
            }
            catch (final NumberFormatException nex)
            {
                LOGGER.warn("Non-numeric timeout parameter value {} provided via request parameters", timeoutParam);
            }
        }

        logEntry.recordRequestValues(transformRequest.getSourceMediaType(),
                transformRequest.getSourceSize() != null ? transformRequest.getSourceSize() : -1, transformRequest.getTargetMediaType(),
                transformRequest.getTransformRequestOptions());
        LOGGER.debug(
                "Handling JSON transformation request for source file reference {} from source mimetype {} to target {}, using extension {}, timeout {} and request parameters {}",
                transformRequest.getSourceReference(), transformRequest.getSourceMediaType(), transformRequest.getTargetMediaType(),
                transformRequest.getTargetExtension(), transformRequest.getTimeout(), transformRequest.getTransformRequestOptions());

        this.validateTransformRequest(transformRequest, transformReply);

        if (!(transformReply.getStatus() >= 400 && transformReply.getStatus() <= 599))
        {
            Path sourceFile = null;
            Path targetFile = null;
            try
            {
                sourceFile = this.prepareSourceFile(transformRequest.getSourceReference());

                // sourceFile should now be in local temporary files, so there should be no IOException
                final long sourceSize = Files.size(sourceFile);
                // re-record since we now have a reliable source size
                logEntry.recordRequestValues(transformRequest.getSourceMediaType(), sourceSize, transformRequest.getTargetMediaType(),
                        transformRequest.getTransformRequestOptions());

                final String sourceFileName = sourceFile.getFileName().toString();
                final String targetFileName = this.getEffectiveTargetFileName(sourceFileName, transformRequest.getTargetExtension());
                targetFile = this.context.createTempFile("target_", "_" + targetFileName);

                this.doTransform(logEntry, sourceFile, transformRequest.getSourceMediaType(), targetFile,
                        transformRequest.getTargetMediaType(), transformRequest.getTimeout(),
                        transformRequest.getTransformRequestOptions());

                final String targetReference = this.sharedFileAccessor.saveFile(targetFile, transformRequest.getTargetMediaType());

                transformReply.setTargetReference(targetReference);
                transformReply.setStatus(HttpStatus.CREATED_201);
            }
            catch (final SharedFileAccessException shex)
            {
                transformReply.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                final String priamryMessage = sourceFile == null ? "Failed to retrieve source file" : "Failed to store targetFile";
                transformReply.setErrorDetails(messageWithCause(priamryMessage, shex));
            }
            catch (final StatusException stex)
            {
                transformReply.setStatus(stex.getStatus());
                transformReply.setErrorDetails(messageWithCause("Failed to perform transformation", stex));
            }
            catch (final Exception ex)
            {
                transformReply.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                transformReply.setErrorDetails(messageWithCause("Unexpected error during transformation request processing", ex));
            }
            finally
            {
                this.context.discardTempFile(sourceFile);
                this.context.discardTempFile(targetFile);
            }
        }

        logEntry.setStatus(transformReply.getStatus(), transformReply.getErrorDetails());
        LOGGER.debug("Sending {} response for JSON transformation - full reply: {}",
                transformReply.getStatus() == HttpStatus.CREATED_201 ? "success" : "error", transformReply);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(transformReply.getStatus());
        response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8.asString());
        try (final OutputStream os = response.getOutputStream())
        {
            this.jsonMapper.writeValue(os, transformReply);
        }

        this.transformationLog.closeCurrentEntry();
    }

    private String getEffectiveSourceFileName(final Part filePart)
    {
        String sourceFileName = filePart.getSubmittedFileName();
        if (sourceFileName == null || sourceFileName.isBlank())
        {
            throw new StatusException(HttpStatus.BAD_REQUEST_400, "Source file name was not supplied");
        }
        final int lastSlashIdx = sourceFileName.indexOf('/');
        sourceFileName = lastSlashIdx != -1 ? sourceFileName.substring(lastSlashIdx + 1) : sourceFileName;
        return sourceFileName;
    }

    private String getEffectiveTargetFileName(final String sourceFileName, final String targetExtension)
    {
        final int lastDotIdx = sourceFileName.indexOf('.');
        final String targetFileName = (lastDotIdx != -1 ? sourceFileName.substring(0, lastDotIdx) : sourceFileName) + '.' + targetExtension;
        return targetFileName;
    }

    private Path prepareSourceFile(final Part filePart, final String sourceFileName) throws IOException
    {
        Path sourceFile;
        sourceFile = this.context.createTempFile("source_", "_" + sourceFileName);
        try (InputStream is = filePart.getInputStream())
        {
            Files.copy(is, sourceFile, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ioex)
        {
            final long usableSpace = sourceFile.toFile().getUsableSpace();
            final long requiredSpace = filePart.getSize();
            if (usableSpace <= requiredSpace)
            {
                LOGGER.error("Not enough space available to store {} bytes in {}", requiredSpace, sourceFile);
                throw new StatusException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the source file", ioex);
            }
            throw ioex;
        }
        return sourceFile;
    }

    private Path prepareSourceFile(final String sourceReference) throws IOException
    {
        final Path retrievedFile;

        try
        {
            retrievedFile = this.sharedFileAccessor.retrieveAsTemporyFile(sourceReference);
        }
        catch (final SharedFileAccessException shex)
        {
            if (shex.getStatus() == HttpStatus.INSUFFICIENT_STORAGE_507)
            {
                throw new StatusException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the source file", shex);
            }
            else if (shex.getStatus() == HttpStatus.NOT_FOUND_404)
            {
                throw new StatusException(HttpStatus.BAD_REQUEST_400, "Referenced source file does not exist in shared store", shex);
            }
            throw shex;
        }

        final String fileName = retrievedFile.getFileName().toString();
        final int lastDotIdx = fileName.lastIndexOf('.');
        final String extension = lastDotIdx != -1 ? fileName.substring(lastDotIdx + 1) : null;

        final Path sourceFile = this.context.createTempFile("source_", "." + extension);
        try
        {
            Files.move(retrievedFile, sourceFile);
        }
        catch (final IOException ioex)
        {
            final long usableSpace = sourceFile.toFile().getUsableSpace();
            final long requiredSpace = Files.size(retrievedFile);
            if (usableSpace <= requiredSpace)
            {
                LOGGER.error("Not enough space available to store {} bytes in {}", requiredSpace, sourceFile);
                throw new StatusException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the source file", ioex);
            }
            throw ioex;
        }
        return sourceFile;
    }

    private void doTransform(final MutableEntry logEntry, final Path sourceFile, final String sourceMimetype, final Path targetFile,
            final String targetMimetype, final Long timeout, final Map<String, String> transformerOptions) throws IOException
    {
        final long sourceSize = Files.size(sourceFile);

        final Optional<String> transformer = this.registry.findTransformer(sourceMimetype, sourceSize, targetMimetype, transformerOptions);
        if (transformer.isEmpty())
        {
            throw new StatusException(HttpStatus.BAD_REQUEST_400, "No transformers are able to handle the request");
        }

        final String transformerName = transformer.get();
        logEntry.recordSelectedTransformer(transformerName);

        final Map<String, String> effectiveTransformerOptions = new HashMap<>();
        effectiveTransformerOptions.putAll(this.registry.getDefaultOptions(transformerName));
        transformerOptions.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .forEach(e -> effectiveTransformerOptions.put(e.getKey(), e.getValue()));

        final Transformer transformerInstance = this.registry.getTransformer(transformerName);

        // this may be overridden within a transformer, e.g. if it has to do further request handling
        logEntry.markStartOfTransformation();

        try
        {
            transformerInstance.transform(sourceFile, sourceMimetype, targetFile, targetMimetype,
                    timeout != null ? timeout.longValue() : this.defaultTransformTimeout, effectiveTransformerOptions);
        }
        finally
        {
            if (logEntry.getTransformationDuration() == -1)
            {
                logEntry.markEndOfTransformation();
            }
        }
    }

    private Part getPart(final HttpServletRequest request, final String partName, final boolean mandatory)
            throws IOException, ServletException
    {
        final Part part = request.getPart(partName);

        if (part == null && mandatory)
        {
            LOGGER.debug("Rejecting transformation request with missing part {}", partName);
            throw new StatusException(HttpStatus.BAD_REQUEST_400, partName + " is a required request part");
        }
        return part;
    }

    private String getParameter(final HttpServletRequest request, final String name, final boolean mandatory)
    {
        final String parameterValue = request.getParameter(name);

        if ((parameterValue == null || parameterValue.isBlank()) && mandatory)
        {
            LOGGER.debug("Rejecting transformation request with missing parameter {}", name);
            throw new StatusException(HttpStatus.BAD_REQUEST_400, name + " is a required request parameter");
        }

        return parameterValue;
    }

    private void validateTransformRequest(final TransformRequest request, final TransformReply reply)
    {
        // cannot use TransformRequestValidator as that relies on Spring framework validation which we do not include
        final List<String> errorDetails = new ArrayList<>();

        // do not validate requestId like Alfresco does - it is not used for anything other than mapping to reply
        if (request.getSourceReference() == null || request.getSourceReference().isBlank())
        {
            errorDetails.add("Source reference may not be null or blank");
        }
        if (request.getSourceSize() == null || request.getSourceSize() <= 0)
        {
            errorDetails.add("Source size may not be null or non-positive");
        }
        if (request.getSourceMediaType() == null || request.getSourceMediaType().isBlank())
        {
            errorDetails.add("Source media type may not be null or blank");
        }
        if (request.getTargetMediaType() == null || request.getTargetMediaType().isBlank())
        {
            errorDetails.add("Target media type may not be null or blank");
        }
        if (request.getTargetExtension() == null || request.getTargetExtension().isBlank())
        {
            errorDetails.add("Target extension may not be null or blank");
        }

        // do not validate clientData like Alfresco does - it is not used for anything other than mapping to reply
        // do not validate schema like Alfresco does - it is not used for anything other than mapping to reply

        if (request.getTimeout() != null && request.getTimeout() <= 0)
        {
            errorDetails.add("Timeout cannot be 0 or less if specified");
        }

        if (!errorDetails.isEmpty())
        {
            reply.setStatus(HttpStatus.BAD_REQUEST_400);
            reply.setErrorDetails(errorDetails.stream().collect(Collectors.joining(", ")));
        }
    }

    private Map<String, String> getTransformationRequestParameters(final HttpServletRequest request)
    {
        final Map<String, String> parameters = new HashMap<>();
        final Enumeration<String> parameterNames = request.getParameterNames();
        parameterNames.asIterator().forEachRemaining(name -> {
            if (!RequestConstants.NON_TRANSFORMATION_PARAMETER_NAMES.contains(name))
            {
                final String parameterValue = request.getParameter(name);

                if (parameterValue != null && !parameterValue.isBlank())
                {
                    parameters.put(name, parameterValue);
                }
            }
        });

        return parameters;
    }

    // same as in Alfresco transformer - hard/impossible to differ if response should look roughly the same
    private static String messageWithCause(final String prefix, final Throwable e)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" - ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());

        Throwable cause = e.getCause();
        while (cause != null)
        {
            sb.append(", cause ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            cause = cause.getCause();
        }

        return sb.toString();
    }
}
