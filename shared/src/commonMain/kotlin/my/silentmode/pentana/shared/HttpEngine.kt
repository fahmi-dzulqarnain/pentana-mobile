package my.silentmode.pentana.shared

import io.ktor.client.engine.HttpClientEngine

/** The platform's default Ktor engine (Darwin on iOS, OkHttp on Android). */
internal expect fun defaultHttpEngine(): HttpClientEngine
