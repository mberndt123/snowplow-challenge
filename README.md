# The Snowplow Analytics technical test

This repo contains my solution to the [Snowplow Analytics](https://gist.github.com/goodits/20818f6ded767bca465a7c674187223e) technical test.

It is a simple REST service based on [http4s](https://http4s.org/) and [H2](https://www.h2database.com/) (a more complete list of the employed libraries can be found in `build.sbt`)

The `main` method requires a single parameter: a path to the file where the H2 database will be stored, e. g. `./test`. If this file doesn't exist, the program will initialize it.

To run the project, open a terminal in the project repository and type `sbt 'snowplowTask/run ./test'` ([sbt](https://www.scala-sbt.org/) must be installed).
