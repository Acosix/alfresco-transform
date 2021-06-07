# OnlyOffice Transformer

## Supported Mimetypes

This transformer supports all the transformations supported by the [OnlyOffice Conversion API](https://api.onlyoffice.com/editors/conversionapi) with the following exceptions:

- fb2
- fodt
- fods
- fodp
- mht
- xps

Note that OnlyOffice documents file extensions as types whereas the transformer exclusively uses mimetypes for the deifnition of supported transformations, so there may be edge cases when Alfresco has not determiend / set the correct mimetype for a content despite the node having a particular extension in the name.

## Configuration properties

| Property | Default | Remarks |
| :--- | :--- | :--- |
| onlyoffice.conversionUrl |  | The full URL to the OnlyOffice conversion API - if the URL begins with `https://`, the SSL configuration will be loaded, otherwise no SSL communication (even if redirect to HTTPS) is supported |
| onlyoffice.jwtSecret |  | The value of the shared secret for signing / validating the 
JWT - if no secret is set, JWT will not be used |
| onlyoffice.tokenHeaderName |  | The name of the HTTP header to use (in addition to the request payload) for providing a JWT, if enabled - if not set, the HTTP default `Authorization` header is used |
| onlyoffice.defaultTimeout | `900000` | The default timeout to use for OnlyOffice conversion requests - only used if requests to the transformer specified an explicit, non-positive timeout |
| onlyoffice.callback.publicSsl | `${application.ssl}` | Whether to generate HTTPs-based callback URLs for the conversion API to retrieve the source document - relevant if either the transformer application itself uses SSL/TLS or sits behind a SSL/TLS proxy / gateway |
| onlyoffice.callback.publicHost | `${application.host}` | The host name to use in generating callback URLs for the conversion API to retrieve the source document - relevant if the transformer application sits behind a proxy / gateway, or `application.host` has not been configured with the actual host name |
| onlyoffice.callback.publicPort | `${application.port}` | The port to use in generating the callback URLs for the conversion API to retrieve the source document - relevant if the transformer application sits behind a proxy / gateway, or any kind of port-mapping network routing |
| onlyoffice.callback.publicContext |  | The base URL path to use in generating callback URLs for the conversion API to retrieve the source document - relevant if the transformer application sits behind a proxy or gateway with URL remapping; if not configured, the technical endpoint context `/onlyOfficeCallback` is used |

In addition to the configuration properties listed above, the global SSL configuration property default can be overridden by using the same property name and replacing the `application.` prefix with the `onlyoffice.` prefix (e.g. `application.ssl.truststore.path` becomes `onlyoffice.ssl.truststore.path`).

## Priorities and limits, compared to Alfresco's default LibreOffice transformer

The OnlyOffice transformer uses a default priority of 45 for its defined transformations. This is slightly higher than the default priority used by the LibreOffice transformer or any other transformer. For some constellations, the OnlyOffice transformer uses more specific priority values.

For the following transformations, the LibreOffice transformer has specified explicit priorities that differ from the default value of 50, most likely to accommodate specific alternative / fallback transformation routes. Due to a generally higher result quality in tests, the OnlyOffice transformer has been set with an explicitly higher priority.

- text/html to application/pdf
- text/csv to application/pdf
- text/tab-separated-values to application/pdf
- application.vnd.ms-excel-template to application/pdf
- application/vnd.ms-powerpoint.slideshow.macroenabled.12 to application/pdf
- application.vnd.ms-excel-template to application/pdf

For all transformations from the following source mimetypes to PDF or any image format, the OnlyOffice transformer uses a slightly lower priority than the Alfresco's LibreOffice transformer, as LibreOffice is generally superior in handling its native formats:

- application/vnd.oasis.opendocument.text
- application/vnd.oasis.opendocument.spreadsheet
- application/vnd.oasis.opendocument.presentation

In contrast to Alfresco's LibreOffice transformer, the OnlyOffice transformer does not impose any default limits on source file sizes for its transformations. Due to a bug in Alfresco's transformer registry implementation within ACA (see the list of [RenditionService2 issues](./RenditionService2Issues.md)), the LibreOffice transformer may effectively overrule the "no limit" policy of the OnlyOffice transformer if its transform configuration is loaded after the OnlyOffice transformer's was loaded. Unfortunately there is no reliable way to guarantee the load order in all possible constellations.

## Transform options

In order to be applicable for the rendering of simple thumbnails for Alfresco Share's document library, the OnlyOffice transformer supports the following ImageMagick transform options:

- thumbnail (effectively ignored)
- allowEnlargement (effectively ignored)
- resizeHeight
- resizeWidth
- maintainAspectRatio - defaults to `true`

The following specific transform options are supported:

- any transformation to PDF
    - pdfa - `true`/`false` flag whether a PDF/A should be generated (defaults to `true`)
- any transformation from CSV
    - csvDelimiter - the character used to delimit columns in the file; if not specified, the default character will be determined based on the specific mimetype, with `text/csv` using a comma, and `text/tab-separated-values` using a tab
- any spreadsheet format (including CSV) to PDF
    - pdfLocale - the region / locale to use for handling numeric / date / currency formats (e.g. `de-DE`)
    - pdfFitToHeight - "the height of the converted area in number of pages" (unclear what OnlyOffice API docs mean with that - defaults to `1`)
    - pdfFitToWidth - "the width of the converted area in number of pages" (unclear what OnlyOffice API docs mean with that - defaults to `1`)
    - pdfIncludeGridLines - whether grid lines should be included in the PDF (defaults to `false` in the API)
    - pdfIncludeHeadings - whether column/row headers should be included in the PDF (defaults to `false` in the API)
    - pdfIgnorePrintArea - whether the spreadsheet defined print area should be ignored when converting to PDF (defaults to `false`)
    - pdfMarginBottom - the bottom page margin to apply (defaults to `19.1mm` in the API)
    - pdfMarginTop - the top page margin to apply (defaults to `19.1mm` in the API)
    - pdfMarginLeft - the left page margin to apply (defaults to `17.8mm` in the API)
    - pdfMarginRight - the right page margin to apply (defaults to `17.8mm` in the API)
    - pdfOrientation - the orientation of the page (defaults to `portrait` in the API)
    - pdfPageHeight - the right page margin to apply (defaults to `297mm` in the API)
    - pdfPageWidth - the right page margin to apply (defaults to `210mm` in the API)
    - pdfScale - the scale of the PDF file (defaults to `100` in the API)