package com.engboost.server

import com.engboost.server.modules.ModuleRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }

    val registry = ModuleRegistry()

    routing {
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        get("/api/v1/modules/active") {
            call.respond(registry.activeManifest())
        }

        get("/api/v1/modules/{moduleId}/{version}/artifact") {
            val moduleId = call.parameters["moduleId"]
            val version = call.parameters["version"]?.toIntOrNull()
            val artifact = registry.artifactFile(moduleId, version)

            if (artifact == null) {
                call.respondText("Module artifact not found", status = HttpStatusCode.NotFound)
            } else {
                call.respondFile(artifact)
            }
        }
    }
}
