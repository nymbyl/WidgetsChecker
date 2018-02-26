package duke

import org.xml.sax.Attributes
import org.xml.sax.InputSource
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

interface Copyable {
    fun copy()
}

open class SingleFilter<T>(t: T) : XMLFilterImpl() {
    var value = t
    fun item(): T { return value }
    //fun reset(new: T) { value = new }
}

class CombinedAsSingleFilter<T>(t: T, val filters: List<SingleFilter<T>>) : SingleFilter<T>(t) {
    var inObject: Boolean = false

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("object" == qName) {
            inObject = true
        }
        filters.forEach {
            it.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
            if (inObject) {

                filters.forEach {
                    it.endElement(uri, localName, qName)
                }
            }

            if ("object" == qName) {
                inObject = false
            }
        }
}

/* just parses for a single repeating object */
class RepeaterFilter<T>(var filter: SingleFilter<T>, var callback: (T) -> Unit) : XMLFilterImpl() {
    var inObject = false

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("object" == qName) {
            inObject = true
        }

        if (inObject) {
            filter.startElement(uri, localName, qName, atts)
        }
        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {

        if (inObject) {
            filter.endElement(uri, localName, qName)
        }

        if ("object" == qName) {
            inObject = false
            callback(filter.item())
        }

        if (contentHandler != null) {
            contentHandler.endElement(uri, localName, qName)
        }
    }
}

class ObjectFilter() : XMLFilterImpl() {

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("object" == qName) {
            println("OBJECT")
        }

        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }
}

class RecordFilter(record: Record) : SingleFilter<Record>(record) {

    var currentNumber: String? = null

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("record" == qName) {
            currentNumber = atts.getValue("number")
            value.number = currentNumber
        }

        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if ("record" == qName) {
            value.number = currentNumber
            // NOTE: if we don't null - it will retain value previously assigned
            currentNumber = null
        }

        if (contentHandler != null) {
            contentHandler.endElement(uri, localName, qName)
        }
    }
}

class ItemFilter(var record: Record) : SingleFilter<Record>(record) {

    var currentName: String? = null

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {

        if ("item" == qName) {
            currentName = atts.getValue("name")
        }

        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        // in another words, one up - in case item happens to be null ??
        if ("record" == qName) {
            var item = Item(currentName)
            value.item = item
            currentName = null
        }

        if (contentHandler != null) {
            contentHandler.endElement(uri, localName, qName)
        }
    }
}

class Driver() {

    fun parseSingle() {
        val parser = XMLReaderFactory.createXMLReader()

        val objectFilter = ObjectFilter()
        objectFilter.parent = parser

        var record = Record()
        val recordFilter = RecordFilter(record)
        recordFilter.setParent(objectFilter)

        val itemFilter = ItemFilter(record)
        itemFilter.setParent(recordFilter)

        itemFilter.parse(InputSource(StringReader(xmlObject)))
        println(record)
    }

    fun parseCombined() {
        val parser = XMLReaderFactory.createXMLReader()

        val record = Record()

        var recordList = mutableListOf<Record>()

        val combinedFilter = RepeaterFilter(RecordFilter(record), {
            // NOTE: have to copy cause it TypedFilter continually
            // populates object
            recordList.add(it.copy())
        })
        combinedFilter.parent = parser
        combinedFilter.parse(InputSource(StringReader(xmlMultiple)))

        println(recordList)
    }

    /*
    fun parseCombinedMultiple() {
        val parser = XMLReaderFactory.createXMLReader()

        val record = Record()
        val filterList = listOf(
                RecordFilter(record.copy()),
                ItemFilter(record.copy())
        )

        var recordList = mutableListOf<Record>()

        val multipleFilter = CombinedFilter(record, filterList, {
            // NOTE: have to copy cause it TypedFilter continually
            // populates object
            recordList.add(it.copy())
        })

        multipleFilter.parent = parser
        multipleFilter.parse(InputSource(StringReader(xmlMultiple)))

        println(recordList)
    }
    */

    fun parseMultipleAsSingle() {
        val parser = XMLReaderFactory.createXMLReader()

        val record = Record()

        var recordList = mutableListOf<Record>()

        val record2 = Record()
        val filterList = listOf(
                RecordFilter(record),
                ItemFilter(record)
        )
        val filter = CombinedAsSingleFilter(record, filterList)
        val combinedFilter = RepeaterFilter(filter, {
            // NOTE: have to copy cause it TypedFilter continually
            // populates object
            recordList.add(it.copy())
        })
        combinedFilter.parent = parser
        combinedFilter.parse(InputSource(StringReader(xmlMultiple)))

        println(recordList)
    }
}

fun main(args: Array<String>) {

    val driver = Driver()
    println("**** parse 1:")
    driver.parseSingle()

    println("***** parse 2:")
    driver.parseCombined()

    //println("****** parse 3:")
    //driver.parseCombinedMultiple()

    println("****** parse 4:")
    driver.parseMultipleAsSingle()
}