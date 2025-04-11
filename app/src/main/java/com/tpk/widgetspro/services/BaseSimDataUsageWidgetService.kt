package com.tpk.widgetspro.services

import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill

class BaseSimDataUsageWidgetService : BaseUsageWidgetUpdateService() {
    override val intervalKey = "sim_data_usage_interval"
    override val widgetProviderClass = BaseSimDataUsageWidgetProvider::class.java

    override fun updateWidgets() {
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderCircle::class.java)
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderPill::class.java)
    }
}