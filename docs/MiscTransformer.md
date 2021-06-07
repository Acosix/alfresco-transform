# Miscellaneous Transformer

## Supported Mimetypes

This transformer module includes / will include multiple different transformers with their own set of supported mimetypes. The current set of included transformers and their supported mimetypes are:

- Chrome / Chromium DevTools-based transformer
    - Sources: `text/html`, `application/xhtml+xml`, `image/svg+xml`
    - Targets: `application/pdf`, `image/png`, `image/jpeg`
- Mail (`message/rfc822`) to HTML (`text/html` / `application/xhtml+xml`) transformer

## Dependencies

The Chrome / Chromium DevTools-based transformer requires a Chrome / Chromium instance to be set up for remote debugging in order to perform transformations. The default Docker image built by this project does not include Chrome / Chromium. Multiple Chrome / Chromium Docker images are available within the Docker community for running such an instance. This project uses the [Acosix Baseimage (Ubuntu) Headless Chrome](https://hub.docker.com/r/acosix/baseimage-chrome-headless) ([source project](https://github.com/Acosix/acosix-docker-generic/tree/master/baseimage-chrome-headless)) image for [its test setup](../misc/docker-compose.yml).

## Configuration properties

| Property | Default | Remarks |
| :--- | :--- | :--- |
| devtools.host | localhost | Host on which a Chrome / Chromium browser with exposed DevTools Protocol / RDP port is running / available |
| devtools.port | 9222 | The port on which the Chrome / Chromium browser listens for DevTools Protocol / RDP requests |
| devtools.connectTimeout | 30000 | The timeout for connection attempts to a Chrome / Chromium browser with exposed DevTools Protocol / RDP in milliseconds |
| devtools.connectLostTimeout | 15 | The interval for checking web socket connections to a Chrome / Chromium browser with exposed DevTools Protocol / RDP in seconds |
| mailHtml.defaultTimezone | UTC | The default timezone to use when rendering the mail send date |
| mailHtml.defaultLocale | en | The default locale to use when rendering the mail sent date and selecting the mail header HTML template |
| mailHtml.mailHeaderCss.resource | mailHeader.css | The name of the resource holding the static stylesheet rules for the mail header - can be an absolute resource name on the classpath or a relative file path to the current working directory |
| mailHtml.mailHeaderHtml.defaultResource | mailHeader_en.html | The name of the resource holding the mail header HTML template - can be an absolute resource name on the classpath or a relative file path to the current working directory |
| mailHtml.mailHeaderHtml.localisedResource.en | mailHeader_en.html | The name of the resource holding the mail header HTML template for the `en` locale - can be an absolute resource name on the classpath or a relative file path to the current working directory |
| mailHtml.mailHeaderHtml.localisedResource.de | mailHeader_de.html | The name of the resource holding the mail header HTML template for the `de` locale - can be an absolute resource name on the classpath or a relative file path to the current working directory |

The Mail to HTML transformer can be customised with alternative or additional localised mail header templates, and an alternative stylesheet source file. The last key fragment of a localised resource takes the form of a Java `Locale.toString()` representation, e.g. `de_DE` for German specific to Germany. The locale is one of the transform options that can be provided by a client, or set via the default locale configuration.
When defining custom / alternative mail header HTML templates, the following requirements need to be adhered to:

- root element must have the class `mailHeader`
- placeholders to receive data from mail headers need to be defined as `<span class="$fieldName"></span>` where `$fieldName` may be any of the supported fields
    - from, to, cc, bcc
    - date
    - subject
    - attachments
- any elements that should be hidden if a specific field is not set / empty should also be given the class specific to the field (see above)
- a custom mail header stylesheet source file is specified if the structure diverges significantly from the default template(s)

## Transform options

### DevTools (Chrome / Chromium) Transformer

The following specific transform options are supported:

- Target `application/pdf`
    - pdfLandscape - `true`/`false` flag whether to print in landscape or portrait (default)
    - pdfPrintBackground - `true`/`false` flag whether to print the web page's background (default) or not
    - pdfPreferCSSPageSize - `true`/`false` flag whether to prefer the CSS-defined page size or not (default)
    - pdfPageWidth - the width of a sheet of paper in millimeters (defaults to ISO A4 width of 210mm)
    - pdfPageHeight - the height of a sheet of paper in millimeters (defaults to ISO A4 height of 297mm)
    - pdfMarginTop - the margin from the top of the page in millimeters (defaults to 19.1mm)
    - pdfMarginBottom - the margin from the bottom of the page in millimeters (defaults to 19.1mm)
    - pdfMarginLeft - the margin from the left of the page in millimeters (defaults to 17.8mm)
    - pdfMarginRight - the margin from the right of the page in millimeters (defaults to 17.8mm)
    - pdfPageRanges - the range of pages to print; may be a comma separated list of page numbers or page ranges, i.e. `1,3,5-11` (defaults to empty value for printing all pages)
    - pdfIgnoreInvalidPageRanges - `true`/`false` flag whether to ignore invalid page ranges provided via the `pageRanges` parameter or not (default)
    - pdfHeaderTemplate - the HTML template markup for a common page header (can use classes `date`, `title`, `url`, `pageNumer`, `totalPages` on elements to dynamically inject values)
    - pdfFooterTemplate - the HTML template markip for a common page footer
- Targets `image/png` / `image/jpeg`
    - screenshotViewportX - the left-most position of the viewport in pixels
    - screenshotViewportY - the top-most position of the viewport in pixels
    - screenshotViewportWidth - the width of the viewport in pixels (defaults to 787 for A4-like dimension based on 96dpi)
    - screenshotViewportHeight - the height of the viewport in pixels (defaults to 1114 pixels for A4-like dimension based on 96dpi)
    - screenshotViewportScale - the page scale factor for the viewport
    - screenshotCompressionQuality - the compression quality (JPEG-only) in the `[0..100]` range

### Mail to HTML Transformer

The following specific transform options are supported:

- locale - stringified Java locale, e.g. `de_DE`, used to pick the mail header HTML template and date/time format for rendering the mail sent date
- timezone - a valid Java timezone identifier, e.g. `UTC` or `Europe/Berlin`, used for rendering the mail sent date