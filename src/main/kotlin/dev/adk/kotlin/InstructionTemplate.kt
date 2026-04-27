package dev.adk.kotlin

data class InstructionTemplate(
    val text: String,
    val bypassStateInjection: Boolean = false,
) {
    init {
        require(text.isNotEmpty()) { "Instruction template cannot be empty." }
    }
}
