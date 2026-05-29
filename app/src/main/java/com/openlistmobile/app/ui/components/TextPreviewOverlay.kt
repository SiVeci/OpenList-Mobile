package com.openlistmobile.app.ui.components

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPreviewOverlay(
    content: String?,
    isLoading: Boolean,
    fileName: String?,
    onDismiss: () -> Unit
) {
    if (!isLoading && content == null) return

    var darkReadingMode by remember { mutableStateOf(false) }
    val isDarkSystem = isSystemInDarkTheme()
    val effectiveDark = darkReadingMode || isDarkSystem
    val bgColor = if (effectiveDark) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surface
    val textColor = if (effectiveDark) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = fileName ?: "Text Preview",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { darkReadingMode = !darkReadingMode }) {
                            Icon(Icons.Default.Brightness6, contentDescription = "Toggle Reading Mode")
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val text = content ?: ""
                        when {
                            isMarkdownFile(fileName) -> MarkdownPreview(text, effectiveDark)
                            isCodeFile(fileName) -> CodePreview(text, codeLanguageFor(fileName), effectiveDark)
                            else -> PlainTextPreview(text, textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(content: String, darkMode: Boolean) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val markwon = remember(context, darkMode) {
        val prismTheme = if (darkMode) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create()
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(
                SyntaxHighlightPlugin.create(createPrism4j(), prismTheme)
            )
            .build()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.12f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            markwon.setMarkdown(textView, content)
        }
    )
}

@Composable
private fun CodePreview(content: String, language: String?, darkMode: Boolean) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gutterBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val highlighted = remember(content, language, darkMode) {
        if (language == null) {
            content
        } else {
            val prismTheme = if (darkMode) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create()
            Prism4jSyntaxHighlight
                .create(createPrism4j(), prismTheme)
                .highlight(language, content)
        }
    }
    val lineNumberWidth = ((lineCount.toString().length * 10) + 28).dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        AndroidView(
            modifier = Modifier
                .width(lineNumberWidth)
                .background(gutterBackground)
                .padding(horizontal = 8.dp),
            factory = { context ->
                codeTextView(context).apply {
                    gravity = Gravity.END
                }
            },
            update = { textView ->
                textView.text = lineNumbers
                textView.setTextColor(gutterColor.toArgb())
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 20.dp)
        ) {
            AndroidView(
                modifier = Modifier.wrapContentWidth(unbounded = true),
                factory = { context -> codeTextView(context) },
                update = { textView ->
                    textView.text = highlighted
                    textView.setTextColor(textColor.toArgb())
                }
            )
        }
    }
}

@Composable
private fun PlainTextPreview(content: String, textColor: Color) {
    Text(
        text = content,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            lineHeight = 26.sp
        ),
        color = textColor
    )
}

private fun codeTextView(context: android.content.Context): TextView =
    TextView(context).apply {
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(0f, 1f)
        setHorizontallyScrolling(true)
    }

private fun isMarkdownFile(fileName: String?): Boolean =
    extensionOf(fileName) in setOf("md", "markdown", "mdown")

private fun isCodeFile(fileName: String?): Boolean =
    extensionOf(fileName) in setOf(
        "bat", "cmd", "sh", "shell",
        "c", "h", "cc", "cpp", "cxx", "hpp",
        "cs", "css", "go", "gradle", "java",
        "js", "jsx", "ts", "tsx",
        "json", "kt", "kts",
        "html", "htm", "svg", "xml",
        "py", "rb", "rs", "sql", "swift", "yaml", "yml"
    )

private fun codeLanguageFor(fileName: String?): String? =
    when (extensionOf(fileName)) {
        "c", "h" -> "c"
        "cc", "cpp", "cxx", "hpp" -> "cpp"
        "cs" -> "csharp"
        "css" -> "css"
        "go" -> "go"
        "gradle", "java" -> "java"
        "js", "jsx", "ts", "tsx" -> "javascript"
        "json" -> "json"
        "kt", "kts" -> "kotlin"
        "html", "htm", "svg", "xml" -> "markup"
        "py" -> "python"
        "sql" -> "sql"
        "swift" -> "swift"
        "yaml", "yml" -> "yaml"
        else -> null
    }

private fun extensionOf(fileName: String?): String =
    fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()

private fun createPrism4j(): Prism4j {
    val locator = runCatching {
        Class.forName("com.openlistmobile.app.ui.components.CodeGrammarLocator")
            .getDeclaredConstructor()
            .newInstance() as GrammarLocator
    }.getOrElse {
        object : GrammarLocator {
            override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? = null
            override fun languages(): Set<String> = emptySet()
        }
    }
    return Prism4j(locator)
}
