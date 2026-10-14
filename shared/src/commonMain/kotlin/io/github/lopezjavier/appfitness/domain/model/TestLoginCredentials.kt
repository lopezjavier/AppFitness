package io.github.lopezjavier.appfitness.domain.model

/** Email and password of the seeded test user. Only the `local` flavor carries them. */
data class TestLoginCredentials(val email: String, val password: String) {
    // The password is not logged even by accident.
    override fun toString(): String = "TestLoginCredentials(email=$email)"
}
