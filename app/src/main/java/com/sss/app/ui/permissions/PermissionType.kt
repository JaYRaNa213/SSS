package com.sss.app.ui.permissions

enum class PermissionType(
    val title: String,
    val whatItDoes: String,
    val whyNeeded: String,
    val ifSkipped: String,
    val settingsHint: String
) {
    PHOTOS(
        "Photos access",
        "Lets 4S see the screenshots on your phone.",
        "4S detects each new screenshot and files it into your selected folder.",
        "Nothing can be sorted into folders.",
        "Choose \"Allow all\" (not \"Selected photos\") so every new screenshot is found."
    ),
    OVERLAY(
        "Display over other apps",
        "Shows the small floating 4S button on top of other apps.",
        "Switch or create folders while you watch a video, without opening 4S.",
        "Capture still works, but there is no floating button.",
        "On the next screen find 4S and turn on \"Allow display over other apps\"."
    ),
    NOTIFICATIONS(
        "Notifications",
        "Shows a small notification while capture is running.",
        "You can see that capture is on and stop or pause it from the notification.",
        "Capture still runs, but you will not see the notification.",
        "Allow notifications for 4S."
    ),
    BATTERY(
        "Battery: no restrictions",
        "Stops the phone from putting 4S to sleep in the background.",
        "Some phones kill background apps and screenshots would stop being saved.",
        "Capture may stop after a while on some phones.",
        "On the next screen choose \"Don't optimize\" / \"No restrictions\" for 4S."
    );

    companion object {
        const val PRIVACY = "Your screenshots never leave your phone. Text recognition (OCR) runs on your device."
    }
}