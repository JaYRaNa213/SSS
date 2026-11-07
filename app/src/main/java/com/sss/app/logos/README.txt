SUNS APP LOGO ASSETS

Source:
- master/suns-logo-master-1024.png

ANDROID (Jetpack / Android Studio)
1. Copy the contents of android/app/src/main/res/ into your app's:
   app/src/main/res/
2. In AndroidManifest.xml, use:
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher_round"
3. Adaptive icon XML and foreground/background files are included.
4. Play Store upload icon: android/play-store/play-store-icon-512.png

IOS (Xcode)
1. Copy ios/Assets.xcassets/AppIcon.appiconset into your project's
   Assets.xcassets folder.
2. In Xcode, select the target > General > App Icons and Launch Screen,
   then choose AppIcon as the App Icon source.
3. The 1024px icon is included. The image has a black background, so it is
   opaque and suitable for store icon use.

WEB / PWA
- Use web/favicon-32.png as favicon (and favicon-16.png if needed).
- Use web/apple-touch-icon.png for iOS home-screen web clips.
- Use web/pwa-192.png and web/pwa-512.png in your web app manifest.

LAUNCH / SPLASH
- launch/splash-logo-1024.png is a square logo image. Place it centered on
  your splash screen over a black background; do not stretch it to fill
  the entire screen.

IMPORTANT
- These are generated raster PNG assets. Keep the master PNG as the source
  for future resizing.
- Android launcher and store icons are separate from splash-screen layouts.
- For a production Android 12+ splash screen, use the Android SplashScreen
  API and configure the window splash background/icon in your theme.
