<p align="center">
  <a href="https://embrace.io/?utm_source=github&utm_medium=logo" target="_blank">
    <picture>
      <source srcset="https://embrace.io/docs/images/embrace_logo_white-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: dark)" />
      <source srcset="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: light), (prefers-color-scheme: no-preference)" />
      <img src="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" alt="Embrace">
    </picture>
  </a>
</p>

The Embrace SDK is effectively an instrumentation library/agent designed for mobile that auto-instruments mobile events and records them as OTel signals. It actually uses the vanilla Java SDK to create the OTel signals behind the scenes.
It can be considered an alternative to the official [Android instrumentation library](https://github.com/open-telemetry/opentelemetry-android), but it uses the Embrace data model for user session transition, captures more mobile telemetry out of the box, and provides a different API that is designed with mobile devs in mind.

[![codecov](https://codecov.io/gh/embrace-io/embrace-android-sdk/graph/badge.svg?token=4kNC8ceoVB)](https://codecov.io/gh/embrace-io/embrace-android-sdk)
[![android api](https://img.shields.io/badge/Android_API-21-green.svg "Android min API 21")](https://dash.embrace.io/signup/)
[![build](https://img.shields.io/github/actions/workflow/status/embrace-io/embrace-android-sdk/ci-gradle.yml)](https://github.com/embrace-io/embrace-android-sdk/actions)

# Getting Started

> :warning: **This is for native Android apps**: Use our Unity, ReactNative and Flutter SDKs for cross-platform apps 

- [Go to our dashboard](https://dash.embrace.io/signup/) to create an account and get your API key
- Check our [guide](https://embrace.io/docs/android/integration/) for instructions to integrate the SDK into your app

# Upgrading from 5.x

- Follow our [upgrade guide](https://github.com/embrace-io/embrace-android-sdk/blob/master/UPGRADING.md)

# Usage

- Refer to our [features page](https://embrace.io/docs/android/features/) to learn about the features the Embrace Android SDK provides

# Support

- Join our [Community Slack](https://embraceio-community.slack.com/)
- Contact us [support@embrace.io](mailto:support@embrace.io)

# License

[![AGPLv3](https://img.shields.io/github/license/suitecrm/suitecrm.svg)](./LICENSE.txt)

Android Embrace SDK is published under the AGPLv3 license.
