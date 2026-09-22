package dev.appboypov.herdridea.herdr.shared.exceptions

/** A named action could not run; the message says why, for the caller. */
class HerdrActionException(message: String) : RuntimeException(message)
