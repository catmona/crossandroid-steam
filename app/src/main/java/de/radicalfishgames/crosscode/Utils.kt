package de.radicalfishgames.crosscode

import java.io.File

/** @author X1nto */
infix fun File.or(another: File): File = if (exists()) this else another
