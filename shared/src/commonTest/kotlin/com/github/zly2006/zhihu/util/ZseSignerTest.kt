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

package com.github.zly2006.zhihu.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ZseSignerTest {
    @Test
    fun encryptZseV4IsDeterministic() {
        val first = ZseSigner.encryptZseV4("hello")
        val second = ZseSigner.encryptZseV4("hello")

        assertEquals(first, second)
        assertEquals("+65P=3h=2RPlvZqa+mRtnl9K", first)
    }

    @Test
    fun encryptZseV4ChangesWithInput() {
        assertNotEquals(
            ZseSigner.encryptZseV4("hello"),
            ZseSigner.encryptZseV4("world"),
        )
    }

    @Test
    fun matchesRustEncryptZseV4AndRoundTrips() {
        val vectors = Json.parseToJsonElement(RUST_VECTORS).jsonArray
        assertEquals(24, vectors.size)
        for (element in vectors) {
            val input = element.jsonObject
                .getValue("input")
                .jsonPrimitive.content
            val output = element.jsonObject
                .getValue("output")
                .jsonPrimitive.content
            assertEquals(output, ZseSigner.encryptZseV4(input), input)
            assertEquals(input, ZseSigner.decryptZseV4(output), input)
        }
    }
}

private const val RUST_VECTORS = """
[
  {"input":"","output":"+6x5gF33/nzqkOPPcKmtMP/g"},
  {"input":"a","output":"+65J62yLAm5TA391stvL+4qC"},
  {"input":"hello","output":"+65P=3h=2RPlvZqa+mRtnl9K"},
  {"input":"world","output":"+66W+pI9PVOY/ZGSBzmaUzG8"},
  {"input":"hello world?a=1&b=2","output":"+VIzaV8gMXP=GxbS+ywiIb5lVCJy5lLFqKB/2t7Vj1=c"},
  {"input":"你好，知乎++ Rust 解密","output":"fVz4cu6JsClHoASgDOqwOM1uOleN14Gsm7qRQOYokAC09yOifC/NgbWVpHA/ruoSn9+wg2HZRKaaAGswRGRolwDjuC/CC2Ze7imrwpMCG/2j4FeOpmQVtoYu1WJKcbAy"},
  {"input":"101_3_3.0+/api/v4/answers/1+abc","output":"bDBwxtkHXjaHxrSrt4teARFkrqtqHDcchu8zqZIz7ls5DWqMI4V=tvPMo3h8mCIK"},
  {"input":"0123456789abcdef0123456789abcdef","output":"mOI2n/7jKBbCGH37ym9harGU8o=+j3VxDU6mJdxjjSnfuBlFDHOwxafbTzOCJDo/"},
  {"input":"client_id=abc&grant_type=refresh_token&timestamp=1700000000000&source=com.zhihu.web&signature=deadbeef&refresh_token=tok%2F1","output":"+65w9yfnu110+Ck1BjqXNO2Of0k1ZNci1X9Lg/yy/UxkM7hO/UKJ0Udn5aYxtzWLO7v1q=4SrGjtxS48UzNglpXHBf6kN/hWnFbH7V5cqcD0UBDYdBKuiC0ztJsuFIgHl5jwwtd2GUZRZxcyiCuJN82DgtNCjLZHu4Kx7mwzASkkr82Cc1zvDAyg2SqQ0xNW1TIAyX24d2bLmJTVrfDldcxe"},
  {"input":" ~!*()' -._ 中文 /?&=%+#","output":"+66wGiKOdDOAH362D06Z0/4IpfXJVPbQqnD6wttK6WUM8n1KBo1OrGXyGC88YF+t0v3OqpLku6MyiGtJRF35fmVz"},
  {"input":"line1\nline2\ttab","output":"+eUdLdo/c7i65frzvcwvTJ/ytLcwGy=NvGVmGS9g6Dgi"},
  {"input":"emoji 😀","output":"+Vtkbqn2czFOrDv4WSBWdHTioUgQnXacbUg=Ra1TmRpY"},
  {"input":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","output":"+8azkdKYdpVzJIuSATffn+g4gTiIRV1Wh6goujyd3qz2xHgqBXP0NjgF3t22KgkAR3T1Vs=QW7RN+BBeQAJ73wnpoT2PG2FvJ0nrWMmqH9rC"},
  {"input":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","output":"+6eZwJ8UORxUU1zRJPPWnBUebgSufvmNWbQNGJ=19zYXjD+F/Oz9Ze8=Mq8jToVazL=aOTvRZAJ/PDSsperLXUDSdC=U=TXY8Dwxw9mq9r+l4xI97Dw4zeuCsmch+LaV9ZYMon63rYnwDUcBOnRqsI3RbXfNAihjLF+gA9WpXDWPj5G+cHDZBxdtbyzT/ml1drg4gpr4RV+af6go4Ddd3wRCxH=cUXP0JDNF3GACKgbPr3T17LSQWJzb+BMrCAJ7PqjpopAAG2tQN0nrkv5qHCi2"},
  {"input":"M","output":"+65jPoonGc9tSUAJ8plNSt/X"},
  {"input":"0123456789abcdef","output":"+3I6j4xQjzIK11CqSeXfRQQYuBG==HOwTnpbTYX2JDCR"},
  {"input":"/","output":"+6xSYo7mO1l3lYHKCCa1DLJb"},
  {"input":"%","output":"+65l8Lknn/0iHDOXPrfDUltW"},
  {"input":"äöü","output":"+8l+8Ai5YuHb=bAHuJP5ZUnnzdwOXlumbcbtyoMmLqM+"},
  {"input":"混合 English 与中文","output":"+6x9c4+13Tugk/Q=aP/evKd=ANbvhM7PGQkL+iouDAfdT+BTZxaIr3sAfQx6XPrxxXe7O23JDv8GX=DoAV4pg89+"},
  {"input":"\"quoted\"","output":"+6xt=Dj=vPb+BVJj=/U86YYx"},
  {"input":"a+b=c&d=e f","output":"+XUVc91z69Vp5N27VQnJd82OXKpTOZCrWxSDN6tnKv4W"},
  {"input":"\u0000null","output":"+6605Z3+h7ZIau0Lp+Udtda+"},
  {"input":"end","output":"+65GlqOr97+v2Ti4jprWchA0"}
]
"""
