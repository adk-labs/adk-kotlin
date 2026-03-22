package dev.adk.kotlin

data class InstructionTemplate(
    val text: String,
    val bypassStateInjection: Boolean = false,
) {
    init {
        require(text.isNotBlank()) { "Instruction template cannot be blank." }
    }
}
