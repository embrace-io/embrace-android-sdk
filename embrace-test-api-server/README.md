# embrace-test-api-server

A fake of Embrace's server backend API endpoints on OkHttp's `MockWebServer`: the endpoints that the SDK and the
Embrace Gradle plugin call, not the API the SDK offers apps. The SDK's integration tests, the Gradle plugin's
integration tests and the benchmarks' endpoint served on the device all use its `FakeApiServer`, which serves one of the
backend's APIs, a `FakeBackendApi`: it routes each request to one of the API's endpoints, records it and answers it.
`SdkBackendApi` is the API the SDK calls, with its endpoints in `SdkBackendEndpoint` and its config endpoint in
`RemoteConfigEndpoint`.

It depends on no module of the SDK, and its build file uses none of `buildSrc`'s convention plugins, so that a build
outside this one can include it as well.
