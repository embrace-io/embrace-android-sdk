package io.embrace.analysis.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `quoted strings, bare numbers, NULL sentinel and escaped quotes parse like csv reader`() {
        val text = "\n\"what\",\"k\",\"val\"\n\"section\",\"emb-sdk-start\",26.163336\n\"ttid_ms\",\"\",[NULL]\n" +
            "\"x\",\"say \"\"hi\"\", ok\",1\n"
        assertEquals(
            listOf(
                listOf("what", "k", "val"),
                listOf("section", "emb-sdk-start", "26.163336"),
                listOf("ttid_ms", "", "[NULL]"),
                listOf("x", "say \"hi\", ok", "1"),
            ),
            Csv.parse(text),
        )
    }

    @Test
    fun `an unquoted line splits on commas and an empty trailing field survives`() {
        assertEquals(listOf("a", "b", ""), Csv.parseLine("a,b,"))
        assertEquals(listOf(""), Csv.parseLine(""))
    }
}
