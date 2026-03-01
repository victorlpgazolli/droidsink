package model.command

fun printHelp() {
    println("Available commands:")
    allCommands.forEach { command ->
        println("   ${command.name}: ${command.description}")
        command.parameters.forEach { parameter ->
            val defaultValue = "(default: ${parameter.defaultValue})".takeIf { parameter.defaultValue != null } ?: ""
            println("       ${parameter.name}: ${parameter.description} $defaultValue")
        }
        println()
    }
}