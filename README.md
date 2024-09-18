<p align="center">
  <a href="https://embrace.io/?utm_source=github&utm_medium=logo" target="_blank">
    <picture>
      <source srcset="https://embrace.io/docs/images/embrace_logo_white-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: dark)" />
      <source srcset="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: light), (prefers-color-scheme: no-preference)" />
      <img src="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" alt="Embrace">
    </picture>
  </a>
</p>

[![codecov](https://codecov.io/gh/embrace-io/embrace-android-sdk/graph/badge.svg?token=4kNC8ceoVB)](https://codecov.io/gh/embrace-io/embrace-android-sdk)
[![android api](https://img.shields.io/badge/Android_API-21-green.svg "Android min API 21")](https://dash.embrace.io/signup/)
[![build](https://img.shields.io/github/actions/workflow/status/embrace-io/embrace-android-sdk/ci-gradle.yml)](https://github.com/embrace-io/embrace-android-sdk/actions)
[![latest version](https://shields.io/github/v/release/embrace-io/embrace-android-sdk)](https://shields.io/github/v/release/embrace-io/embrace-android-sdk)

# About
The Embrace Android SDK builds on top of [OpenTelemetry](https://opentelemetry.io) to capture performance data for 
Android apps, enabling full-stack observability of your system by connecting mobile and backend telemetry in a seamless way.

Telemetry recorded through this SDK can be consumed on the Embrace platform for Embrace customers, but it can also be used by those who are
not Embrace customers to export collected data directly to any OTel Collector, either one that they host or is hosted by another vendors. 
In effect, this SDK is an alternative to [opentelemetry-android](https://github.com/open-telemetry/opentelemetry-android) or using the [OpenTelemetry Java SDK](https://github.com/open-telemetry/opentelemetry-java) directly for Android apps that want to leverage the 
OpenTelemetry ecosystem for observability, but also want all the advanced telemetry capture that Embrace is known for like ANR thread sampling, native crash
capture, and so forth.

Currently, only Spans and Logs are supported, but other signals will be added in the future.

# Getting Started
## Non-Embrace Users
### Android Project Setup
1. In your app's Gradle file, add a dependency to the latest version of the Embrace Swazzler Gradle plugin: `io.embrace:embrace-swazzler:<version>`.
   - This plugin is responsible for configuring your app at build time to auto-capture telemetry. This includes:
     - Updating dependencies to include optional modules that are needed for certain features.
     - Setting up configuration files to be read at runtime.
     - Doing bytecode instrumentation to enable the capture of certain telemetry.
2. For multi-module projects, in the Gradle files of modules you want to invoke Embrace SDK API methods, add a dependency to the main Embrace SDK module: `'io.embrace:embrace-android-sdk:<version>`.
3. In the `main` directory of your app's root source folder (i.e. `app/src/main/`), add in a file called `embrace-config.json` that contains `{}` as its only line.
   - To further configure the SDK, additional attributes can be added to this configuration file. 
   - See our [configuration documentation page](https://embrace.io/docs/android/features/configuration-file/) for further details.
4. In your app's Gradle properties file, add in the entry `embrace.disableMappingFileUpload=true`
   - This allows the SDK to function without sending data to Embrace.
5. In Android Studio, do a Gradle Sync. Barring any errors, you should be able to configure and start the SDK to begin recording and exporting data.

### Configure Exporters and Start SDK
Using the Embrace SDK without being an Embrace customer requires you to set up OTel Exporters to work with the SDK so that recorded telemetry can be sent somewhere. 
To do that, create and configure instances of your chosen exporters and register them with the Embrace SDK before you start it.

```kotlin

val grafanaCloudExporter = OtlpHttpSpanExporter.builder()
    .setEndpoint("https://myinstance.grafana.net/otlp/v1/traces")
    .addHeader("Authorization", "YourToken")
    .build()

Embrace.getInstance().addSpanExporter(grafanaCloudExporter)
Embrace.getInstance().addLogRecordExporter(SystemOutLogRecordExporter.create())

```

It is recommended that you start the Embrace SDK in the your `Application` object's `onCreate()` function (or even earlier) to minimize
the amount of time when telemetry isn't being recorded. This allows performance problems such as crashes and ANRs to be captured as soon
as possible.

```kotlin
internal class MyApplication : Application() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do your exporter setup before starting the SDK

        Embrace.getInstance().start(this)
    }
}
```

For details about the features the Embrace Android SDK supports, refer to our [features page](https://embrace.io/docs/android/features/).

## Prospective Embrace Customers
- If you want to try out the Embrace product along with using this SDK, [go to our website](https://dash.embrace.io/signup/) and begin the sign up process.
- After you've obtained an `appId` and `API token`, checkout our [integration guide](https://embrace.io/docs/android/integration/) for further instructions.

## Existing Embrace Customers Upgrading from 5.x
- For existing customers on older versions of the Embrace SDK, follow the instructions in our [upgrade guide](https://github.com/embrace-io/embrace-android-sdk/blob/main/UPGRADING.md).

# Contributing
- See [CONTRIBUTING.md](CONTRIBUTING.md).

# Support
- Create a [bug report](https://github.com/embrace-io/embrace-android-sdk/issues/new?assignees=&labels=&projects=&template=bug_report.md&title=) or enter a [feature request](https://github.com/embrace-io/embrace-android-sdk/issues/new?assignees=&labels=&projects=&template=feature_request.md&title=) for the Embrace team to triage.
- Join our [Community Slack](https://embraceio-community.slack.com/)

# License

[![Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-orange)](./LICENSE.txt)

Embrace Android SDK is published under the Apache-2.0 license.
