package com.habittracker.domain.model

data class TemplateWithIdentities(
    val template: HabitTemplate,
    val recommendedBy: Set<Identity>,
)
