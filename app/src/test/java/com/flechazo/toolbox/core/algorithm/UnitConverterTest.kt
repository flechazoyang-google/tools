package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.unit_converter.UnitCategory
import com.flechazo.toolbox.feature.unit_converter.UnitConverterViewModel
import com.flechazo.toolbox.feature.unit_converter.convertAll
import com.flechazo.toolbox.feature.unit_converter.UnitConverterUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {

    @Test
    fun lengthConversion() {
        val length = UnitCategory.LENGTH
        val m = length.units.first { it.symbol == "m" }
        val km = length.units.first { it.symbol == "km" }
        assertEquals(0.001, length.convert(1.0, m, km), 1e-9)
        assertEquals(1000.0, length.convert(1.0, km, m), 1e-9)
    }

    @Test
    fun inchToCm() {
        val length = UnitCategory.LENGTH
        val inch = length.units.first { it.symbol == "in" }
        val cm = length.units.first { it.symbol == "cm" }
        assertEquals(2.54, length.convert(1.0, inch, cm), 1e-9)
    }

    @Test
    fun chineseJin() {
        val weight = UnitCategory.WEIGHT
        val jin = weight.units.first { it.symbol == "jin" }
        val kg = weight.units.first { it.symbol == "kg" }
        assertEquals(0.5, weight.convert(1.0, jin, kg), 1e-9)
    }

    @Test
    fun temperatureSpecialCases() {
        val temp = UnitCategory.TEMPERATURE
        val c = temp.units.first { it.symbol == "°C" }
        val f = temp.units.first { it.symbol == "°F" }
        val k = temp.units.first { it.symbol == "K" }
        assertEquals(100.0, temp.convert(212.0, f, c), 1e-9)
        assertEquals(373.15, temp.convert(100.0, c, k), 1e-9)
        assertEquals(32.0, temp.convert(0.0, c, f), 1e-9)
    }

    @Test
    fun kelvinConversions() {
        val temp = UnitCategory.TEMPERATURE
        val c = temp.units.first { it.symbol == "°C" }
        val f = temp.units.first { it.symbol == "°F" }
        val k = temp.units.first { it.symbol == "K" }
        assertEquals(0.0, temp.convert(273.15, k, c), 1e-9)
        // 旧实现把 K→°F 落到 else 分支，错误地算成 value-273.15
        assertEquals(32.0, temp.convert(273.15, k, f), 1e-9)
        assertEquals(212.0, temp.convert(373.15, k, f), 1e-9)
    }

    @Test
    fun dataUnitsBinary() {
        val data = UnitCategory.DATA
        val mb = data.units.first { it.symbol == "MB" }
        val b = data.units.first { it.symbol == "B" }
        assertEquals(1048576.0, data.convert(1.0, mb, b), 1e-6)
    }

    @Test
    fun viewModelResults() {
        val vm = UnitConverterViewModel()
        vm.setInput("2")
        val state = vm.state.value
        val meters = state.results[state.fromIndex] // from = meter default
        assertEquals(2.0, meters!!, 1e-9)
        // toIndex 默认 0 = mm
        assertEquals(2000.0, state.results[state.toIndex]!!, 1e-6)
    }

    @Test
    fun invalidInputYieldsNaN() {
        val vm = UnitConverterViewModel()
        vm.setInput("abc")
        assertTrue(vm.state.value.results.all { it.isNaN() })
    }

    @Test
    fun topLevelConvertAllMatches() {
        val s = UnitConverterUiState(input = "1")
        val r = convertAll(s)
        assertEquals(UnitCategory.LENGTH.units.size, r.size)
        assertEquals(1000.0, r[0], 1e-9) // mm
    }
}
