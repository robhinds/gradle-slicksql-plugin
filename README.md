# Gradle Slick from SQL plugin

Gradle plugin to work with flyway migration for managing your SQL.

The plugin expects a gradle multiproject setup with one subproject managing the SQL files (in flyway migration format).

The plugin then runs a Mysql docker image and runs flyway migrate on the docker based DB instance to create the schema, and then runs the slick generate source code from the docker DB instance. IT then destroys and removes the docker image after the build is done.

