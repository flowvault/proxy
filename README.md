[![Build Status](https://travis-ci.org/flowvault/proxy.svg?branch=master)](https://travis-ci.org/flowvault/proxy)

# Proxy

API Proxy server that is hosted at https://api.flow.io

## Main features:

  - Resolves an incoming path to a backend application (e.g. /users =>  'user' application)
  - Authorizes the API token if present via the Authorization header (basic auth)
  - Authorizes the Bearer token if present (JWT)
  - If the path contains an :organization prefix, verifies that the user is a member of
    said org. This also verifies that the organization prefix is valid.
  - Inject X-Flow-Auth header containing all of the data verified, including user id,
    organization and membership role when checked
  - Implements optional configuration of independent thread pools for each backend
    service (catalog service has one thread pool)
  - Any path that starts with /internal is treated as internal to Flow. Validates that the
    provided API Key is valid for the flow organization
  - Implements JSONP proxy based on presence of url parameter named 'callback' and optional
    parameter named 'method' that is used to specify the HTTP method to use when proxying request
    to the backend.
  - Implements all responses in an 'envelope' with HTTP 200 response
    codes based on presence of url parameter named 'envelope' with value 'response'
  - Support envelope=request query parameter that allows you to POST a JSON object that we will
    use to create the request
  - Converts www form urlencoded strings (body and query for JSONP) into form data, validating
    and converting types according to one or more apidoc schemas (via environment variable
    named APIDOC_SERVICE_URIS)

## Bypassing proxy

We support manually configuring the behavior of the proxy on a per
request basis via the following headers:

  - X-Flow-Host (e.g. http://localhost:6291) - if specified, we
    forward the request to this host

  - X-Flow-Service (e.g. 'organization') - if specified, we forward
    the request to this service

If you specify a header, you must also specify an Authorization header
for a user that is a member of the 'flow' organization.

## Future features:

  - Implement expansion by detecting 'expand' query parameters
  
  - Implement backwards compatibility layer by upgrading responses
    from the latest version of the API to the user's requested version

## Internal URLs

View current configuration, including all services and routes:

```
http://localhost:9000/_internal_/config
```

## Example configuration files

environment variable | example URL
-------------------- | ---------------
PROXY_CONFIG_URIS    | https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config
APIDOC_SERVICE_URIS  | https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/latest/api.service.json

Multiple URIS can be provided as a single, comma-separated string.

[Learn more about apidoc](http://apidoc.me)
