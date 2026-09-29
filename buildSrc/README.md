# Internal Embrace Plugin
This [convention plugin](https://docs.gradle.org/current/samples/sample_convention_plugins.html)
is used for all of Embrace's library modules to configure their gradle scripts in a consistent way.


## Config generation

All SDK config is defined in `config-schema/embrace-config.yaml`, grouped by the feature it affects. `GenerateConfigTask`
generates the Kotlin for it with KotlinPoet into `build/generated/`:

- `embrace-android-config`: the remote config model, the SDK interfaces the Gradle plugin instruments, and `EmbraceConfig`.
- `embrace-gradle-plugin`: the embrace-config.json model and the code that instruments the SDK with it.

To add an option, add it to its feature. It declares the remote and embrace-config.json fields it reads alongside its
default:

```yaml
- name: maxLength
  type: Int
  default: 128
  remote: {key: logs.max_length}           # rollout, clamp and valid_range change how the value is read
  local: {key: sdk_config.logs.max_length, description: Shown in embrace-config-schema.json.}
  android: {instrumented: EnabledFeatureConfig.getLogMaxLength}   # delivers the local value to the SDK
```

A field is declared where it is first used, and named by its path alone after that. JSON objects that hold fields of
several features are listed under `remote.objects` and `local.objects`. Options resolve as remote, then local, then
default; for the rare option that can't, `android.resolver` names a function in `ConfigResolvers.kt`. Then add
assertions to `EmbraceConfigTest`. `ConfigSchemaParser` documents every key and rejects anything it can't generate.

`embrace-config-schema.json` is generated from the local fields. Run `./gradlew :embrace-gradle-plugin:configSchemaDump`
after changing them; `check` fails while the committed file is out of date. A local field with `internal: true` is
left out of it.

Adding, removing or reordering remote fields changes the cached binary schema and fails
`EmbraceBinaryVersionGuardTest`. If that's intentional, set `@BinaryVersion` on `CachedConfiguration` to the value
the test reports.
