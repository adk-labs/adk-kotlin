package dev.adk.kotlin

interface BasePlanner {
    fun prepareRequest(
        session: AgentSession,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val planningInstruction = buildPlanningInstruction(session, llmRequest)?.trim()
        if (planningInstruction.isNullOrEmpty()) {
            return llmRequest
        }

        return llmRequest.copy(
            systemInstructions = llmRequest.systemInstructions + planningInstruction,
        )
    }

    fun buildPlanningInstruction(
        session: AgentSession,
        llmRequest: LlmRequest,
    ): String? = null

    fun processPlanningResponse(
        llmResponse: LlmResponse,
    ): LlmResponse = llmResponse
}

typealias Planner = BasePlanner

data class BuiltInPlanner(
    val thinkingConfig: ThinkingConfig,
) : BasePlanner {
    fun applyThinkingConfig(llmRequest: LlmRequest): LlmRequest {
        val requestConfig = (llmRequest.config ?: GenerateContentConfig()).copy(
            thinkingConfig = thinkingConfig,
        )
        return llmRequest.copy(config = requestConfig)
    }

    override fun prepareRequest(
        session: AgentSession,
        llmRequest: LlmRequest,
    ): LlmRequest = applyThinkingConfig(super.prepareRequest(session, llmRequest))
}

class PlanReActPlanner : BasePlanner {
    override fun buildPlanningInstruction(
        session: AgentSession,
        llmRequest: LlmRequest,
    ): String = PLANNING_INSTRUCTION

    override fun processPlanningResponse(
        llmResponse: LlmResponse,
    ): LlmResponse =
        when (llmResponse) {
            is ModelResponse.Final -> {
                val finalMessage = extractFinalAnswer(llmResponse.message)
                llmResponse.copy(message = finalMessage)
            }

            is ModelResponse.ToolCalls -> llmResponse
        }

    internal fun extractFinalAnswer(message: String): String {
        val finalAnswerIndex = message.lastIndexOf(FINAL_ANSWER_TAG)
        if (finalAnswerIndex == -1) {
            return message
        }

        val answer = message.substring(finalAnswerIndex + FINAL_ANSWER_TAG.length).trim()
        return answer.ifEmpty { message }
    }

    companion object {
        const val PLANNING_TAG = "/*PLANNING*/"
        const val REPLANNING_TAG = "/*REPLANNING*/"
        const val REASONING_TAG = "/*REASONING*/"
        const val ACTION_TAG = "/*ACTION*/"
        const val FINAL_ANSWER_TAG = "/*FINAL_ANSWER*/"

        internal val PLANNING_INSTRUCTION =
            listOf(
                """
                When answering the question, prefer using the available tools to gather information instead of relying on memorized knowledge.

                Follow this process when answering the question:
                1. First, create a plan in natural language.
                2. Then use tools to execute the plan and add reasoning between tool calls to summarize the current state and next step.
                3. End with one final answer.

                Follow this format:
                - Put the planning section under $PLANNING_TAG.
                - Put tool actions under $ACTION_TAG and reasoning under $REASONING_TAG.
                - Put the final answer under $FINAL_ANSWER_TAG.
                """.trimIndent(),
                """
                Planning requirements:
                - The plan should be sufficient to answer the user query if followed correctly.
                - The plan should be coherent, cover the query, and only use tools available to the agent.
                - The plan should use a numbered list of decomposed steps.
                - If execution fails, revise the plan under $REPLANNING_TAG and continue with the updated plan.
                """.trimIndent(),
                """
                Reasoning requirements:
                - Summarize the current trajectory using the user query and tool outputs.
                - Use the reasoning to decide the next step that moves the trajectory closer to the final answer.
                """.trimIndent(),
                """
                Final answer requirements:
                - The final answer should be precise and follow the user's formatting requirements.
                - If the available tools or information are insufficient, explain why and ask for the missing information.
                """.trimIndent(),
                """
                Tool action requirements:
                - The available tools are described in the context and can be used directly.
                - Do not invent imports, helper libraries, or parameters that are not present in the available tools.
                - Use readable and relevant tool calls that directly support the plan and the reasoning.
                """.trimIndent(),
                """
                Very important:
                - Ask for clarification when you need more information to answer the question.
                - Prefer available context over repeated tool use.
                """.trimIndent(),
            ).joinToString("\n\n")
    }
}

fun builtInPlanner(thinkingConfig: ThinkingConfig): BuiltInPlanner =
    BuiltInPlanner(thinkingConfig = thinkingConfig)

fun builtInPlanner(block: ThinkingConfigDsl.() -> Unit): BuiltInPlanner =
    BuiltInPlanner(thinkingConfig = thinkingConfig(block))

fun planReActPlanner(): PlanReActPlanner = PlanReActPlanner()
