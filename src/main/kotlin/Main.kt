import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// хранилища для приходящих данных
@Serializable
data class WeatherResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("hourly_units") val hourlyUnits: HourlyUnits = HourlyUnits(),
    val hourly: HourlyData = HourlyData()
)

@Serializable
data class HourlyUnits(
    @SerialName("temperature_2m") val temperatureUnit: String = ""
)

@Serializable
data class HourlyData(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperatures: List<Double> = emptyList()
)

// общая функция для получения данных о погоде в промежутке времени
suspend fun getWeather(latitude: Double, longitude: Double, startDate: String, endDate: String): WeatherResponse {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }

    try {
        val response: WeatherResponse = client.get("https://archive-api.open-meteo.com/v1/archive") {
            url {
                parameters.append("latitude", latitude.toString())
                parameters.append("longitude", longitude.toString())
                parameters.append("hourly", "temperature_2m")
                parameters.append("start_date", startDate)
                parameters.append("end_date", endDate)
            }
        }.body()
        return response

    } catch (exception: Exception) {
        println("Ошибка: ${exception.stackTraceToString()}")
    } finally {
        client.close()
    }
    return WeatherResponse()
}

// погода в данном месте в данное время
suspend fun getCurrentWeather(arguments : ArgumentsForCurrentWeather): String {
    val response = getWeather(arguments.coordinates.latitude, arguments.coordinates.longitude,
                              arguments.date, arguments.date)
    return response.hourly.temperatures[arguments.time].toString() + response.hourlyUnits.temperatureUnit
}

data class ExtremeWeather(val minTemperature: String, val maxTemperature: String,
                          val minData: String, val maxData: String)

// экстремальные значения погоды в промежутке времени
suspend fun getExtremeWeather(arguments : ArgumentsForExtremalWeather): ExtremeWeather {
    val response = getWeather(arguments.coordinates.latitude, arguments.coordinates.longitude,
                              arguments.startDate, arguments.endDate)

    val temperatures = response.hourly.temperatures
    val (minimumTemperatureIndex, minimumTemperature) = temperatures.withIndex().minBy { it.value }
    val (maximumTemperatureIndex, maximumTemperature) = temperatures.withIndex().minBy { it.value }

    val unit = response.hourlyUnits.temperatureUnit
    return ExtremeWeather(minimumTemperature.toString() + unit,
                          maximumTemperature.toString() + unit,
                          response.hourly.time[minimumTemperatureIndex],
                          response.hourly.time[maximumTemperatureIndex])
}

// все возможные проверки корректности введенных данных

// координаты
data class Coordinates(val latitude: Double, val longitude: Double)
fun checkCoordinates(latitude: String, longitude: String): Coordinates? {
    val latitudeInt = latitude.toDoubleOrNull()
    val longitudeInt = longitude.toDoubleOrNull()
    if (latitudeInt == null || longitudeInt == null) {
        return null
    }
    if  (latitudeInt in -90.0..90.0 &&  longitudeInt in -180.0..180.0) {
        return Coordinates(latitudeInt, longitudeInt)
    }
    return null
}

// дата
fun checkDate(dateString: String): Boolean {
   return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateFormated = LocalDate.parse(dateString, formatter)
        val currentDate = LocalDate.now()

        return dateFormated <= currentDate
    } catch (e: DateTimeParseException) {
         false
    }
}

// порядок дат
fun checkTwoDates(startDate : String, endDate : String): Boolean {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormated = LocalDate.parse(startDate, formatter)
    val endDateFormated = LocalDate.parse(endDate, formatter)

    return startDateFormated <= endDateFormated
}

//время
fun checkTime(time: String): Int? {
    val parsedTime = time.split(":")
    if (parsedTime.size != 2) {
        return null
    }

    val hours = parsedTime[0].toIntOrNull()
    val minutes = parsedTime[1].toIntOrNull()

    if (hours == null || minutes == null) {
        return null
    }

    if (hours in 0..23 && minutes in 0..59) {
        return hours
    }
    return null
}

data class ArgumentsForCurrentWeather(val coordinates: Coordinates, val date: String, val time: Int)

// общая проверка аргументов(случай 1)
fun checkAndParseCurrentArguments(argumentsString: String): ArgumentsForCurrentWeather? {
    val arguments = argumentsString.split(" ")
    if (arguments.size != 4) {
        println("Введено некорректное число аргументов\n")
        return null
    }
    val coordinates = checkCoordinates(arguments[0], arguments[1])
    if (coordinates == null) {
        println("Введены некорректные координаты\n")
        return null
    }
    val date = arguments[2]
    if (!checkDate(date)) {
        println("Введена некорректная дата\n")
        return null
    }
    val time = checkTime(arguments[3])
    if (time == null) {
        println("Введено некорректное время\n")
        return null
    }
    return ArgumentsForCurrentWeather(coordinates, date, time)
}

data class ArgumentsForExtremalWeather(val coordinates: Coordinates, val startDate: String, val endDate: String)

// общая проверка аргументов (случай 2)
fun checkAndParseExtremalArguments(argumentsString: String): ArgumentsForExtremalWeather? {
    val arguments = argumentsString.split(" ")
    if (arguments.size != 4) {
        println("Введено некорректное число аргументов\n")
        return null
    }
    val coordinates = checkCoordinates(arguments[0], arguments[1])
    if (coordinates == null) {
        println("Введены некорректные координаты\n")
        return null
    }
    val startDate = arguments[2]
    val endDate = arguments[3]
    if (!checkDate(startDate) || !checkDate(endDate)) {
        println("Введена некорректная дата\n")
        return null
    }
    if (!checkTwoDates(startDate, endDate)) {
        println("Введен некорректный порядок дат\n")
        return null
    }
    return ArgumentsForExtremalWeather(coordinates, startDate, endDate)
}

suspend fun main() {
    println("Доброго времени суток! \n")

    while (true) {
        println(
            "Для того чтобы посмотреть погоду в конкретном месте в конкретное время, введите 1 \n" +
                    "Для того, чтобы узнать минумум и максимум за промежуток времени, введите 2 \n"
        )
        val scenario = readln()
        println()

        if (scenario == "1") {
            println(
                "Введите данные в формате : \n" +
                        "широта долгота день(гггг-мм-дд) время(чч:мм)\n"
            )
            val argumentsString = readln()
            println()
            val parsedArguments = checkAndParseCurrentArguments(argumentsString) ?: continue
            val temperature = getCurrentWeather(parsedArguments)

            println("Температура по запросу $temperature\n")
        } else if (scenario == "2") {
            println(
                "Введите данные в формате : \n" +
                        "широта долгота день старта(гггг-мм-дд) день окончания(гггг-мм-дд)\n"
            )

            val argumentsString = readln()
            println()
            val parsedArguments = checkAndParseExtremalArguments(argumentsString) ?: continue
            val extremeTemperatures =
                getExtremeWeather(parsedArguments)


            println(
                "В данный промежуток времени максимальная температура ${extremeTemperatures.maxTemperature} достигалась " +
                        "${extremeTemperatures.maxData.substringBefore('T')} в ${extremeTemperatures.maxData.substringAfter('T')} \n" +
                        "Минимальная температура ${extremeTemperatures.minTemperature} достигалась " +
                        "${extremeTemperatures.minData.substringBefore('T')} в ${extremeTemperatures.minData.substringAfter('T')} \n"
            )

        } else {
            println("Введено некорректное число\n")
        }
    }
}