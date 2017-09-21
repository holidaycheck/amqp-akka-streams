# amqp-akka-streams

This library provides you Sinks and Sources that you can use for producing and consuming messages from AMQP queue.
It relies on [RabbitMQ Java Client](http://www.rabbitmq.com/java-client.html) in version 4.x but it should work
flawlessly with any broker compliant with AMQP protocol in versions 0-9-1, 0-9 or 0-8.

The utilities are grouped in class of `AmqpProducer` (for producing) and `AmqpConsumer` (for consuming) which rely on 
common implicit dependency of `AmqpConnection`. Connection class provides you access to resource handling, like ability 
to check the connection status and to close the connection to the broker. 

## AmqpProducer
`AmqpProducer` holds Akka Stream's Sink. It is intended to be used to publish messages to AMQP queue or AMQP exchange. 
Producer requires implicit parameter of `PayloadMarshaller[T]` for data serialization and `AmqpConnection` for access
to AMQP broker.

*Example usage*
```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.holidaycheck.streams.amqp.AmqpProducer
import com.holidaycheck.streams.amqp.AmqpConnection

implicit val system: ActorSystem = ActorSystem()
implicit val materializer: ActorMaterializer = ActorMaterializer()
implicit val stringMarshaller: AmqpProducer.PayloadMarshaller[String] = ByteString(_)

val connectionConfiguration = AmqpConnection.Configuration(
  host = "localhost",
  port = 5672,
  virtualHost = "vh",
  username = "guest",
  password = "guest"
)
implicit val connection: AmqpConnection = AmqpConnection(connectionConfiguration)
val producer = AmqpProducer(AmqpProducer.Configuration.publishToQueue("queue"))

Source.single("test message").runWith(producer.sink).flatMap { _ =>
  connection.shutdown() // closes the connection and cleanups the resources
}

```

## AmqpConsumer
`AmqpConsumer` holds two objects: Source from which you can read incoming messages and Sink where you can *ack* them.
Acking is necessary in order to inform the message broker that the message is successfully consumed. Consumer requires
implicit parameter of `PayloadUnmarshaller[T]` for data deserialization and `AmqpConnection` for access to AMQP broker. 
By default consumer provides `Delivery` with body of `ByteString` unless you provide it own instance of unmarshaller.

*Example usage*
```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.holidaycheck.streams.amqp.AmqpConsumer
import com.holidaycheck.streams.amqp.AmqpConnection

implicit val system: ActorSystem = ActorSystem()
implicit val materializer: ActorMaterializer = ActorMaterializer()
implicit val stringUnmarshaller: AmqpConsumer.PayloadUnmarshaller[String] = _.decodeString("utf-8")

val connectionConfiguration = AmqpConnection.Configuration(
  host = "localhost",
  port = 5672,
  virtualHost = "vh",
  username = "guest",
  password = "guest"
)
implicit val connection: AmqpConnection = AmqpConnection(connectionConfiguration)
val consumer = AmqpConsumer(AmqpConsumer.Configuration("queue"))

consumer.source.map { delivery =>
  println(s"Consuming ${delivery.body}...")
  delivery
}.runWith(consumer.sink).flatMap { _ =>
  connection.shutdown() // closes the connection and cleanups the resources
}

```

## How to add it to your project
The library is available both for Scala 2.11.x and 2.12.x. All you have to do is to add the dependency:
```scala
"com.holidaycheck" %% "amqp-akka-streams" % "2.0.0"
```