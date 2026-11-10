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

@file:OptIn(ExperimentalUnsignedTypes::class)
@file:Suppress("ktlint:standard:argument-list-wrapping")

package com.github.zly2006.zhihu.util

/**
 * Pure Kotlin port of `rs-zse-sign`. The ciphertext matches the Rust `encrypt_zse_v4` output
 * byte for byte, including the fixed prefix byte.
 *
 * zse-v4.js uses `Math.floor(Math.random() * 127)` as that prefix. The Rust signer pins
 * `Math.random` to `0.1`, so the prefix is always `floor(0.1 * 127) = 12`. The plaintext is
 * `12 || 0x00 || encodeURIComponent(input)`, then PKCS#7 and the 16-byte block cipher.
 */
object ZseSigner {
    private val ZK = uintArrayOf(
        1170614578u, 1024848638u, 1413669199u, 3951632832u, 3528873006u, 2921909214u, 4151847688u, 3997739139u,
        1933479194u, 3323781115u, 3888513386u, 460404854u, 3747539722u, 2403641034u, 2615871395u, 2119585428u,
        2265697227u, 2035090028u, 2773447226u, 4289380121u, 4217216195u, 2200601443u, 3051914490u, 1579901135u,
        1321810770u, 456816404u, 2903323407u, 4065664991u, 330002838u, 3506006750u, 363569021u, 2347096187u,
    )

    private val ZB = intArrayOf(
        20, 223, 245, 7, 248, 2, 194, 209, 87, 6, 227, 253, 240, 128, 222, 91, 237, 9, 125, 157, 230,
        93, 252, 205, 90, 79, 144, 199, 159, 197, 186, 167, 39, 37, 156, 198, 38, 42, 43, 168, 217,
        153, 15, 103, 80, 189, 71, 191, 97, 84, 247, 95, 36, 69, 14, 35, 12, 171, 28, 114, 178, 148,
        86, 182, 32, 83, 158, 109, 22, 255, 94, 238, 151, 85, 77, 124, 254, 18, 4, 26, 123, 176, 232,
        193, 131, 172, 143, 142, 150, 30, 10, 146, 162, 62, 224, 218, 196, 229, 1, 192, 213, 27, 110,
        56, 231, 180, 138, 107, 242, 187, 54, 120, 19, 44, 117, 228, 215, 203, 53, 239, 251, 127, 81,
        11, 133, 96, 204, 132, 41, 115, 73, 55, 249, 147, 102, 48, 122, 145, 106, 118, 74, 190, 29, 16,
        174, 5, 177, 129, 63, 113, 99, 31, 161, 76, 246, 34, 211, 13, 60, 68, 207, 160, 65, 111, 82,
        165, 67, 169, 225, 57, 112, 244, 155, 51, 236, 200, 233, 58, 61, 47, 100, 137, 185, 64, 17, 70,
        234, 163, 219, 108, 170, 166, 59, 149, 52, 105, 24, 212, 78, 173, 45, 0, 116, 226, 119, 136,
        206, 135, 175, 195, 25, 92, 121, 208, 126, 139, 3, 75, 141, 21, 130, 98, 241, 40, 154, 66, 184,
        49, 181, 46, 243, 88, 101, 183, 8, 23, 72, 188, 104, 179, 210, 134, 250, 201, 164, 89, 216,
        202, 220, 50, 221, 152, 140, 33, 235, 214,
    )

    private const val ALPHABET = "6fpLRqJO8M/c3jnYxFkUVC4ZIG12SiH=5v0mXDazWBTsuw7QetbKdoPyAl+hN9rgE"
    private val KEY16 = "059053f7d15e01d7".encodeToByteArray()
    private val HEX = "0123456789ABCDEF".encodeToByteArray()
    private val REVERSE_ALPHABET = IntArray(128) { 255 }.also { reverse ->
        ALPHABET.forEachIndexed { index, char -> reverse[char.code] = index }
    }

    private fun readU32Be(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

    private fun writeU32Be(v: Int, out: ByteArray, off: Int) {
        out[off] = (v ushr 24).toByte()
        out[off + 1] = (v ushr 16).toByte()
        out[off + 2] = (v ushr 8).toByte()
        out[off + 3] = v.toByte()
    }

    private fun gTransform(tt: Int): Int {
        val ti =
            (ZB[tt ushr 24 and 0xFF] shl 24) or
                (ZB[tt ushr 16 and 0xFF] shl 16) or
                (ZB[tt ushr 8 and 0xFF] shl 8) or
                ZB[tt and 0xFF]
        return ti xor ti.rotateLeft(2) xor ti.rotateLeft(10) xor ti.rotateLeft(18) xor ti.rotateLeft(24)
    }

    private fun rBlock(input16: ByteArray, keyOffset: (Int) -> Int): ByteArray {
        val tr = IntArray(36)
        tr[0] = readU32Be(input16, 0)
        tr[1] = readU32Be(input16, 4)
        tr[2] = readU32Be(input16, 8)
        tr[3] = readU32Be(input16, 12)
        for (i in 0 until 32) {
            val ta = gTransform(tr[i + 1] xor tr[i + 2] xor tr[i + 3] xor keyOffset(i))
            tr[i + 4] = tr[i] xor ta
        }
        return ByteArray(16).also {
            writeU32Be(tr[35], it, 0)
            writeU32Be(tr[34], it, 4)
            writeU32Be(tr[33], it, 8)
            writeU32Be(tr[32], it, 12)
        }
    }

    private fun encryptBlock(input16: ByteArray): ByteArray = rBlock(input16) { ZK[it].toInt() }

    private fun decryptBlock(input16: ByteArray): ByteArray = rBlock(input16) { ZK[31 - it].toInt() }

    private fun xBlocks(data: ByteArray, iv0: ByteArray): ByteArray {
        var iv = iv0
        val out = ByteArray(data.size)
        var outOff = 0
        var off = 0
        while (off < data.size) {
            val mixed = ByteArray(16)
            for (i in 0 until 16) {
                mixed[i] = (data[off + i].toInt() xor iv[i].toInt()).toByte()
            }
            iv = encryptBlock(mixed)
            iv.copyInto(out, outOff, 0, 16)
            off += 16
            outOff += 16
        }
        return out
    }

    private fun encodeUriComponent(input: String): ByteArray {
        val raw = input.encodeToByteArray()
        val out = ArrayList<Byte>(raw.size * 3)
        for (byte in raw) {
            val value = byte.toInt() and 0xFF
            val unescaped = value in 'A'.code..'Z'.code ||
                value in 'a'.code..'z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code ||
                value == '_'.code ||
                value == '.'.code ||
                value == '!'.code ||
                value == '~'.code ||
                value == '*'.code ||
                value == '\''.code ||
                value == '('.code ||
                value == ')'.code
            if (unescaped) {
                out += byte
            } else {
                out += '%'.code.toByte()
                out += HEX[value ushr 4]
                out += HEX[value and 0x0F]
            }
        }
        return out.toByteArray()
    }

    private fun customEncode(bytesIn: ByteArray): String {
        val rem = bytesIn.size % 3
        val bytes = if (rem == 0) bytesIn else bytesIn + ByteArray(3 - rem)
        val out = StringBuilder((bytes.size / 3) * 4)
        var i = 0
        var p = bytes.size - 1
        while (p >= 0) {
            var v = 0
            val b0 = bytes[p].toInt() and 0xFF
            val m0 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or (b0 xor m0)
            val b1 = bytes[p - 1].toInt() and 0xFF
            val m1 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or ((b1 xor m1) shl 8)
            val b2 = bytes[p - 2].toInt() and 0xFF
            val m2 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or ((b2 xor m2) shl 16)
            out.append(ALPHABET[v and 63])
            out.append(ALPHABET[(v ushr 6) and 63])
            out.append(ALPHABET[(v ushr 12) and 63])
            out.append(ALPHABET[(v ushr 18) and 63])
            p -= 3
        }
        return out.toString()
    }

    fun encryptZseV4(input: String): String {
        val encoded = encodeUriComponent(input)
        val plain = ByteArray(2 + encoded.size)
        plain[0] = 12
        plain[1] = 0
        encoded.copyInto(plain, 2)
        val pad = 16 - (plain.size % 16)
        val padded = ByteArray(plain.size + pad)
        plain.copyInto(padded)
        for (index in plain.size until padded.size) {
            padded[index] = pad.toByte()
        }
        val first = ByteArray(16)
        for (i in 0 until 16) {
            first[i] = (padded[i].toInt() xor KEY16[i].toInt() xor 42).toByte()
        }
        val c0 = encryptBlock(first)
        val cipher = ByteArray(padded.size)
        c0.copyInto(cipher)
        if (padded.size > 16) {
            xBlocks(padded.copyOfRange(16, padded.size), c0).copyInto(cipher, 16)
        }
        return customEncode(cipher)
    }

    fun decryptZseV4(encoded: String): String {
        val cipher = decodeCustom(encoded)
        val plain = ByteArray(cipher.size)
        val first = decryptBlock(cipher.copyOfRange(0, 16))
        for (i in 0 until 16) {
            plain[i] = (first[i].toInt() xor KEY16[i].toInt() xor 42).toByte()
        }
        var previous = cipher
        var offset = 16
        while (offset < cipher.size) {
            val chunk = cipher.copyOfRange(offset, offset + 16)
            val decrypted = decryptBlock(chunk)
            for (i in 0 until 16) {
                plain[offset + i] = (decrypted[i].toInt() xor previous[i].toInt()).toByte()
            }
            previous = chunk
            offset += 16
        }
        val unpadded = pkcs7Unpad(plain)
        require(unpadded.size >= 2 && unpadded[1] == 0.toByte()) { "invalid plaintext header" }
        return decodeUriComponent(unpadded.copyOfRange(2, unpadded.size)).decodeToString()
    }

    private fun decodeCustom(encoded: String): ByteArray {
        require(encoded.length % 4 == 0) { "invalid encoded length" }
        val processed = ByteArray((encoded.length / 4) * 3)
        var i = 0
        var out = 0
        var p = 0
        val bytes = encoded.encodeToByteArray()
        while (p < bytes.size) {
            val indices = IntArray(4) { index ->
                val char = bytes[p + index].toInt() and 0xFF
                require(char < 128) { "invalid alphabet char" }
                val decoded = REVERSE_ALPHABET[char]
                require(decoded != 255) { "invalid alphabet char" }
                decoded
            }
            p += 4
            val value = indices[0] or (indices[1] shl 6) or (indices[2] shl 12) or (indices[3] shl 18)
            for (shift in intArrayOf(0, 8, 16)) {
                val mask = (58 ushr (8 * (i % 4))) and 0xFF
                i += 1
                processed[out++] = (((value ushr shift) and 0xFF) xor mask).toByte()
            }
        }
        processed.reverse()
        val trim = processed.size % 16
        require(trim <= 2) { "invalid block alignment" }
        val cipher = processed.copyOf(processed.size - trim)
        require(cipher.isNotEmpty() && cipher.size % 16 == 0) { "invalid ciphertext length" }
        return cipher
    }

    private fun pkcs7Unpad(bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty()) { "empty plaintext" }
        val pad = bytes.last().toInt() and 0xFF
        require(pad in 1..16 && pad <= bytes.size) { "invalid pkcs7 padding" }
        require((0 until pad).all { (bytes[bytes.size - 1 - it].toInt() and 0xFF) == pad }) {
            "invalid pkcs7 padding"
        }
        return bytes.copyOf(bytes.size - pad)
    }

    private fun decodeUriComponent(input: ByteArray): ByteArray {
        val out = ArrayList<Byte>(input.size)
        var i = 0
        while (i < input.size) {
            if (input[i] == '%'.code.toByte()) {
                require(i + 2 < input.size) { "invalid percent encoding" }
                val high = hexValue(input[i + 1])
                val low = hexValue(input[i + 2])
                out += ((high shl 4) or low).toByte()
                i += 3
            } else {
                out += input[i]
                i += 1
            }
        }
        return out.toByteArray()
    }

    private fun hexValue(byte: Byte): Int {
        val value = byte.toInt() and 0xFF
        return when (value) {
            in '0'.code..'9'.code -> value - '0'.code
            in 'a'.code..'f'.code -> value - 'a'.code + 10
            in 'A'.code..'F'.code -> value - 'A'.code + 10
            else -> throw IllegalArgumentException("invalid percent encoding")
        }
    }
}
