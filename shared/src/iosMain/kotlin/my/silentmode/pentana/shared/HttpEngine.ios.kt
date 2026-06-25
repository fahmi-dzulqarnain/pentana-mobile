package my.silentmode.pentana.shared

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

// Referencing Darwin here keeps the linker from stripping the engine from the static framework.
internal actual fun defaultHttpEngine(): HttpClientEngine = Darwin.create()
