# FieldTrack Android app

Empty until **Phase 5**. The Kotlin / Jetpack Compose technician app lives here.

Planned structure once Phase 5 starts:

```
android/
  app/src/main/java/com/vyoop/fieldtrack/
    ui/         Compose screens + ViewModels (one package per screen)
    data/
      local/    Room database, DAOs, the pending-changes outbox
      remote/   Retrofit API interfaces, DTOs
      repo/     Repository -- the only thing the UI talks to
    di/         Hilt modules
    sync/       WorkManager workers
```

The architectural rule that matters most (PRD, "Android app screens"): **screens never
call the network directly.** They read from Room through the repository; WorkManager
pushes queued changes to the API. That is what makes the app work offline.
