import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
fun exec(cmd: String): String = memScoped {
    val pipe = popen(cmd, "r") ?: error("popen failed")

    val buffer = ByteArray(4096)
    val output = StringBuilder()

    while (true) {
        val read = fgets(buffer.refTo(0), buffer.size, pipe) ?: break
        output.append(read.toKString())
    }

    pclose(pipe)
    output.toString()
}

fun greet() {
    println("Hello from Kotlin/Native!")
}

fun help(){
    println("Available commands:")
    println("  greet    - Print a greeting message")
}

fun start() {
    println("Starting the service...")
}
fun stop() {
    println("Stopping the service...")
}
fun install() {
    println("Installing the service...")
    exec("adb install -r droidsink.apk")
    println(exec("adb devices"))
}
fun run() {
    println("Running the service...")
}

fun main(args: Array<String>) {
    if(args.isEmpty()) {
        help()
        return
    }
    val (command) = args

    when(command) {
        "install" -> install()
        "start" -> start()
        "stop" -> stop()
        "run" -> run()
        else -> println("Unknown command: $command").also { help() }
    }
}
