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

import org.jetbrains.exposed.sql.Database

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SchemaUtils.create

import org.jetbrains.squash.definition.TableDefinition
import org.jetbrains.squash.dialects.h2.H2Connection
import org.jetbrains.squash.statements.insertInto
import org.jetbrains.squash.statements.values
import org.jetbrains.squash.statements.fetch

import org.jetbrains.squash.definition.varchar
import org.jetbrains.squash.definition.integer
import org.jetbrains.squash.definition.primaryKey
import org.jetbrains.squash.definition.autoIncrement
import org.jetbrains.squash.definition.reference
import org.jetbrains.squash.definition.nullable

import org.jetbrains.squash.query.from
import org.jetbrains.squash.query.where
import org.jetbrains.squash.query.select

//
//import org.jetbrains.squash.graph.SourceHolder

import org.jetbrains.squash.expressions.eq
import org.jetbrains.squash.graph.bind
import org.jetbrains.squash.results.get

/*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource

var ds: DataSource = SimpleDriverDataSource(LegacyDriver::class.java!!,
        "jdbc:legacy://database", "username", "password")
var jdbc = JdbcTemplate(ds)


import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

http://zetcode.com/db/jdbctemplate/

https://hackernoon.com/spring-5-jdbc-support-for-kotlin-7cc31f4db4a5

https://github.com/seratch/kotliquery
https://github.com/MicroUtils/kotlin-logging
https://medium.com/@OhadShai/first-steps-with-kotlin-exposed-cb361a9bf5ac

public class SpringDBQueryObject {

    public static void main(String[] args) throws SQLException {

        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriver(new com.mysql.jdbc.Driver());
        ds.setUrl("jdbc:mysql://localhost:3306/testdb");
        ds.setUsername("user7");
        ds.setPassword("s$cret");

        String sql = "SELECT Count(*) FROM Cars";

        JdbcTemplate jtm = new JdbcTemplate(ds);
        int numOfCars = jtm.queryForObject(sql, Integer.class);

        System.out.format("There are %d cars in the table", numOfCars);
    }
}
*/

// https://hackernoon.com/spring-5-jdbc-support-for-kotlin-7cc31f4db4a5
// https://blog.philipphauer.de/do-it-yourself-orm-alternative-hibernate-drawbacks/
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

fun jdbiExample() {
    val jdbi: Jdbi = Jdbi.create("jdbc:h2:mem:test") // (H2 in-memory database)
    // NOTE: strangely have to call this
    jdbi.installPlugins()
    jdbi.installPlugin(KotlinPlugin())
    jdbi.installPlugin(KotlinSqlObjectPlugin())
    jdbi.installPlugin(H2DatabasePlugin())

    var handle: Handle = jdbi.open()

    // could do something like this:
    /*
    with(handle) {
        var con = getConnection()
        handle.close()
    }
    */
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

    con.close()
}

object Cities : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val name = varchar("name", 50)
}

// Entity definition
data class City(
    val id: Int,
    val name: String
)

// FIXME: would like to be able to do something
// like this (like in SQLAlchemy)
// mapper(City, Cities)
// e.g.
//mapper(User, user, properties={
//    'addresses' : relationship(Address, backref='user', order_by=address.c.id)
//})
//
//mapper(Address, address)
/*
maybe using something like this:

    private fun mapToUser(rs: ResultSet, rowNum: Int) = User(
            id = rs.getInt("id")
            , email = rs.getString("email")
            , name = mergeNames(rs) // execute custom mapping logic
            , role = if (rs.getBoolean("guest")) Role.GUEST else Role.USER //understandable and direct type conversion
            , dateCreated = rs.getTimestamp("date_created").toInstant()
            , state = State.valueOf(rs.getString("state"))
    )
 */
fun exposedExample() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    // NOTE: this is threadlocal static - might be an issue
    // https://medium.com/@OhadShai/first-steps-with-kotlin-exposed-cb361a9bf5ac
    transaction {
        // print sql to std-out
        create (Cities)

        // insert new city. SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
        val stPeteId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        for (city in Cities.selectAll()) {
            //println(city)
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }
    }
}

object Cities2 : TableDefinition() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
}

object Citizens : TableDefinition() {
    val id = varchar("id", 10).primaryKey()
    val name = varchar("name", length = 50)
    val cityId = reference(Cities2.id, "city_id").nullable()
}

//data class City(val id: Int, val name: String)
data class Citizen(val id: String, val name: String, val city: City)

interface Load {
    val name: String
    val value: Int
}
/*
mysql connection might be something lke this:
  var transaction = MySqlConnection.create(url, user, password)
}
example queries:

https://github.com/orangy/squash/blob/171055eed6a36f36c6237caf68446fdca9799167/squash-core/test/org/jetbrains/squash/tests/QueryTests.kt
 */
fun squashExample() {
    // NOTE: not sure the best way to do this (typically)
    var transaction = H2Connection.createMemoryConnection().createTransaction()

    transaction.apply {
        connection.monitor.before {
            println(it)
        }
        // takes a list - could be one though
        databaseSchema().create(listOf(Citizens, Cities2))

        val munichId = insertInto(Cities2).values {
            it[name] = "Munich"
        }.fetch(Cities2.id).execute()

        insertInto(Citizens).values {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
        }.execute()

        val stmt = from(Citizens)
        val qry = stmt.where(Citizens.id eq "eugene" )

        // sourceholder object (can only 'bind' to interface)
        val citizen = qry.bind<Load>(Citizens).execute().single()

        println(citizen)
        println(citizen.name)

        val row = from(Citizens)
                .where { Citizens.id eq "eugene" }
                .select(Citizens.name, Citizens.id, Citizens.cityId)
                //.select("*") -note this does not work
                .execute()
                .single()

        print("citizen=${row[Citizens.id]}")
        print("city=${row[Citizens.cityId]}")
        // or just row.get(Citizens.id)
    }
}

fun main(args: Array<String>) {
    //jdbiExample()
    exposedExample()
    // kind of like squash better, but exposed is better maintained, supported
    //squashExample()
}
