# amqp-akka-streams

This library provides you Sinks and Sources that you can use for producing and consuming messages from AMQP queue.

The utilities are grouped in class of `AmqpProducer` (for producing) and `AmqpConsumer` (for consuming) which share common
trait called `AmqpContext`. The common context interface provides you access to resource handling, like ability to check
the connection status and to close the connection to the broker. 

## AmqpProducer
`AmqpProducer` holds Akka Stream's Sink. It is intended to be used to publish messages to AMQP queue. Producer
requires implicit parameter of `PayloadMarshaller[T]` for data serialization.

*Example usage*
```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.holidaycheck.streams.amqp.AmqpProducer

implicit val system: ActorSystem = ActorSystem()
implicit val materializer: ActorMaterializer = ActorMaterializer()
implicit val stringMarshaller: AmqpProducer.PayloadMarshaller[String] = ByteString(_)

val configuration = AmqpConfiguration(/* some connection configuration */)
val producer = AmqpProducer(configuration)

Source.single("test message").runWith(producer.sink).flatMap { _ =>
  producer.shutdown() // closes the connection and cleanups the resources
}

```

## AmqpConsumer
`AmqpConsumer` holds two objects: Source form which you can read incoming messages and and Sink where you can *ack* them.
Acking is necessary in order to inform the message broker that the message is successfully consumed. Consumer requires
implicit parameter of `PayloadUnmarshaller[T]` for data deserialization. By default consumer provides `Delivery` with
body of `ByteString`.

*Example usage*
```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.holidaycheck.streams.amqp.AmqpConsumer

implicit val system: ActorSystem = ActorSystem()
implicit val materializer: ActorMaterializer = ActorMaterializer()
implicit val stringUnmarshaller: AmqpConsumer.PayloadUnmarshaller[String] = _.decodeString("utf-8")

val configuration = AmqpConfiguration(/* some connection configuration */)
val consumer = AmqpConsumer(configuration)

consumer.source.map { delivery =>
  println(s"Consuming ${delivery.body}...")
  delivery
}.runWith(consumer.sink).flatMap { _ =>
  consumer.shutdown() // closes the connection and cleanups the resources
}

```

## How to add it to your project
The library is available both for Scala 2.11.x and 2.12.x. All you have to do is to add the dependency:
```scala
"com.holidaycheck" %% "amqp-akka-streams" % "1.3.1"
```