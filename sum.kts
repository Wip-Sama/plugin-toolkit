if (args.size < 2) {
    println("Please provide two integers as arguments.")
} else {
    val num1 = args[0].toIntOrNull()
    val num2 = args[1].toIntOrNull()

    if (num1 != null && num2 != null) {
        println(num1 + num2)
    } else {
        println("Error: Both arguments must be integers.")
    }
}
