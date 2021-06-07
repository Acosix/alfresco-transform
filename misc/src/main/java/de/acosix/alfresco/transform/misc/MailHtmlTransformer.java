/*
 * Copyright 2021 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.acosix.alfresco.transform.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformationException;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.TransformationLog.MutableEntry;
import de.acosix.alfresco.transform.base.impl.AbstractTransformer;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.SharedFileInputStream;

/**
 * @author Axel Faust
 */
public class MailHtmlTransformer extends AbstractTransformer
{

    private static final int BUFFER_SIZE = 8 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger(MailHtmlTransformer.class);

    private static final String MESSAGE_RFC_822 = "message/rfc822";

    private static final String TEXT_HTML = "text/html";

    private static final String TEXT_PLAIN = "text/plain";

    private static final String APPLICATION_XHTML = "application/xhtml+xml";

    public MailHtmlTransformer(final Context context, final TransformationLog transformationLog)
    {
        super("MailHtml", context, transformationLog);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void doTransform(final MutableEntry logEntry, final Path sourceFile, final String sourceMimetype, final Path targetFile,
            final String targetMimetype, final long timeout, final Map<String, String> options)
    {
        if (!MESSAGE_RFC_822.equals(sourceMimetype) && !(TEXT_HTML.equals(targetMimetype) || APPLICATION_XHTML.equals(targetMimetype)))
        {
            throw new TransformationException(400, "Only conversion from RFC 822 email format to HTML is supported");
        }

        try (SharedFileInputStream mis = new SharedFileInputStream(sourceFile.toFile()))
        {
            final MimeMessage mail = new MimeMessage(null, mis);
            final StringBuilder mailContent = this.resolvePrimaryMailContent(mail);
            this.injectMailHeader(mail, options, mailContent);

            final Map<String, MimeBodyPart> inlineParts = this.collectInlineParts(mail);

            try (OutputStreamWriter osw = new OutputStreamWriter(
                    Files.newOutputStream(targetFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                    StandardCharsets.UTF_8))
            {
                if (inlineParts.isEmpty())
                {
                    osw.write(mailContent.toString());
                }
                else
                {
                    this.writeHtmlWithInlineParts(osw, mailContent, inlineParts);
                }
            }
            catch (final IOException iex)
            {
                throw new TransformationException(500, "Error writing transformation result file", iex);
            }
        }
        catch (final IOException | MessagingException ex)
        {
            throw new TransformationException(500, "Error reading mail for transformation to HTML", ex);
        }
    }

    private StringBuilder resolvePrimaryMailContent(final MimeMessage mimeMessage)
    {
        try
        {
            final StringBuilder mailContent;

            final String contentType = mimeMessage.getContentType();
            if (TEXT_HTML.equals(contentType) || contentType.startsWith(TEXT_HTML + ";"))
            {
                final String content = String.valueOf(mimeMessage.getContent());
                mailContent = new StringBuilder(content);
            }
            else if (TEXT_PLAIN.equals(contentType) || contentType.startsWith(TEXT_PLAIN + ";"))
            {
                mailContent = new StringBuilder(BUFFER_SIZE);
                mailContent.append("<!DOCTYPE html><html><head><title>").append(Encode.forHtmlContent(mimeMessage.getSubject()))
                        .append("</title></head><body>");

                String content = String.valueOf(mimeMessage.getContent());
                content = Encode.forHtmlContent(content);
                content = content.replaceAll("\\n", "<br />");
                mailContent.append(content).append("</body></html>");
            }
            else
            {
                final Object content = mimeMessage.getContent();
                if (content instanceof Multipart)
                {
                    mailContent = this.resolvePrimaryMailContent(mimeMessage.getSubject(), (Multipart) content);
                }
                else
                {
                    throw new TransformationException(400, "Could not find text or HTML primary content in mail");
                }
            }

            return mailContent;
        }
        catch (IOException | MessagingException ex)
        {
            throw new TransformationException(500, "Failed to read primary content of mail", ex);
        }
    }

    private StringBuilder resolvePrimaryMailContent(final String subject, final Multipart mailPart) throws MessagingException, IOException
    {
        final StringBuilder mailContent;

        final List<Multipart> mpsToProcess = new LinkedList<>();
        mpsToProcess.add(mailPart);

        BodyPart htmlPart = null;
        BodyPart textPart = null;

        while (htmlPart == null && !mpsToProcess.isEmpty())
        {
            final Multipart mp = mpsToProcess.remove(0);

            final int count = mp.getCount();
            for (int i = 0; i < count; i++)
            {
                final BodyPart part = mp.getBodyPart(i);

                final String contentType = part.getContentType();
                if (TEXT_HTML.equals(contentType) || contentType.startsWith(TEXT_HTML + ";"))
                {
                    htmlPart = part;
                }
                else if (TEXT_PLAIN.equals(contentType) || contentType.startsWith(TEXT_PLAIN + ";"))
                {
                    textPart = part;
                }
                else
                {
                    final Object partContent = part.getContent();
                    if (partContent instanceof Multipart)
                    {
                        mpsToProcess.add((Multipart) partContent);
                    }
                }
            }
        }

        if (htmlPart != null)
        {
            final String partContent = String.valueOf(htmlPart.getContent());
            mailContent = new StringBuilder(partContent);
        }
        else if (textPart != null)
        {
            mailContent = new StringBuilder(BUFFER_SIZE);
            mailContent.append("<!DOCTYPE html><html><head><title>").append(Encode.forHtmlContent(subject)).append("</title></head><body>");

            String partContent = String.valueOf(textPart.getContent());
            partContent = Encode.forHtmlContent(partContent);
            partContent = partContent.replaceAll("\\n", "<br />");
            mailContent.append(partContent).append("</body></html>");
        }
        else
        {
            throw new TransformationException(400, "Could not find text or HTML primary content in mail");
        }
        return mailContent;
    }

    private void injectMailHeader(final MimeMessage mimeMessage, final Map<String, String> options, final StringBuilder mailHtml)
            throws MessagingException
    {
        final String htmlResource = this.resolveEffectiveHtmlResource(options);
        final String cssResource = this.context.getStringProperty("mailHtml.mailHeaderCss.resource");

        if (htmlResource == null)
        {
            throw new TransformationException(500,
                    "A HTML template resource for the mail header was not configured / could not be determined");
        }
        if (cssResource == null)
        {
            throw new TransformationException(500, "A CSS resource for the mail header was not configured");
        }

        final StringBuilder dynamicMailHeaderCss = new StringBuilder(1024);
        final StringBuilder mailHeader = new StringBuilder(this.loadTextResource(htmlResource));

        this.prepareMailHeader(mimeMessage, options, mailHeader, dynamicMailHeaderCss);

        final int headEnd = mailHtml.indexOf("</head>");
        // insert back-to-front so that we don't have to update / re-determine the offset
        mailHtml.insert(headEnd, "\n</style>");
        mailHtml.insert(headEnd, dynamicMailHeaderCss);
        mailHtml.insert(headEnd, '\n');
        mailHtml.insert(headEnd, this.loadTextResource(cssResource));
        mailHtml.insert(headEnd, "<style type=\"text/css\">\n");

        final int bodyStart = mailHtml.indexOf("<body", headEnd);
        final int bodyStartEnd = mailHtml.indexOf(">", bodyStart);
        mailHtml.insert(bodyStartEnd + 1, mailHeader);
    }

    private void prepareMailHeader(final MimeMessage mimeMessage, final Map<String, String> options, final StringBuilder mailHeader,
            final StringBuilder dynamicMailHeaderCss) throws MessagingException
    {
        final String defaultTz = this.context.getStringProperty("mailHtml.defaultTimezone", "UTC");
        final String tz = options.getOrDefault("timezone", defaultTz);
        final TimeZone tzO = TimeZone.getTimeZone(tz);

        final String defaultLocale = this.context.getStringProperty("mailHtml.defaultLocale", "en_GB");
        final String locale = options.getOrDefault("locale", defaultLocale);
        final Locale localeO = Locale.forLanguageTag(locale);

        final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG, localeO);
        df.setTimeZone(tzO);

        final Date sentDate = mimeMessage.getSentDate();
        final String sentDateS = df.format(sentDate);

        this.addFieldToHeader("from", this.addressesToContent(mimeMessage.getFrom()), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("date", Encode.forHtmlContent(sentDateS), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("subject", Encode.forHtmlContent(mimeMessage.getSubject()), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("to", this.addressesToContent(mimeMessage.getRecipients(RecipientType.TO)), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("cc", this.addressesToContent(mimeMessage.getRecipients(RecipientType.CC)), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("bcc", this.addressesToContent(mimeMessage.getRecipients(RecipientType.BCC)), mailHeader,
                dynamicMailHeaderCss);
        this.addFieldToHeader("replyTo", this.addressesToContent(mimeMessage.getReplyTo()), mailHeader, dynamicMailHeaderCss);
        this.addFieldToHeader("attachments", this.partsToAttachmentsContent(mimeMessage), mailHeader, dynamicMailHeaderCss);
    }

    private String resolveEffectiveHtmlResource(final Map<String, String> options)
    {
        final String defaultLocale = this.context.getStringProperty("mailHtml.defaultLocale", "en");
        final String locale = options.getOrDefault("locale", defaultLocale);

        final StringBuilder keyBuilder = new StringBuilder("mailHtml.mailHeaderHtml.localisedResource.");
        final int localeStartIdx = keyBuilder.length();
        keyBuilder.append(locale.toLowerCase(Locale.ENGLISH));

        String htmlResource = null;
        boolean canContinue = true;
        do
        {
            htmlResource = this.context.getStringProperty(keyBuilder.toString());

            final int lastUnderscore = keyBuilder.lastIndexOf("_");
            canContinue = htmlResource == null && lastUnderscore > localeStartIdx;
            if (canContinue)
            {
                keyBuilder.delete(lastUnderscore, keyBuilder.length());
            }
        }
        while (canContinue);

        if (htmlResource == null)
        {
            LOGGER.debug("Unable to find localised mail header HTML resource for locale {}", locale);
            htmlResource = this.context.getStringProperty("mailHtml.mailHeaderHtml.defaultResource");
        }
        else
        {
            LOGGER.debug("Found localised mail header HTML resource for locale key {} (request locale {})",
                    keyBuilder.substring(localeStartIdx), locale);
        }
        return htmlResource;
    }

    private void addFieldToHeader(final String field, final String fieldContent, final StringBuilder mailHeader,
            final StringBuilder dynamicMailHeaderCss)
    {
        if (fieldContent != null && !fieldContent.isBlank())
        {
            final String placeholderSpanPattern = "<span\\s+class=\"" + field + "\">";
            final Matcher matcher = Pattern.compile(placeholderSpanPattern).matcher(mailHeader);
            while (matcher.find())
            {
                final int spanContentStart = matcher.end();
                mailHeader.insert(spanContentStart, fieldContent);
            }
        }
        else
        {
            dynamicMailHeaderCss.append("body > .mailHeader .").append(field).append(" { display: none; }\n");
        }
    }

    private String addressesToContent(final Address[] addresses)
    {
        String result;
        if (addresses != null && addresses.length > 0)
        {
            final StringBuilder addressContent = new StringBuilder(addresses.length * 256);
            for (final Address address : addresses)
            {
                if (address instanceof InternetAddress)
                {
                    final String mailAddress = ((InternetAddress) address).getAddress();
                    final String personalName = ((InternetAddress) address).getPersonal();

                    addressContent.append("<a href=\"mailto:").append(Encode.forHtmlAttribute(mailAddress)).append("\">");
                    if (personalName != null)
                    {
                        addressContent.append(Encode.forHtmlContent(personalName));
                    }
                    else
                    {
                        addressContent.append(Encode.forHtmlContent(mailAddress));
                    }

                    addressContent.append("</a>");
                }
            }
            result = addressContent.toString();
        }
        else
        {
            result = "";
        }
        return result;
    }

    private String partsToAttachmentsContent(final MimeMessage mimeMessage)
    {
        String result;
        try
        {
            final List<Multipart> mpsToProcess = new LinkedList<>();
            final Object content = mimeMessage.getContent();
            if (content instanceof Multipart)
            {
                final Multipart mp = (Multipart) content;
                mpsToProcess.add(mp);
            }

            final StringBuilder attachmentsContentBuilder = new StringBuilder(BUFFER_SIZE);

            while (!mpsToProcess.isEmpty())
            {
                final Multipart mp = mpsToProcess.remove(0);
                final int partCount = mp.getCount();
                for (int i = 0; i < partCount; i++)
                {
                    final BodyPart part = mp.getBodyPart(i);
                    final Object partContent = part.getContent();
                    if (partContent instanceof Multipart)
                    {
                        mpsToProcess.add((Multipart) partContent);
                    }
                    else
                    {
                        final String disposition = part.getDisposition();
                        final String fileName = part.getFileName();
                        final int size = part.getSize();

                        if (Part.ATTACHMENT.equals(disposition))
                        {
                            attachmentsContentBuilder.append("<span>").append(Encode.forHtmlContent(fileName)).append(" (")
                                    .append(Encode.forHtmlContent(this.sizeToString(size))).append(")</span>");
                        }
                    }
                }
            }

            result = attachmentsContentBuilder.toString();
        }
        catch (final IOException | MessagingException ex)
        {
            throw new TransformationException(500, "Failed to process mail content for attachment list", ex);
        }

        return result;
    }

    private Map<String, MimeBodyPart> collectInlineParts(final MimeMessage mimeMessage)
    {
        final Map<String, MimeBodyPart> inlineParts = new HashMap<>();

        try
        {
            final List<Multipart> mpsToProcess = new LinkedList<>();
            final Object content = mimeMessage.getContent();
            if (content instanceof Multipart)
            {
                final Multipart mp = (Multipart) content;
                mpsToProcess.add(mp);
            }

            while (!mpsToProcess.isEmpty())
            {
                final Multipart mp = mpsToProcess.remove(0);
                final int partCount = mp.getCount();
                for (int i = 0; i < partCount; i++)
                {
                    final BodyPart part = mp.getBodyPart(i);
                    final Object partContent = part.getContent();
                    if (partContent instanceof Multipart)
                    {
                        mpsToProcess.add((Multipart) partContent);
                    }
                    else if (part instanceof MimeBodyPart)
                    {
                        final String disposition = part.getDisposition();
                        String contentId = ((MimeBodyPart) part).getContentID();

                        if (Part.INLINE.equals(disposition) && contentId != null && !contentId.isBlank())
                        {
                            if (contentId.startsWith("<") && contentId.endsWith(">"))
                            {
                                contentId = contentId.substring(1, contentId.length() - 1);
                            }
                            inlineParts.put(contentId, (MimeBodyPart) part);
                        }
                    }
                }
            }
        }
        catch (final IOException | MessagingException ex)
        {
            throw new TransformationException(500, "Failed to process mail content for inline content elements", ex);
        }

        return inlineParts;
    }

    private void writeHtmlWithInlineParts(final Writer writer, final StringBuilder mailContent, final Map<String, MimeBodyPart> inlineParts)
            throws IOException
    {
        int offset = 0;
        int nextCid = mailContent.indexOf("src=\"cid:", offset);

        while (nextCid != -1)
        {
            writer.write(mailContent.substring(offset, nextCid));

            final int cidEnd = mailContent.indexOf("\"", nextCid + 9);
            final String cid = mailContent.substring(nextCid + 9, cidEnd);

            if (inlineParts.containsKey(cid))
            {
                final MimeBodyPart bodyPart = inlineParts.get(cid);
                this.writeDataUrlForPart(writer, bodyPart);
                offset = cidEnd + 1;
            }
            else
            {
                writer.write(mailContent.substring(nextCid, cidEnd + 1));
                offset = cidEnd + 1;
            }
            nextCid = mailContent.indexOf("src=\"cid:", offset);
        }

        writer.write(mailContent.substring(offset));
    }

    private void writeDataUrlForPart(final Writer writer, final MimeBodyPart bodyPart)
    {
        try
        {
            String contentType = bodyPart.getContentType();
            final int contentTypeSepIdx = contentType.indexOf(';');
            if (contentTypeSepIdx != -1)
            {
                contentType = contentType.substring(0, contentTypeSepIdx);
            }

            writer.write("src=\"data:");
            writer.write(Encode.forHtmlAttribute(contentType));
            if (contentType.startsWith("text/"))
            {
                writer.write(";charset=UTF-8");
            }
            writer.write(";base64,");

            // can skip decoding + encoding if already in base64
            final String encoding = bodyPart.getEncoding();
            final byte[] buf = new byte[BUFFER_SIZE];
            int read = -1;
            if ("base64".equalsIgnoreCase(encoding))
            {
                try (final InputStream is = bodyPart.getRawInputStream())
                {
                    while ((read = is.read(buf)) != -1)
                    {
                        final String readS = new String(buf, 0, read, StandardCharsets.ISO_8859_1);
                        writer.write(readS);
                    }
                }
            }
            else
            {
                final Encoder base64 = Base64.getEncoder();
                try (final InputStream is = bodyPart.getInputStream())
                {
                    while ((read = is.read(buf)) != -1)
                    {
                        final byte[] realRead = new byte[read];
                        System.arraycopy(buf, 0, realRead, 0, read);
                        final byte[] encoded = base64.encode(realRead);
                        writer.write(new String(encoded, 0, encoded.length, StandardCharsets.ISO_8859_1));
                    }
                }
            }
            writer.write('"');
        }
        catch (final MessagingException | IOException ex)
        {
            throw new TransformationException(500, "Failed to inject inline attachments into HTML via data URL", ex);
        }
    }

    private String loadTextResource(final String resourceName)
    {
        String textResource = null;

        final Path path = Paths.get(resourceName);
        if (Files.isReadable(path) && Files.isRegularFile(path))
        {
            try
            {
                textResource = Files.readString(path, StandardCharsets.UTF_8);
            }
            catch (final IOException ioex)
            {
                LOGGER.warn("Failed to read from {} path", path, ioex);
            }
        }

        if (textResource == null)
        {
            final InputStream resource = this.getClass().getClassLoader().getResourceAsStream(resourceName);
            if (resource != null)
            {
                try (InputStreamReader isr = new InputStreamReader(resource, StandardCharsets.UTF_8))
                {
                    final StringBuilder sb = new StringBuilder(BUFFER_SIZE);
                    final char[] buf = new char[BUFFER_SIZE];
                    int read = -1;
                    while ((read = isr.read(buf)) != -1)
                    {
                        sb.append(buf, 0, read);
                    }
                    textResource = sb.toString();
                }
                catch (final IOException ioex)
                {
                    LOGGER.warn("Failed to read from classpath resource {}", resourceName, ioex);
                }
            }
        }

        if (textResource == null)
        {
            throw new TransformationException(500, "Failed to load server-side text resource for conversion to HTML");
        }
        return textResource;
    }

    private String sizeToString(final int size)
    {
        final BigDecimal magnitudeCheckBD = BigDecimal.valueOf(10 * 1024);
        final BigDecimal magnitudeDivisorBD = BigDecimal.valueOf(1024);
        BigDecimal sizeBD = BigDecimal.valueOf(size);
        int magnitudes = 0;

        while (sizeBD.compareTo(magnitudeCheckBD) >= 0)
        {
            sizeBD = sizeBD.divide(magnitudeDivisorBD, 2, RoundingMode.HALF_UP);
            magnitudes++;
        }

        String magnitudeSuffix;
        switch (magnitudes)
        {
            case 0:
                magnitudeSuffix = "B";
                break;
            case 1:
                magnitudeSuffix = "KiB";
                break;
            case 2:
                magnitudeSuffix = "MiB";
                break;
            case 3:
                magnitudeSuffix = "GiB";
                break;
            case 4:
                magnitudeSuffix = "TiB";
                break;
            default:
                LOGGER.warn("Excessively large attachment with allegedly {} bytes found", size);
                magnitudeSuffix = "B";
                sizeBD = BigDecimal.valueOf(size);
        }

        final String result = sizeBD.toPlainString() + " " + magnitudeSuffix;
        return result;
    }
}
