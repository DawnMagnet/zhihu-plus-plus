/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ml.shubham0204.sentence_embeddings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/**
 * WordPiece tokenizer for the only model this app loads, `shibing624/text2vec-base-chinese`.
 *
 * That `tokenizer.json` is BertNormalizer + BertPreTokenizer + WordPiece + a single-sentence
 * template. The Hugging Face `tokenizers` crate also implements BPE, Unigram and oniguruma
 * pretokenizers, which this app never executes, so those algorithms stay out of the client.
 * Token ids are checked against `tokenizers` 0.22.2 on the same file.
 */
class BertWordPieceTokenizer(
    tokenizerBytes: ByteArray,
) {
    private val vocab: Map<String, Int>
    private val unkId: Int
    private val continuingPrefix: String
    private val maxInputChars: Int
    private val cleanText: Boolean
    private val handleChineseChars: Boolean
    private val lowercase: Boolean
    private val stripAccents: Boolean
    private val addedTokens: List<AddedToken>
    private val singleTemplate: List<TemplatePiece>

    init {
        val root = Json.parseToJsonElement(tokenizerBytes.decodeToString()).jsonObject
        val model = root.getValue("model").jsonObject
        vocab = model.getValue("vocab").jsonObject.mapValues { it.value.jsonPrimitive.int }
        val unkToken = model.getValue("unk_token").jsonPrimitive.content
        unkId = vocab.getValue(unkToken)
        continuingPrefix = model.getValue("continuing_subword_prefix").jsonPrimitive.content
        maxInputChars = model.getValue("max_input_chars_per_word").jsonPrimitive.int
        val normalizer = root.getValue("normalizer").jsonObject
        cleanText = normalizer.getValue("clean_text").jsonPrimitive.boolean
        handleChineseChars = normalizer.getValue("handle_chinese_chars").jsonPrimitive.boolean
        lowercase = normalizer.getValue("lowercase").jsonPrimitive.boolean
        val stripAccentsField = normalizer.getValue("strip_accents")
        stripAccents = if (stripAccentsField is JsonNull) {
            lowercase
        } else {
            stripAccentsField.jsonPrimitive.boolean
        }
        addedTokens = root.getValue("added_tokens").jsonArray.map { element ->
            val token = element.jsonObject
            AddedToken(
                content = token.getValue("content").jsonPrimitive.content,
                id = token.getValue("id").jsonPrimitive.int,
            )
        }
        val specialIds = root.getValue("post_processor").jsonObject
            .getValue("special_tokens").jsonObject
            .mapValues { (_, value) ->
                value.jsonObject.getValue("ids").jsonArray.first().jsonPrimitive.int
            }
        singleTemplate = root.getValue("post_processor").jsonObject
            .getValue("single").jsonArray
            .map { element ->
                val piece = element.jsonObject
                when {
                    piece.containsKey("SpecialToken") -> {
                        val spec = piece.getValue("SpecialToken").jsonObject
                        val name = spec.getValue("id").jsonPrimitive.content
                        TemplatePiece.Special(
                            id = specialIds.getValue(name),
                            typeId = spec.getValue("type_id").jsonPrimitive.int,
                        )
                    }
                    piece.containsKey("Sequence") -> TemplatePiece.Sequence(
                        typeId = piece.getValue("Sequence").jsonObject.getValue("type_id").jsonPrimitive.int,
                    )
                    else -> error("unsupported tokenizer template piece")
                }
            }
    }

    fun encode(text: String): Encoding {
        val ids = ArrayList<Int>()
        val typeIds = ArrayList<Int>()
        val pieces = splitAddedTokens(text)
        val sequence = ArrayList<Int>()
        for (piece in pieces) {
            when (piece) {
                is Piece.Special -> sequence += piece.id
                is Piece.Text -> {
                    if (piece.text.isEmpty()) continue
                    wordPiece(preTokenize(normalize(piece.text))).forEach { sequence += it }
                }
            }
        }
        for (template in singleTemplate) {
            when (template) {
                is TemplatePiece.Special -> {
                    ids += template.id
                    typeIds += template.typeId
                }
                is TemplatePiece.Sequence -> {
                    for (id in sequence) {
                        ids += id
                        typeIds += template.typeId
                    }
                }
            }
        }
        return Encoding(
            ids = LongArray(ids.size) { ids[it].toLong() },
            attentionMask = LongArray(ids.size) { 1L },
            tokenTypeIds = LongArray(typeIds.size) { typeIds[it].toLong() },
        )
    }

    private fun splitAddedTokens(text: String): List<Piece> {
        if (addedTokens.isEmpty() || text.isEmpty()) return listOf(Piece.Text(text))
        val pieces = ArrayList<Piece>()
        var index = 0
        while (index < text.length) {
            var match: AddedToken? = null
            for (token in addedTokens) {
                if (text.startsWith(token.content, index) &&
                    (match == null || token.content.length > match.content.length)
                ) {
                    match = token
                }
            }
            if (match != null) {
                pieces += Piece.Special(match.id)
                index += match.content.length
            } else {
                var next = text.length
                for (token in addedTokens) {
                    val found = text.indexOf(token.content, index)
                    if (found >= 0) next = minOf(next, found)
                }
                pieces += Piece.Text(text.substring(index, next))
                index = next
            }
        }
        return pieces
    }

    private fun normalize(text: String): String {
        val cleaned = if (cleanText) {
            buildString {
                var index = 0
                while (index < text.length) {
                    val codePoint = text.codePointAt(index)
                    index += Character.charCount(codePoint)
                    when {
                        codePoint == 0 || codePoint == 0xFFFD || isControl(codePoint) -> Unit
                        isWhitespace(codePoint) -> append(' ')
                        else -> appendCodePoint(codePoint)
                    }
                }
            }
        } else {
            text
        }
        val spaced = if (handleChineseChars) {
            buildString {
                var index = 0
                while (index < cleaned.length) {
                    val codePoint = cleaned.codePointAt(index)
                    index += Character.charCount(codePoint)
                    if (isChineseChar(codePoint)) {
                        append(' ')
                        appendCodePoint(codePoint)
                        append(' ')
                    } else {
                        appendCodePoint(codePoint)
                    }
                }
            }
        } else {
            cleaned
        }
        val cased = if (lowercase) spaced.lowercase() else spaced
        return if (stripAccents) removeAccents(cased) else cased
    }

    private fun preTokenize(text: String): List<String> {
        val words = ArrayList<String>()
        for (word in text.split(WHITESPACE)) {
            if (word.isEmpty()) continue
            val chars = codePoints(word)
            val current = StringBuilder()
            for (char in chars) {
                if (isPunctuation(char)) {
                    if (current.isNotEmpty()) {
                        words += current.toString()
                        current.clear()
                    }
                    words += char
                } else {
                    current.append(char)
                }
            }
            if (current.isNotEmpty()) words += current.toString()
        }
        return words
    }

    private fun wordPiece(words: List<String>): List<Int> {
        val ids = ArrayList<Int>()
        for (word in words) {
            val chars = codePoints(word)
            if (chars.size > maxInputChars) {
                ids += unkId
                continue
            }
            var start = 0
            val wordIds = ArrayList<Int>()
            var failed = false
            while (start < chars.size) {
                var end = chars.size
                var found: Int? = null
                while (start < end) {
                    val piece = buildString {
                        if (start > 0) append(continuingPrefix)
                        for (index in start until end) append(chars[index])
                    }
                    val id = vocab[piece]
                    if (id != null) {
                        found = id
                        break
                    }
                    end -= 1
                }
                if (found == null) {
                    failed = true
                    break
                }
                wordIds += found
                start = end
            }
            if (failed) ids += unkId else ids += wordIds
        }
        return ids
    }

    data class Encoding(
        val ids: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    private data class AddedToken(val content: String, val id: Int)

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Special(val id: Int) : Piece
    }

    private sealed interface TemplatePiece {
        data class Special(val id: Int, val typeId: Int) : TemplatePiece
        data class Sequence(val typeId: Int) : TemplatePiece
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        fun isControl(codePoint: Int): Boolean {
            if (codePoint == '\t'.code || codePoint == '\n'.code || codePoint == '\r'.code) return false
            val type = Character.getType(codePoint)
            return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
        }

        fun isWhitespace(codePoint: Int): Boolean {
            if (codePoint == ' '.code || codePoint == '\t'.code || codePoint == '\n'.code || codePoint == '\r'.code) {
                return true
            }
            val type = Character.getType(codePoint)
            return type == Character.SPACE_SEPARATOR.toInt() ||
                type == Character.LINE_SEPARATOR.toInt() ||
                type == Character.PARAGRAPH_SEPARATOR.toInt()
        }

        fun isChineseChar(codePoint: Int): Boolean = codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x2F800..0x2FA1F

        fun isPunctuation(char: String): Boolean {
            val codePoint = char.codePointAt(0)
            if (codePoint in 33..47 || codePoint in 58..64 || codePoint in 91..96 || codePoint in 123..126) {
                return true
            }
            val type = Character.getType(codePoint)
            return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()
        }

        fun removeAccents(text: String): String {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            return buildString {
                var index = 0
                while (index < normalized.length) {
                    val codePoint = normalized.codePointAt(index)
                    index += Character.charCount(codePoint)
                    if (Character.getType(codePoint) != Character.NON_SPACING_MARK.toInt()) {
                        appendCodePoint(codePoint)
                    }
                }
            }
        }

        fun codePoints(text: String): List<String> {
            val chars = ArrayList<String>(text.length)
            var index = 0
            while (index < text.length) {
                val count = Character.charCount(text.codePointAt(index))
                chars += text.substring(index, index + count)
                index += count
            }
            return chars
        }
    }
}
