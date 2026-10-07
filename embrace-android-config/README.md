# embrace-android-config

Defines how the SDK should behave according to its configuration.

Resolved configs are generated at build time from `src/main/config.yaml` by `GenerateConfigTask`.

Their unit tests are generated too, and run automatically as part of `test`. To run them manually:

```bash
./gradlew :embrace-android-config:test --tests '*GeneratedTest'
```
