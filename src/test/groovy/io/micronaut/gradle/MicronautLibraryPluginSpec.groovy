package io.micronaut.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.environment.Jvm

class MicronautLibraryPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile
    File kotlinBuildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        kotlinBuildFile = testProjectDir.newFile('build.gradle.kts')
    }

    def "test JUnit 5 platform excludes work"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
                testRuntime "junit"
                processing {
                    incremental true
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            test {
                useJUnitPlatform {
                    excludeTags 'someTag'
                }
            }            
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def testJavaFile = testProjectDir.newFile("src/test/java/example/FooTest.java")
        testJavaFile.parentFile.mkdirs()
        testJavaFile << """
package example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("someTag")
class FooTest {
    
    @Test
    void testShouldFail() {
        Assertions.assertTrue(false);
    }
}
"""

        when:
        def result = runGradle('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
    }

    def "test lombok works"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
                testRuntime "junit"
                processing {
                    incremental true
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                annotationProcessor 'org.projectlombok:lombok:1.18.12'
                compileOnly 'org.projectlombok:lombok:1.18.12'
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        def testJavaFile = testProjectDir.newFile("src/test/java/example/FooTest.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import lombok.Data;
import io.micronaut.context.annotation.*;

@Data
@ConfigurationProperties("foo")
class Foo {
    private String name;
}
"""
        testJavaFile.parentFile.mkdirs()
        testJavaFile << """
package example;

import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.context.annotation.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name="foo.name", value="Good")
class FooTest {
    @javax.inject.Inject
    Foo foo;
    
    @Test
    void testLombokValue() {
        Assertions.assertEquals("Good", foo.getName());
    }
}
"""

        when:
        def result = runGradle('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }

    def "test add jaxrs processing"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("io.micronaut.jaxrs:micronaut-jaxrs-server")
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/foo")
public class Foo {

    @GET
    @Path("/")
    public String index() {
        return "Example Response";
    }
}
"""

        when:
        def result = runGradle('assemble', "--stacktrace")

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test add openapi processing"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
                
                processing {
                    incremental true
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                annotationProcessor "io.micronaut.configuration:micronaut-openapi"
                compileOnly "io.swagger.core.v3:swagger-annotations"
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;

@javax.inject.Singleton
@OpenAPIDefinition(
    info = @Info(
            title = "demo",
            version = "0.0"
    )
)
class Foo {}
"""

        when:
        def result = runGradle('assemble')

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        result.output.contains("Generating OpenAPI Documentation")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply defaults for micronaut-library and java"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
                
                processing {
                    incremental true
                }
            }
            
            repositories {
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('assemble')

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/main/example/$FooDefinition.class'
        ).exists()
    }

    @IgnoreIf({ jvm.java16Compatible }) // https://youtrack.jetbrains.com/issue/KT-45545
    def "test apply defaults for micronaut-library and kotlin with kotlin DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("1.4.32")
                id("org.jetbrains.kotlin.kapt") version("1.4.32")
                id("org.jetbrains.kotlin.plugin.allopen") version("1.4.32")
                id("io.micronaut.library")
            }
            
            micronaut {
                version("2.3.3")
                processing {
                    incremental(true)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/main/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('assemble')

        println result.output
        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }

    @IgnoreIf({ jvm.java16Compatible }) // https://youtrack.jetbrains.com/issue/KT-45545
    def "test custom sourceSet for micronaut-library and kotlin with kotlin DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile.delete()
        kotlinBuildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version("1.4.32")
                id("org.jetbrains.kotlin.kapt") version("1.4.32")
                id("org.jetbrains.kotlin.plugin.allopen") version("1.4.32")
                id("io.micronaut.library")
            }
            
            sourceSets {
                val custom by creating {
                    compileClasspath += sourceSets["main"].output
                    runtimeClasspath += sourceSets["main"].output
                }
            }            
            micronaut {
                version("2.3.3")
                processing {
                    incremental(true)
                    sourceSets(
                        sourceSets["custom"]        
                    )                    
                }
            }            
            
            repositories {
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "custom", "kotlin", "example")
        def javaFile = testProjectDir.newFile("src/custom/kotlin/example/Foo.kt")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('compileCustomKotlin')

        println result.output
        then:
        result.task(":compileCustomKotlin").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }

    def "test custom sourceset micronaut-library and java"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            sourceSets {
                custom {
                    java {
                        srcDirs("src/custom/java")
                    }
                }
            }            
            micronaut {
                version "2.3.3"
                processing {
                    incremental true
                    sourceSets(
                        sourceSets.custom        
                    )                    
                }
            }
            
            repositories {
                mavenCentral()
            }

            
        """
        testProjectDir.newFolder("src", "custom", "java", "example")
        def javaFile = testProjectDir.newFile("src/custom/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('compileCustomJava')

        then:
        result.task(":compileCustomJava").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
        new File(
                testProjectDir.getRoot(),
                'build/classes/java/custom/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply junit 5 platform is junit jupiter is present"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation("io.micronaut.test:micronaut-test-junit5")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")            
            }
            
        """
        testProjectDir.newFolder("src", "test", "java", "example")
        def javaFile = testProjectDir.newFile("src/test/java/example/Foo.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.context.BeanContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import javax.inject.Inject;

@MicronautTest
class Foo {
    @Inject
    BeanContext context;

    @Test
    void testItWorks() {
        Assertions.assertTrue(context.isRunning());
    }
}
"""

        when:
        def result = runGradle('test')

        then:
        result.task(":test").outcome == TaskOutcome.SUCCESS
        result.output.contains("Creating bean classes for 1 type elements")
    }

    def "test custom sourceSets for micronaut-library and groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
                id "groovy"
            }
            
            sourceSets {
                custom {
                    groovy {
                        srcDirs("src/custom/groovy")
                    }
                }
            }                    
            micronaut {
                version "2.3.3"
                processing {
                    incremental true
                    sourceSets(
                        sourceSets.custom        
                    )                    
                }                
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                customImplementation("org.codehaus.groovy:groovy")
            }
        """
        testProjectDir.newFolder("src", "custom", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/custom/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('compileCustomGroovy')

        then:
        result.task(":compileCustomGroovy").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/custom/example/Foo.class'
        ).exists()
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/custom/example/$FooDefinition.class'
        ).exists()
    }

    def "test apply defaults for micronaut-library and groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.library"
                id "groovy"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            repositories {
                mavenCentral()
            }
            
        """
        testProjectDir.newFolder("src", "main", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/main/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

@javax.inject.Singleton
class Foo {}
"""

        when:
        def result = runGradle('assemble')

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/main/example/$FooDefinition.class'
        ).exists()
    }

    def "test add openapi processing - groovy"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "groovy"
                id "io.micronaut.library"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compileOnly "io.micronaut.configuration:micronaut-openapi"
                compileOnly "io.swagger.core.v3:swagger-annotations"
            }
            
        """
        testProjectDir.newFolder("src", "main", "groovy", "example")
        def javaFile = testProjectDir.newFile("src/main/groovy/example/Foo.groovy")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example

import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.info.*

@javax.inject.Singleton
@OpenAPIDefinition(
    info = @Info(
            title = "demo",
            version = "0.0"
    )
)
class Foo {}
"""

        when:
        def result = runGradle('assemble')

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        new File(
                testProjectDir.getRoot(),
                'build/classes/groovy/main/example/$FooDefinition.class'
        ).exists()
        result.output.contains("Generating OpenAPI Documentation")
    }

    private BuildResult runGradle(String... args) {
        DefaultGradleRunner runner = ((DefaultGradleRunner) GradleRunner.create())
        if (Jvm.current.java16Compatible) {
            runner.withJvmArguments(
                    '--illegal-access=permit',
                    '--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED'
            )
        }
        runner.withProjectDir(testProjectDir.root)
              .withArguments(args)
              .withPluginClasspath()
              .build()
    }
}
