package com.guiderun.app.accessibility.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestVoiceParserTest {

    @Test
    fun `parses all three fields with arabic numerals`() {
        // Arrange
        val text = "地点 图书馆南门 时长 60 分钟 备注 慢跑节奏"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("图书馆南门", result?.location)
        assertEquals(60, result?.durationMinutes)
        assertEquals("慢跑节奏", result?.notes)
    }

    @Test
    fun `parses three fields without spaces`() {
        // Arrange
        val text = "地点图书馆南门，时长一小时，备注慢跑"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("图书馆南门", result?.location)
        assertEquals(60, result?.durationMinutes)
        assertEquals("慢跑", result?.notes)
    }

    @Test
    fun `parses fields in arbitrary order`() {
        // Arrange
        val text = "时长 90 分钟 地点 操场 备注 配速适中"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("操场", result?.location)
        assertEquals(90, result?.durationMinutes)
        assertEquals("配速适中", result?.notes)
    }

    @Test
    fun `parses half hour as 30 minutes`() {
        // Arrange
        val text = "时长半小时"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals(30, result?.durationMinutes)
    }

    @Test
    fun `parses two hours as 120 minutes`() {
        // Arrange
        val text = "时长两小时"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals(120, result?.durationMinutes)
    }

    @Test
    fun `snaps non bucket number to nearest bucket`() {
        // Arrange — 45 分钟 → 最接近 30
        val text = "时长 45 分钟"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals(30, result?.durationMinutes)
    }

    @Test
    fun `parses single field only`() {
        // Arrange
        val text = "地点 中央公园"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("中央公园", result?.location)
        assertNull(result?.durationMinutes)
        assertNull(result?.notes)
    }

    @Test
    fun `recognizes alternate location keyword`() {
        // Arrange
        val text = "集合点 操场北门"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("操场北门", result?.location)
    }

    @Test
    fun `returns null when no keyword matches`() {
        // Arrange
        val text = "今天天气真不错"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertNull(result)
    }

    @Test
    fun `returns null for blank input`() {
        // Arrange
        val text = "   "

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertNull(result)
    }

    @Test
    fun `later occurrence overrides earlier one for same key`() {
        // Arrange — 用户中途纠正：先说错地点，后改正
        val text = "地点 图书馆 地点 操场"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("操场", result?.location)
    }

    @Test
    fun `trims trailing punctuation from extracted segments`() {
        // Arrange
        val text = "地点 操场北门， 时长 60 分钟。 备注 慢跑节奏。"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("操场北门", result?.location)
        assertEquals(60, result?.durationMinutes)
        assertEquals("慢跑节奏", result?.notes)
    }

    @Test
    fun `supports colon separator`() {
        // Arrange
        val text = "地点：南门，时长：60，备注：随意"

        // Act
        val result = RequestVoiceParser.parse(text)

        // Assert
        assertEquals("南门", result?.location)
        assertEquals(60, result?.durationMinutes)
        assertEquals("随意", result?.notes)
    }
}
