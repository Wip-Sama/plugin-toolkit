package com.wip.additions

import org.koin.dsl.module

class SumService {
    fun calculateSum(args: Array<String>): String {
        if (args.isEmpty()) {
            return "Please provide at least one integer as arguments."
        }
        var sum = 0
        return try {
            args.forEach { arg ->
                sum += arg.trim().toInt()
            }
            sum.toString()
        } catch (e: NumberFormatException) {
            "Error: All arguments must be integers."
        }
    }
}

val additionsModule = module {
    single { SumService() }
}
