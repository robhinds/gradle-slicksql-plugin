package com.github.robhinds.slicksql

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
        dockerDbStopTask(project)
        dockerDbRmTask(project)
        dockerDbStartTask(project)
        dockerDbGetInfoTask(project)
        waitForDockerDbTask(project)
        generateSlickSourcesTask(project)
        codegenCleanTask(project)
    }

    protected addCodeGenPhase(Project project) {
        project.configurations { codegen }
    }

    protected addDependencies(Project project) {
        project.dependencies.add("codegen", "org.scala-lang:scala-reflect:2.11.8")
        project.dependencies.add("codegen", "com.typesafe.slick:slick_2.11:3.2.0")
        project.dependencies.add("codegen", "com.typesafe.slick:slick-codegen_2.11:3.2.0")
        project.dependencies.add("codegen", "com.typesafe.slick:slick-hikaricp_2.11:3.2.0")
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

    protected DockerRunTask dockerDbStartTask(Project project) {
        project.tasks.create("dockerDbStart", DockerRunTask) {
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
            finalizedBy project.tasks.dockerDbRm
        }
    }

    protected DockerStopTask dockerDbStopTask(Project project) {
        project.tasks.create("dockerDbStop", DockerStopTask) {
            project.afterEvaluate {
                containerId = project.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
        }
    }

    protected DockerRmTask dockerDbRmTask(Project project) {
        project.tasks.create("dockerDbRm", DockerRmTask) {
            project.afterEvaluate {
                containerId = project.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
            dependsOn project.tasks.dockerDbStop
        }
    }

    protected DockerInspectContainerTask dockerDbGetInfoTask(Project p) {
        p.tasks.create("dockerDbGetInfo", DockerInspectContainerTask) {
            p.afterEvaluate {
                containerId = p.extensions.generator.dockerContainerName
                dockerHost = project.extensions.generator.localDockerHost
            }
            dependsOn p.tasks.dockerDbStart

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

    protected Exec waitForDockerDbTask(Project project) {
        project.tasks.create("waitForDockerDb", Exec) {
            project.afterEvaluate {
                doFirst {
                    commandLine 'bash', '-c', "while ! curl -s localhost:${project.extensions.generator.port}; do sleep 1; done > /dev/null"
                }
            }
            dependsOn project.tasks.dockerDbGetInfo
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
                project.tasks.getByPath("${project.extensions.generator.migrationProjectName}:flywayMigrate").dependsOn project.tasks.waitForDockerDb
                project.tasks.dockerDbRm.mustRunAfter project.tasks.generateSlickSources
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