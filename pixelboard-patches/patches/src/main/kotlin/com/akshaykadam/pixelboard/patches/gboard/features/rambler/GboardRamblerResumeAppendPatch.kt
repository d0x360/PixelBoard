package com.akshaykadam.pixelboard.patches.gboard.features.rambler

import com.akshaykadam.pixelboard.patches.shared.MutableMethod
import com.akshaykadam.pixelboard.patches.shared.addInstructions
import com.akshaykadam.pixelboard.patches.shared.bytecodePatch
import com.akshaykadam.pixelboard.patches.shared.Constants.COMPATIBILITY_GBOARD
import com.akshaykadam.pixelboard.patches.gboard.shared.GboardMethodTarget
import com.akshaykadam.pixelboard.patches.gboard.shared.findMutableMethodOrNull
import com.akshaykadam.pixelboard.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import com.akshaykadam.pixelboard.patches.gboard.shared.isMethodReference
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallId
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * AgenticDictationTextEditor.commitTextWithDiff(operator, transformer, snapshot, current,
 * rewrite, cursorAwareText, formatter, locale). The obfuscated owner is only a lookup hint: the
 * method must also carry the editor's own log strings and the expected parameter shape.
 */
private val commitTextWithDiffTargets = listOf(
    // 18.4.1 release
    GboardMethodTarget(
        classType = "Lfcd;",
        name = "r",
        parameterTypes = listOf(
            "Lowa;",
            "Lgyf;",
            "Loxh;",
            "Loxh;",
            "Ljava/lang/String;",
            "Laakd;",
            "Lggc;",
            "Ljava/util/Locale;",
        ),
        returnType = "V",
    ),
)

private val commitTextWithDiffStrings = setOf(
    "commitTextWithDiff: operator is null.",
    "commitTextWithDiff: text changed during dictation, replace everything",
)

private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val FIRST_SCRATCH_LOCALS = 4

internal val gboardRamblerResumeAppendPatch = bytecodePatch(
    description = "Keep resumed Rambler dictation at the cursor instead of letting the " +
        "rewrite diff insert it inside the earlier text.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val method = commitTextWithDiffTargets
            .firstNotNullOfOrNull { findMutableMethodOrNull(it) }
            ?.takeIf { it.isCommitTextWithDiff() }
            ?: return@execute
        val call = RuntimeCallId.RAMBLER_RESUME_APPEND_REWRITE_COMMIT_TEXT
        val implementation = method.implementation ?: return@execute
        if (implementation.instructions.any { it.isMethodReference(RuntimeAbiCatalog.abi(call).reference) }) {
            return@execute
        }
        val textField = method.surroundingTextField() ?: return@execute
        val parameterWords = method.parameterTypes.size // static, no wide parameters
        check(implementation.registerCount - parameterWords >= FIRST_SCRATCH_LOCALS) {
            "commitTextWithDiff has no free scratch locals for the resume hook"
        }
        val firstParameter = implementation.registerCount - parameterWords
        val snapshot = firstParameter + 2
        val current = firstParameter + 3
        val rewrite = firstParameter + 4
        // Locals are unassigned at method entry, so v0..v3 are free scratch registers.
        method.addInstructions(
            0,
            """
                move-object/from16 v0, v$snapshot
                move-object/from16 v1, v$current
                iget-object v2, v1, $textField
                move-object/from16 v3, v$rewrite
                ${RuntimeCallEmitter.invoke(call, "v0, v1, v2, v3")}
                move-result-object v0
                move-object/16 v$rewrite, v0
            """.trimIndent(),
        )
    }
}

private fun MutableMethod.isCommitTextWithDiff(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags) || returnType != "V") return false
    val parameters = parameterTypes.map { it.toString() }
    if (parameters.size != 8 || parameters[4] != "Ljava/lang/String;" ||
        parameters[2] != parameters[3] || parameters[7] != "Ljava/util/Locale;"
    ) {
        return false
    }
    val strings = implementation?.instructions.orEmpty().mapNotNullTo(mutableSetOf()) {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
    }
    return strings.containsAll(commitTextWithDiffStrings)
}

/** The current surrounding text: the CharSequence read off the `current` type before toString(). */
private fun MutableMethod.surroundingTextField(): String? {
    val surroundingTextType = parameterTypes[3].toString()
    val instructions = implementation?.instructions?.toList() ?: return null
    instructions.forEachIndexed { index, instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@forEachIndexed
        val field = (instruction as ReferenceInstruction).reference as? FieldReference
            ?: return@forEachIndexed
        if (field.definingClass != surroundingTextType || field.type != CHAR_SEQUENCE) {
            return@forEachIndexed
        }
        val next = instructions.getOrNull(index + 1) as? ReferenceInstruction
        val nextMethod = next?.reference as? MethodReference
        if (nextMethod?.name == "toString" && nextMethod.returnType == "Ljava/lang/String;") {
            return "${field.definingClass}->${field.name}:${field.type}"
        }
    }
    return null
}
