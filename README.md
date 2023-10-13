<p align="center">
  <a href="https://embrace.io/?utm_source=github&utm_medium=logo" target="_blank">
    <picture>
      <source srcset="https://embrace.io/docs/images/embrace_logo_white-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: dark)" />
      <source srcset="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: light), (prefers-color-scheme: no-preference)" />
      <img src="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" alt="Embrace">
    </picture>
  </a>
</p>

The Embrace Android SDK gives you performance and stability insights into the user experience of your mobile apps.

[![codecov](https://codecov.io/gh/embrace-io/embrace-android-sdk/graph/badge.svg?token=4kNC8ceoVB)](https://codecov.io/gh/embrace-io/embrace-android-sdk)
[![android api](https://img.shields.io/badge/Android_API-21-green.svg "Android min API 21")](https://dash.embrace.io/signup/)
[![build](https://img.shields.io/github/actions/workflow/status/embrace-io/embrace-android-sdk/ci-gradle.yml)](https://github.com/embrace-io/embrace-android-sdk/actions)

# Getting Started

> :warning: **This is for native Android apps**: Use our Unity, ReactNative and Flutter SDKs for cross-platform apps 

- [Go to our dashboard](https://dash.embrace.io/signup/) to create an account and get your API key
- Check our [guide](https://embrace.io/docs/android/integration/) for instructions to integrate the SDK into your app

## Upgrading from 5.x

- Follow our [upgrade guide](https://github.com/embrace-io/embrace-android-sdk/blob/master/UPGRADING.md)

# Usage

- Refer to our [features page](https://embrace.io/docs/android/features/) to learn about the features the Embrace Android SDK provides

# Support

- Join our [Community Slack](https://embraceio-community.slack.com/)
- Contact us [support@embrace.io](mailto:support@embrace.io)

# Development

## Code Formatting and Linting

We use `detekt` to lint our Kotlin code. GitHub workflows will run this check automatically and flag any violations.

To run the check locally, you can run the following command in the root directory of the project:

`./gradlew detekt`

In many cases, running the command will fix the errors, so if you run it again, you might get fewer errors, leaving only the ones you need to fix manually.

## License

See the [LICENSE](https://github.com/embrace-io/embrace-android-sdk/blob/master/LICENSE) for details.
