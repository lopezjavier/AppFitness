package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials

/** The prod flavor has no test login: the credentials are not compiled into it. */
@Suppress("FunctionOnlyReturningConstant")
fun flavorTestLogin(): TestLoginCredentials? = null
