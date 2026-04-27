package dev.adk.kotlin

class EventStream(
    private val eventSupplier: () -> Event?,
) : Iterable<Event> {
    override fun iterator(): Iterator<Event> =
        object : Iterator<Event> {
            private var nextEvent: Event? = null
            private var finished = false

            override fun hasNext(): Boolean {
                if (finished) {
                    return false
                }
                if (nextEvent == null) {
                    nextEvent = eventSupplier()
                    finished = nextEvent == null
                }
                return !finished
            }

            override fun next(): Event {
                if (!hasNext()) {
                    throw NoSuchElementException("No more events.")
                }

                val current = requireNotNull(nextEvent)
                nextEvent = null
                return current
            }
        }

    companion object {
        fun from(events: Iterable<Event>): EventStream {
            val iterator = events.iterator()
            return EventStream {
                if (iterator.hasNext()) {
                    iterator.next()
                } else {
                    null
                }
            }
        }
    }
}
