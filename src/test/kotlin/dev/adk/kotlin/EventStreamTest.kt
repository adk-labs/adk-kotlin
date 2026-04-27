package dev.adk.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventStreamTest {
    @Test
    fun `event stream fetches events lazily until supplier returns null`() {
        val events =
            ArrayDeque(
                listOf(
                    Event(invocationId = "invocation-1", author = "user", content = UserMessage("Hi")),
                    Event(invocationId = "invocation-1", author = "planner", content = ModelMessage("Hello")),
                ),
            )
        var supplierCalls = 0
        val iterator =
            EventStream {
                supplierCalls += 1
                events.removeFirstOrNull()
            }.iterator()

        assertEquals(0, supplierCalls)
        assertTrue(iterator.hasNext())
        assertEquals(1, supplierCalls)
        assertTrue(iterator.hasNext())
        assertEquals(1, supplierCalls)
        assertEquals("user", iterator.next().author)
        assertTrue(iterator.hasNext())
        assertEquals(2, supplierCalls)
        assertEquals("planner", iterator.next().author)
        assertFalse(iterator.hasNext())
        assertEquals(3, supplierCalls)
        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }
    }

    @Test
    fun `event stream can be created from existing events`() {
        val stream =
            EventStream.from(
                listOf(
                    Event(invocationId = "invocation-1", author = "user"),
                    Event(invocationId = "invocation-1", author = "planner"),
                ),
            )

        assertEquals(listOf("user", "planner"), stream.map { it.author })
    }
}
