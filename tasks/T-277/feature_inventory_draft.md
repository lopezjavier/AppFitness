# Feature inventory (DRAFT, T-277 Run A)

Source: `app/AppRoutes.kt` route names plus `planning/epics` story titles. Not re-verified screen by screen; the PM/planner confirms. Mark: REMOVE = proposed for removal by T-277, else keep.

## Inicio / shell
- Sign-in with Google, welcome screen (`WelcomeScreen`) - US-05
- Biometric onboarding (`OnboardingScreen`) - US-06
- Navigable shell, 3 tabs + Profile, adaptive layout (`DiaryGraph`, `WorkoutGraph`, `TrainerGraph`, `ProfileGraph`) - US-04, US-46
- Design-system catalogue (`CatalogRoute`, dev only) - US-03, US-56
- Daily automatic sync on first open - US-60

## Diario
- Daily diary by comida, edit/delete (`DiaryHome`, `DiaryDayRoute`, `EditEntryRoute`) - US-19, US-55
- Calorie and macro dashboard - US-20
- Food search and logging (`FoodSearchRoute`, `AddFoodRoute`) - US-17, US-121, US-122
- Barcode scan, manual food creation, editing (`CreateFoodRoute`, `EditFoodRoute`) - US-18, US-47, US-135
- Food from a photo (AI estimate), label/barcode photos, portion estimate - US-48, US-49, US-54
- Log a meal by typing it - US-132
- Food portions, cooking methods, OFF full data - US-22, US-50, US-16b, US-118, US-119
- Saved meals/templates (`SaveTemplateRoute`, `TemplateDetailsRoute`) - US-21
- Custom meal structure and defaults (`MealSlotsRoute`, `MealDefaultsRoute`) - US-23, US-51
- Diary history and summaries (`DiaryHistoryRoute`) - US-24
- Energy-check block on foods whose macros do not match kcal - US-116

## Peso
- Manual weight and measurements (`WeightRoute`) - US-25
- Metrics panel, goal, trend chart (`WeightHistoryRoute`) - US-27
- Health Connect sync (manual) and watch data (`ActivityRoute`) - US-26, US-61..US-64
- Energy strategy and calorie target, macro sliders (Preferences/goals) - US-07, US-08

## Entreno
- Exercise catalogue, detail, picker, favourites (`ExercisePickerRoute`, `ExerciseDetailRoute`) - US-09, US-28, US-96
- Ask about an exercise (chat tab) - US-130
- Routine builder, folders, next workout (`RoutineBuilderRoute`, `RoutineDetailsRoute`) - US-10, US-35, US-36
- Live session, rest timer, ghost data, set types, RPE, warm-up calculator (`LiveSessionRoute`) - US-11..US-13, US-29..US-33, US-38
- Rest-end alert with screen off - US-64 (E11)
- Notes per set and per routine exercise - US-66
- Discomfort log per exercise (`DiscomfortSheet`) - US-67, US-108
- History, session detail, calendar, editing (`HistoryRoute`, `SessionDetailRoute`, `EditWorkoutRoute`, `PastWorkoutRoute`) - US-14, US-37
- Strength, muscle volume, stats, exercise progress (`StrengthRoute`, `MuscleVolumeRoute`, `StatsRoute`, `ExerciseProgressRoute`) - US-15, US-39..US-41

## Trainer
- Trainer home (`TrainerHome`) - US-120
- "Antes de empezar" (`BeforeStartRoute`) - US-78
- "Para conocerte" (`YourDataRoute`, `IntakeRoute`): years training + maintenance kcal stay - US-81. Free-text field and its help: REMOVE (T-277, nothing reads it)
- "Lo que el entrenador sabe de ti" (`FreeTextLinesRoute`, trainer row `trainer_free_text_row`): REMOVE - US-89
- Plan proposal and accept (`PlanProposalRoute`, `TrainerPlanRoute`) - US-69, US-79, US-80, US-99, US-112..US-115
- Block creator and preview (`BlockBuilderRoute`, `BlockPreviewRoute`, `TrainerBlockRoute`) - US-72, US-93, US-94
- Folder review (`FolderBlockRoute`, `FolderReviewResultRoute`) - US-95
- Weekly summary (`WeekSummaryRoute`) - US-98, US-105
- Block review and next block (`BlockReviewRoute`) - US-101..US-103, US-110, US-111. Its "free_text_section_review" label belongs to the removed screen: REMOVE with it
- Reports and past reports (`ReportsRoute`, `PastReportRoute`) - US-129
- Progression, deload, adherence, paused week, welcome back, extend week - US-70, US-73, US-75, US-97, US-100, US-104, US-106, US-107, US-134
- Phases over the year, preferences - US-65, US-82

## Perfil
- Profile root, personal data (`ProfileHome`) - US-04, US-06
- Preferences hub and sections (`PreferencesRoute`, `PreferencesSectionRoute`) - US-65
- Weekly check-in: energy plus free note (`CheckInRoute`) - US-59. The note field: judged, see audit (shown only in its own edit form)
- Export my data - US-125
- About, version, licences (`AboutRoute`) - US-45
- Delete account / account status - (E2/E7 backend)

## Centro de avisos
- In-app notification center (`NotificationCenterRoute`) - US-57, US-109
- Weight and circumference reminders - US-58
