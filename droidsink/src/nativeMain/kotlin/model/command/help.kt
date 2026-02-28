package model.command

fun printHelp() {
    println("Available commands:")
    allCommands.forEach { command ->
        println("   ${command.name}: ${command.description}")
        command.parameters.forEach {
            println("       ${it.name}: ${it.description}")
        }
        println()
    }
}