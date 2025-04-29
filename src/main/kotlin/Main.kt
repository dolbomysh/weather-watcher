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
suspend fun getCurrentWeather(latitude: Double, longitude: Double, date: String, time: Int): String {
    val response = getWeather(latitude, longitude, date, date)
    return response.hourly.temperatures[time].toString() + response.hourlyUnits.temperatureUnit
}

data class ExtremeWeather(val minTemperature: String, val maxTemperature: String,
                          val minData: String, val maxData: String)

// экстремальные значения погоды в промежутке времени
suspend fun getExtremeWeather(latitude: Double, longitude: Double,
                              startDate: String, endDate: String): ExtremeWeather {
    val response = getWeather(latitude, longitude, startDate, endDate)

    var minTemperature : Double = 500.0
    var maxTemperature : Double = -500.0
    var minIndex = 0
    var maxIndex = 0

    for (i in 0..<response.hourly.temperatures.size) {
        if (response.hourly.temperatures[i] >= maxTemperature) {
            maxTemperature = response.hourly.temperatures[i]
            maxIndex = i
        }
        if (response.hourly.temperatures[i] <= minTemperature) {
            minTemperature = response.hourly.temperatures[i]
            minIndex = i
        }
    }
    val unit = response.hourlyUnits.temperatureUnit
    return ExtremeWeather(maxTemperature.toString() + unit, maxTemperature.toString() + unit,
                          response.hourly.time[minIndex], response.hourly.time[maxIndex])
}

// все возможные проверки корректности введенных данных


// координаты
fun checkCoordinates(latitude: String, longitude: String): Boolean {
    return try {
        val latitudeInt = latitude.toDouble()
        val longitudeInt = longitude.toDouble()

        (-90 <= latitudeInt) and (latitudeInt <= 90) and (-180 <= longitudeInt) and (longitudeInt <= 180)
    } catch (e: NumberFormatException) {
        false
    }
}

// дата
fun checkDate(dateString: String): Boolean {
   return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateFormated = LocalDate.parse(dateString, formatter)
        val currentDate = LocalDate.now()

        return dateFormated.isBefore(currentDate) || dateFormated.isEqual(currentDate)
    } catch (e: DateTimeParseException) {
         false
    }
}

// порядок дат
fun checkTwoDates(startDate : String, endDate : String): Boolean {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormated = LocalDate.parse(startDate, formatter)
    val endDateFormated = LocalDate.parse(endDate, formatter)

    return startDateFormated.isBefore(endDateFormated) || startDateFormated.isEqual(endDateFormated)
}

//время
fun checkTime(time: String): Boolean {
    val parsedTime = time.split(":")
    if (parsedTime.size != 2) return false

    return try {
        val hours = parsedTime[0].toInt()
        val minutes = parsedTime[1].toInt()

        hours in 0..23 && minutes in 0..59
    } catch (e: NumberFormatException) {
        false
    }
}

// общая проверка аргументов
fun checkArguments(scenario: Int, argumentsString: String): Boolean {
    val arguments = argumentsString.split(" ")
    if (arguments.size != 4) {
        println("Введено некорректное число аргументов\n")
        return false
    }
    if (!checkCoordinates(arguments[0], arguments[1])) {
        println("Введены некорректные координаты\n")
        return false
    }
    if (!checkDate(arguments[2])) {
        println("Введена некорректная дата\n")
        return false
    }

    if (scenario == 1) {
        if (!checkTime(arguments[3])) {
            println("Введено некорректное время\n")
            return false
        }
    } else if (scenario == 2) {
        if (!checkDate(arguments[3])) {
            println("Введена некорректная дата\n")
            return false
        }
        if (!checkTwoDates(arguments[2], arguments[3])) {
            println("Введен некорректный порядок дат\n")
            return false
        }
    }
    return true
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
            if (!checkArguments(1, argumentsString)) {
                continue
            }
            val arguments = argumentsString.split(" ")
            val temperature = getCurrentWeather(
                arguments[0].toDouble(), arguments[1].toDouble(),
                arguments[2], arguments[3].substring(0, 2).toInt()
            )

            println("Температура по запросу $temperature\n")
        } else if (scenario == "2") {
            println(
                "Введите данные в формате : \n" +
                        "широта долгота день старта(гггг-мм-дд) день окончания(гггг-мм-дд)\n"
            )

            val argumentsString = readln()
            println()
            if (!checkArguments(2, argumentsString)) {
                continue
            }
            val arguments = argumentsString.split(" ")
            val extremeTemperatures =
                getExtremeWeather(arguments[0].toDouble(), arguments[1].toDouble(), arguments[2], arguments[3])


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