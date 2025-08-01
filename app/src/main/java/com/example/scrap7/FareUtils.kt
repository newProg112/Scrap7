package com.example.scrap7

fun calculateFare(distanceKm: Float): Double {
    val baseFare = 2.50        // Flat starting price
    val costPerKm = 1.25       // Cost per kilometer
    return baseFare + (costPerKm * distanceKm)
}