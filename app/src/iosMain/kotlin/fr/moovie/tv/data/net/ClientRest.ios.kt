package fr.moovie.tv.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual val clientRest: HttpClient = HttpClient(Darwin) {
    expectSuccess = false
}
