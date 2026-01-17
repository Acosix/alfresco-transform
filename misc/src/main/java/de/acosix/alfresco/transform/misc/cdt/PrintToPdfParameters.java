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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Encapsulates the parameters of a {@code Page.printToPDF} command.
 *
 * @author Axel Faust
 */
public class PrintToPdfParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private Boolean landscape;

    private Boolean displayHeaderFooter;

    private Boolean printBackground;

    private Double scale;

    private Double paperWidth;

    private Double paperHeight;

    private Double marginTop;

    private Double marginBottom;

    private Double marginLeft;

    private Double marginRight;

    private String pageRanges;

    private Boolean ignoreInvalidPageRanges;

    private String headerTemplate;

    private String footerTemplate;

    private Boolean preferCSSPageSize;

    private TransferMode transferMode;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain()
    {
        return "Page";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand()
    {
        return "printToPDF";
    }

    /**
     * @param landscape
     *     the landscape to set
     */
    public void setLandscape(final Boolean landscape)
    {
        this.landscape = landscape;
    }

    /**
     * @param displayHeaderFooter
     *     the displayHeaderFooter to set
     */
    public void setDisplayHeaderFooter(final Boolean displayHeaderFooter)
    {
        this.displayHeaderFooter = displayHeaderFooter;
    }

    /**
     * @param printBackground
     *     the printBackground to set
     */
    public void setPrintBackground(final Boolean printBackground)
    {
        this.printBackground = printBackground;
    }

    /**
     * @param scale
     *     the scale to set
     */
    public void setScale(final Double scale)
    {
        if (scale != null && scale <= 0)
        {
            throw new IllegalArgumentException("Page scale must be a positive value");
        }
        this.scale = scale;
    }

    /**
     * @param paperWidth
     *     the paperWidth to set
     */
    public void setPaperWidth(final Double paperWidth)
    {
        if (paperWidth != null && paperWidth <= 0)
        {
            throw new IllegalArgumentException("Paper dimensions must be positive values");
        }
        this.paperWidth = paperWidth;
    }

    /**
     * @param paperHeight
     *     the paperHeight to set
     */
    public void setPaperHeight(final Double paperHeight)
    {
        if (paperHeight != null && paperHeight <= 0)
        {
            throw new IllegalArgumentException("Paper dimensions must be positive values");
        }
        this.paperHeight = paperHeight;
    }

    /**
     * @param marginTop
     *     the marginTop to set
     */
    public void setMarginTop(final Double marginTop)
    {
        if (marginTop != null && marginTop < 0)
        {
            throw new IllegalArgumentException("Page margins may nto be negative");
        }
        this.marginTop = marginTop;
    }

    /**
     * @param marginBottom
     *     the marginBottom to set
     */
    public void setMarginBottom(final Double marginBottom)
    {
        if (marginBottom != null && marginBottom < 0)
        {
            throw new IllegalArgumentException("Page margins may nto be negative");
        }
        this.marginBottom = marginBottom;
    }

    /**
     * @param marginLeft
     *     the marginLeft to set
     */
    public void setMarginLeft(final Double marginLeft)
    {
        if (marginLeft != null && marginLeft < 0)
        {
            throw new IllegalArgumentException("Page margins may nto be negative");
        }
        this.marginLeft = marginLeft;
    }

    /**
     * @param marginRight
     *     the marginRight to set
     */
    public void setMarginRight(final Double marginRight)
    {
        if (marginRight != null && marginRight < 0)
        {
            throw new IllegalArgumentException("Page margins may nto be negative");
        }
        this.marginRight = marginRight;
    }

    /**
     * @param pageRanges
     *     the pageRanges to set
     */
    public void setPageRanges(final String pageRanges)
    {
        this.pageRanges = pageRanges;
    }

    /**
     * @param ignoreInvalidPageRanges
     *     the ignoreInvalidPageRanges to set
     */
    public void setIgnoreInvalidPageRanges(final Boolean ignoreInvalidPageRanges)
    {
        this.ignoreInvalidPageRanges = ignoreInvalidPageRanges;
    }

    /**
     * @param headerTemplate
     *     the headerTemplate to set
     */
    public void setHeaderTemplate(final String headerTemplate)
    {
        this.headerTemplate = headerTemplate;
    }

    /**
     * @param footerTemplate
     *     the footerTemplate to set
     */
    public void setFooterTemplate(final String footerTemplate)
    {
        this.footerTemplate = footerTemplate;
    }

    /**
     * @param preferCSSPageSize
     *     the preferCSSPageSize to set
     */
    public void setPreferCSSPageSize(final Boolean preferCSSPageSize)
    {
        this.preferCSSPageSize = preferCSSPageSize;
    }

    /**
     * @param transferMode
     *     the transferMode to set
     */
    public void setTransferMode(final TransferMode transferMode)
    {
        this.transferMode = transferMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        if (this.landscape != null)
        {
            jsonGenerator.writeBooleanField("landscape", this.landscape);
        }
        if (this.displayHeaderFooter != null)
        {
            jsonGenerator.writeBooleanField("displayHeaderFooter", this.displayHeaderFooter);
        }
        if (this.printBackground != null)
        {
            jsonGenerator.writeBooleanField("printBackground", this.printBackground);
        }
        if (this.scale != null)
        {
            jsonGenerator.writeNumberField("scale", this.scale);
        }
        if (this.paperWidth != null)
        {
            jsonGenerator.writeNumberField("paperWidth", this.paperWidth);
        }
        if (this.paperHeight != null)
        {
            jsonGenerator.writeNumberField("paperHeight", this.paperHeight);
        }
        if (this.marginTop != null)
        {
            jsonGenerator.writeNumberField("marginTop", this.marginTop);
        }
        if (this.marginBottom != null)
        {
            jsonGenerator.writeNumberField("marginBottom", this.marginBottom);
        }
        if (this.marginLeft != null)
        {
            jsonGenerator.writeNumberField("marginLeft", this.marginLeft);
        }
        if (this.marginRight != null)
        {
            jsonGenerator.writeNumberField("marginRight", this.marginRight);
        }
        if (this.pageRanges != null)
        {
            jsonGenerator.writeStringField("pageRanges", this.pageRanges);
        }
        if (this.ignoreInvalidPageRanges != null)
        {
            jsonGenerator.writeBooleanField("ignoreInvalidPageRanges", this.ignoreInvalidPageRanges);
        }
        if (this.headerTemplate != null)
        {
            jsonGenerator.writeStringField("headerTemplate", this.headerTemplate);
        }
        if (this.footerTemplate != null)
        {
            jsonGenerator.writeStringField("footerTemplate", this.footerTemplate);
        }
        if (this.preferCSSPageSize != null)
        {
            jsonGenerator.writeBooleanField("preferCSSPageSize", this.preferCSSPageSize);
        }
        if (this.transferMode != null)
        {
            jsonGenerator.writeStringField("transferMode", this.transferMode.name());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("PrintToPdfParameters [");
        if (this.landscape != null)
        {
            builder.append("landscape=");
            builder.append(this.landscape);
            builder.append(", ");
        }
        if (this.displayHeaderFooter != null)
        {
            builder.append("displayHeaderFooter=");
            builder.append(this.displayHeaderFooter);
            builder.append(", ");
        }
        if (this.printBackground != null)
        {
            builder.append("printBackground=");
            builder.append(this.printBackground);
            builder.append(", ");
        }
        if (this.scale != null)
        {
            builder.append("scale=");
            builder.append(this.scale);
            builder.append(", ");
        }
        if (this.paperWidth != null)
        {
            builder.append("paperWidth=");
            builder.append(this.paperWidth);
            builder.append(", ");
        }
        if (this.paperHeight != null)
        {
            builder.append("paperHeight=");
            builder.append(this.paperHeight);
            builder.append(", ");
        }
        if (this.marginTop != null)
        {
            builder.append("marginTop=");
            builder.append(this.marginTop);
            builder.append(", ");
        }
        if (this.marginBottom != null)
        {
            builder.append("marginBottom=");
            builder.append(this.marginBottom);
            builder.append(", ");
        }
        if (this.marginLeft != null)
        {
            builder.append("marginLeft=");
            builder.append(this.marginLeft);
            builder.append(", ");
        }
        if (this.marginRight != null)
        {
            builder.append("marginRight=");
            builder.append(this.marginRight);
            builder.append(", ");
        }
        if (this.pageRanges != null)
        {
            builder.append("pageRanges=");
            builder.append(this.pageRanges);
            builder.append(", ");
        }
        if (this.ignoreInvalidPageRanges != null)
        {
            builder.append("ignoreInvalidPageRanges=");
            builder.append(this.ignoreInvalidPageRanges);
            builder.append(", ");
        }
        if (this.headerTemplate != null)
        {
            builder.append("headerTemplate=");
            builder.append(this.headerTemplate);
            builder.append(", ");
        }
        if (this.footerTemplate != null)
        {
            builder.append("footerTemplate=");
            builder.append(this.footerTemplate);
            builder.append(", ");
        }
        if (this.preferCSSPageSize != null)
        {
            builder.append("preferCSSPageSize=");
            builder.append(this.preferCSSPageSize);
            builder.append(", ");
        }
        if (this.transferMode != null)
        {
            builder.append("transferMode=");
            builder.append(this.transferMode);
        }
        builder.append("]");
        return builder.toString();
    }

    public enum TransferMode
    {
        ReturnAsBase64,
        ReturnAsStream;
    }
}