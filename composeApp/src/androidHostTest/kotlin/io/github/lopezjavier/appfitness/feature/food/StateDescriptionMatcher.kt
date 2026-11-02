package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher

/** Matches the node whose merged semantics announce exactly [text] as its state description. */
internal fun stateDescriptionIs(text: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
