/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.example.text.styling.parser

import java.util.Collections.emptyList
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The role of this parser is just to showcase ways of working with text. It should not be
 * expected to support complex markdown elements.
 *
 * Parse text and extract markdown elements:
 *
 *  * Paragraphs starting with “> ” are transformed into quotes. Quotes can't contain
 * other markdown elements.
 *  *  Text enclosed in “`” will be transformed into inline code block.
 *  * Lines starting with “+ ” or “* ” will be transformed into bullet points. Bullet
 * points can contain nested markdown elements, like code.
 *
 */
object Parser {

    /**
     * Parse a text and extract the [TextMarkdown].
     *
     * @param string string to be parsed into markdown elements
     * @return the [TextMarkdown]
     */
    fun parse(string: String): TextMarkdown {
        val parents = mutableListOf<Element>()

        val patternQuote = Pattern.compile(QUOTE_REGEX)
        val pattern = Pattern.compile(BULLET_POINT_CODE_BLOCK_REGEX)

        val matcher = patternQuote.matcher(string)
        var lastStartIndex = 0

        // A sequence is basically an iterator that ends when a null value is returned. Thanks
        // to this we can avoid the use of "while" loops, which are usually more difficult to read
        // and prone to errors. Here we are creating a sequence of pairs that include the startIndex
        // and endIndex based on the last index that was used in the previous iteration.
        generateSequence { matcher.findBoundaries(lastStartIndex) }
            .forEach { (startIndex, endIndex) ->
                // we found a quote
                if (lastStartIndex < startIndex) {
                    // check what was before the quote
                    val text = string.subSequence(lastStartIndex, startIndex)
                    parents.addAll(findElements(text, pattern))
                }
                // a quote can only be a paragraph long, so look for end of line
                val endOfQuote = getEndOfParagraph(string, endIndex)
                lastStartIndex = endOfQuote
                val quotedText = string.subSequence(endIndex, endOfQuote)
                parents.add(Element(Element.Type.QUOTE, quotedText, emptyList<Element>()))
            }

        // check if there are any other element after the quote
        if (lastStartIndex < string.length) {
            val text = string.subSequence(lastStartIndex, string.length)
            parents.addAll(findElements(text, pattern))
        }

        return TextMarkdown(parents)
    }

    private fun getEndOfParagraph(string: CharSequence, endIndex: Int): Int {
        val endOfParagraph = string.indexOf(LINE_SEPARATOR, endIndex)

        return when (endOfParagraph) {
        // we don't have an end of line, so the quote is the last element in the text
        // so we can consider that the end of the quote is the end of the text
            -1 -> string.length
        // add the new line as part of the element
            else -> endOfParagraph + 1
        }
    }

    private fun findElements(string: CharSequence, pattern: Pattern): List<Element> {
        val parents = mutableListOf<Element>()
        val matcher = pattern.matcher(string)
        var lastStartIndex = 0

        // Also using a sequence here. See the one in the `parse` function above for more info.
        generateSequence { matcher.findBoundaries(lastStartIndex) }
            .forEach { (startIndex, endIndex) ->
                // we found a mark
                val mark = string.subSequence(startIndex, endIndex)
                if (lastStartIndex < startIndex) {
                    // check what was before the mark
                    parents.addAll(
                        findElements(
                            string.subSequence(lastStartIndex, startIndex),
                            pattern
                        )
                    )
                }
                val text: CharSequence
                // check what kind of mark this was
                when (mark) {
                    BULLET_PLUS, BULLET_STAR -> {
                        // every bullet point is max until a new line or end of text
                        val endOfBulletPoint = getEndOfParagraph(string, endIndex)
                        text = string.subSequence(endIndex, endOfBulletPoint)
                        lastStartIndex = endOfBulletPoint
                        // also see what else we have in the text
                        val subMarks = findElements(text, pattern)
                        val bulletPoint = Element(Element.Type.BULLET_POINT, text, subMarks)
                        parents.add(bulletPoint)
                    }
                    CODE_BLOCK -> {
                        // a code block is set between two "`" so look for the other one
                        // if another "`" is not found, then this is not a code block
                        var markEnd = string.indexOf(CODE_BLOCK, endIndex)
                        if (markEnd == -1) {
                            // we don't have an end of code block so this is just text
                            markEnd = string.length
                            text = string.subSequence(startIndex, markEnd)
                            lastStartIndex = markEnd
                        } else {
                            // we found the end of the code block
                            text = string.subSequence(endIndex, markEnd)
                            // adding 1 so we can ignore the ending "`" for the code block
                            lastStartIndex = markEnd + 1
                        }
                        parents.add(Element(Element.Type.TEXT, text, emptyList<Element>()))
                    }
                }
            }

        // check if there's any more text left
        if (lastStartIndex < string.length) {
            val text = string.subSequence(lastStartIndex, string.length)
            parents.add(Element(Element.Type.TEXT, text, emptyList<Element>()))
        }

        return parents
    }

    private const val BULLET_PLUS = "+ "
    private const val BULLET_STAR = "* "
    private const val QUOTE_REGEX = "(?m)^> "
    private const val BULLET_POINT_STAR = "(?m)^\\$BULLET_STAR"
    private const val BULLET_POINT_PLUS = "(?m)^\\$BULLET_PLUS"
    private const val BULLET_POINT_REGEX = "($BULLET_POINT_STAR|$BULLET_POINT_PLUS)"
    private const val CODE_BLOCK = "`"
    private const val BULLET_POINT_CODE_BLOCK_REGEX = "($BULLET_POINT_REGEX|$CODE_BLOCK)"

    private val LINE_SEPARATOR = System.getProperty("line.separator")
}

private fun Matcher.findBoundaries(start: Int): Pair<Int, Int>? =
    if (find(start)) start() to end() else null