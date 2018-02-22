package duke
import org.xml.sax.Attributes
import org.xml.sax.InputSource
//import org.xml.sax.SAXException
import org.xml.sax.helpers.XMLFilterImpl

//import org.xml.sax.XMLReader
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

class CombinedFilter(val filters: List<TypedFilter<Record>>) : XMLFilterImpl() {
    var currentRecord = Record()

    var inObject = false
    var recordList = mutableListOf<Record>()

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("object" == qName) {
            inObject = true
        }

        if (inObject) {
            filters.forEach {
                it.startElement(uri, localName, qName, atts)
            }
        }
        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
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
            recordList.add(currentRecord)

            currentRecord = Record()

            filters.forEach {
                it.reset(currentRecord)
            }
        }

        if (contentHandler != null) {
            contentHandler.endElement(uri, localName, qName)
        }
    }

    fun records(): List<Record> {
        return recordList
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

open class TypedFilter<T>(t: T) : XMLFilterImpl() {
    var value = t
    fun item(): T { return value }
    fun reset(t: T) { value = t }
}

class RecordFilter(record: Record) : TypedFilter<Record>(record) {

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("record" == qName) {
            value.number = atts.getValue("number")
            println("RECORD ${atts.getValue("number")}")
        }

        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }
}

class ItemFilter(var record: Record) : TypedFilter<Record>(record) {

    override fun startElement(uri: String, localName: String, qName: String,
                              atts: Attributes) {
        if ("item" == qName) {
            val item = Item(atts.getValue("name"))
            value.item = item
            println("ITEM ${atts.getValue("name")}")
        }

        // Pass the event to downstream filters.
        if (contentHandler != null) {
            contentHandler.startElement(uri, localName, qName, atts)
        }
    }
}

class Driver() {

    fun parseCombined() {
        val parser = XMLReaderFactory.createXMLReader()

        val record = Record()
        val filterList = listOf<TypedFilter<Record>>(
                RecordFilter(record),
                ItemFilter(record)
        )

        val combinedFilter = CombinedFilter(filterList)
        combinedFilter.parent = parser
        combinedFilter.parse(InputSource(StringReader(xmlMultiple)))

        println(combinedFilter.records())
    }

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
}

fun main(args: Array<String>) {

    val driver = Driver()
    println("**** parse 1:")
    driver.parseSingle()
    println("***** parse 2:")
    driver.parseCombined()
}