package duke

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.helpers.XMLFilterImpl

import org.xml.sax.helpers.XMLReaderFactory
import java.io.StringReader

val xmlObject = """
<feed>
  <object>
    <records>
      <record number="1">
        <item name="item-1"></item>
      </record>
    </records>
  </object>
</feed>
"""

val xmlMultiple = """
<feed>
  <object>
    <records>
      <record number="1">
        <item name="item-1"></item>
      </record>
    </records>
  </object>
  <object>
    <records>
      <record number="2">
        <item name="item-2"></item>
      </record>
    </records>
  </object>
</feed>
"""

data class Item(var name: String? = null)
data class Record(var number: String? = null, var item: Item? = null)

abstract class SingleFilterThing<T>() : DefaultHandler() {
    var value: T? = null
    open fun getThing(): T? { return value }
    open fun setThing(new: T) { value = new }
}

class RecordFilterThing() : SingleFilterThing<Record>() {
    var currentNumber: String? = null

    override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
        if ("record" == qName) {
            currentNumber = atts.getValue("number")
            value?.let { it.number = currentNumber }
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if ("record" == qName) {
            value?.let { it.number = currentNumber }
            // NOTE: if we don't null - it will retain value previously assigned
            currentNumber = null
        }
    }
}

class ItemFilterThing() : SingleFilterThing<Record>() {
    var currentName: String? = null

    override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {

        if ("item" == qName) {
            currentName = atts.getValue("name")
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if ("record" == qName) {
            var item = Item(currentName)
            value?.let { it.item = item }
            currentName = null
        }
    }
}

class CombinedFilterThing<T>(val filters: List<SingleFilterThing<T>>) : SingleFilterThing<T>() {

    override fun setThing(new: T) {
        filters.forEach {
            it.setThing(new)
        }
        value = new
    }
    override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
        filters.forEach {
            it.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        filters.forEach {
            it.endElement(uri, localName, qName)
        }
    }
}

class RepeaterFilterThing<T>(val filter: SingleFilterThing<T>,
    var start: (SingleFilterThing<T>) -> Unit,
    var end: (SingleFilterThing<T>) -> Unit) : XMLFilterImpl() {

    var inObject = false

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        filter.characters(ch, start, length)
    }
    override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {

        if ("object" == qName) {
            inObject = true
            start(filter)
        }

        if (inObject) {
            filter.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {

        if (inObject) {
            filter.endElement(uri, localName, qName)
        }

        if ("object" == qName) {
            inObject = false
            end(filter)
        }
    }
}

class Driver() {

    fun parseSingle() {
        //val parserFactory:SAXParserFactory = SAXParserFactory.newInstance()
        //val parser: SAXParser = parserFactory.newSAXParser()
        val parser = XMLReaderFactory.createXMLReader()

        var record = Record()
        val recordFilter = RecordFilterThing()
        recordFilter.setThing(record)
        parser.contentHandler = recordFilter

        parser.parse(InputSource(StringReader(xmlObject)))
        //parser.parse(InputSource(StringReader(xmlObject)), recordFilter)
        //itemFilter.parse(InputSource(StringReader(xmlObject)))
        println(record)
    }

    fun parseSingleCombined() {
        val parser = XMLReaderFactory.createXMLReader()

        var record = Record()

        val recordFilter = RecordFilterThing()
        recordFilter.setThing(record)

        val itemFilter = ItemFilterThing()
        itemFilter.setThing(record)

        val combined = CombinedFilterThing(listOf(recordFilter, itemFilter))
        parser.contentHandler = combined

        parser.parse(InputSource(StringReader(xmlObject)))
        println(record)
    }

    fun parseMultipleThings() {
        val parser = XMLReaderFactory.createXMLReader()

        val combined = CombinedFilterThing(listOf(RecordFilterThing(), ItemFilterThing()))

        val records = mutableSetOf<Record>()
        val repeaterFilter = RepeaterFilterThing(combined,
                start = { it.setThing(Record()) /*; println("start") */ },
                end = { it.getThing()?.let { records.add(it.copy()) } /*; println("end")*/ }
        )

        repeaterFilter.parent = parser
        repeaterFilter.parse(InputSource(StringReader(xmlMultiple)))

        for (record in records) {
            println(record)
        }
    }
}

fun main(args: Array<String>) {
    val driver = Driver()
    println("**** parse 1:")
    driver.parseSingle()

    println("**** parse 2:")
    driver.parseSingleCombined()

    println("***** parse 3:")
    driver.parseMultipleThings()
}