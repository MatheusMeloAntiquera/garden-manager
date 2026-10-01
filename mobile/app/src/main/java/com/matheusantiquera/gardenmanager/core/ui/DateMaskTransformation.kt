package com.matheusantiquera.gardenmanager.core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Mostra até 8 dígitos digitados como `dd/mm/aaaa`; o valor guardado continua sendo só os dígitos. */
object DateMaskTransformation : VisualTransformation {
    private const val MAX_DIGITS = 8

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(MAX_DIGITS)
        val masked = buildString {
            digits.forEachIndexed { index, char ->
                if (index == 2 || index == 4) append('/')
                append(char)
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(masked.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }

        return TransformedText(AnnotatedString(masked), mapping)
    }
}
