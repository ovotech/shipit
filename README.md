# :shipit:

:shipit: is a deployment log service. How to use:

1. Send a simple HTTP POST to :shipit: every time you deploy any of your services.
2. See all your deployments in a lovely table.
3. Let :shipit: take care of sending Slack notifications to #announce_change for you.

## How to run locally

You'll need access to the `eng-services` AWS account.

Set `AWS_PROFILE` to point at that account, and run `sbt run`.

e.g. `AWS_PROFILE=eng-services sbt run`.

The app will load all necessary configuration from the AWS parameter store.
