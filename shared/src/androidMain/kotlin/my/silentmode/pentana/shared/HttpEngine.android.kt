package my.silentmode.pentana.shared

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun defaultHttpEngine(): HttpClientEngine = OkHttp.create()
