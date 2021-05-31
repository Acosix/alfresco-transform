# About

This project aims to provide both the basis for and specific implementations of custom Alfresco transformer applications (so-called "T-Engines") that can be used in the new transformer framework around RenditionService2, geared towards out-of-process, mostly asynchronous transformations.

## Compatibility

The base and specific transformer implementations are built to be compatible with the T-Engine HTTP API used by the Local Transform Service since ACS 6.1/6.2 (different feature availability for Enterprise and Community). While not yet implemented, this project aims to also support handling transformations via the ActiveMQ instance deployed as part of the Alfresco Digital Business Platform, as well as handle metadata extraction. No plans exist as of yet to support metadata embedding, as there appears to be no support within the open source core of ACS / Community Edition of Alfresco.

## Why implement a custom / separate base for Transformers?

Within the alfresco-transform-core GitHub project, Alfresco provides a [common base](https://github.com/Alfresco/alfresco-transform-core/tree/master/alfresco-transformer-base) for creating custom transform engines, which is also used for reference in the official documentation for [creating custom T-Engine](https://docs.alfresco.com/transform-service/latest/config/engine/) implementations. This project provides its own base mostly from scratch for the following reasons:

- **avoidance of Spring Boot**: a matter of preference, but trivial projects like a T-Engine with maybe a hand full of components and very narrowly defined APIs should not need Spring Boot, and be able to save on the dependency budget
- **avoidance of technical debt**: as outlined in [a summary of RenditionService2 issues](./docs/RenditionService2Issues.md), the framework as a whole is fraught with issues and in parts questionable code quality, and given how ineffective Alfresco's contribution / bugfixing processes have been in the past, it was deemed too much of a risk - only a very few, select DTO classes from the alfresco-transform-model library are reused in this project
- **inflexible / barely existent configuration mechanism**: Alfresco's base only provides for a monolithic JSON-based configuration file which does not allow any granular configuration / customisation in a deployment of Alfresco without copying, adapting and overriding the whole file - it should be possible e.g. to change the priority of a single, specific transformation with a simple key-value property or environment variable

## Current Features

### Base

- Alfresco Shared File Store support
- Transformer configuration API endpoint (`<baseUrl>/transform/config`)
- Transformation log page endpoint (`<baseUrl>/log`)
- Transformation test page endpoint (`<baseUrl>/`)
- Transformation request API endpoint (`<baseUrl>/transform`)
    - multipart/form requests with file content from test page or via Local Transform client in ACS
    - JSON requests with file reference in Shared File Store (requests not issued from any currently known ACS Community component)
- Readyness / liveness probe endpoint (`<baseUrl>/live` and `<baseUrl>/ready`)
- [Properties-based configuration mechanism](./docs/Configuration.md) with multi-tiered override (core default < specific transformer default < configuration file < system properties), and addition (core default < specific transformer default < configuration file) support
- Separate internal APIs for transformer and metadata extractor implementations, to consolidate "fake" mimetype detection in base and keep implementations clean

### Transformers

- [OnlyOffice transformer](./docs/OnlyOfficeTransformer.md) using the [OnlyOffice Conversion API](https://api.onlyoffice.com/editors/conversionapi) found in [OnlyOffice Document Server / ONLYOFFICE Docs](https://github.com/ONLYOFFICE/Docker-DocumentServer)
    - Slightly higher priority for converting MS Office formats to PDF / images compared to Alfresco's LibreOffice transformer due to higher result quality
    - Slightly lower priority for converting OpenDocument formats to PDF / images compared to Alfresco's LibreOffice transformer, as tests - specifically with presentations - have shown OnlyOffice to produce strange PDF fragments in some cases
- [Misc transformer](./docs/MiscTransformer.md)
    - Chrome / Chromium DevTools-based conversion of HTML / SVG to PDF / PNG / JPEG

# Build

This project uses a Maven build and targets Java 11. Since the project also produces Docker images in addition to the JAR libraries of the base and specific transformer applications, a local Docker engine is also required to run the build. Both JARs and Docker images are built when running the `mvn clean install` command on the root of the project.

If a Maven toolchains plugin configuration is present in the user's home folder, it will be used accordingly to pick the appropriate JDK to run compilation / build steps regardless of the JDK used to run Maven.

## Dependencies

This project depends on and also includes the following projects / libraries in its shaded JARs as well as pre-built Docker images:

- [Jetty](https://github.com/eclipse/jetty.project) Server and Client, Eclipse Public License Version 2.0
- [SLF4J API](http://www.slf4j.org/), MIT license
- [Logback Classic](http://logback.qos.ch/), Eclipse Public License Version 1.0 / GNU Lesser General Public License Version 2.1
- FasterXML [Jackson Core](https://github.com/FasterXML/jackson-core), [Jackson Databind](https://github.com/FasterXML/jackson-databind) and [Jackson Annotations](https://github.com/FasterXML/jackson-annotations), Apache License Version 2.0
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket), Nathan Rajlich, MIT License
- [OWASP Java Encoder](https://owasp.org/owasp-java-encoder/), BSD 3-Clause "New" or "Revised" License
- Alfresco Transform Model, GNU Lesser General Public License Version 3

The license / copyright notices as provided by each project can be found in the [licenses](./licenses/) directory.

## Deliverables

This project currently builds the following Docker images, available on DockerHub:

- acosix/alfresco-transform-base
- acosix/alfresco-transform-onlyoffice
- acosix/alfresco-transform-misc

This project currently builds the following Maven / Java artifacts, available on Maven Central (release versions) or Sonatype Open Source Repository Hosting (SNAPSHOTS) - excluding POMs, as well as other technical or semantically empty artifacts:

- de.acosix.alfresco.transform:de.acosix.alfresco.transform.base:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.base:sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.base:shaded:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.base:shaded-sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.base:javadoc:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.onlyoffice:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.onlyoffice:shaded:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.onlyoffice:sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.onlyoffice:shaded-sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.onlyoffice:javadoc:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.misc:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.misc:shaded:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.misc:sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.misc:shaded-sources:jar
- de.acosix.alfresco.transform:de.acosix.alfresco.transform.misc:javadoc:jar

## Using SNAPSHOT builds

In order to use a pre-built SNAPSHOT artifact published to the Open Source Sonatype Repository Hosting site, the artifact repository may need to be added to the POM, global settings.xml or an artifact repository proxy server. The following is the XML snippet for inclusion in a POM file.

```xml
<repositories>
    <repository>
        <id>ossrh</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```