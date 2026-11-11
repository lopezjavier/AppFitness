# Feature inventory

Updated by T-287 on 2026-11-11 after the T-277 cleanup; a task that adds or removes a functionality updates this file.

Verified on 2026-11-11 against the repo: 50 route registrations in `composeApp/src/commonMain/kotlin/io/github/lopezjavier/appfitness/app/AppNavHost.kt` (`composable<` and `dialog<`, one per route type in [`AppRoutes.kt`](../composeApp/src/commonMain/kotlin/io/github/lopezjavier/appfitness/app/AppRoutes.kt); `CatalogRoute` only when `catalogAvailable`); 8 Edge Functions in `supabase/functions/`; no live `pg_cron` job; every RPC named here is defined by a migration in `supabase/migrations/`. Stories are the owner of each functionality by its title in [`epics/`](epics/); `-` means no story. Items that are not built yet (for example US-125, export my data, E20) are not listed. Removed items are not listed either: [ADR-107](00-decisions.md) holds that history.

Columns: functionality, label as shown in the app (Spanish), route type or host, backend (RPC, table or Edge Function), story.

## Inicio

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Sign in with Google | "Continuar con Google" | `WelcomeScreen` (outside the nav graph) | Edge Function `account-status` | US-05 |
| Biometric onboarding (goals, measures) | "Objetivos", "Medidas" | `OnboardingScreen` (outside the nav graph) | - | US-06 |
| Shell: 4 tabs with adaptive layout | "Diario", "Entreno", "Trainer", "Perfil" | graphs `DiaryGraph`, `WorkoutGraph`, `TrainerGraph`, `ProfileGraph` | - | US-04, US-46 |
| Daily automatic sync on the first open | - | background, on the Diario tab | RPCs `mark_health_auto_sync`, `import_health_activity`, `import_health_measurements` | US-60 |
| Design-system catalogue (dev builds only) | "Catálogo de componentes" | `CatalogRoute` | - | US-03, US-56 |

## Diario

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Daily diary by comida, with the calorie and macro dashboard | "Diario" | `DiaryHome` | RPC `food_diary_summary` | US-19, US-20 |
| Day picker of the diary | "Calendario" | `DiaryDayRoute` | RPC `food_diary_summary` | US-19 |
| Edit or delete a diary entry | "Editar alimento" | dialog `EditEntryRoute` (`EditEntrySheet`) | table `food_log_entries` | US-19 |
| Diary history and weekly average | "Calorías", "Media diaria" | `DiaryHistoryRoute` | RPC `food_diary_summary` | US-24 |
| Comidas grouped in the Diario | "Comidas del día" | `DiaryHome` | - | US-55 |
| Food search and logging into a meal | "Añadir alimento" | `FoodSearchRoute`, dialog `AddFoodRoute` | Edge Function `food-search`; RPC `most_eaten_foods` | US-17, US-121, US-122 |
| Barcode scan and manual food creation | "Crear alimento", "Código de barras" | `CreateFoodRoute`; `AddFoodRoute` | Edge Function `food-barcode`; RPC `create_personal_food` | US-18 |
| Editing a manual food, including its unit when unused | "Editar alimento" | `EditFoodRoute` | RPCs `update_personal_food`, `personal_food_in_use` | US-47, US-52 |
| Food from a photo (AI estimate) and label photos | "Añadir con IA", "Estimar con IA", "Etiqueta nutricional" | `AddFoodRoute` (sheet) | Edge Function `food-photo` (modes dish, label, barcode, weight) | US-48, US-49, US-54 |
| Log a meal by describing it in text | "Describir comida" | `AddFoodRoute` (sheet) | Edge Function `food-photo` (mode text) | US-132 |
| Food portions, OFF full data and micronutrients | "Información nutricional adicional" | `CreateFoodRoute`, `EditFoodRoute`, `FoodSearchRoute` | RPC `copy_off_food` | US-22, US-16b, US-118, US-119 |
| Energy-check block on foods whose kcal and macros disagree | "Calorías y macros" | `CreateFoodRoute`, `EditFoodRoute` | SQL check `food_energy_check` inside `create_personal_food`, `update_personal_food`, `copy_off_food` | US-116 |
| Saved meals (comidas): save, open, edit | "Guardar como comida", "Comida", "Editar comida" | dialog `SaveTemplateRoute`; `TemplateDetailsRoute` | RPCs `save_meal_template`, `save_meal_as_comida` | US-21, US-53 |
| Custom meal structure | "Comidas del día" | `MealSlotsRoute` (from Perfil > Preferencias) | RPCs `save_meal_structure`, `list_meal_slots_in_use` | US-23 |
| Default comidas per meal and weekday | "Comidas por defecto", "Elige una comida" | `MealDefaultsRoute` | RPCs `save_meal_defaults`, `apply_meal_defaults` | US-51 |
| Cooking methods with added fat | "Cocina" | `PreferencesSectionRoute` | - | US-50 |

## Peso

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Manual weight and body measurements | "Peso", "Circunferencias" | `WeightRoute` (`CircumferenceSheet`) | RPCs `save_body_measurement`, `delete_body_measurement` | US-25 |
| Metrics panel, goal and trend chart | "Historial de peso", "Composición corporal" | `WeightHistoryRoute`, `WeightRoute` | - | US-27 |
| Health Connect sync (manual) and watch data | "Actividad", "Sincronizar" | `ActivityRoute` | RPCs `import_health_activity`, `import_health_measurements` | US-26, US-61, US-62, US-63, US-64 (watch data) |
| Energy strategy, calorie target and macro split | "Objetivo calórico", "Macros" | `PreferencesSectionRoute` | RPC `save_preferences` | US-07, US-08 |

## Entreno

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Exercise catalogue, picker and favourites | "Ejercicios" | `ExercisePickerRoute` | RPCs `exercise_alternatives`, `delete_custom_exercise` | US-09, US-96 |
| Exercise detail: history, records and questions | "Ejercicio", "Récords", "Preguntar" | `ExerciseDetailRoute` (tab "Preguntar" is the exercise chat) | RPCs `exercise_history`, `personal_records`, `exercise_session_records`; Edge Function `exercise-chat` | US-28, US-34, US-130 |
| Routine builder | "Rutina", "Rango de repeticiones" | `RoutineBuilderRoute` | RPC `save_routine` | US-10 |
| Routine details | "Rutina" | `RoutineDetailsRoute` | RPC `save_routine` | US-10 |
| Routine folders and next workout | "Mover «…» a…", "Revisar tus rutinas" | `WorkoutHome` (tab "Rutinas"), `RoutineDetailsRoute` | RPCs `create_routine_folder`, `rename_routine_folder`, `delete_routine_folder`, `move_routine_to_folder`, `save_routine_order`, `set_next_routine` | US-35 |
| Routine tracking and "Actualizar rutina" | "Actualizar rutina" | `LiveSessionRoute` | RPC `update_routine_from_session` | US-36 |
| Live session: rest timer, ghost data, set types, RPE, metric types, volume options | "EN VIVO", "Descanso de este ejercicio", "Tipo de serie", "RPE de la serie" | `LiveSessionRoute` (its sheets and dialogs) | RPCs `start_workout_session`, `pause_workout_session`, `resume_workout_session`, `finish_workout_session`, `discard_workout_session`, `set_session_rest`, `last_performance`, `delete_workout_set` | US-11, US-12, US-13, US-29, US-30, US-31, US-32, US-38 |
| Warm-up calculator and disc loader | "Calculadora de calentamiento", "Cargador de discos" | sheets of `LiveSessionRoute` | - | US-33 |
| Rest-end alert with the screen off | - | Android notification of the rest timer | - | US-64 (rest alert) |
| Notes per set and per routine exercise | "Nota de la serie", "Nota del ejercicio" | sheet `NoteSheet` of `LiveSessionRoute` and `RoutineBuilderRoute` | tables `workout_sets.notes`, `routine_exercises.notes` | US-66 |
| Discomfort log per exercise | "Molestia" | sheet `DiscomfortSheet` of `LiveSessionRoute` | table `exercise_discomforts`; RPC `dismiss_discomfort_note` | US-67 |
| Workout history, session detail, calendar and editing | "Historial de entrenos", "Entreno completado", "Editar entreno", "Entreno pasado" | `HistoryRoute`, `SessionDetailRoute`, `EditWorkoutRoute`, `PastWorkoutRoute` | RPC `save_workout_as_routine` | US-14, US-37 |
| Strength, muscle volume, stats and exercise progress | "Fuerza", "Volumen", "Entrenamiento", "Progreso del ejercicio" | `StrengthRoute`, `MuscleVolumeRoute`, `StatsRoute`, `ExerciseProgressRoute` | RPCs `weekly_muscle_volume`, `training_summary`, `weekly_records`, `weekly_set_counts`, `muscle_group_sets` | US-15, US-39, US-40, US-41 |

## Trainer

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Compact Trainer with the boxes "Tu plan", "Tu bloque", "Resumen semanal" | "Trainer", "Tu plan", "Tu bloque", "Resumen semanal" | `TrainerHome` | RPCs `coach_week_summary`, `coach_plan_weeks`, `coach_block_pause` | US-120 |
| Data a plan or block uses, confirmed by the user | "Antes de empezar", "Tus datos" | `BeforeStartRoute`, `YourDataRoute` | RPCs `coach_plan_inputs`, `coach_derived_estimates`, `confirm_derived_values` | US-68, US-78 |
| Years training and maintenance kcal (the "Para conocerte" data, now row editors) | "Tiempo entrenando", "Calorías de mantenimiento" | `YourDataRoute` (row editor sheets) | RPCs `save_coach_intake`, `ack_maintenance_handover` | US-81 (amended by ADR-107) |
| Plan calories, macros and outcomes: proposal and acceptance | "Plan nutricional", "Plan pendiente", "Plan activo" | `PlanProposalRoute`, `TrainerPlanRoute` | Edge Function `coach` (`plan_nutrition`); RPCs `accept_coach_plan`, `discard_coach_plan` | US-69, US-99 |
| Cardio and daily steps in the plan | "Cardio", "Pasos", "Editar el cardio" | `TrainerPlanRoute` (cardio sheet) | RPC `set_coach_plan_cardio` | US-79, US-80 |
| Calorie changes I approve | "Ajuste de calorías" | `TrainerHome` (card) | RPC `evaluate_coach_calorie_change` | US-99 |
| Block creator and preview | "Bloque de entrenamiento", "Bloque propuesto", "Bloque guardado" | `BlockBuilderRoute`, `BlockPreviewRoute`, `TrainerBlockRoute` | RPCs `save_coach_block`, `remove_coach_block` | US-72 |
| Starting a block from scratch or from my folder | "Bloque nuevo", "Desde una carpeta" | `BlockBuilderRoute`, `FolderBlockRoute` | RPC `open_coach_block_new` | US-93 |
| Removing a block without a new plan | "¿Eliminar el bloque?" | `TrainerBlockRoute` (dialog) | RPC `remove_coach_block` | US-94 |
| Reviewing a routine folder with the coach | "Revisión de «…»" | `FolderBlockRoute`, `FolderReviewResultRoute` | computed on the device (ADR-97) | US-95 |
| Weeks of the block and the "Actual" week pencil that extends it | "Semanas del bloque", "Semana N" | `TrainerBlockRoute` | RPC `set_coach_block_end` | US-134 |
| Missed week pauses the block; welcome back after a break | "Semana en pausa" | `TrainerBlockRoute`; `NotificationCenterRoute` | RPCs `coach_block_pause`, `record_paused_week_report` | US-106, US-107 |
| Loads progress on their own inside the block; progression that needs my choice | "Deshacer", "Sugerencia" | `TrainerBlockRoute`, `TrainerHome` | RPCs `progression_suggestions`, `apply_coach_progression`, `decide_progression_flag`, `undo_coach_change` | US-97, US-104, US-70 |
| Deloads on their own, requested or cancelled | "Cancelar descarga", "Hacer descarga la próxima semana" | `TrainerBlockRoute`, `TrainerHome` | RPCs `evaluate_coach_deload`, `deload_suggestions`, `request_coach_deload`, `cancel_coach_deload` | US-75, US-100 |
| Diet break in a cut | "Descanso de dieta" | `TrainerPlanRoute` | RPC `apply_coach_diet_break` | US-115 |
| Weekly summary with the training and nutrition sections, stall and nutrition diagnosis | "Resumen semanal", "¿Por qué no progreso?", "¿Por qué no avanza tu peso?" | `WeekSummaryRoute` | RPCs `coach_week_summary`, `mark_coach_week_summary_seen`, `evaluate_coach_calorie_change` | US-98, US-105, US-112, US-113, US-114 |
| End-of-block review, phase change and next block | "Revisión del bloque" | `BlockReviewRoute` | RPCs `open_coach_block_review`, `mark_coach_block_review_seen`, `keep_coach_phase`, `accept_coach_plan` | US-101, US-102, US-103 |
| Phases over the year | "Fase aceptada: …" | `PlanProposalRoute`, `BlockReviewRoute` | RPCs `coach_weeks_in_phase`, `keep_coach_phase` | US-82 |
| Goal switch with "Bloque nuevo" and a clean restart | "Bloque nuevo" | `BeforeStartRoute` (new-block mode), `BlockBuilderRoute` | RPC `open_coach_block_new` | US-110, US-111 |
| Discomfort that does not go away: the swap offer | "Ahora no" | `TrainerBlockRoute` (offer) | RPCs `coach_discomfort_followups`, `coach_swap_candidates`, `coach_swap_flagged_exercise`, `decline_discomfort_swap` | US-108 |
| Past reports and the weekly reports | "Informes", "Informe" | `ReportsRoute`, `PastReportRoute` | RPCs `list_coach_reports`, `get_coach_report` | US-129 |

## Perfil

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| Profile root and personal data | "Perfil", "Datos personales" | `ProfileHome` | - | US-04, US-06 |
| Preferences hub and sections | "Preferencias", "Entrenamiento", "Avisos", "Mi entrenamiento", "Calculadoras" | `PreferencesRoute`, `PreferencesSectionRoute` | RPC `save_preferences` | US-65 |
| Training intention and preferences | "Objetivo de entrenamiento" | `PreferencesSectionRoute` | RPC `save_preferences` | US-65, US-82 |
| About, version and licences | "Acerca de", "Licencias de código abierto" | `AboutRoute` | - | US-45 |
| Sign out and delete account | "¿Cerrar sesión?", "Borrar cuenta" | `ProfileHome` (dialogs) | Edge Function `delete-account` | US-05 |

## Centro de avisos

| Functionality | Label in the app | Route or host | Backend | Story |
|---|---|---|---|---|
| In-app notification center | "Centro de avisos", "Todo al día" | `NotificationCenterRoute` | - | US-57, US-109 |
| Weekly check-in (energy only) | "Check-in semanal", "Energía de la semana" | `CheckInRoute` | table `weekly_check_ins` (`energy`) | US-59 |
| Weight and circumference reminders | "Avisos" | computed on the device, never stored (`04` §6) | - | US-58 |

## Backend jobs and Edge Functions

| Functionality | Host | Backend | Story |
|---|---|---|---|
| Edge Function `health` (keep-alive ping, no JWT) | `supabase/functions/health` | pinged by `keepalive.yml` | US-02, US-124 |
| Edge Function `account-status` | `supabase/functions/account-status` | sign-in and account check | US-05 |
| Edge Function `delete-account` | `supabase/functions/delete-account` | deletes the account and its data | US-05 |
| Edge Function `food-search` | `supabase/functions/food-search` | text search, own foods and OFF | US-17 |
| Edge Function `food-barcode` | `supabase/functions/food-barcode` | barcode lookup | US-18 |
| Edge Function `food-photo` | `supabase/functions/food-photo` | AI estimate: dish, label, barcode, weight and text modes | US-48, US-49, US-54, US-132 |
| Edge Function `coach` | `supabase/functions/coach` | deterministic kinds `plan_nutrition`, `plan_recompute`, `plan_diet_break`, `plan_phase_change`, `plan_goal_switch` | US-69, US-99, US-115 |
| Edge Function `exercise-chat` | `supabase/functions/exercise-chat` | exercise question and answer | US-130 |
| Scheduled database jobs | none live | the cleanup job was unscheduled by migration `20270125100200` | - |
| Keep-alive schedule | `.github/workflows/keepalive.yml` | GET `health` every 3 days | US-124 |
