## Configuration sources / mechanism

Each transformer application built on using the [common base project](../base/) supports a key-value oriented configuration mechanism using regular Java properties. Specifically, the following sources are consulted in order to build the effective set of configuration properites:

- Classpath-located `defaultTransformer.properties`, defined within the common base project, defining core application configuration properties relating HTTP(S) handling, logging, Shared File Store access, and transformer application name/version
- Classpath-located `transformer.properties`, defined typically within the specific transformer application source project, defining supported transformations, transform options, and any other, custom properites used to initialise components, e.g. URL endpoints for using external conversion services
- Filesystem-located `transformer.properties` (in the current working directory), used to statically provide overrides / additions to the configuration of the application
- Environment variables, prefixed with `T_`, allowing an override of any configuration property defined via the mechanisms outlined above
- System properties specified via `-D` flags in the call to the Java binary, allowing an override of any configuration property defined via the mechanisms outlined above

Despite Spring not being used within the common base project or specific transformers, the configuration mechanism supports use of simple placeholders to reference values from other properties in the form `${other.property}` which will be resolved when looking up a particular property at runtime.

## Common configuration properties

| Property | Default | Remarks |
| :--- | :--- | :--- |
| application.name | (varies) | Name of the application - used in transform test / log pages - purely cosmetic |
| application.version | (varies) | Version of the specific transformer - used in the `/version` API endpoint for potential use/display in ACS |
| application.host | `localhost` | Name of the host on which the application runs - in the common base, this is only used for recording entries in the transformation log, but may be used by specific transformers for other purposes |
| application.bindHost |  | Host name or IP on which to bind for incoming HTTP requests - if not set, the application will listen on all network interfaces |
| application.port |  | Port number on which to listen for HTTP requests - if not set, the application will use a default of `8080` if `application.ssl` is configured as `false`, otherwise `8443` will be used |
| application.minThreads | `5` | The minimum number of threads to keep alive for handling HTTP requests |
| application.maxThreads | `200` | The maximum number of threads to allow for handling HTTP requests |
| application.requestLog.path | `request.log` | The path / location of the HTTP request log file written by Jetty - in the Docker images built as part of this project, this property is overridden via a default environment variable to use `/var/log/acosix-transformer/request.log` |
| application.requestLog.retainDays | `7` | The number of days to keep the daily rotated request log files |
| application.requestLog.format | `%{yyyy-MM-dd'T'HH:mm:ssZZZ}t %X \"%r\" %I %s %{ms}T %O` | The log message format to use for the request log file |
| application.default.transformTimeout | `900000` | The default timeout value for any transformation in milliseconds, unless a request specifies its own timeout |
| localTransformationLog.maxEntries | `100` | The maximum number of transformation log entries to keep in the in-memory transformation log |
| application.multipartRequest.maxFileSize | `-1` | The maximum file size to accept in multipart transformation requests (as submitted by ACS or via the transform test form) |
| application.multipartRequest.maxRequestSize | `-1` | The maximum request size to accept in multipart transformation requests (as submitted by ACS or via the transform test form) |

## SSL configuration properties

These properties specify general global SSL configuration properties, which are generally used to specify how the transformer's own HTTP server API should be secured, but also used as default values for any HTTP client configuration a specific transformer may add. The majority of these properties can be directly traced to configuration properties on [Jetty's SslContextFactory class](https://github.com/eclipse/jetty.project/blob/jetty-10.0.x/jetty-util/src/main/java/org/eclipse/jetty/util/ssl/SslContextFactory.java).

| Property | Default | Remarks |
| :--- | :--- | :--- |
| application.ssl | `false` | Toggle whether HTTP API uses SSL/TLS or not |
| application.ssl.certAlias |  | Alias within the keystore for the servers certificate |
| application.ssl.includeProtocols | TLSv1.2,TLSv1.3 | Allowed SSL protocols |
| application.ssl.excludeProtocols |  | Disallowed SSL protocols - relevant only if allowed list is empty; if both are empty, Jetty defaults are active |
| application.ssl.includeCipherSuites |  | Allowed cipher suites |
| application.ssl.excludeCipherSuites |  | Disallowed cipher suites - if both allowed/disallowed config is empty, Jetty defaults are active |
| application.ssl.useCiperSuitesOrder | `true` | Use cipher cuites in the order specified in the allowed list |
| application.ssl.validateCerts | `true` | Whether SSL certificates are to be validated |
| application.ssl.validatePeerCerts | `true` | Whether SSL peer certificates are to be validated |
| application.ssl.crldpEnabled | `false` | Toggles support for CRL Distribution Points |
| application.ssl.ocspEnabled | `false` | Toggles support for the On-Line Certificate Status Protocol |
| application.ssl.ocspResponderUrl |  | Sets the location of the OSCP Responder |
| application.ssl.keystore.path |  | Path to the keystore holding the server's private key and certificate |
| application.ssl.keystore.provider |  | The name of the JSSE keystore provider to load the keystore - if not set, a default is used that is specific to the JVM / OS combination in which the application runs |
| application.ssl.keystore.type |  | The type of the JSSE keystore to use when loading the keystore - if not set, a default is used that is specific to the JVM / OS / JSSE provider combination in which the application runs |
| application.ssl.keystore.provider |  | The name of the JSSE keystore provider to load the keystore - if not set, a default is used that is specific to the JVM / OS combination in which the application runs |
| application.ssl.keystore.password |  | The password with which the keystore is secured - Planned: if this is the path to a readable file, the password will be read from that file, which may be a mounted secret e.g. in Docker deployments |
| application.ssl.keyManager.factoryAlgorithm |  | The name of the algorithm used by the key manager factory - uses an implicit Jetty default of `SunX509` |
| application.ssl.keyManager.password |  | The password for the key manager |
| application.ssl.truststore.path |  | Path to the keystore holding the server's private key and certificate |
| application.ssl.truststore.provider |  | The name of the JSSE keystore provider to load the truststore - if not set, a default is used that is specific to the JVM / OS combination in which the application runs |
| application.ssl.truststore.type |  | The type of the JSSE keystore to use when loading the truststore - if not set, a default is used that is specific to the JVM / OS / JSSE provider combination in which the application runs |
| application.ssl.truststore.provider |  | The name of the JSSE keystore provider to load the truststore - if not set, a default is used that is specific to the JVM / OS combination in which the application runs |
| application.ssl.truststore.password |  | The password with which the truststore is secured - Planned: if this is the path to a readable file, the password will be read from that file, which may be a mounted secret e.g. in Docker deployments |
| application.ssl.trustManager.factoryAlgorithm |  | The name of the algorithm used by the trust manager factory - uses an implicit Jetty default of `SunX509` |
| application.ssl.secureRandomAlgorithm |  | The name of an algorithm to use for the internal secure random number generator |
| application.ssl.trustAll | `false` | Toggles whether all certificates should be trusted when no key- and/or truststore has been configured |
| application.ssl.sniRequired |  | Toggles whether a SNI mismatch is handled as a certificate match error or left to other handlers, e.g. on the HTTP level, to process |

## Shared File Store configuration properties

When the transformer application receives requests in JSON format, the files to convert as well as the result files must be exchanged via the Alfresco Shared File Store. In default ACS Community Edition, this is typically not relevant in any known configuration up to and including ACS 7.0, but may be relevant for deployments to support an Enterprise Edition installation or when dealing with custom ACS code.
The Shared File Store accessor used in this project supports SSL/TLS encrypted connections with a Shared File Store service, and for this uses the default SSL configuration properties as the basis for configuration - specific properties may be overridden by using the same property name and replacing the `application.` prefix with the `sfs.` prefix (e.g. `application.ssl.truststore.path` becomes `sfs.ssl.truststore.path`).

In addition to SSL configuration, the Shared File Store accessor supports the following configuration properties:

| Property | Default | Remarks |
| :--- | :--- | :--- |
| sfs.url |  | The base URL to the Shared File Store service - if the URL begins with `https://`, the SSL configuration will be loaded, otherwise no SSL communication (even if redirect to HTTPS) is supported |
| sfs.responseTimeoutMillis | `5000` | The response timeout in milliseconds to use when any operation on the Shared File Store is called |

## Supported transformations, priorities and source size limits

Arguably one of the most important configurations for any transformer application is the set of supported transformations, and specific priorities as well as source file size limits for those transformations. Where Alfresco's transformer uses a monolithic engine JSON configuration file (e.g. [Tika engine config JSON](https://github.com/Alfresco/alfresco-transform-core/blob/master/alfresco-transform-tika/alfresco-transform-tika/src/main/resources/tika_engine_config.json)), which does not allow for making granular changes, requiring instead a complete duplication and edit of the file, all transformers based on this project may be configured with simple and granular key-value properties, either in a configuration file, system properties or environment variables as part of a Docker Compose or Helm-based deployment definition.

All configurations in this area have a common key prefix in the form of `transformer.<name>.`. While each supported transformation may be configured granularly, there exist a set of conventions to simplify configuration without removing the potential of granular configuration. The following simplifications exist:

- `transformer.<name>.default.priority` - allows the default priority for all transformations of a transformer to be specified
- `transformer.<name>.default.maxSourceSizeBytes` - allows the default source file size limit for all transformations of a transformer to be specified
- `transformer.<name>.sourceMimetypes` - allows a comma-separated list of mimetypes to be specified that are valid as sources
- `transformer.<name>.targetMimetypes` - allows a comma-separated list of mimetypes to be specified that are valid as targets; together with the previous option, this allows a simple cartesian product of supported tranformations to be defined, where each source type is transformable to each target type
- `transformer.<name>.<mimetype>.priority` - specifies a default priority for all transformations from a given source mimetype to its targets, overriding the more global default if configured
- `transformer.<name>.<mimetype>.maxSourceSizeBytes` - specifies the default source file size limit for all transformations from a given source mimetype to its targets, overriding the more global default if configured
- `transformer.<name>.<mimetype>.targetMimetypes` - allows a comma-separated list of mimetypes to be specified that are valid as targets for the specified source mimetype, overriding the more global default targets if configured

Finally, at a fine-granular level, te following property configuration patterns are supported:

- `transformer.<name>.<sourceMimetype>.<targetMimetype>.supported` - specifies whether this combination of source and target mimetype is a supported transformation - if specified as `false` this may remove a specific transformation from the cartesian product of transformations generated by the simplified properties, if specified as `true` this may add a specific transformation without either source or target needing to be specified in the simplified properties
- `transformer.<name>.<sourceMimetype>.<targetMimetype>.priority` - specifies the effective priority for this transformation
- `transformer.<name>.<sourceMimetype>.<targetMimetype>.maxSourceSizeBytes` - specifies the effective source file size limit for this transformation

## Transform options

The set of supported transform options for specific transformers is typically defined as part of the implementation and not changeable via configuration. Nevertheless, for the sake of consistency, this configuration uses the same key-value granular properties-based configuration approach as the rest of a transformer application's configuration. And in at least one aspect, this allows end-users / administrators to configure default values to assume for specific options unless they are explicitly specified within a transformation request - which is actually a feature not available in Alfresco's transformer framework at all.

Transform options themselves are defines as either a group of options or an individual option. A group is configured using the key pattern `transformerOptions.element.<name>.elements`, with the value being a comma-separated list of other groups or simple option fields included in this grouping. Any name referenced in such a property which itself does not specify a set of elements via this configuration pattern is automatically treated as a simple transform option field.
Both transform option groups and values can be specified as required using a key pattern `transformerOptions.element.<name>.required` with `true` as the value. At runtime, any element contain in a required group inherits its required-status from that group.
Each transformer can be associated with a group of transform options via the `transformer.<name>.transformerOptions` key pattern, using a comma-separated list of group names as reference values.

It is possible to configure different default values for individual transform option fields for individual transformers. Using the key pattern `transformerOptions.<transformerName>.<fieldName>`, a default value can be specified for that particular trnasformer and field combination. The type of the value supported depends on the specific field and how the transformer will interpret it.
