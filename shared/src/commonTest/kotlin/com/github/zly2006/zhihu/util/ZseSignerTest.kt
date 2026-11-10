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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ZseSignerTest {
    @Test
    fun encryptZseV4IsDeterministic() {
        val first = ZseSigner.encryptZseV4("hello")
        val second = ZseSigner.encryptZseV4("hello")

        assertEquals(first, second)
        assertEquals(MASTER_HELLO, first)
    }

    @Test
    fun encryptZseV4ChangesWithInput() {
        assertNotEquals(
            ZseSigner.encryptZseV4("hello"),
            ZseSigner.encryptZseV4("world"),
        )
        assertEquals(MASTER_WORLD, ZseSigner.encryptZseV4("world"))
    }

    @Test
    fun xZse96HeaderMatchesMaster() {
        assertEquals(
            MASTER_HEADER,
            ZhihuFetchSignature.createZse96Header(
                zse93 = "101_3_3.0",
                url = "https://www.zhihu.com/api/v4/me",
                dc0 = "token",
            ),
        )
        assertEquals(
            MASTER_HEADER_WITH_BODY,
            ZhihuFetchSignature.createZse96Header(
                zse93 = "101_3_3.0",
                url = "https://www.zhihu.com/api/v4/me",
                dc0 = "token",
                body = """{"hello":"world"}""",
            ),
        )
    }

    @Test
    fun loginRefreshBodyMatchesMaster() {
        val formData = listOf(
            "client_id" to "abc",
            "grant_type" to "refresh_token",
            "timestamp" to "1700000000000",
            "source" to "com.zhihu.web",
            "signature" to "deadbeef",
            "refresh_token" to "tok%2F1",
        ).joinToString("&") { (key, value) -> "$key=$value" }

        assertEquals(MASTER_REFRESH_FORM, ZseSigner.encryptZseV4(formData))
    }
}

private const val MASTER_HELLO = "+6xPvLM9SJjT+GToL9YAivj/"
private const val MASTER_WORLD = "+65CcmGtmyq0DMsf4jUsBidi"
private const val MASTER_HEADER =
    "2.0_F8/7KowGzlmasG8ngOUM2K20KwpAQSQdcAoxx=MvGUflQ2xQw1K9=XrkuH2ohSk2"
private const val MASTER_HEADER_WITH_BODY =
    "2.0_0grUSe+gqzIeODknIL9XmOXkz/UvcvSq+9GKW54Z=2YLlz5YZsgI7Sjr734z+1oU"
private const val MASTER_REFRESH_FORM =
    "+RQdA=AVQZCn9AUh89V7knIZYsjk0ov5KmY=2=dj2oCuJJDNwQZsQGp21lapQJmsC=Tv2IZSPGPgHn/R3qRLpaH5+5=rnUBbAKLn0r=LZP1jbpP=UmgPPboGmghl3yW4vc2=yyKG2n2YlLGfkP6c0RRzC44WJAkl=DScgJ7qW7nL"
