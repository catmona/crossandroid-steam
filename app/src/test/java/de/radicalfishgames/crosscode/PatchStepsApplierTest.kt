package de.radicalfishgames.crosscode

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PatchStepsApplierTest {
    private fun resolver(files: Map<String, String>): FileResolver = { path -> files[path] }

    @Test
    fun includeOfObjectDeepMergesIntoCurrentValue() {
        // xenons-playable-classes INCLUDEs plain objects of new events and landmarks.
        val original = """{"commonEvents":{"old":{"a":1}}}"""
        val patch = """[{"type":"ENTER","index":"commonEvents"},{"type":"INCLUDE","src":"events.json"},{"type":"EXIT"}]"""
        val out =
            JSONObject(
                PatchStepsApplier.applyOrThrow(
                    original,
                    patch,
                    resolver(mapOf("mod:events.json" to """{"new":{"b":2},"old":{"c":3}}""")),
                ),
            )
        val events = out.getJSONObject("commonEvents")
        assertEquals(2, events.getJSONObject("new").getInt("b"))
        assertEquals(1, events.getJSONObject("old").getInt("a"))
        assertEquals(3, events.getJSONObject("old").getInt("c"))
    }

    @Test
    fun includeOfArrayRunsSteps() {
        val patch = """[{"type":"INCLUDE","src":"mod:steps.json"}]"""
        val steps = """[{"type":"SET_KEY","index":"x","content":5}]"""
        val out = JSONObject(PatchStepsApplier.applyOrThrow("{}", patch, resolver(mapOf("mod:steps.json" to steps))))
        assertEquals(5, out.getInt("x"))
    }

    @Test
    fun conditionChecksForInstalledMods() {
        val mods = setOf("lqm-joern-mod", "Qine")
        fun eval(cond: String) = PatchCondition.evaluate(cond, mods)
        assertEquals(false, eval("activeMods.find(e => e.name === 'arcane-labs') || activeMods.find(e => e.name === 'arcane-lab')"))
        assertEquals(true, eval("!activeMods.find(e => e.name === 'arcane-labs') && !activeMods.find(e => e.name === 'arcane-lab')"))
        assertEquals(
            true,
            eval(
                "(!activeMods.find(e => e.name === 'arcane-labs') && !activeMods.find(e => e.name === 'arcane-lab')) || " +
                    "!activeMods.find(e => e.name === 'lqm-joern-mod') || !activeMods.find(e => e.name === 'Qine')",
            ),
        )
        assertEquals(true, eval("activeMods.find(m => m.name == \"Qine\")"))
        // Anything else is unknown.
        assertEquals(null, eval("window.localStorage.getItem(\"azure-x\") != \"false\""))
    }

    @Test
    fun ifStepSkipsThenStepsWhenModMissing() {
        val patch =
            """[{"type":"IF","cond":"activeMods.find(e => e.name === 'arcane-lab')","thenSteps":[{"type":"SET_KEY","index":"a","content":1}]},
               {"type":"IF","cond":"!activeMods.find(e => e.name === 'arcane-lab')","thenSteps":[{"type":"SET_KEY","index":"b","content":2}]}]"""
        val out = JSONObject(PatchStepsApplier.applyOrThrow("{}", patch, activeMods = emptySet()))
        assertEquals(false, out.has("a"))
        assertEquals(2, out.getInt("b"))
    }

    @Test
    fun importOfArrayAppendsToArray() {
        val patch = """[{"type":"ENTER","index":"list"},{"type":"IMPORT","src":"mod:more.json"}]"""
        val out = JSONObject(PatchStepsApplier.applyOrThrow("""{"list":[1]}""", patch, resolver(mapOf("mod:more.json" to "[2,3]"))))
        assertEquals("[1,2,3]", out.getJSONArray("list").toString())
    }
}
