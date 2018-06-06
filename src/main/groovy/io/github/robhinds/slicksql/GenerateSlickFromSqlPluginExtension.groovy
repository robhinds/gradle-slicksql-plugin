package io.github.robhinds.slicksql

class GenerateSlickFromSqlPluginExtension {
    String dockerImageName = "mysql:5.7.21"
    String dockerContainerName = "codegen-db"
    String localDockerHost = System.getenv("DOCKER_HOST") ?: "https://localhost:2376"
    String dbName
    String migrationProjectName
    String userName
    String dbUrl
    String port
    String pkg
    String customCodeGenerator = 'slick.codegen.SourceCodeGenerator'
    String jdbcDriver = "com.mysql.cj.jdbc.Driver"
    String slickProfile = "slick.jdbc.MySQLProfile"
}
