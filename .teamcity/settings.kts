import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.projectFeatures.buildReportTab
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2023.05"

project {
    description = "Contains all other projects"

    params {
        password("TOKEN", "credentialsJSON:45c033b7-1df4-491d-b734-c1cc493c2917", display = ParameterDisplay.HIDDEN)
    }

    features {
        buildReportTab {
            id = "PROJECT_EXT_1"
            title = "Code Coverage"
            startPage = "coverage.zip!index.html"
        }
    }

    cleanup {
        baseRule {
            preventDependencyCleanup = false
        }
    }

    subProject(MusionFrontend)
    subProject(MusionBackend)
    subProject(Sandbox)
}


object MusionBackend : Project({
    name = "MusionBackend"

    vcsRoot(MusionBackend_HttpsGithubComAliakseizhyrauliouMusicPlayerGitRefsHeadsDev)

    buildType(MusionBackend_Deploy)
    buildType(MusionBackend_Build)
    buildType(MusionBackend_RunBackendInDocker)

    template(MusionBackend_TriggerViaApi)

    params {
        text("TAG_NAME", "latest", label = "Tag", description = "Tag for docker image", allowEmpty = false)
    }
})

object MusionBackend_Build : BuildType({
    name = "Build"

    params {
        checkbox("DEPLOY", "false", label = "Deploy Docker Image",
                  checked = "true", unchecked = "false")
        checkbox("RUN_APP", "false", label = "Run App in Docker",
                  checked = "true", unchecked = "false")
    }

    vcs {
        root(MusionBackend_HttpsGithubComAliakseizhyrauliouMusicPlayerGitRefsHeadsDev)

        checkoutDir = "backend"
    }

    steps {
        script {
            name = "Install dependencies"
            scriptContent = "npm install"
        }
        script {
            name = "Build App"
            scriptContent = "npm run build"
        }
        script {
            name = "Build Image"
            scriptContent = "docker build -t aliakseizhurauliou/musion-backend:%TAG_NAME% ."
        }
        script {
            name = "Trigger Deploy"
            scriptContent = """
                trigger_value="%DEPLOY%"
                
                # Проверить, установлено ли значение trigger в "true"
                if [ "${'$'}trigger_value" = "true" ]; then
                    echo "Trigger is set to true. Starting Deploy."
                
                	curl -H "Authorization: Bearer %TOKEN%" -X POST "http://8.217.195.189:8111/app/rest/buildQueue" --data "<build><buildType id='MusionBackend_Deploy'/></build>" -H "Content-Type: application/xml"
                
                else
                    echo "Trigger is not set to true. Skipping Deploy."
                fi
            """.trimIndent()
        }
        script {
            name = "Trigger Run App"
            scriptContent = """
                trigger_value="%RUN_APP%"
                
                # Проверить, установлено ли значение trigger в "true"
                if [ "${'$'}trigger_value" = "true" ]; then
                    echo "Trigger is set to true. Starting perform Docker."
                
                	curl -H "Authorization: Bearer %TOKEN%" -X POST "http://8.217.195.189:8111/app/rest/buildQueue" --data "<build><buildType id='MusionBackend_RunBackendInDocker'/></build>" -H "Content-Type: application/xml"
                
                else
                    echo "Trigger is not set to true. Skipping to runnigng Docker."
                fi
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            branchFilter = "+:dev"
        }
    }

    features {
        perfmon {
        }
    }
})

object MusionBackend_Deploy : BuildType({
    name = "Deploy"
    description = "Deploy to docker registry"

    steps {
        dockerCommand {
            name = "Push Image"
            commandType = push {
                namesAndTags = "aliakseizhurauliou/musion-backend:%TAG_NAME%"
            }
        }
    }

    triggers {
        finishBuildTrigger {
            enabled = false
            buildType = "${MusionBackend_Build.id}"
            successfulOnly = true
            branchFilter = "+:dev"

            buildParams {
                checkbox("RUN_DEPLOY", "false", label = "Deploy", description = "Is trigger deploy after job",
                          checked = "true", unchecked = "false")
            }
        }
    }

    dependencies {
        snapshot(MusionBackend_Build) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
})

object MusionBackend_RunBackendInDocker : BuildType({
    name = "Run Backend In Docker"
    description = "Running backend in docker"

    params {
        checkbox("RUN_POSTGRES", "false", label = "Run postgres database", description = "Is run postgres database",
                  checked = "true", unchecked = "false")
        checkbox("RUN_BACKEND", "false", label = "Run backend", description = "Is run backend",
                  checked = "true", unchecked = "false")
        checkbox("RUN_ALL", "false", label = "Run all services", description = "Is run all services",
                  checked = "true", unchecked = "false")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutDir = "MusionTemplates"
    }

    steps {
        script {
            name = "Run Backend in Docker"
            scriptContent = "docker compose up -d backend"
        }
    }

    triggers {
        finishBuildTrigger {
            enabled = false
            buildType = "${MusionBackend_Deploy.id}"
            successfulOnly = true
            branchFilter = "+:dev"

            buildParams {
                checkbox("RUN_APP", "false", label = "Run app", description = "Is run app after deploy",
                          checked = "true", unchecked = "false")
            }
        }
    }

    dependencies {
        snapshot(MusionBackend_Build) {
            reuseBuilds = ReuseBuilds.NO
        }
        snapshot(MusionBackend_Deploy) {
            reuseBuilds = ReuseBuilds.NO
        }
    }
})

object MusionBackend_TriggerViaApi : Template({
    name = "Trigger VIA API"
    description = "Trigger builds via rest API"

    params {
        text("BUILD_ID", "", label = "Build ID", allowEmpty = false)
        text("BUILD_NAME", "", label = "Build Name", allowEmpty = false)
        text("PARAM", "", allowEmpty = false)
    }

    steps {
        script {
            name = "Trigger Build"
            id = "RUNNER_10"
            scriptContent = """
                trigger_value="%PARAM%"
                
                # Проверить, установлено ли значение trigger в "true"
                if [ "${'$'}trigger_value" = "true" ]; then
                    echo "Trigger is set to true. Starting %BUILD_NAME%."
                
                	curl -H "Authorization: Bearer %TOKEN%" -X POST "http://8.217.195.189:8111/app/rest/buildQueue" --data "<build><buildType id='%BUILD_ID%'/></build>" -H "Content-Type: application/xml"
                
                else
                    echo "Trigger is not set to true. Skipping %BUILD_NAME%."
                fi
            """.trimIndent()
        }
    }
})

object MusionBackend_HttpsGithubComAliakseizhyrauliouMusicPlayerGitRefsHeadsDev : GitVcsRoot({
    name = "https://github.com/aliakseizhyrauliou/music_player.git#refs/heads/dev"
    url = "https://github.com/aliakseizhyrauliou/music_player.git"
    branch = "refs/heads/dev"
    branchSpec = "refs/heads/*"
    authMethod = password {
        userName = "aliakseizhyrauliou"
        password = "credentialsJSON:8f9e34c2-4d10-4dfd-a7fc-e23412a0020e"
    }
})


object MusionFrontend : Project({
    name = "MusionFrontend"
    description = "Build frontend app"
})


object Sandbox : Project({
    name = "Sandbox"
    description = "Technical Build Configs"

    template(Sandbox_TriggerViaApi)
})

object Sandbox_TriggerViaApi : Template({
    name = "Trigger VIA API"
    description = "Trigger builds via rest API"

    params {
        text("BUILD_ID", "", label = "Build ID", allowEmpty = false)
        text("BUILD_NAME", "", label = "Build Name", allowEmpty = false)
        text("PARAM", "", allowEmpty = false)
    }

    steps {
        script {
            name = "Trigger Build"
            id = "RUNNER_10"
            scriptContent = """
                trigger_value="%PARAM%"
                
                # Проверить, установлено ли значение trigger в "true"
                if [ "${'$'}trigger_value" = "true" ]; then
                    echo "Trigger is set to true. Starting %BUILD_NAME%."
                
                	curl -H "Authorization: Bearer %TOKEN%" -X POST "http://8.217.195.189:8111/app/rest/buildQueue" --data "<build><buildType id='%BUILD_ID%'/></build>" -H "Content-Type: application/xml"
                
                else
                    echo "Trigger is not set to true. Skipping %BUILD_NAME%."
                fi
            """.trimIndent()
        }
    }
})
