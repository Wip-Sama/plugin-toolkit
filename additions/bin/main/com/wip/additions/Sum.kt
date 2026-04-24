package com.wip.additions

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Please provide at least two integers as arguments.")
        return
    }
    var sum = 0
    try {
        args.forEach { arg ->
            sum += arg.toInt()
        }
    } catch (e: NumberFormatException) {
        println("Error: All arguments must be integers.")
        return
    }
    println(sum)
}
