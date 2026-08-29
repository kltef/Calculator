package com.cascalc.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.cascalc.engine.HistoryEntry
import com.cascalc.engine.SolutionStep
import java.io.File
import java.io.FileOutputStream

/**
 * V7's export and sharing.
 *
 * Files are written to the app's `cache/shared` directory and handed out
 * through a [FileProvider], because Android will not let another app read a
 * `file://` path from our storage — the share sheet silently fails or throws
 * `FileUriExposedException`.
 */
object Sharing {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val SHARE_DIR = "shared"

    fun shareText(context: Context, text: String, subject: String = "Calculation") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addNewTaskFlag())
    }

    /** Plain-text rendering of a result and its working, for sharing or notes. */
    fun formatSolution(input: String, result: String, steps: List<SolutionStep>): String =
        buildString {
            appendLine(input)
            appendLine("= $result")
            if (steps.isNotEmpty()) {
                appendLine()
                appendLine("Working:")
                steps.forEachIndexed { index, step ->
                    appendLine("${index + 1}. ${step.explanation}")
                    step.expression?.let { appendLine("   $it") }
                }
            }
        }.trim()

    fun formatHistory(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { "${it.input} = ${it.result.exact}" }

    /** Writes a PNG into cache and returns a shareable content URI. */
    fun shareImage(context: Context, bitmap: Bitmap, name: String = "graph.png") {
        val file = File(shareDir(context), name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(context, file, "image/png")
    }

    /** Renders text as a single-page PDF and shares it. */
    fun sharePdf(context: Context, title: String, body: String, name: String = "solution.pdf") {
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create(),
        )
        val paint = android.graphics.Paint().apply { textSize = 12f }
        val titlePaint = android.graphics.Paint().apply {
            textSize = 18f
            isFakeBoldText = true
        }

        var y = MARGIN + 20f
        page.canvas.drawText(title, MARGIN, y, titlePaint)
        y += 28f
        for (line in body.lines()) {
            // Long lines are wrapped by character count rather than measured
            // width; a solution line is short and this keeps it dependency-free.
            line.chunked(MAX_LINE_CHARS).forEach { chunk ->
                if (y > PAGE_HEIGHT - MARGIN) return@forEach
                page.canvas.drawText(chunk, MARGIN, y, paint)
                y += 16f
            }
        }
        document.finishPage(page)

        val file = File(shareDir(context), name)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addNewTaskFlag())
    }

    private fun shareDir(context: Context): File =
        File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    private fun Intent.addNewTaskFlag(): Intent = apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val MAX_LINE_CHARS = 78
}
