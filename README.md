[![Build Status](https://travis-ci.com/flowvault/proxy.svg?token=8bzVqzHy6JVEQr9mN9hx&branch=master)](https://travis-ci.com/flowvault/proxy)

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
