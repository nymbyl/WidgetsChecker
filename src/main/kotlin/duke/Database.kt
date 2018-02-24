package duke

import java.sql.Connection
import org.jdbi.v3.core.Jdbi

import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin

import org.jdbi.v3.core.mapper.Nested
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.statement.SqlQuery

import org.jdbi.v3.core.h2.H2DatabasePlugin
import org.jdbi.v3.sqlobject.kotlin.onDemand
import org.jdbi.v3.core.Handle


data class IdAndName(val id: Int, val name: String)
data class Thing(@Nested val idAndName: IdAndName,
                 val nullable: String?,
                 val nullableDefaultedNull: String? = null,
                 val nullableDefaultedNotNull: String? = "not null",
                 val defaulted: String = "default value")

interface ThingDao {
    @SqlUpdate("insert into something (id, name) values (:something.idAndName.id, :something.idAndName.name)")
    fun insert(something: Thing)

    @SqlQuery("select id, name from something")
    fun list(): List<Thing>
}

// simpler:
//val qry = handle.createQuery("select id, name from something where id = :id")
//val things = qry.bind("id", brian.id).mapTo<Thing>.list()
//
//qryAll.mapTo<Thing>.useSequence {
//    it.forEach(::println)
//}

fun main(args: Array<String>) {
    val jdbi: Jdbi = Jdbi.create("jdbc:h2:mem:test") // (H2 in-memory database)
    // NOTE: strangely have to call this
    jdbi.installPlugins()
    jdbi.installPlugin(KotlinPlugin())
    jdbi.installPlugin(KotlinSqlObjectPlugin())
    jdbi.installPlugin(H2DatabasePlugin())

    var handle: Handle = jdbi.open()
    var con: Connection = handle.getConnection()
    con.createStatement().use({ s -> s.execute("create table something ( id identity primary key, name varchar(50), integerValue integer, intValue integer )") })

    val dao = jdbi.onDemand<ThingDao>()

    val brian = Thing(IdAndName(1, "Brian"), null)
    val keith = Thing(IdAndName(2, "Keith"), null)

    dao.insert(brian)
    dao.insert(keith)

    val rs = dao.list()
    for (r in rs) {
        println(r)
    }

}
// https://hackernoon.com/spring-5-jdbc-support-for-kotlin-7cc31f4db4a5
//https://blog.philipphauer.de/do-it-yourself-orm-alternative-hibernate-drawbacks/
