package io.github.robhinds.slicksql

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import de.gesellix.gradle.docker.tasks.*

class GenerateSlickFromSqlPlugin implements Plugin<Project> {

    String generatedSourcesFolder = "src/generated-sources/scala"

    void apply(Project project) {
        project.extensions.create('generator', GenerateSlickFromSqlPluginExtension)
        project.plugins.apply('de.gesellix.docker')
        addCodeGenPhase(project)
        addDependencies(project)
        createSourceSet(project)
        stopDatabaseTask(project)
        removeDatabaseContainerTask(project)
        startDatabaseTask(project)
        databaseConnectionDetailsTask(project)
        waitForDatabaseAvailableTask(project)
        generateSlickSourcesTask(project)
        codegenCleanTask(project)
    }

    protected addCodeGenPhase(Project project) {
        project.configurations { codegen }
    }

    protected addDependencies(Project project) {
        project.dependencies.add("codegen", "org.scala-lang:scala-reflect:2.12.8")
        project.dependencies.add("codegen", "com.typesafe.slick:slick_2.12:3.2.3")
        project.dependencies.add("codegen", "com.typesafe.slick:slick-codegen_2.12:3.2.3")
        project.dependencies.add("codegen", "com.typesafe.slick:slick-hikaricp_2.12:3.2.3")
        project.dependencies.add("codegen", "mysql:mysql-connector-java:6.0.6")
    }

    protected createSourceSet(Project project) {
        project.sourceSets() {
            main {
                scala {
                    srcDirs += project.file(generatedSourcesFolder)
                }
            }
        }
    }

    protected DockerRunTask startDatabaseTask(Project project) {
        project.tasks.create("startDatabase", DockerRunTask) {
            project.afterEvaluate {
                imageName = project.extensions.generator.dockerImageName
                containerName = project.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
                env = [
                        "db_name="
                ]

                env = [
                        "MYSQL_USER=${project.extensions.generator.userName}",
                        "MYSQL_PASSWORD=${project.extensions.generator.userName}",
                        "MYSQL_DATABASE=${project.extensions.generator.dbName}",
                        "MYSQL_ALLOW_EMPTY_PASSWORD=true",
                        "db_name=${project.extensions.generator.dbName}"
                ]
                containerConfiguration = [
                        "HostConfig": [
                                "PublishAllPorts": true
                        ]
                ]
            }
            finalizedBy project.tasks.removeDatabaseContainer
        }
    }

    protected DockerStopTask stopDatabaseTask(Project project) {
        project.tasks.create("stopDatabase", DockerStopTask) {
            project.afterEvaluate {
                containerId = project.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
        }
    }

    protected DockerRmTask removeDatabaseContainerTask(Project project) {
        project.tasks.create("removeDatabaseContainer", DockerRmTask) {
            project.afterEvaluate {
                containerId = project.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
            dependsOn project.tasks.stopDatabase
        }
    }

    protected DockerInspectContainerTask databaseConnectionDetailsTask(Project p) {
        p.tasks.create("databaseConnectionDetails", DockerInspectContainerTask) {
            p.afterEvaluate {
                containerId = p.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
            dependsOn p.tasks.startDatabase

            doLast {
                def port = containerInfo.content.NetworkSettings.Ports["3306/tcp"][0].HostPort
                def jdbcUrl = "jdbc:mysql://${p.extensions.generator.userName}:${p.extensions.generator.userName}@localhost:${port}/" +
                        "${p.extensions.generator.dbName}?user=${p.extensions.generator.userName}&nullNamePatternMatchesAll=true"
                project.project("${project.extensions.generator.migrationProjectName}".toString()).ext['dbUrl'] = jdbcUrl
                project.extensions.generator.dbUrl = jdbcUrl
                project.extensions.generator.port = port
            }
        }
    }

    protected Exec waitForDatabaseAvailableTask(Project project) {
        project.tasks.create("waitForDatabaseAvailable", Exec) {
            project.afterEvaluate {
                doFirst {
                    commandLine 'bash', '-c', "while ! curl -s localhost:${project.extensions.generator.port}; do sleep 1; done > /dev/null"
                }
            }
            dependsOn project.tasks.databaseConnectionDetails
        }
    }

    protected JavaExec generateSlickSourcesTask(Project project) {
        project.tasks.create("generateSlickSources", JavaExec) {
            project.afterEvaluate {
                outputs.dir generatedSourcesFolder
                main = project.extensions.generator.customCodeGenerator
                doFirst {
                    args = [
                            project.extensions.generator.slickProfile,
                            project.extensions.generator.jdbcDriver,
                            project.extensions.generator.dbUrl,
                            generatedSourcesFolder,
                            project.extensions.generator.pkg
                    ]
                }
                classpath project.configurations.codegen
                project.tasks.generateSlickSources.dependsOn "${project.extensions.generator.migrationProjectName}:flywayMigrate"
                project.tasks.getByPath("${project.extensions.generator.migrationProjectName}:flywayMigrate").dependsOn project.tasks.waitForDatabaseAvailable
                project.tasks.removeDatabaseContainer.mustRunAfter project.tasks.generateSlickSources
                project.tasks.compileScala.dependsOn project.tasks.generateSlickSources
            }
        }
    }

    protected Delete codegenCleanTask(Project project) {
        project.tasks.create("codegenClean", Delete) {
            project.afterEvaluate {
                delete generatedSourcesFolder
            }
        }
        project.tasks.clean.dependsOn project.tasks.codegenClean
    }

}