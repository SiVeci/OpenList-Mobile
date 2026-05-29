package com.openlistmobile.app.ui.components;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
        include = {
                "c",
                "clike",
                "cpp",
                "csharp",
                "css",
                "go",
                "java",
                "javascript",
                "json",
                "kotlin",
                "markdown",
                "markup",
                "python",
                "sql",
                "swift",
                "yaml"
        },
        grammarLocatorClassName = ".CodeGrammarLocator"
)
public class CodePrismBundle {
}
