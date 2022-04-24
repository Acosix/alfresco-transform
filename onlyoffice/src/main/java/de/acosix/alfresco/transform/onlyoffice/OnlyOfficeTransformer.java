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
package de.acosix.alfresco.transform.onlyoffice;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.StringRequestContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.RequestConstants;
import de.acosix.alfresco.transform.base.SharedFileAccessException;
import de.acosix.alfresco.transform.base.SharedFileAccessor;
import de.acosix.alfresco.transform.base.StatusException;
import de.acosix.alfresco.transform.base.TransformationException;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.impl.AbstractTransformer;

/**
 * @author Axel Faust
 */
public class OnlyOfficeTransformer extends AbstractTransformer
{

    private static final int UTF_8_CODE_PAGE = 65001;

    private static final String CODE_PAGE = "codePage";

    private static final String TEXT_CSV = "text/csv";

    private static final String TEXT_TAB_SEPARATED_VALUES = "text/tab-separated-values";

    private static final Logger LOGGER = LoggerFactory.getLogger(OnlyOfficeTransformer.class);

    private static final Collection<String> SPREADSHEET_MIMETYPES = Collections.unmodifiableList(Arrays.asList(TEXT_CSV,
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroenabled.12", "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.ms-excel.template.macroenabled.12", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.spreadsheet-template"));

    private static final Map<String, String> FILE_TYPE_MAPPINGS;
    static
    {
        final Map<String, String> fileTypeMappings = new HashMap<>();

        fileTypeMappings.put("text/plain", "txt");
        fileTypeMappings.put("text/html", "html");
        fileTypeMappings.put(TEXT_CSV, "csv");
        fileTypeMappings.put(TEXT_TAB_SEPARATED_VALUES, "csv");
        fileTypeMappings.put("text/richtext", "rtf");
        fileTypeMappings.put("image/bmp", "bmp");
        fileTypeMappings.put("image/x-windows-bmp", "bmp");
        fileTypeMappings.put("image/gif", "gif");
        fileTypeMappings.put("image/jpeg", "jpg");
        fileTypeMappings.put("image/png", "png");
        fileTypeMappings.put("application/epub+zip", "epub");
        fileTypeMappings.put("application/rtf", "rtf");
        fileTypeMappings.put("application/x-rtf", "rtf");
        fileTypeMappings.put("application/pdf", "pdf");
        fileTypeMappings.put("application/msword", "doc");
        fileTypeMappings.put("application/vnd.ms-excel", "xls");
        fileTypeMappings.put("application/vnd.ms-powerpoint", "ppt");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        fileTypeMappings.put("application/vnd.ms-word.document.macroenabled.12", "docm");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx");
        fileTypeMappings.put("application/vnd.ms-word.template.macroenabled.12", "dotm");
        fileTypeMappings.put("application/vnd.oasis.opendocument.text", "odt");
        fileTypeMappings.put("application/vnd.oasis.opendocument.text-template", "ott");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        fileTypeMappings.put("application/vnd.ms-excel.sheet.macroenabled.12", "xlsm");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx");
        fileTypeMappings.put("application/vnd.ms-excel.template.macroenabled.12", "xltm");
        fileTypeMappings.put("application/vnd.oasis.opendocument.spreadsheet", "ods");
        fileTypeMappings.put("application/vnd.oasis.opendocument.spreadsheet-template", "ots");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.presentationml.slide", "ppsx");
        fileTypeMappings.put("application/vnd.ms-powerpoint.presentation.macroenabled.12", "potm");
        fileTypeMappings.put("application/vnd.ms-powerpoint.slide.macroenabled.12", "ppsm");
        fileTypeMappings.put("application/vnd.openxmlformats-officedocument.presentationml.template", "potx");
        fileTypeMappings.put("application/vnd.ms-powerpoint.template.macroenabled.12", "potm");
        fileTypeMappings.put("application/vnd.oasis.opendocument.presentation", "odp");
        fileTypeMappings.put("application/vnd.oasis.opendocument.presentation-template", "otp");

        FILE_TYPE_MAPPINGS = Collections.unmodifiableMap(fileTypeMappings);
    }

    private static final Map<String, Integer> ENCODING_MAPPINGS;
    static
    {
        final Map<String, Integer> encodingMappings = new HashMap<>();

        // https://github.com/ONLYOFFICE/server/blob/win-v4.3.0.110/Common/sources/commondefines.js#L736
        encodingMappings.put("ibm-437", 437);
        encodingMappings.put("dos-720", 720);
        encodingMappings.put("ibm-737", 737);
        encodingMappings.put("ibm-775", 775);
        encodingMappings.put("ibm-850", 850);
        encodingMappings.put("ibm-852", 852);
        encodingMappings.put("ibm-855", 855);
        encodingMappings.put("ibm-857", 857);
        encodingMappings.put("ibm-858", 858);
        encodingMappings.put("ibm-860", 860);
        encodingMappings.put("ibm-861", 861);
        encodingMappings.put("dos-862", 862);
        encodingMappings.put("ibm-863", 863);
        encodingMappings.put("ibm-865", 865);
        encodingMappings.put("ibm-869", 869);
        encodingMappings.put("cp866", 866);
        encodingMappings.put("windows-874", 874);
        encodingMappings.put("shift_jis", 932);
        encodingMappings.put("gb2312", 936);
        encodingMappings.put("ks_c_5601-1987", 949);
        encodingMappings.put("big5", 950);
        encodingMappings.put("utf-16", 1200);
        encodingMappings.put("utf-16be", 1201);
        encodingMappings.put("windows-1250", 1250);
        encodingMappings.put("windows-1251", 1251);
        encodingMappings.put("windows-1252", 1252);
        encodingMappings.put("windows-1254", 1254);
        encodingMappings.put("windows-1255", 1255);
        encodingMappings.put("windows-1256", 1256);
        encodingMappings.put("windows-1257", 1257);
        encodingMappings.put("windows-1258", 1258);
        encodingMappings.put("x-mac-cyrillic", 10007);
        encodingMappings.put("utf-32", 12000);
        encodingMappings.put("utf-32be", 12001);
        encodingMappings.put("koi8-r", 20866);
        encodingMappings.put("koi8-u", 21866);
        encodingMappings.put("iso-8859-1", 28591);
        encodingMappings.put("iso-8859-2", 28592);
        encodingMappings.put("iso-8859-3", 28593);
        encodingMappings.put("iso-8859-4", 28594);
        encodingMappings.put("iso-8859-5", 28595);
        encodingMappings.put("iso-8859-6", 28596);
        encodingMappings.put("iso-8859-7", 28597);
        encodingMappings.put("iso-8859-8", 28598);
        encodingMappings.put("iso-8859-9", 28599);
        encodingMappings.put("iso-8859-13", 28603);
        encodingMappings.put("iso-8859-14", 28604);
        encodingMappings.put("iso-8859-15", 28605);
        encodingMappings.put("euc-kr", 51949);
        encodingMappings.put("utf-7", 65000);
        encodingMappings.put("utf-8", UTF_8_CODE_PAGE);

        ENCODING_MAPPINGS = Collections.unmodifiableMap(encodingMappings);
    }

    private final SharedFileAccessor sharedFileAccessor;

    private final boolean publicSsl;

    private final String publicHost;

    private final int publicPort;

    private final String publicContext;

    private final String onlyOfficeConversionUrl;

    private final String tokenHeaderName;

    private final int defaultConversionTimeout;

    private final HttpClient httpClient;

    private TokenManager tokenManager;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    protected OnlyOfficeTransformer(final String name, final Context context, final TransformationLog transformationLog,
            final SharedFileAccessor sharedFileAccessor, final HttpClient onlyOfficeClient)
    {
        super(name, context, transformationLog);

        this.sharedFileAccessor = sharedFileAccessor;
        this.publicSsl = context.getBooleanProperty("onlyoffice.callback.publicSsl", false);
        this.publicHost = context.getStringProperty("onlyoffice.callback.publicHost");
        this.publicPort = context.getIntegerProperty("onlyoffice.callback.publicPort", this.publicSsl ? 8443 : 8080, 1, 65535);
        this.publicContext = context.getStringProperty("onlyoffice.callback.publicContext");

        this.onlyOfficeConversionUrl = context.getStringProperty("onlyoffice.conversionUrl");
        this.tokenHeaderName = context.getStringProperty("onlyoffice.tokenHeaderName");
        this.defaultConversionTimeout = context.getIntegerProperty("onlyoffice.defaultTimeout", 900000, 1, Integer.MAX_VALUE);

        this.httpClient = onlyOfficeClient;
    }

    /**
     * @param tokenManager
     *            the tokenManager to set
     */
    public void setTokenManager(final TokenManager tokenManager)
    {
        this.tokenManager = tokenManager;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void doTransform(final MutableEntry logEntry, final Path sourceFile, final String sourceMimetype, final Path targetFile,
            final String targetMimetype, final long timeout, final Map<String, String> options)
    {
        LOGGER.debug("Starting transformation of {} from {} to {} in {} with timeout of {} ms and options {}", sourceFile, sourceMimetype,
                targetMimetype, targetFile, timeout, options);

        // need to save the source file so it can be externally referenced
        // this also allows us to seamlessly switch from single instance to multi instance mode depending on shared file accessor
        final String fileReference = this.sharedFileAccessor.saveFile(sourceFile, sourceMimetype);
        LOGGER.debug("Saved source file in shared file store with reference {}", fileReference);
        try
        {
            final Request request = this.buildConversionRequest(sourceMimetype, targetMimetype, fileReference, options);

            final InputStreamResponseListener listener = new InputStreamResponseListener();

            logEntry.markStartOfTransformation();
            request.send(listener);
            final Response response = listener.get(timeout > 0 ? timeout : this.defaultConversionTimeout, TimeUnit.MILLISECONDS);
            logEntry.markEndOfTransformation();

            final String fileUrl = this.processResponseAndExtractResultFileUrl(sourceMimetype, targetMimetype, response, listener);
            this.downloadFile(fileUrl, targetFile);

            LOGGER.debug("Completed transformation from {} to {}", sourceMimetype, targetMimetype);
        }
        catch (final TimeoutException e)
        {
            LOGGER.info("Timed out waiting for response from OnlyOffice from {} to {}", sourceMimetype, targetMimetype);
            throw new TransformationException(HttpStatus.REQUEST_TIMEOUT_408, "Transformation did not complete within the allowed timeout");
        }
        catch (final InterruptedException e)
        {
            LOGGER.error("Thread was interrupted waiting for OnlyOffice conversion response");
            Thread.currentThread().interrupt();
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage(), e);
        }
        catch (final ExecutionException e)
        {
            LOGGER.error("Request to convert {} to {} via OnlyOffice failed", sourceMimetype, targetMimetype);
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage(), e);
        }
        finally
        {
            try
            {
                this.sharedFileAccessor.deleteFile(fileReference);
            }
            catch (final SharedFileAccessException e)
            {
                if (e.getStatus() != HttpStatus.NOT_FOUND_404)
                {
                    LOGGER.error("Failed to delete OnlyOffice callback file after transformation", e);
                }
            }
        }
    }

    private Request buildConversionRequest(final String sourceMimetype, final String targetMimetype, final String fileReference,
            final Map<String, String> options)
    {
        String payload = this.generateRegularRequestPayload(sourceMimetype, targetMimetype, fileReference, options);
        LOGGER.debug("Mapped transformation to base request payload {}", payload);

        final Request request = this.httpClient.newRequest(this.onlyOfficeConversionUrl).method(HttpMethod.POST);
        request.headers(h -> h.add(HttpHeader.ACCEPT, MimeTypes.Type.APPLICATION_JSON.asString()));

        if (this.tokenManager != null)
        {
            final String token = this.tokenManager.createToken(payload);
            payload = "{\"token\": \"" + token + "\"}";

            final String headerName = this.tokenHeaderName != null && !this.tokenHeaderName.isBlank() ? this.tokenHeaderName
                    : HttpHeader.AUTHORIZATION.asString();
            request.headers(h -> h.add(headerName, "Bearer " + token));

            LOGGER.debug("Generated token-based request");
        }

        request.body(new StringRequestContent(MimeTypes.Type.APPLICATION_JSON.asString(), payload));
        return request;
    }

    private String generateRegularRequestPayload(final String sourceMimetype, final String targetMimetype, final String fileReference,
            final Map<String, String> options)
    {
        final String sourceFileType = FILE_TYPE_MAPPINGS.get(sourceMimetype);
        String targetFileType = FILE_TYPE_MAPPINGS.get(targetMimetype);

        if (sourceFileType == null || targetFileType == null)
        {
            throw new TransformationException(HttpStatus.BAD_REQUEST_400, "Source / target mimetype is not supported");
        }
        if (targetFileType.equals("pdf"))
        {
            final String createPdfAOption = options.get("pdfa");
            final boolean createPDFA = Boolean.parseBoolean(createPdfAOption);
            if (createPDFA)
            {
                targetFileType = "pdfa";
            }
        }

        final StringBuilder callbackUrlBuilder = new StringBuilder(256);
        callbackUrlBuilder.append(this.publicSsl ? "https" : "http").append("://").append(this.publicHost.trim());
        if (this.publicPort != (this.publicSsl ? 443 : 80))
        {
            callbackUrlBuilder.append(':').append(this.publicPort);
        }
        callbackUrlBuilder.append('/')
                .append(this.publicContext != null && !this.publicContext.isBlank() ? this.publicContext.trim()
                        : SourceFileAccessHandler.DEFAULT_CONTEXT)
                .append('/').append(UrlEncoded.encodeString(fileReference, StandardCharsets.UTF_8));
        final String callbackUrl = callbackUrlBuilder.toString();

        final ByteArrayOutputStream os = new ByteArrayOutputStream(4096);

        try (JsonGenerator generator = this.jsonMapper.getFactory().createGenerator(os))
        {
            generator.writeStartObject();
            generator.writeStringField("url", callbackUrl);
            generator.writeStringField("key", fileReference);
            generator.writeStringField("filetype", sourceFileType);
            generator.writeStringField("outputtype", targetFileType);

            // supposedly set - not found any reasonable examples for this
            final String encoding = options.get(RequestConstants.SOURCE_ENCODING);
            if (encoding != null && !encoding.isBlank())
            {
                final Integer codePage = ENCODING_MAPPINGS.get(encoding.toLowerCase(Locale.ENGLISH).trim());
                generator.writeNumberField(CODE_PAGE, codePage != null ? codePage : UTF_8_CODE_PAGE);
            }
            else
            {
                generator.writeNumberField(CODE_PAGE, UTF_8_CODE_PAGE);
            }

            if (targetMimetype.startsWith("image/"))
            {
                this.processThumbnailOptions(generator, options);
            }

            if (sourceMimetype.equals(TEXT_CSV) || sourceMimetype.equals(TEXT_TAB_SEPARATED_VALUES))
            {
                this.processCSVOptions(generator, sourceMimetype, options);
            }

            if (targetMimetype.equals("application/pdf") && SPREADSHEET_MIMETYPES.contains(sourceMimetype))
            {
                this.processSpreadsheetOptions(generator, options);
            }

            generator.writeEndObject();
        }
        catch (final IOException ex)
        {
            LOGGER.error("Failed to generate request payload for OnlyOffice conversion");
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                    "Failed to process OnlyOffice transformer option into request JSON", ex);
        }

        return new String(os.toByteArray(), StandardCharsets.UTF_8);
    }

    private void processThumbnailOptions(final JsonGenerator generator, final Map<String, String> options) throws IOException
    {
        generator.writeObjectFieldStart("thumbnail");

        // keep vs stretch (default option 2 ignores height/width)
        final String maintainAspectRatioOption = options.get("maintainAspectRatio");
        final boolean maintainAspectRatio = maintainAspectRatioOption == null || !maintainAspectRatioOption.isBlank()
                || Boolean.parseBoolean(maintainAspectRatioOption);
        generator.writeNumberField("aspect", maintainAspectRatio ? 1 : 0);

        processIntegerOption(generator, "resizeWidth", "width", options, false, false);
        processIntegerOption(generator, "resizeHeight", "height", options, false, false);

        generator.writeEndObject();
    }

    private void processCSVOptions(final JsonGenerator generator, final String sourceMimetype, final Map<String, String> options)
            throws IOException
    {
        final String delimiterOption = options.get("csvDelimiter");
        final int delimiterValue;
        if (delimiterOption != null)
        {
            if (delimiterOption.length() == 0)
            {
                // source-specific default
                if (sourceMimetype.equals(TEXT_TAB_SEPARATED_VALUES))
                {
                    delimiterValue = 1;
                }
                else
                {
                    delimiterValue = 4;
                }
            }
            else if (delimiterOption.length() == 1)
            {
                switch (delimiterOption)
                {
                    case "\t":
                        delimiterValue = 1;
                        break;
                    case ";":
                        delimiterValue = 2;
                        break;
                    case ".":
                        delimiterValue = 3;
                        break;
                    case ",":
                        delimiterValue = 4;
                        break;
                    case " ":
                        delimiterValue = 5;
                        break;
                    default:
                        throw new TransformationException(HttpStatus.BAD_REQUEST_400, "CSV delimiter character is not supported");
                }
            }
            else
            {
                throw new TransformationException(HttpStatus.BAD_REQUEST_400, "CSV delimiter can only be one character");
            }
        }
        else
        {
            // source-specific default
            if (sourceMimetype.equals(TEXT_TAB_SEPARATED_VALUES))
            {
                delimiterValue = 1;
            }
            else
            {
                delimiterValue = 4;
            }
        }

        generator.writeNumberField("delimiter", delimiterValue);
    }

    private void processSpreadsheetOptions(final JsonGenerator generator, final Map<String, String> options) throws IOException
    {
        processStringOption(generator, "pdfLocale", "region", options);

        final long pdfOptionCount = options.keySet().stream()
                .filter(k -> k.startsWith("pdf") && !"pdfLocale".equals(k) && !"pdfa".equals(k))
                .filter(k -> options.get(k) != null && !options.get(k).isBlank()).count();
        if (pdfOptionCount > 0)
        {
            generator.writeObjectFieldStart("spreadsheetLayout");

            processIntegerOption(generator, "pdfFitToWidth", "fitToWidth", options, false, true);
            processIntegerOption(generator, "pdfFitToHeight", "fitToHeight", options, false, true);

            processIntegerOption(generator, "pdfScale", "scale", options, false, false);

            processBooleanOption(generator, "pdfIncludeGridLines", "gridLines", options);
            processBooleanOption(generator, "pdfIncludeHeadings", "headings", options);
            processBooleanOption(generator, "pdfIgnorePrintArea", "ignorePrintArea", options);

            processStringOption(generator, "pdfOrientation", "orientation", options);

            final long pdfMarginCount = options.keySet().stream().filter(k -> k.startsWith("pdfMargin"))
                    .filter(k -> options.get(k) != null && !options.get(k).isBlank()).count();
            if (pdfMarginCount > 0)
            {
                generator.writeObjectFieldStart("margins");
                processStringOption(generator, "pdfMarginBottom", "bottom", options);
                processStringOption(generator, "pdfMarginTop", "top", options);
                processStringOption(generator, "pdfMarginLeft", "left", options);
                processStringOption(generator, "pdfMarginRight", "right", options);
                generator.writeEndObject();
            }

            final long pdfPageDimCount = options.keySet().stream().filter(k -> k.startsWith("pdfPage"))
                    .filter(k -> options.get(k) != null && !options.get(k).isBlank()).count();
            if (pdfPageDimCount > 0)
            {
                generator.writeObjectFieldStart("pageSize");
                processStringOption(generator, "pdfPageWidth", "width", options);
                processStringOption(generator, "pdfPageHeight", "height", options);
                generator.writeEndObject();
            }

            generator.writeEndObject();
        }
    }

    private String processResponseAndExtractResultFileUrl(final String sourceMimetype, final String targetMimetype, final Response response,
            final InputStreamResponseListener listener)
    {
        if (response.getStatus() == HttpStatus.OK_200)
        {
            try (final InputStream is = listener.getInputStream())
            {
                final JsonNode rootNode = this.jsonMapper.readTree(is);
                final JsonNode errorNode = rootNode.get("error");

                if (errorNode != null)
                {
                    final int error = errorNode.asInt();
                    switch (error)
                    {
                        case -2:
                            LOGGER.info("Timed out in OnlyOffice transforming from {} to {}", sourceMimetype, targetMimetype);
                            throw new TransformationException(HttpStatus.REQUEST_TIMEOUT_408, "Transformation timed out in OnlyOffice");
                        case -4:
                            LOGGER.error("OnlyOffice failed to retrieve source file - check public callback and/or proxy configuration");
                            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                                    "Transformation in OnlyOffice failed due content callback error");
                        case -8:
                            LOGGER.error("Invalid token reported by OnlyOffice - check JWT configuration");
                            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                                    "Transformation in OnlyOffice failed due to invalid token");
                        default:
                            LOGGER.info("Transformation in OnlyOffice from {} to {} failed with error code {}", sourceMimetype,
                                    targetMimetype, error);
                            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                                    "Transformation failed in OnlyOffice with error code " + error);
                    }
                }

                final JsonNode endConvertNode = rootNode.get("endConvert");
                if (endConvertNode == null || !endConvertNode.asBoolean())
                {
                    LOGGER.error("OnlyOffice response to synchronous transformation request does not indicate conversion completed");
                    throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                            "Transformation in OnlyOffice did not complete as expected");
                }

                final JsonNode fileUrlNode = rootNode.get("fileUrl");
                if (fileUrlNode == null)
                {
                    LOGGER.error("OnlyOffice response to synchronous transformation request does not contain a URL for the result file");
                    throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                            "Transformation in OnlyOffice completed but response does not provide a URL for result");
                }
                final String fileUrl = fileUrlNode.asText();
                LOGGER.debug("Extracted result file URL {} from response to transformation request from {} to {}", fileUrl, sourceMimetype,
                        targetMimetype);
                return fileUrl;
            }
            catch (final IOException e)
            {
                LOGGER.error("Error processing OnlyOffice transformation response JSON");
                throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Error handling OnlyOffice transformation response",
                        e);
            }
        }
        else
        {
            discardResponse(listener);
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500,
                    "Transformation request to OnlyOffice failed with status " + response.getStatus() + " - " + response.getReason());
        }
    }

    private void downloadFile(final String fileUrl, final Path targetFile)
    {
        final InputStreamResponseListener listener = new InputStreamResponseListener();
        this.httpClient.newRequest(fileUrl).method(HttpMethod.GET).send(listener);
        try
        {
            final Response response = listener.get(2, TimeUnit.SECONDS);

            if (response.getStatus() == HttpStatus.OK_200)
            {
                this.processFileResponse(targetFile, listener, response);
            }
            else
            {
                discardResponse(listener);
                LOGGER.error("Failed to retrieve file from {} with HTTP status {} - {}", fileUrl, response.getStatus(),
                        response.getReason());
                throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve OnlyOffice result file");
            }
        }
        catch (final StatusException stex)
        {
            throw stex;
        }
        catch (final InterruptedException e)
        {
            LOGGER.error("Thread was interrupted waiting to download result file from {}", fileUrl);
            Thread.currentThread().interrupt();
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve OnlyOffice result file", e);
        }
        catch (final ExecutionException | TimeoutException | IOException e)
        {
            LOGGER.error("Failed to retrieve result file from {}", fileUrl);
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve OnlyOffice result file", e);
        }
    }

    private void processFileResponse(final Path targetFile, final InputStreamResponseListener listener, final Response response)
            throws IOException
    {
        final HttpFields headers = response.getHeaders();

        final String contentType = headers.get(HttpHeader.CONTENT_TYPE);
        final long size = headers.getLongField(HttpHeader.CONTENT_LENGTH);

        try (final InputStream is = listener.getInputStream())
        {
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ioex)
        {
            // Alfresco transformer apps just generically state insufficient storage at this point
            final long usableSpace = targetFile.toFile().getUsableSpace();
            if (usableSpace <= size)
            {
                LOGGER.error("Not enough space available to store {} bytes in {}", size, targetFile);
                throw new SharedFileAccessException(HttpStatus.INSUFFICIENT_STORAGE_507,
                        "Insufficient space to store the OnlyOffice result file", ioex);
            }
            throw ioex;
        }

        LOGGER.debug("Read OnlyOffice result file to {} with {} bytes and {} as content type", targetFile, size, contentType);
    }

    private static void processStringOption(final JsonGenerator generator, final String optionName, final String fieldName,
            final Map<String, String> options) throws IOException
    {
        final String optionValue = options.get(optionName);
        if (optionValue != null && !optionValue.isBlank())
        {
            generator.writeStringField(fieldName, optionValue);
        }
    }

    private static void processBooleanOption(final JsonGenerator generator, final String optionName, final String fieldName,
            final Map<String, String> options) throws IOException
    {
        final String optionValue = options.get(optionName);
        if (optionValue != null && !optionValue.isBlank())
        {
            final boolean value = Boolean.parseBoolean(optionValue);
            generator.writeBooleanField(fieldName, value);
        }
    }

    private static void processIntegerOption(final JsonGenerator generator, final String optionName, final String fieldName,
            final Map<String, String> options, final boolean allowNegative, final boolean allowZero) throws IOException
    {
        final String optionValue = options.get(optionName);
        if (optionValue != null && !optionValue.isBlank())
        {
            final String typeDescription;
            if (allowNegative)
            {
                if (allowZero)
                {
                    typeDescription = "integer";
                }
                else
                {
                    typeDescription = "non-zero integer";
                }
            }
            else
            {
                if (allowZero)
                {
                    typeDescription = "non-negative integer";
                }
                else
                {
                    typeDescription = "positive integer";
                }
            }

            if (!optionValue.matches(allowNegative ? "^-?\\d+$" : "^\\d+$"))
            {
                throw new TransformationException(HttpStatus.BAD_REQUEST_400, optionName + " must be a " + typeDescription);
            }
            final int value = Integer.parseInt(optionValue);
            if (!allowZero && value == 0)
            {
                throw new TransformationException(HttpStatus.BAD_REQUEST_400, optionName + " must be a " + typeDescription);
            }
            generator.writeNumberField(fieldName, value);
        }
    }

    private static void discardResponse(final InputStreamResponseListener listener)
    {
        try (final InputStream is = listener.getInputStream())
        {
            // NO-OP
        }
        catch (final IOException ignore)
        {
            // ignore - close input stream primarily as indicator to Jetty client components to discard any further received data
        }
    }
}
