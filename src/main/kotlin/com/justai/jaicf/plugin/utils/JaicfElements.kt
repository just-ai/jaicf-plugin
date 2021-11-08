package com.justai.jaicf.plugin.utils

import org.jetbrains.kotlin.name.FqName

const val SCENARIO_PACKAGE = "com.justai.jaicf.builder"
const val SCENARIO_GRAPH_BUILDER_CLASS = "$SCENARIO_PACKAGE.ScenarioGraphBuilder"
val scenarioGraphBuilderClassFqName = FqName(SCENARIO_GRAPH_BUILDER_CLASS)
val ScenarioPackageFqName = FqName(SCENARIO_PACKAGE)
const val SCENARIO_EXTENSIONS_CLASS_NAME = "ScenarioKt"
val rootBuilderClassFqName = FqName("$SCENARIO_PACKAGE.RootBuilder")
const val SCENARIO_MODEL_FIELD_NAME = "model"

const val STATE_NAME_ARGUMENT_NAME = "name"
const val CREATE_MODEL_METHOD_NAME = "createModel"
const val SCENARIO_METHOD_NAME = "Scenario"
const val APPEND_METHOD_NAME = "append"
const val APPEND_CONTEXT_ARGUMENT_NAME = "context"
const val DO_APPEND_METHOD_NAME = "doAppend"
const val APPEND_OTHER_ARGUMENT_NAME = "other"

const val REACTIONS_PACKAGE = "com.justai.jaicf.reactions"
const val REACTIONS_CLASS_NAME = "Reactions"
const val REACTIONS_CLASS = "$REACTIONS_PACKAGE.$REACTIONS_CLASS_NAME"
val reactionsClassFqName = FqName(REACTIONS_CLASS)

const val PLUGIN_PACKAGE = "com.justai.jaicf.plugin"
const val PATH_ARGUMENT_ANNOTATION_NAME = "PathValue"
const val STATE_DECLARATION_ANNOTATION_NAME = "StateDeclaration"
const val STATE_NAME_ANNOTATION_NAME = "StateName"
const val STATE_BODY_ANNOTATION_NAME = "StateBody"
const val STATE_NAME_ANNOTATION_ARGUMENT_NAME = "name"
const val USES_REACTION_ANNOTATION_NAME = "UsesReaction"
const val USES_REACTION_METHOD_ARGUMENT_NAME = "name"

const val JAICF_GROUP_ID = "com.just-ai.jaicf"
const val JAICF_CORE_ARTIFACT_ID = "core"
