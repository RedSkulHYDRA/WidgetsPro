package com.tpk.widgetspro.services

import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill

class BaseWifiDataUsageWidgetService : BaseUsageWidgetUpdateService() {
    override val intervalKey = "wifi_data_usage_interval"
    override val widgetProviderClass = BaseWifiDataUsageWidgetProvider::class.java

    override fun updateWidgets() {
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderCircle::class.java)
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderPill::class.java)
    }
}