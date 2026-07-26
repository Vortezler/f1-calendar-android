package com.praval.f1calendar.data.live

import com.praval.f1calendar.data.live.dto.OpenF1DriverDto
import com.praval.f1calendar.data.live.dto.OpenF1IntervalDto
import com.praval.f1calendar.data.live.dto.OpenF1LapDto
import com.praval.f1calendar.data.live.dto.OpenF1PositionDto
import com.praval.f1calendar.data.live.dto.OpenF1SessionDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * [OpenF1](https://openf1.org) — open live and historical F1 timing. No key, no account.
 *
 * The comparison filters really are query *keys* containing operators (`date>=`), which is how
 * OpenF1 models range queries. Retrofit percent-encodes them and the server decodes them back.
 *
 * Every endpoint returns the full matching history, so callers must bound requests with a
 * timestamp filter — an unbounded `intervals` call for a whole grand prix is megabytes.
 */
interface OpenF1Api {

    @GET("v1/sessions")
    suspend fun sessions(
        @Query("session_key") sessionKey: String = LATEST,
    ): List<OpenF1SessionDto>

    @GET("v1/drivers")
    suspend fun drivers(
        @Query("session_key") sessionKey: Int,
    ): List<OpenF1DriverDto>

    @GET("v1/position")
    suspend fun positions(
        @Query("session_key") sessionKey: Int,
        @Query("date>=") since: String? = null,
    ): List<OpenF1PositionDto>

    @GET("v1/intervals")
    suspend fun intervals(
        @Query("session_key") sessionKey: Int,
        @Query("date>=") since: String? = null,
    ): List<OpenF1IntervalDto>

    @GET("v1/laps")
    suspend fun laps(
        @Query("session_key") sessionKey: Int,
        @Query("date_start>=") since: String? = null,
    ): List<OpenF1LapDto>

    companion object {
        const val BASE_URL = "https://api.openf1.org/"
        const val LATEST = "latest"
    }
}
