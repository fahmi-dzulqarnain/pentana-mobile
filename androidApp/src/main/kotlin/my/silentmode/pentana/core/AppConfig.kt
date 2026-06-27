package my.silentmode.pentana.core

object AppConfig {
    // Mirror iosApp AppConfig.baseURL. Update IP per network (`ipconfig getifaddr en0`);
    // swap to https://pentana.silentmode.net/api/v1 for a release build.
    const val BASE_URL = "http://192.168.0.177:8000/api/v1"
}
