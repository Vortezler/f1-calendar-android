package com.praval.f1calendar.data.remote

import com.praval.f1calendar.data.remote.dto.RaceResponse
import com.praval.f1calendar.data.remote.dto.StandingsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Jolpica-F1, the drop-in successor to the retired Ergast API. No key required.
 *
 * `season` is a string rather than an int so callers can pass the literal "current", which lets the
 * server decide which season is live instead of guessing from the device clock around New Year.
 *
 * Jolpica caps `limit` at 100 per page. A full season is 22–24 rounds and a full classification is
 * ~20 rows, so every call here fits in a single page.
 */
interface ErgastApi {

    @GET("{season}.json")
    suspend fun schedule(
        @Path("season") season: String,
        @Query("limit") limit: Int = 100,
    ): RaceResponse

    @GET("{season}/{round}/results.json")
    suspend fun raceResults(
        @Path("season") season: String,
        @Path("round") round: Int,
        @Query("limit") limit: Int = 100,
    ): RaceResponse

    @GET("{season}/{round}/qualifying.json")
    suspend fun qualifyingResults(
        @Path("season") season: String,
        @Path("round") round: Int,
        @Query("limit") limit: Int = 100,
    ): RaceResponse

    @GET("{season}/driverstandings.json")
    suspend fun driverStandings(
        @Path("season") season: String,
        @Query("limit") limit: Int = 100,
    ): StandingsResponse

    @GET("{season}/constructorstandings.json")
    suspend fun constructorStandings(
        @Path("season") season: String,
        @Query("limit") limit: Int = 100,
    ): StandingsResponse

    companion object {
        const val BASE_URL = "https://api.jolpi.ca/ergast/f1/"
        const val CURRENT = "current"
    }
}
