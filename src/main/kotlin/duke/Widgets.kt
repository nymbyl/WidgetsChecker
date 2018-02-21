package duke

import java.io.File

import kotlin.system.measureTimeMillis

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async

import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.ReceiveChannel

import com.natpryce.konfig.stringType
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.getValue
import com.natpryce.konfig.PropertyGroup

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.core.ResponseDeserializable

import com.google.gson.Gson

data class Person(val uri: String, val alternateId: String)

data class People(val numFound: Int, val offset: Int, val items: ArrayList<Person>) {

    class Deserializer : ResponseDeserializable<People> {
        override fun deserialize(content: String) = Gson().fromJson(content, People::class.java)
    }
}

/* NOTE: the method has paging and will only show 1000 at a time */
fun getListOfPeople(): People? {
    val path = "/widgets/search/modified.json?since=2018-02-20"
    val (request, response, result) = path.httpGet().responseObject(People.Deserializer())
    val (people, error) = result

    if (error != null) {
        println(error)
    }
    return people
}

fun producePeople() = produce<Person>(CommonPool) {
    val people = getListOfPeople()

    if (people != null) {
        println("******** People Loaded ${people.numFound} *******")
        for (person in people.items) {
            send(person)
        }
    }
}

data class WidgetsPerson(val uri: String, val label: String, val attributes: Map<String, String>) {

    class Deserializer : ResponseDeserializable<WidgetsPerson> {
        override fun deserialize(content: String) = Gson().fromJson(content, WidgetsPerson::class.java)
    }
}

fun widgetsPersonResult(person: Person): Deferred<String> {
    var uri = person.uri
    var path = "/widgets/api/v0.9/people/complete/all.json?uri=$uri"

    return async(CommonPool) {
        // NOTE: this is sync version
        val (request, response, result) = path.httpGet().responseObject(WidgetsPerson.Deserializer())

        //path.httpGet().responseObject(WidgetsPerson.Deserializer()) { request, response, result ->
        val (person, error) = result

        var flag: Boolean = false
        var summary: String = ""

        if (person != null) {
            val imageUri = person.attributes["imageUri"]
            flag = true
            summary = "widgets:imageUri=$imageUri $flag"
        } else {
            flag = false
            summary = "widget:null $flag"
        }
        //return
        summary
    }
}

fun vivoPersonResult(person: Person): Deferred<String> {
    var uri = person.uri

    var path = uri.replace("https://scholars.duke.edu", "").replace("/individual/", "/display/")

    return async(CommonPool) {
        val (request, response, result) = path.httpGet().responseString()
        val (data, error) = result

        var flag: Boolean = false
        var summary: String = ""

        when (result) {
            is Result.Failure -> { flag = false }
            is Result.Success -> {
                val html = data as String

                when {
                    html.contains("img class=\"individual-photo\"") -> { flag = true }
                }
            }
        }
        summary = "vivo $path: $flag"
        summary
    }
}

fun asyncProcessor(channel: ReceiveChannel<Person>, results: MutableMap<Person, ArrayList<String>>,
                   thread: Int) = async {
    channel.consumeEach { person ->
        println("uri:${person.uri}, thread:$thread")

        val widgets = widgetsPersonResult(person)
        val widgetsSummary = widgets.await()

        val vivo = vivoPersonResult(person)
        val vivoSummary = vivo.await()

        results[person] = ArrayList<String>()
        results[person]?.add(widgetsSummary)
        results[person]?.add(vivoSummary)
    }
}

object configuration : PropertyGroup() {
    val directory by stringType
}

object widgets : PropertyGroup() {
    val url by stringType
}

/**
 * one system property must be set 'configuration.directory'
 * e.g. -Dconfiguration.directory=conf
 */
fun main(args: Array<String>) = runBlocking<Unit> {
    val config = ConfigurationProperties.systemProperties()

    val configFile = File("${config[configuration.directory]}/scholars.properties")
    val widgetsConfig = ConfigurationProperties.fromFile(configFile)

    var baseUrl = widgetsConfig[widgets.url]

    FuelManager.instance.basePath = baseUrl

    // place to store results - making map first
    val results = mutableMapOf<Person, ArrayList<String>>()

    val producer = producePeople()

    var processors = ArrayList<Deferred<Unit>>()
    val timeElapsed = measureTimeMillis {
        repeat(10) {
            processors.add(asyncProcessor(producer, results, it))
        }

        processors.forEach { it.await() }
    }

    val size = results.size
    println("*** results $size****")
    for ((k, v) in results) {
        // check for :widgets or :vivo
        println(k)
        for (a in v) {
            println("--->$a")
        }
    }
    println("*** results: $size****")
    println("*** took: $timeElapsed ms***")

    // *** took: 28773 ms***
    // *** took: 120292 ms***
}
