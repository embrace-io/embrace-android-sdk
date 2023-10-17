<p align="center">
  <a href="https://embrace.io/?utm_source=github&utm_medium=logo" target="_blank">
    <picture>
      <source srcset="https://embrace.io/docs/images/embrace_logo_white-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: dark)" />
      <source srcset="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" media="(prefers-color-scheme: light), (prefers-color-scheme: no-preference)" />
      <img src="https://embrace.io/docs/images/embrace_logo_black-text_transparent-bg_400x200.svg" alt="Embrace">
    </picture>
  </a>
</p>

The Embrace Android SDK provides instrumentation for Android apps.

[![codecov](https://codecov.io/gh/embrace-io/embrace-android-sdk/graph/badge.svg?token=4kNC8ceoVB)](https://codecov.io/gh/embrace-io/embrace-android-sdk)
[![android api](https://img.shields.io/badge/Android_API-16-green.svg "Android min API 21")](https://dash.embrace.io/signup/)
[![build](https://img.shields.io/github/actions/workflow/status/embrace-io/embrace-android-sdk/ci-gradle.yml)](https://github.com/embrace-io/embrace-android-sdk/actions)

# Getting Started

> :warning: **This is for native android apps**: Leverage in our Unity, ReactNative and Flutter SDKs for cross-platform apps 

- [Go to our dashboard](https://dash.embrace.io/signup/) to create an account and get your API key
- Check our [guide](https://embrace.io/docs/android/integration/) to integrate the SDK into your app

## Upgrading from 5.x

Follow our [upgrading guide](https://github.com/embrace-io/embrace-android-sdk/blob/master/UPGRADING.md)

# Usage

- Refer to our [Features page](https://embrace.io/docs/android/features/) to learn about the features Embrace SDK provides

# Support

- Join our [Community Slack](https://embraceio-community.slack.com/)
- Contact us [support@embrace.io](mailto:support@embrace.io)

# Development

## Code Formatting

In most of our repos we are using Detekt to analyse our kotlin code. This analysis should be done before the new code is merged to master. It’s considered a good practice to run the command before pushing our code. Github workflows will also be running this check.

To run the check locally, you can run the following command in the root directory of the project:

`./gradlew detekt`

n some cases, the errors get fixed just by running the command, so if you run it again, you could get less errors than the first time. As a result, it will list the errors and you need to go and fix them if you consider appropriate.

You can run the command until you get no errors or until you get only the errors you don’t want to fix.

If you have errors you want to ignore, you need to run:

```bash
./gradlew detektBaseline
```

This command will add a line per error to be ignored into the `baseline.xml` file. This way, this file will be updated and the code smell will be also ignored by Github Workflows.

## License

See the [LICENSE](https://github.com/embrace-io/embrace-android-sdk/blob/master/LICENSE) 
for details.
