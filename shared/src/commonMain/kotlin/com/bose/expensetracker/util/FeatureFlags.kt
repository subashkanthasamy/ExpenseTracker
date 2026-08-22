package com.bose.expensetracker.util

/**
 * Build-time switches for features that exist in the codebase but are not shipped yet.
 *
 * Shared so both platforms flip together — a feature visible on one and hidden on the other is
 * worse than either state on its own.
 *
 * These are compile-time constants, not remote config: flipping one is a code change and a new
 * build, which is the point. Nothing here is a security control — hiding an entry point does not
 * stop anything, so a feature that must not be reachable needs its rules closed too.
 */
object FeatureFlags {

    /**
     * The Financial Coach chat screen.
     *
     * Built and working, held back for a later release. The screen, its view model and its
     * navigation route all remain in place; only the entry points are hidden, so re-enabling is
     * this one line rather than reassembling the feature.
     */
    const val FINANCIAL_COACH_ENABLED = false
}
