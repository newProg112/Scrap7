package com.example.scrap7

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    val routes: List<Route>
)

data class Route(
    @SerializedName("overview_polyline")
    val overview_polyline: OverviewPolyline
)

data class OverviewPolyline(
    val points: String
)