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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class BertWordPieceTokenizerTest {
    @Test
    fun matchesHuggingFaceTokenizers022() {
        val bytes = resourceBytes("text2vec-base-chinese-tokenizer.json")
        val tokenizer = BertWordPieceTokenizer(bytes)
        val production = HFTokenizer(bytes)
        val vectors = Json.parseToJsonElement(resource("text2vec-goldens.json")).jsonArray
        assertTrue(vectors.size >= 30)
        for (element in vectors) {
            val obj = element.jsonObject
            val text = obj.getValue("text").jsonPrimitive.content
            val expected = obj.getValue("ids").jsonArray.map { it.jsonPrimitive.int.toLong() }.toLongArray()
            val expectedMask = obj.getValue("attention_mask").jsonArray.map { it.jsonPrimitive.int.toLong() }.toLongArray()
            val expectedTypes = obj.getValue("token_type_ids").jsonArray.map { it.jsonPrimitive.int.toLong() }.toLongArray()
            val actual = tokenizer.encode(text)
            assertEquals(text, expected.toList(), actual.ids.toList())
            assertEquals(text, expectedMask.toList(), actual.attentionMask.toList())
            assertEquals(text, expectedTypes.toList(), actual.tokenTypeIds.toList())
            val throughProductionApi = production.tokenize(text)
            assertEquals(text, expected.toList(), throughProductionApi.ids.toList())
        }
    }

    @Test
    fun tokenizesFeedSizedTextQuickly() {
        val tokenizer = tokenizer()
        val text = "知乎是一个中文问答社区，用户可以在这里分享知识、经验和见解。".repeat(20)
        repeat(5) { tokenizer.encode(text) }
        val elapsed = measureTimeMillis {
            repeat(50) { tokenizer.encode(text) }
        }
        assertTrue("tokenization took ${elapsed}ms", elapsed < 1_000)
    }

    private fun tokenizer(): BertWordPieceTokenizer = BertWordPieceTokenizer(resourceBytes("text2vec-base-chinese-tokenizer.json"))

    private fun resourceBytes(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(name)!!.use { it.readBytes() }

    private fun resource(name: String): String = resourceBytes(name).decodeToString()
}
