package com.logisparktech.doctrack.platform

import com.logisparktech.doctrack.core.notification.NotificationNavigator
import com.logisparktech.doctrack.core.notification.NotificationScheduler
import com.logisparktech.doctrack.core.notification.ScheduledNotification
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val ID_PREFIX = "doctrack-"
private const val KEY_DOCUMENT_ID = "documentId"

/**
 * iOS keeps at most 64 pending local notifications per app, so only the soonest are scheduled;
 * the list is refreshed whenever data changes or the app starts. Pending notifications survive reboots.
 */
class IosNotificationScheduler : NotificationScheduler {
    override val maxPending: Int = 60
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun hasPermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            val ok = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional ||
                status == UNAuthorizationStatusEphemeral
            if (cont.isActive) cont.resume(ok)
        }
    }

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) {
        val existing: List<String> = suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                val ids = requests.orEmpty().filterIsInstance<UNNotificationRequest>()
                    .map { it.identifier }.filter { it.startsWith(ID_PREFIX) }
                if (cont.isActive) cont.resume(ids)
            }
        }
        center.removePendingNotificationRequestsWithIdentifiers(existing)

        val calendar = NSCalendar.currentCalendar
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute
        notifications.forEach { n ->
            val content = UNMutableNotificationContent().apply {
                setTitle(n.title)
                setBody(n.body)
                setSound(UNNotificationSound.defaultSound)
                setUserInfo(mapOf<Any?, Any?>(KEY_DOCUMENT_ID to NSNumber(longLong = n.documentId)))
            }
            val date = NSDate.dateWithTimeIntervalSince1970(n.triggerAtMillis / 1000.0)
            val components = calendar.components(units, fromDate = date)
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier("$ID_PREFIX${n.id}", content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}

/** Shows reminders while the app is open and routes taps to the document details screen. */
object IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    fun install() {
        UNUserNotificationCenter.currentNotificationCenter().delegate = this
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val info = didReceiveNotificationResponse.notification.request.content.userInfo
        val id = (info[KEY_DOCUMENT_ID] as? NSNumber)?.longLongValue
        if (id != null && id > 0) {
            dispatch_async(dispatch_get_main_queue()) { NotificationNavigator.openDocument(id) }
        }
        withCompletionHandler()
    }
}

fun requestIosNotificationPermission(onResult: (Boolean) -> Unit) {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { granted, _ ->
        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
    }
}
