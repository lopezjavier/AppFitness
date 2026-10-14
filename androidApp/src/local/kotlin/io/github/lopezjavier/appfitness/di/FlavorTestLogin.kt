package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials

/** The seeded local test user (planning/08-environment-setup.md §3.2; local-only, not a secret). */
fun flavorTestLogin(): TestLoginCredentials? = TestLoginCredentials("test@appfitness.local", "appfitness-local-test")
