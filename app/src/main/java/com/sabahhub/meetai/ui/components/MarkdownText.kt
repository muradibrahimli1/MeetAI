package com.sabahhub.meetai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal Markdown renderer for the subset our summaries use: `#`/`##`/`###`
 * headings, `-`/`*` bullets, `- [ ]`/`- [x]` checkboxes, and inline `**bold**`.
 * Anything unrecognized renders as a plain paragraph. Avoids pulling in a full
 * Markdown library for a small, fixed format.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    Column(modifier) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(8.dp))

                line.startsWith("### ") -> Heading(line.removePrefix("### "), MaterialTheme.typography.titleSmall)
                line.startsWith("## ") -> Heading(line.removePrefix("## "), MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Heading(line.removePrefix("# "), MaterialTheme.typography.titleLarge)

                line.startsWith("- [ ] ") || line.startsWith("- [] ") ->
                    CheckItem(line.substringAfter("] ").trim(), checked = false)
                line.startsWith("- [x] ", ignoreCase = true) ->
                    CheckItem(line.substringAfter("] ").trim(), checked = true)

                line.startsWith("- ") || line.startsWith("* ") ->
                    Bullet(line.drop(2).trim())

                else -> Paragraph(line)
            }
        }
    }
}

@Composable
private fun Heading(text: String, style: androidx.compose.ui.text.TextStyle) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = inline(text),
        style = style,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(text = inline(text), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(inline(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Spacer(Modifier.width(0.dp))
        Text(inline(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Renders inline `**bold**` spans; leaves everything else as-is. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) {
            append(text.substring(i)); break
        }
        append(text.substring(i, start))
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(start)); break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 2, end))
        }
        i = end + 2
    }
}
