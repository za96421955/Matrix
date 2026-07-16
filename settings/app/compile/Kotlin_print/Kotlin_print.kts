val json = args[0]
val regex = Regex("\"print\"\\s*:\\s*\"([^\"]*)\"")
println(regex.find(json)?.groupValues?.get(1) ?: "")