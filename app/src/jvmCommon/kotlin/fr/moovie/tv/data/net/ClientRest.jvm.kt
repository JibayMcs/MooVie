package fr.moovie.tv.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual val clientRest: HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
}
