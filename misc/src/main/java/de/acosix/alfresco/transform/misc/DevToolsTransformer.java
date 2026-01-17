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
package de.acosix.alfresco.transform.misc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.RequestConstants;
import de.acosix.alfresco.transform.base.TransformationException;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.impl.AbstractTransformer;
import de.acosix.alfresco.transform.misc.cdt.AttachToTargetParameters;
import de.acosix.alfresco.transform.misc.cdt.AttachToTargetResponse;
import de.acosix.alfresco.transform.misc.cdt.CaptureScreenshotParameters;
import de.acosix.alfresco.transform.misc.cdt.CaptureScreenshotParameters.Format;
import de.acosix.alfresco.transform.misc.cdt.CaptureScreenshotResponse;
import de.acosix.alfresco.transform.misc.cdt.CloseTargetParameters;
import de.acosix.alfresco.transform.misc.cdt.CreateTargetParameters;
import de.acosix.alfresco.transform.misc.cdt.CreateTargetResponse;
import de.acosix.alfresco.transform.misc.cdt.DetachFromTargetParameters;
import de.acosix.alfresco.transform.misc.cdt.DevToolsException;
import de.acosix.alfresco.transform.misc.cdt.DevToolsWebSocketClient;
import de.acosix.alfresco.transform.misc.cdt.IOCloseParameters;
import de.acosix.alfresco.transform.misc.cdt.IOReadParameters;
import de.acosix.alfresco.transform.misc.cdt.IOReadResponse;
import de.acosix.alfresco.transform.misc.cdt.PrintToPdfParameters;
import de.acosix.alfresco.transform.misc.cdt.PrintToPdfParameters.TransferMode;
import de.acosix.alfresco.transform.misc.cdt.PrintToPdfResponse;
import de.acosix.alfresco.transform.misc.cdt.Viewport;

/**
 * @author Axel Faust
 */
public class DevToolsTransformer extends AbstractTransformer
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DevToolsTransformer.class);

    private static final String APPLICATION_XHTML = "application/xhtml+xml";

    private static final String APPLICATION_PDF = "application/pdf";

    private static final String IMAGE_JPEG = "image/jpeg";

    private static final String IMAGE_PNG = "image/png";

    private static final String IMAGE_SVG = "image/svg+xml";

    private static final String TEXT_HTML = "text/html";

    private static final Set<String> VALID_SOURCE_TYPES = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(TEXT_HTML, APPLICATION_XHTML, IMAGE_SVG)));

    private static final Set<String> VALID_TARGET_TYPES = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(APPLICATION_PDF, IMAGE_JPEG, IMAGE_PNG)));

    private final DevToolsWebSocketClient client;

    public DevToolsTransformer(final Context context, final TransformationLog transformationLog, final DevToolsWebSocketClient client)
    {
        super("DevTools", context, transformationLog);

        Objects.requireNonNull(client, "A web DevTools Protocol web socket client is required");
        this.client = client;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void doTransform(final MutableEntry logEntry, final Path sourceFile, final String sourceMimetype, final Path targetFile,
            final String targetMimetype, final long timeout, final Map<String, String> options)
    {
        if (!VALID_SOURCE_TYPES.contains(sourceMimetype) || !VALID_TARGET_TYPES.contains(targetMimetype))
        {
            throw new TransformationException(400, "Transformer only supports transformation from (X)HTML or SVG to PDF, JPEG or PNG");
        }

        LOGGER.debug("Transforming {} from {} to {}", sourceFile, sourceMimetype, targetMimetype);

        final long start = System.currentTimeMillis();

        final String dataUrl;
        try
        {
            dataUrl = this.buildUrl(sourceFile, sourceMimetype, options.get(RequestConstants.SOURCE_ENCODING));
        }
        catch (final IOException ioex)
        {
            throw new TransformationException(500, "Failed to prepare file for transformation", ioex);
        }

        long remainingTimeout = timeout - (System.currentTimeMillis() - start);
        if (remainingTimeout <= 0)
        {
            throw new TransformationException(408, "Exceeded timed out preparing file for transformation");
        }

        synchronized (this.client)
        {
            if (!this.client.isConnected())
            {
                this.client.reconnect();
            }
        }

        try
        {
            final String targetId = this.createTarget(dataUrl, targetMimetype, options);
            try
            {
                final String sessionId = this.createSession(targetId);
                try
                {
                    this.waitForPageLoad(sessionId, remainingTimeout);
                    remainingTimeout = timeout - (System.currentTimeMillis() - start);

                    if (APPLICATION_PDF.equals(targetMimetype))
                    {
                        this.createPdf(sessionId, targetFile, options);
                    }
                    else
                    {
                        this.createScreenshot(sessionId, targetFile, targetMimetype, options);
                    }
                }
                finally
                {
                    this.closeSession(sessionId);
                }
            }
            finally
            {
                this.closeTarget(targetId);
            }
        }
        catch (final DevToolsException dtex)
        {
            throw new TransformationException(500, "Transformation via DevTools Protocol failed", dtex);
        }
    }

    private String buildUrl(final Path sourceFile, final String sourceMimetype, final String sourceEncoding) throws IOException
    {
        final Charset cs = sourceEncoding != null ? Charset.forName(sourceEncoding) : StandardCharsets.UTF_8;
        final List<String> lines = Files.readAllLines(sourceFile, cs);

        final StringBuilder urlBuilder = new StringBuilder(20480);
        urlBuilder.append("data:").append(sourceMimetype).append(";charset=UTF-8,");

        for (final String line : lines)
        {
            this.writeDataUrlEncodedHtmlLine(urlBuilder, line);
        }

        LOGGER.trace("Prepared data URL {} from source file {} (mimetype {}, encoding {})", urlBuilder, sourceFile, sourceMimetype,
                sourceEncoding);

        return urlBuilder.toString();
    }

    private void writeDataUrlEncodedHtmlLine(final StringBuilder urlBuilder, final String line)
    {
        // whitespace, newline, reserved characters (RFC 3986) must be percent encoded
        for (int i = 0, max = line.length(); i < max; i++)
        {
            final char ch = line.charAt(i);
            switch (ch)
            {
                case '\t':
                    urlBuilder.append("%09");
                    break;
                case ' ':
                    urlBuilder.append("%20");
                    break;
                // gen-delims (RFC 3986)
                case ':':
                    urlBuilder.append("%3A");
                    break;
                case '/':
                    urlBuilder.append("%2F");
                    break;
                case '?':
                    urlBuilder.append("%3F");
                    break;
                case '#':
                    urlBuilder.append("%23");
                    break;
                case '[':
                    urlBuilder.append("%5B");
                    break;
                case ']':
                    urlBuilder.append("%5D");
                    break;
                case '@':
                    urlBuilder.append("%40");
                    break;
                // sub-delims (RFC 3986)
                case '!':
                    urlBuilder.append("%21");
                    break;
                case '$':
                    urlBuilder.append("%24");
                    break;
                case '&':
                    urlBuilder.append("%26");
                    break;
                case '\'':
                    urlBuilder.append("%27");
                    break;
                case '(':
                    urlBuilder.append("%28");
                    break;
                case ')':
                    urlBuilder.append("%29");
                    break;
                case '*':
                    urlBuilder.append("%2A");
                    break;
                case '+':
                    urlBuilder.append("%2B");
                    break;
                case ',':
                    urlBuilder.append("%2C");
                    break;
                case ';':
                    urlBuilder.append("%3B");
                    break;
                case '=':
                    urlBuilder.append("%3D");
                    break;
                // naturally, % must be encoded as well
                case '%':
                    urlBuilder.append("%25");
                    break;
                default:
                    urlBuilder.append(ch);
            }
        }
        urlBuilder.append("%0D");
    }

    private String createTarget(final String dataUrl, final String targetMimetype, final Map<String, String> options)
    {
        final CreateTargetParameters createTargetRq = new CreateTargetParameters();
        createTargetRq.setUrl(dataUrl);

        if (IMAGE_JPEG.equals(targetMimetype) || IMAGE_PNG.equals(targetMimetype))
        {
            final String viewportWidth = options.get("screenshotViewportWidth");
            final String viewportHeight = options.get("screenshotViewportHeight");

            try
            {
                final int width = Integer.parseInt(viewportWidth);
                final int height = Integer.parseInt(viewportHeight);

                createTargetRq.setHeight(width);
                createTargetRq.setHeight(height);
            }
            catch (final NumberFormatException nfe)
            {
                throw new TransformationException(400, "Viewport definition parameters must be valid numbers");
            }
        }

        final CreateTargetResponse createTargetRs = this.client.send(createTargetRq, CreateTargetResponse::new);
        final String targetId = createTargetRs.getTargetId();

        LOGGER.debug("Created new target {} for transformation", targetId);

        return targetId;
    }

    private String createSession(final String targetId)
    {

        final AttachToTargetParameters attachTargetRq = new AttachToTargetParameters();
        attachTargetRq.setTargetId(targetId);
        attachTargetRq.setFlatten(Boolean.TRUE);
        final AttachToTargetResponse attachTargetRs = this.client.send(attachTargetRq, AttachToTargetResponse::new);
        final String sessionId = attachTargetRs.getSessionId();

        LOGGER.debug("Created new session {} for transformation via target {}", sessionId, targetId);

        return sessionId;
    }

    private void waitForPageLoad(final String sessionId, final long timeout)
    {
        this.client.send("Page", "enable", sessionId);

        final CountDownLatch pageLoadedLatch = new CountDownLatch(1);
        this.client.registerListener(sessionId, (domain, command, eventSessionId, payload) -> {
            boolean keepListening = true;
            if ("Page".equals(domain) && "loadEventFired".equals(command))
            {
                pageLoadedLatch.countDown();
                keepListening = false;
            }
            return keepListening;
        });

        try
        {
            if (!pageLoadedLatch.await(timeout, TimeUnit.MILLISECONDS))
            {
                throw new TransformationException(408, "Timed out waiting for page to load");
            }
        }
        catch (final InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            throw new TransformationException(500, "Interrupted while waiting for page to load", iex);
        }
    }

    private void createPdf(final String sessionId, final Path targetFile, final Map<String, String> options)
    {
        final PrintToPdfParameters printToPdfRq = new PrintToPdfParameters();
        try
        {
            this.processPDFOptions(options, printToPdfRq);
        }
        catch (final IllegalArgumentException ex)
        {
            throw new TransformationException(400, ex.getMessage());
        }
        printToPdfRq.setTransferMode(TransferMode.ReturnAsStream);
        final PrintToPdfResponse printToPdfRs = this.client.send(sessionId, printToPdfRq, PrintToPdfResponse::new);

        final IOReadParameters ioReadRq = new IOReadParameters();
        ioReadRq.setHandle(printToPdfRs.getStream());
        ioReadRq.setSize(10240);

        final Decoder base64Decoder = Base64.getDecoder();

        try (OutputStream os = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            boolean eof = false;

            while (!eof)
            {
                final IOReadResponse ioReadRs = this.client.send(sessionId, ioReadRq, IOReadResponse::new);

                final String data = ioReadRs.getData();
                final boolean isBase64 = Boolean.TRUE.equals(ioReadRs.getBase64Encoded());

                if (isBase64)
                {
                    final byte[] dataBytes = base64Decoder.decode(data);
                    os.write(dataBytes);
                }
                else
                {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                }

                eof = Boolean.TRUE.equals(ioReadRs.getEof());
            }
        }
        catch (final IOException ioex)
        {
            throw new TransformationException(500, "Failed to retrieve result PDF", ioex);
        }
        finally
        {
            try
            {
                final IOCloseParameters ioCloseRq = new IOCloseParameters();
                ioCloseRq.setHandle(printToPdfRs.getStream());
                this.client.send(sessionId, ioCloseRq);
            }
            catch (final DevToolsException dte)
            {
                LOGGER.warn("Failed to close IO stream in session {}", sessionId, dte);
            }
        }
    }

    private void createScreenshot(final String sessionId, final Path targetFile, final String targetMimetype,
            final Map<String, String> options)
    {
        final CaptureScreenshotParameters screenshotRq = new CaptureScreenshotParameters();
        try
        {
            this.processScreenshotOptions(options, screenshotRq);
        }
        catch (final IllegalArgumentException ex)
        {
            throw new TransformationException(400, ex.getMessage());
        }
        if (IMAGE_JPEG.equals(targetMimetype))
        {
            screenshotRq.setFormat(Format.jpeg);
        }
        final CaptureScreenshotResponse screenshotRs = this.client.send(sessionId, screenshotRq, CaptureScreenshotResponse::new);

        final Decoder base64Decoder = Base64.getDecoder();

        try (OutputStream os = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            final String data = screenshotRs.getData();
            final byte[] dataBytes = base64Decoder.decode(data);
            os.write(dataBytes);
        }
        catch (final IOException ioex)
        {
            throw new TransformationException(500, "Failed to write result image", ioex);
        }
    }

    private void closeSession(final String sessionId)
    {
        try
        {
            final DetachFromTargetParameters detachTargetRq = new DetachFromTargetParameters();
            detachTargetRq.setSessionId(sessionId);
            this.client.send(detachTargetRq);
        }
        catch (final DevToolsException dte)
        {
            LOGGER.warn("Error closing session {}", sessionId, dte);
        }
        finally
        {
            this.client.discardSessionData(sessionId);
        }
    }

    private void closeTarget(final String targetId)
    {
        try
        {
            final CloseTargetParameters closeTargetRq = new CloseTargetParameters();
            closeTargetRq.setTargetId(targetId);
            this.client.send(closeTargetRq);
        }
        catch (final DevToolsException dte)
        {
            LOGGER.warn("Error closing target {}", targetId, dte);
        }
    }

    private void processPDFOptions(final Map<String, String> options, final PrintToPdfParameters params)
    {
        params.setLandscape(Boolean.parseBoolean(options.getOrDefault("pdfLandscape", "false")));
        params.setPrintBackground(Boolean.parseBoolean(options.getOrDefault("pdfPrintBackground", "true")));
        params.setPreferCSSPageSize(Boolean.parseBoolean(options.getOrDefault("pdfPreferCSSPageSize", "false")));

        try
        {
            this.handleFloatingPointParam("pdfMarginLeft", options, true, params::setMarginLeft);
            this.handleFloatingPointParam("pdfMarginRight", options, true, params::setMarginRight);
            this.handleFloatingPointParam("pdfMarginTop", options, true, params::setMarginTop);
            this.handleFloatingPointParam("pdfMarginBottom", options, true, params::setMarginBottom);

            this.handleFloatingPointParam("pdfPageWidth", options, true, params::setPaperWidth);
            this.handleFloatingPointParam("pdfPageHeight", options, true, params::setPaperHeight);
        }
        catch (final NumberFormatException nfe)
        {
            throw new TransformationException(400, "Paper dimensions and margins must be valid numbers");
        }

        final String pageRanges = options.get("pdfPageRanges");
        if (pageRanges != null && !pageRanges.isBlank())
        {
            params.setPageRanges(pageRanges);
            params.setIgnoreInvalidPageRanges(Boolean.parseBoolean(options.getOrDefault("pdfIgnoreInvalidPageRanges", "false")));
        }

        final String headerTemplate = options.get("pdfHeaderTemplate");
        final String footerTemplate = options.get("pdfFooterTemplate");

        if (headerTemplate != null && !headerTemplate.isBlank())
        {
            params.setHeaderTemplate(footerTemplate);
            params.setDisplayHeaderFooter(Boolean.TRUE);
        }

        if (footerTemplate != null && !footerTemplate.isBlank())
        {
            params.setFooterTemplate(footerTemplate);
            params.setDisplayHeaderFooter(Boolean.TRUE);
        }

        LOGGER.debug("Mapped PDF request parameters {} from options {}", params, options);
    }

    private void processScreenshotOptions(final Map<String, String> options, final CaptureScreenshotParameters params)
    {
        params.setCaptureBeyondViewport(true);
        try
        {
            this.handleFloatingPointParam("screenshotCompressionQuality", options, false, params::setQuality);
        }
        catch (final NumberFormatException nfe)
        {
            throw new TransformationException(400, "JPEG quality must be a valid number");
        }

        if (options.containsKey("screenshotViewportX") || options.containsKey("screenshotViewportY")
                || options.containsKey("screenshotViewportWidth") || options.containsKey("screenshotViewportHeight")
                || options.containsKey("screenshotViewportScale"))
        {
            final String viewportX = options.getOrDefault("screenshotViewportX", "0");
            final String viewportY = options.getOrDefault("screenshotViewportY", "0");
            final String viewportWidth = options.get("screenshotViewportWidth");
            final String viewportHeight = options.get("screenshotViewportHeight");
            final String viewportScale = options.getOrDefault("screenshotViewportScale", "1");

            if (viewportWidth == null || viewportHeight == null)
            {
                throw new TransformationException(400, "Both viewport dimensions (width / height) must be specified");
            }

            try
            {
                final int x = Integer.parseInt(viewportX);
                final int y = Integer.parseInt(viewportY);
                final int width = Integer.parseInt(viewportWidth);
                final int height = Integer.parseInt(viewportHeight);
                final double scale = Double.parseDouble(viewportScale);

                final Viewport viewport = new Viewport(x, y, width, height, scale);
                params.setClip(viewport);
            }
            catch (final NumberFormatException nfe)
            {
                throw new TransformationException(400, "Viewport definition parameters must be valid numbers");
            }
        }
    }

    private void handleFloatingPointParam(final String name, final Map<String, String> params, final boolean mmToIn,
            final Consumer<Double> valueConsumer)
    {
        final String value = params.get(name);
        if (value != null && !value.isBlank())
        {
            double valueD = Double.parseDouble(value);
            if (mmToIn)
            {
                valueD /= 25.4;
            }
            valueConsumer.accept(valueD);
        }
    }
}
