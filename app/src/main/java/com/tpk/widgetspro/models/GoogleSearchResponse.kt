package com.tpk.widgetspro.models

import com.google.gson.annotations.SerializedName

data class GoogleSearchResponse(
    @SerializedName("items") val items: List<GoogleSearchItem>?
) {
    data class GoogleSearchItem(
        @SerializedName("link") val link: String
    )
}