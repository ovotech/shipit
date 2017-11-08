# :shipit:

:shipit: is a deployment log service. How to use:

1. Send a simple HTTP POST to :shipit: every time you deploy any of your services.
2. See all your deployments in a lovely table.
3. Let :shipit: take care of sending Slack notifications to #announce_change for you.

## How to run locally

Export all necessary environment variables (see `conf/application.conf`). Then `sbt run`.

## Documentation

See https://shipit.ovotech.org.uk/guide
