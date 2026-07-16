# embrace-android-semconv

This repo defines a federated [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/) registry for the
Embrace Android SDK. It depends on the core OTel semantic conventions registry as well as
the Embrace semantic conventions, generating the Kotlin source files that exposes constants
for semantic conventions that it references and could not be obtained from the core
semantic convention Kotlin files generated in `opentelemetry-kotlin`.

## Regenerating

The Kotlin under `src/main/kotlin/io/embrace/android/embracesdk/semconv/` is generated with
[weaver](https://github.com/open-telemetry/weaver), pinned by `WEAVER_VERSION` in
[`versions.env`](versions.env). CI installs it via the shared **`setup-weaver`** GitHub Action in
[`embrace-io/embrace-semconv`](https://github.com/embrace-io/embrace-semconv), passing this repo's
pinned version.

To run the regeneration locally, install weaver using that script, and it will put the executable in
your `PATH`, e.g. run `.github/actions/setup-weaver/install-weaver.sh` from a checkout of
the `embrace-semconv` repo. Pass `WEAVER_VERSION` as a parameter to install the same version.

If you change anything under `src/main/semconv/`, `src/main/templates/`, or change the Weaver
version, rerun the generate task and commit the regenerated source code together with your change*:

```
./gradlew :embrace-android-semconv:generateEmbraceSemanticConventions
```

The `Semconv drift` CI workflow fails any PR whose committed generated code doesn't match what the
model and templates should produce.

## Layout

```
src/main/
  semconv/
    manifest.yaml          # registry name + federated dependencies (see "Dependencies")
    <domain>.yaml          # attribute definitions + groups, one file per domain
  templates/registry/kotlin/
    weaver.yaml                     # selects which groups generate + the jq filter shaping them
    embrace_semconv_template.kt.j2  # the Kotlin emitted per group
  kotlin/io/embrace/android/embracesdk/semconv/
    Emb*Attributes.kt      # GENERATED — do not edit; regenerate instead
```

## The model: definitions vs. groups

A `definition/2` registry file has two independent sections:

- **`attributes:`** — a flat pool of attribute *definitions*, each keyed by `key` (its on-the-wire
  name) plus `type`, `brief`, `examples`, `stability`. A definition is just data; it belongs to no
  group and, on its own, generates nothing.
- **`attribute_groups:`** — named bundles. Each has an `id`, `visibility`, `stability`, `brief`, and a
  list of members written as `- ref: <key>`. A group *references* attributes; it does not contain
  their definitions.

Definitions and groups are **decoupled**: grouping is arbitrary and independent of the key's
namespace (a group may ref attributes from several namespaces, and an attribute may be ref'd by
several groups). The `.yaml` *file* an attribute or group lives in is purely organizational — Weaver
merges every file under `src/main/semconv/` (recursively) plus all dependency registries into a
single model before doing anything. Files are split by domain (`session.yaml`, `crash.yaml`,
`state.yaml`, …) only for human readability.

## What gets generated, where, and why

Weaver runs in two phases — **resolve**, then **generate**:

1. **Resolve** — merge all local files + imported registries into one model. Every `- ref: <key>` in
   a group is looked up in the definition pool and the full definition is inlined into that group's
   member list.
2. **Generate** — walk the groups and emit one Kotlin file per group.

The rules that follow from this:

### One group → one file, named by the group id

`weaver.yaml` uses `application_mode: each`, so the template runs once per group. The file/class name
comes only from the group id:

```
class name = pascal_case( group_id with '.' → '_' ) + "Attributes"
```

- `emb.session`        → `EmbSessionAttributes.kt`
- `emb.network_state`  → `EmbNetworkStateAttributes.kt`

The **group id is the class name.** The source `.yaml` filename has no effect on output — e.g.
`state.yaml` holds five groups and produces five separate classes.

### Only `emb.*` groups generate

The filter in `weaver.yaml` keeps groups whose id `startswith("emb.")`. Groups defined in dependency
registries (the shared registry's `registry.embrace.emb`, or any core-OTel group) are dropped — they
exist only to carry definitions into the pool, not to produce classes here.

### A constant is emitted for every attribute a generated group `ref`s — and only those

```kotlin
const val <SCREAMING_SNAKE(key)>: String = "<key>"   // value is the key, verbatim
```

Generation is driven by the **ref**, not by the definition:

- An attribute **defined here but referenced by no group generates nothing** — it sits inert in the
  pool. (Corollary: a local `attributes:` entry is dead weight until some group refs it.)
- A `ref` whose definition lives in **another registry still generates a constant here**, because
  refs resolve against the whole merged pool.

### Refs resolve across every registry in `manifest.yaml`

A group can `ref` an attribute defined:

- **locally** (any `src/main/semconv/*.yaml`),
- in the **shared Embrace registry** (`embrace` dependency) — e.g. `emb.user_session_id` /
  `emb.session_part_id`, defined once there and ref'd by `emb.session` here, and
- in **OpenTelemetry core** (`otel` dependency) — e.g. `- ref: session.id` emits
  `SESSION_ID = "session.id"` into whichever `emb.*` class refs it.

A definition's origin affects only the generated **KDoc text** (its `brief`) and Weaver's internal
`provenance` metadata — never which file the constant lands in. A ref that resolves to nothing is a
**hard error**, which is a useful safety net when moving definitions out to a shared registry.

### Enums

If a definition's `type` is a mapping with `members`, the template also emits a nested `<Key>Values`
object holding `const val <SCREAMING_SNAKE(member.id)> = "<member.value>"` for each member.

### `@ExperimentalSemconv`

Applied to every constant (and enum member) of a group whose **group** `stability` is `development`.
It is gated by the *group's* stability, not the individual attribute's. All current groups are
`development`.

## Dependencies (federation)

`manifest.yaml` names this registry and declares its federated dependencies:

| name      | registry                                                   | role                                                                    |
|-----------|------------------------------------------------------------|-------------------------------------------------------------------------|
| `otel`    | `open-telemetry/semantic-conventions@v1.43.0`              | core OTel attributes, available to `ref`                                |
| `embrace` | shared Embrace registry (`semantic-conventions-embrace`)   | cross-platform `emb.*` definitions shared across Embrace SDKs, to `ref` |

Dependencies bring their definitions into the pool so local `emb.*` groups can `ref` them. They do
**not** generate classes in this module (filtered out by `startswith("emb.")`).

## Working with it

- **Add an attribute to an existing class** — add its definition to the relevant `<domain>.yaml`
  `attributes:` block, and add a `- ref: <key>` to that domain's group.
- **Add a new class** — add an `attribute_group` with an `emb.<name>` id (→ `Emb<Name>Attributes.kt`)
  and `ref` its attributes.
- **Move a definition to the shared registry** (dedup across Embrace SDKs) — delete the local
  `attributes:` entry but keep the `- ref:` in the group. The ref then resolves to the imported
  definition, so the same constant is still generated (its KDoc adopts the shared wording). If you
  removed the *ref* instead, the constant would silently disappear; if you delete a definition and
  leave a ref that nothing satisfies, generation errors.
- **Renaming** — the group `id` and attribute `key` are the on-the-wire contract expected by the
  backend. Renaming them changes generated class/constant names (downstream churn) and requires
  backend agreement. Renaming a `.yaml` *file* is free (organizational only).
- **Never hand-edit the generated `Emb*Attributes.kt` files** — regenerate.
## Regenerating


If you change anything under `src/main/semconv/`, `src/main/templates/`, or change the Weaver
version, rerun the generate task and commit the regenerated source code together with your change*:

```
./gradlew :embrace-android-semconv:generateEmbraceSemanticConventions
```

The `Semconv drift` CI workflow fails any PR whose committed generated code doesn't match what the
model and templates should produce.
