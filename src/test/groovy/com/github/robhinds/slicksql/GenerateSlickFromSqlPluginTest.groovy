package com.github.robhinds.slicksql

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test


class GenerateSlickFromSqlPluginTest extends GroovyTestCase {

    def plugin = new GenerateSlickFromSqlPlugin()

    @Test void testAddCodeGenConfig() {
        Project p = new ProjectBuilder().build()
        plugin.addCodeGenPhase(p)
        assertNotNull(p.configurations.getByName("codegen"))
    }

//    @Test void testAddDbStopTask() {
//        Project p = new ProjectBuilder().build()
//        p.extensions.generator = {
//            dockerImageName = "some-docker-image"
//            migrationProjectName = ':another-project'
//            userName = "db-user"
//            dbName = "dv-name"
//            pkg = "some.test.package"
//            customCodeGenerator = 'slick.codegen.SourceCodeGenerator'
//            jdbcDriver = "com.mysql.cj.jdbc.Driver"
//            slickProfile = "slick.jdbc.MySQLProfile"
//            dockerContainerName = "codegen-db"
//            localDockerHost = "https://localhost:2376"
//        }
//        plugin.dockerDbStopTask(p)
//        assertNotNull p.getTasksByName("dockerDbStop", true)
//    }

//    dockerDbStopTask(project)
//    dockerDbRmTask(project)
//    dockerDbStartTask(project)
//    dockerDbGetInfoTask(project)
//    waitForDockerDbTask(project)
//    generateSlickSourcesTask(project)
//    codegenCleanTask(project)

}
