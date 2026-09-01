package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway

/**
 * Le plafond par appel est posé dans `clientMoovieIos()` via `HttpTimeout`,
 * là où OkHttp le pose par `callTimeout` : `requestTimeoutMillis` borne la
 * requête entière, corps compris, ce qui est la même garantie.
 */
actual val passerelleSources: HttpGateway = KtorGateway(clientMoovieIos())
